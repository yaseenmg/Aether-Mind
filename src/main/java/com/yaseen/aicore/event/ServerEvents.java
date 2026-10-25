package com.yaseen.aicore.event;

import com.yaseen.aicore.Config;
import com.yaseen.aicore.entity.CompanionAttributes;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.core.CompanionRuntime;
import com.yaseen.aicore.task.TaskManager;
import com.yaseen.aicore.advanced.ThreatMonitor;
import com.yaseen.aicore.autonomy.AutonomyController;
import com.yaseen.aicore.entity.ModEntities;
import com.yaseen.aicore.memory.MemoryStore;
import com.yaseen.aicore.state.CompanionWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public final class ServerEvents {
    private static final String COMPANION_UUID = "aicore_companion_uuid";
    private static final String PENDING_RESPAWN = "aicore_companion_pending_respawn";
    private static final String RESPAWN_INVENTORY = "aicore_companion_respawn_inventory";

    /** Forge-persisted sub-tag: survives death/respawn clones, unlike raw root keys. */
    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void rememberRespawnPoint(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) return;
        var data = CompanionWorldData.get(player.serverLevel());
        var dimension = player.getRespawnDimension();
        var level = player.server.getLevel(dimension);
        var pos = player.getRespawnPosition();
        if (level == null) level = player.serverLevel();
        if (pos == null) pos = level.getSharedSpawnPos();
        data.saveRespawnPoint(player.getUUID(), dimension.location(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void recordCompanionDeath(CompanionEntity dead) {
        if (dead == null || dead.level().getServer() == null || dead.getOwner() == null) return;
        var server = dead.level().getServer();
        var overworld = server.overworld();
        var data = CompanionWorldData.get(overworld);
        UUID ownerId = dead.getOwner();
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null) rememberRespawnPoint(owner);
        else if (!data.getRecordCopy(ownerId).contains("dimension", 8))
            data.saveRespawnPoint(ownerId, net.minecraft.world.level.Level.OVERWORLD.location(), overworld.getSharedSpawnPos().getX(), overworld.getSharedSpawnPos().getY(), overworld.getSharedSpawnPos().getZ());

        // The companion's items have already been dropped by CompanionEntity.die().
        // Do NOT serialize them into a respawn snapshot: doing so caused the old bug
        // where items appeared again after the companion returned.
        data.clearInventory(ownerId);
        data.markPending(ownerId);
        if (owner != null) {
            CompoundTag playerData = persisted(owner);
            if (playerData.hasUUID(COMPANION_UUID) && playerData.getUUID(COMPANION_UUID).equals(dead.getUUID())) playerData.remove(COMPANION_UUID);
        }
    }

    public static void rememberPlayerRespawn(ServerPlayer player) { rememberRespawnPoint(player); }

    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.COMPANION.get(), CompanionAttributes.create().build());
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof CompanionEntity c) {
            c.getCombatLearning().damageTaken(e.getAmount());
            // Retaliate only against a real hostile attacker. A neutral animal or another
            // harmless LivingEntity must never become an enemy merely because damage occurred.
            net.minecraft.world.entity.LivingEntity attacker = resolveAttacker(e);
            if (isHostileAttacker(c, attacker)) {
                TaskManager.cancelCurrentTask(c);
                c.setFriendlyCombat(false);
                c.setFollowEnabled(false);
                c.setTarget(attacker);
            }
        }
        if (e.getSource().getEntity() instanceof CompanionEntity c) c.getCombatLearning().damageDealt(e.getAmount());

        // If a hostile mob attacks the owner, the companion may decide that the threat is
        // relevant and defend the owner even when no bodyguard command was issued.
        if (e.getEntity() instanceof ServerPlayer owner) {
            net.minecraft.world.entity.LivingEntity attacker = resolveAttacker(e);
            CompanionEntity c = findOwnedCompanion(owner.server, owner.getUUID());
            boolean hostile = attacker != null && (attacker instanceof net.minecraft.world.entity.monster.Monster
                    || (attacker instanceof Mob mob && mob.getTarget() == owner));
            // Only intervene when the companion is close enough to realistically reach the
            // fight. Without this, a companion hundreds of blocks away dropped its current
            // task and pulled the mob's aggro onto itself even though it could never path
            // there, leaving the owner's attacker effectively disengaged and the companion
            // sprinting uselessly across the world.
            boolean reachable = c != null && attacker != null
                    && c.distanceToSqr(attacker) <= 32.0D * 32.0D;
            if (c != null && c.level() == owner.level() && attacker != null && attacker != c && hostile && reachable) {
                TaskManager.cancelCurrentTask(c);
                // The mob must actually choose the companion as its target too.
                // Previously only the companion's target was changed, so monsters
                // kept attacking the player (or stopped entirely) and never fought
                // the companion as a real combatant.
                if (attacker instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setTarget(c);
                }
                c.setTarget(attacker);
                c.setFriendlyCombat(false);
                c.setFollowEnabled(false);
            }
        }
    }

    private static net.minecraft.world.entity.LivingEntity resolveAttacker(LivingHurtEvent e) {
        net.minecraft.world.entity.Entity source = e.getSource().getEntity();
        if (source instanceof net.minecraft.world.entity.LivingEntity living) return living;
        net.minecraft.world.entity.Entity direct = e.getSource().getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof net.minecraft.world.entity.LivingEntity living) return living;
        return null;
    }

    private static boolean isHostileAttacker(CompanionEntity c, net.minecraft.world.entity.LivingEntity attacker) {
        if (attacker == null || attacker == c || !attacker.isAlive() || attacker instanceof Player) return false;
        if (attacker instanceof net.minecraft.world.entity.monster.Monster) return true;
        if (attacker instanceof Mob mob) {
            Player owner = c.getOwnerPlayer();
            return mob.getTarget() == c || (owner != null && mob.getTarget() == owner);
        }
        return false;
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent e) {
        if (e.getSource().getEntity() instanceof CompanionEntity c) c.getCombatLearning().win();
        if (e.getEntity() instanceof CompanionEntity dead) recordCompanionDeath(dead);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        int tick = event.getServer().getTickCount();
        CompanionRuntime.tick(event.getServer());
        if (tick % 10 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (tick % 600 == 0) MemoryStore.save(player);
            if (tick % 40 == 0) rememberRespawnPoint(player);
            CompanionEntity companion = findOwnedCompanion(event.getServer(), player.getUUID());
            if (companion == null) {
                if (CompanionWorldData.get(event.getServer().overworld()).isPending(player.getUUID()) && player.isAlive()) spawnPendingCompanion(player);
                continue;
            }
            // The new CompanionRuntime owns reflexes and execution. Legacy autonomy/threat loops are no longer ticked here.
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MemoryStore.load(player);
        com.yaseen.aicore.agent.AgentEngine.warmUp();
        if (!Config.AUTO_SPAWN.get()) return;

        CompanionEntity existing = findOwnedCompanion(player.server, player.getUUID());
        if (existing != null) return;
        CompoundTag data = persisted(player);
        if (CompanionWorldData.get(player.server.overworld()).isPending(player.getUUID()) || data.getBoolean(PENDING_RESPAWN)) {
            // Refresh the player's current bed/world-spawn first. This matters when the
            // bed was changed shortly before the companion died while the player was away.
            rememberRespawnPoint(player);
            spawnPendingCompanion(player);
            return;
        }
        // A stored UUID means a companion exists but is in an unloaded chunk/dimension: do not duplicate it.
        if (data.hasUUID(COMPANION_UUID)) return;
        CompanionEntity companion = spawnFor(player);
        if (companion != null) companion.say("وصلت. أنا معك الآن.");
    }


    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CompanionWorldData.get(player.server.overworld()).isPending(player.getUUID()) && !persisted(player).getBoolean(PENDING_RESPAWN)) return;
        rememberRespawnPoint(player);
        player.server.execute(() -> spawnPendingCompanion(player));
    }
    /** Creates a fresh companion next to the player and records its UUID. */
    public static CompanionEntity spawnFor(ServerPlayer player) {
        return spawnAt(player, player.serverLevel(), player.getX() + 2.0, player.getY(), player.getZ() + 2.0, player.getYRot());
    }

    /** Spawn at the same respawn point Minecraft will use for this player. */
    public static CompanionEntity spawnAtRespawnPoint(ServerPlayer player) {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension = player.getRespawnDimension();
        var level = player.server.getLevel(dimension);
        if (level == null) level = player.serverLevel();

        net.minecraft.core.BlockPos pos = player.getRespawnPosition();
        if (pos == null) pos = level.getSharedSpawnPos();

        net.minecraft.core.BlockPos safe = findSafeSpawn(level, pos);
        return spawnAt(player, level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot());
    }

    public static CompanionEntity spawnPendingCompanion(ServerPlayer player) {
        var worldData = CompanionWorldData.get(player.server.overworld());
        UUID ownerId = player.getUUID();
        CompoundTag legacy = persisted(player);
        boolean pending = worldData.isPending(ownerId) || legacy.getBoolean(PENDING_RESPAWN);
        if (!pending) return null;
        if (findOwnedCompanion(player.server, ownerId) != null) {
            worldData.clearPending(ownerId);
            legacy.putBoolean(PENDING_RESPAWN, false);
            legacy.remove(RESPAWN_INVENTORY);
            return null;
        }
        if (!worldData.isPending(ownerId)) {
            rememberRespawnPoint(player);
            worldData.clearInventory(ownerId);
            worldData.markPending(ownerId);
        }
        var dimId = worldData.dimension(ownerId);
        var level = dimId == null ? player.serverLevel() : player.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimId));
        if (level == null) level = player.serverLevel();
        var origin = new net.minecraft.core.BlockPos((int) worldData.x(ownerId), (int) worldData.y(ownerId), (int) worldData.z(ownerId));
        var safe = findSafeSpawn(level, origin);
        CompanionEntity companion = spawnAt(player, level, safe.getX() + .5, safe.getY(), safe.getZ() + .5, player.getYRot());
        if (companion == null) return null;
        worldData.clearPending(ownerId);
        legacy.putBoolean(PENDING_RESPAWN, false);
        legacy.remove(RESPAWN_INVENTORY);
        companion.say("رجعت إلى نقطة سبونك ومعي أغراضي.");
        return companion;
    }


    private static net.minecraft.core.BlockPos findSafeSpawn(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos origin) {
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                net.minecraft.core.BlockPos base = origin.offset(dx, 0, dz);
                for (int dy = -1; dy <= 2; dy++) {
                    net.minecraft.core.BlockPos feet = base.above(dy);
                    net.minecraft.core.BlockPos head = feet.above();
                    if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
                    if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) continue;
                    if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty()) continue;
                    if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) continue;
                    var box = new net.minecraft.world.phys.AABB(feet.getX() + .1, feet.getY(), feet.getZ() + .1, feet.getX() + .9, feet.getY() + 1.8, feet.getZ() + .9);
                    if (!level.getEntities((net.minecraft.world.entity.Entity) null, box, e -> e.isAlive()).isEmpty()) continue;
                    return feet;
                }
            }
        }
        return origin.above();
    }

    private static CompanionEntity spawnAt(ServerPlayer owner, net.minecraft.server.level.ServerLevel level,
                                           double x, double y, double z, float yaw) {
        CompanionEntity companion = ModEntities.COMPANION.get().create(level);
        if (companion == null) return null;
        companion.setOwner(owner.getUUID());
        companion.moveTo(x, y, z, yaw, 0.0f);
        level.addFreshEntity(companion);
        persisted(owner).putUUID(COMPANION_UUID, companion.getUUID());
        return companion;
    }

    /** True if the player has a recorded companion UUID (it may just be unloaded). */
    public static boolean hasRecordedCompanion(ServerPlayer player) {
        CompoundTag data = persisted(player);
        if (!data.hasUUID(COMPANION_UUID)) return false;
        UUID id = data.getUUID(COMPANION_UUID);
        for (var level : player.server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity instanceof CompanionEntity c && c.isAlive()) return true;
        }
        data.remove(COMPANION_UUID);
        return false;
    }

    public static boolean hasPendingRespawn(ServerPlayer player) { return persisted(player).getBoolean(PENDING_RESPAWN); }

    public static void forgetCompanion(ServerPlayer player) { persisted(player).remove(COMPANION_UUID); persisted(player).remove(PENDING_RESPAWN); persisted(player).remove(RESPAWN_INVENTORY); }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            rememberRespawnPoint(player);
            MemoryStore.save(player);
            MemoryStore.unload(player);
            ThreatMonitor.clear(player.getUUID());
            AutonomyController.clear(player.getUUID());
        }
    }

    /** Fast lookup by the stored UUID (no scan of every entity). Falls back to a local scan for legacy companions. */
    public static CompanionEntity findOwnedCompanion(net.minecraft.server.MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return null;
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        CompoundTag data = player == null ? null : persisted(player);

        // First use the recorded UUID. This is fast and works across dimensions as long
        // as the entity's chunk is loaded.
        if (data != null && data.hasUUID(COMPANION_UUID)) {
            UUID id = data.getUUID(COMPANION_UUID);
            for (var level : server.getAllLevels()) {
                var entity = level.getEntity(id);
                if (entity instanceof CompanionEntity c && c.isAlive() && owner.equals(c.getOwner())) return c;
            }
        }

        // Robust fallback near the player. This repairs old worlds where the player UUID
        // record was lost or the entity UUID changed, without doing an expensive whole-world scan.
        if (player != null) {
            for (CompanionEntity c : player.serverLevel().getEntitiesOfClass(CompanionEntity.class,
                    player.getBoundingBox().inflate(256.0),
                    e -> e.isAlive() && owner.equals(e.getOwner()))) {
                if (data != null) data.putUUID(COMPANION_UUID, c.getUUID());
                return c;
            }
        }
        if (data != null) data.remove(COMPANION_UUID);
        return null;
    }
}
