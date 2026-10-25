package com.yaseen.aicore.quest;

import com.yaseen.aicore.agent.InternalToolExecutor;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.entity.CompanionEquipmentManager;
import com.yaseen.aicore.task.TaskManager;
import com.yaseen.aicore.security.WorldActionSecurity;
import com.yaseen.aicore.tool.impl.CookFoodTool;
import com.yaseen.aicore.tool.impl.CraftItemTool;
import com.yaseen.aicore.tool.impl.GatherBlockTool;
import com.yaseen.aicore.tool.impl.GatherWoodTool;
import com.yaseen.aicore.tool.impl.PlaceBlockTool;
import com.yaseen.aicore.tool.impl.StructureSearchTool;
import com.yaseen.aicore.advanced.StructureTravelController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Autonomous "beat the game / kill the Ender Dragon" director.
 *
 * <p>It makes the companion behave like a real player: it RE-DERIVES the current quest stage every
 * cycle from the real inventory + current dimension (see {@link #evaluate}), then fires ONE existing
 * in-game tool to make progress (gather wood/stone/ore, craft a table, tools, armour, cook, etc).
 * There is no stored progress counter, so the whole chain survives reloads and deaths.</p>
 *
 * <p>It is deliberately deterministic: the weak local LLM is NOT in this loop, which is why the
 * early/mid game runs reliably without burning tokens. The late game (Nether, blaze rods, pearls,
 * stronghold, End) genuinely needs world exploration that the current tool-set cannot fully
 * automate; for those stages the companion equips its best gear and HONESTLY asks the owner to
 * help/lead, instead of pretending it can solo them.</p>
 */
public final class DragonQuestDirector {
    private DragonQuestDirector() {}

    private static final long ACTION_COOLDOWN = 70L;   // ~3.5s between decisions
    private static final long ASK_COOLDOWN = 400L;     // ~20s between owner nags
    private static final int ASK_AFTER_FAILS = 4;      // retries before we ask for help (early game)
    private static final long ADVANCED_ASK_COOLDOWN = 12000L; // ~10 min between end-game help requests
    private static final int IRON_NEEDED = 20;         // tools + shield + chestplate + helmet
    private static final int ADVANCED_ASK_AFTER_FAILS = 8; // advanced exploration gets several autonomous recovery attempts first

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static final class State {
        boolean active;
        QuestStage lastStage;
        long nextTick;
        QuestStage failStage;
        int fails;
        long lastAsk;
        long waitUntil;      // e.g. while a furnace is cooking
        long lastAnnounce;   // rate-limits the "current stage" chat line
        int dir;             // current tunnel / wander direction (0..3)
        int digSteps;        // tunnel steps spent on the current stage
        String lastReason = "started"; // why the director did/did not act on the last tick (for /aic quest status)
        long busySince;      // game time since the running task last made progress (stuck-task watchdog)
        net.minecraft.world.phys.Vec3 busyPos;
        String busyStatus = "";
        BlockPos strongholdTarget;
        long nextEyeThrow;
        BlockPos tablePos;   // last known position of the crafting table we placed (walk-back fix)
    }

    // Stateless, reusable tool instances.
    private static final GatherWoodTool WOOD = new GatherWoodTool();
    private static final GatherBlockTool GATHER = new GatherBlockTool();
    private static final CraftItemTool CRAFT = new CraftItemTool();
    private static final PlaceBlockTool PLACE = new PlaceBlockTool();
    private static final CookFoodTool COOK = new CookFoodTool();
    private static final StructureSearchTool STRUCTURE = new StructureSearchTool();

    public static void start(CompanionEntity c) {
        State st = STATES.computeIfAbsent(c.getUUID(), k -> new State());
        st.active = true; st.lastStage = null; st.failStage = null; st.fails = 0; st.nextTick = 0; st.lastAsk = 0;
        st.waitUntil = 0; st.dir = 0; st.digSteps = 0; st.strongholdTarget = null; st.nextEyeThrow = 0;
        c.setFollowEnabled(false);
        c.getGoal().start("\u062e\u062a\u0645 \u0627\u0644\u0644\u0639\u0628\u0629",
                "\u0627\u0644\u062a\u0642\u062f\u0651\u0645 \u062a\u0644\u0642\u0627\u0626\u064a\u0627\u064b \u0645\u062b\u0644 \u0644\u0627\u0639\u0628 \u062d\u0642\u064a\u0642\u064a \u062d\u062a\u0649 \u0642\u062a\u0644 \u062a\u0646\u064a\u0646 \u0627\u0644\u0625\u0646\u062f\u0631.",
                c.level().getGameTime());
        c.say("\ud83d\udde1\ufe0f \u0628\u062f\u064a\u062a \u0645\u0647\u0645\u0629 \u062e\u062a\u0645 \u0627\u0644\u0644\u0639\u0628\u0629. \u0631\u0627\u062d \u0623\u062a\u0635\u0631\u0641 \u0645\u062b\u0644 \u0644\u0627\u0639\u0628 \u062d\u0642\u064a\u0642\u064a: \u0623\u062c\u0645\u0639\u060c \u0623\u0635\u0646\u0639\u060c \u0623\u0642\u0627\u062a\u0644\u060c \u0648\u0623\u0637\u0644\u0628 \u0645\u0633\u0627\u0639\u062f\u062a\u0643 \u0648\u0642\u062a \u0627\u0644\u062d\u0627\u062c\u0629.");
    }

    public static void stop(CompanionEntity c) {
        State st = STATES.get(c.getUUID());
        if (st != null) st.active = false;
        if (c.getGoal().active()) c.getGoal().cancel();
        c.setFollowEnabled(true);
        c.say("\u23f9\ufe0f \u0648\u0642\u0651\u0641\u062a \u0645\u0647\u0645\u0629 \u062e\u062a\u0645 \u0627\u0644\u0644\u0639\u0628\u0629.");
    }

    /** Human-readable diagnostics for /aic quest status and the quest_control tool. */
    public static String status(CompanionEntity c) {
        State st = STATES.get(c.getUUID());
        if (st == null || !st.active) return "المهمة غير مفعّلة (قل: ختم اللعبة)";
        String stage;
        try { stage = evaluate(c).arabic(); } catch (Throwable t) { stage = "؟"; }
        return "المرحلة=" + stage + " | آخر حالة: " + st.lastReason
                + " | مهمة حركة=" + TaskManager.isActive(c) + " | متابعة=" + c.isFollowEnabled()
                + " | هدف قتال=" + (c.getTarget() != null && c.getTarget().isAlive());
    }

    public static boolean isActive(CompanionEntity c) {
        State s = STATES.get(c.getUUID());
        return s != null && s.active;
    }

    /** Persist only the on/off flag: the stage itself is always re-derived from the real inventory. */
    public static void save(CompanionEntity c, CompoundTag tag) {
        tag.putBoolean("DragonQuestActive", isActive(c));
        State st = STATES.get(c.getUUID());
        if (st != null && st.strongholdTarget != null) {
            tag.putInt("DragonStrongholdX", st.strongholdTarget.getX());
            tag.putInt("DragonStrongholdY", st.strongholdTarget.getY());
            tag.putInt("DragonStrongholdZ", st.strongholdTarget.getZ());
        }
    }

    public static void load(CompanionEntity c, CompoundTag tag) {
        if (!tag.getBoolean("DragonQuestActive")) return;
        State st = STATES.computeIfAbsent(c.getUUID(), k -> new State());
        st.active = true;
        st.nextTick = 0;
        st.waitUntil = 0;
        if (tag.contains("DragonStrongholdX")) st.strongholdTarget = new BlockPos(tag.getInt("DragonStrongholdX"), tag.getInt("DragonStrongholdY"), tag.getInt("DragonStrongholdZ"));
        c.setFollowEnabled(false);
    }

    public static void clear(CompanionEntity c) { STATES.remove(c.getUUID()); }

    public static void tick(CompanionEntity c) {
        if (c.level().isClientSide()) return;
        State st = STATES.get(c.getUUID());
        if (st == null || !st.active) return;
        if (!(c.getOwnerPlayer() instanceof ServerPlayer owner)) { st.lastReason = "المالك غير متصل/غير محمّل"; return; }
        long now = c.level().getGameTime();
        // Yield to live combat and to any running move/mine task so we never fight the rest of the AI.
        if (c.getTarget() != null && c.getTarget().isAlive()) { st.lastReason = "ينتظر انتهاء القتال"; return; }
        if (TaskManager.isActive(c)) {
            // Stuck-task watchdog: only fires when BOTH the task status text and the companion's position stopped
            // changing for ~30 s (long walks and tunnels keep making progress, so they are never cancelled).
            String taskStatus = TaskManager.status(c);
            net.minecraft.world.phys.Vec3 pos = c.position();
            if (st.busySince == 0L || st.busyPos == null || !taskStatus.equals(st.busyStatus) || pos.distanceToSqr(st.busyPos) > 2.25D) {
                st.busySince = now; st.busyPos = pos; st.busyStatus = taskStatus;
            } else if (now - st.busySince > 600L) {
                TaskManager.cancelCurrentTask(c);
                st.busySince = 0L; st.busyPos = null; st.dir = (st.dir + 1) % 4; st.nextTick = 0L;
                st.lastReason = "ألغيت مهمة حركة/تعدين عالقة (30 ثانية بلا تقدم)";
                return;
            }
            st.lastReason = "ينتظر مهمة حركة/تعدين: " + taskStatus;
            return;
        }
        st.busySince = 0L;
        if (c.isEating() || c.isUsingItem()) { st.lastReason = "يأكل/يستخدم عنصراً"; return; }
        if (now < st.nextTick) return;
        st.nextTick = now + ACTION_COOLDOWN;
        // A tunnel that hit lava/water/a drop (or any blocked dig): turn and try another direction.
        String last = TaskManager.takeLastResult(c);
        if (last != null && last.startsWith("FAILED") && (last.contains("tunnel") || last.contains("fluid") || last.contains("drop_ahead") || last.contains("timeout"))) {
            st.dir = (st.dir + 1) % 4;
        }

        QuestStage stage = evaluate(c);
        if (stage != st.lastStage) {
            st.lastStage = stage;
            if (stage != QuestStage.DONE && now - st.lastAnnounce > 600L) { st.lastAnnounce = now; c.say("\ud83c\udfaf \u0627\u0644\u0645\u0631\u062d\u0644\u0629 \u0627\u0644\u062d\u0627\u0644\u064a\u0629: " + stage.arabic() + "."); }
        }
        if (stage == QuestStage.DONE) {
            st.active = false;
            c.setFollowEnabled(true);
            if (c.getGoal().active()) c.getGoal().complete();
            c.say("\ud83c\udfc6 \u062e\u0644\u0635\u0646\u0627! \u0627\u0644\u062a\u0646\u064a\u0646 \u0645\u0627\u062a \u0648\u062e\u062a\u0645\u0646\u0627 \u0627\u0644\u0644\u0639\u0628\u0629.");
            return;
        }

        if (now < st.waitUntil) { st.lastReason = "ينتظر الفرن"; return; }   // e.g. furnace is busy cooking

        String result = act(c, owner, stage, st, now);
        st.lastReason = stage.name() + " => " + (result == null ? "null" : (result.length() > 110 ? result.substring(0, 110) + "…" : result));
        boolean ok = result != null && (result.startsWith("SUCCESS") || result.startsWith("STARTED")
                || result.startsWith("RESULT") || result.contains("success"));
        // Diagnostic trace (ASCII only): the raw tool result for every quest action, so a stalled
        // stage (e.g. wooden tools never finishing) shows its real cause in latest.txt instead of
        // only the Arabic chat help-asks. Table proximity is logged too, since the 3x3 crafts need
        // a crafting table within 4 blocks.
        com.yaseen.aicore.AICompanion.LOG.info("[AICore][quest] stage={} ok={} tableNearby={} result={}",
                stage.name(), ok, blockNearby(c, Blocks.CRAFTING_TABLE, 4), result);
        if (result != null && result.contains("occupied")) { ok = true; st.waitUntil = now + 120L; } // furnace still busy
        int threshold = stage.isAdvanced() ? ADVANCED_ASK_AFTER_FAILS : ASK_AFTER_FAILS;
        long askCooldown = stage.isAdvanced() ? ADVANCED_ASK_COOLDOWN : ASK_COOLDOWN;
        if (ok) {
            if (st.failStage == stage) { st.failStage = null; st.fails = 0; }
        } else {
            if (st.failStage != stage) { st.failStage = stage; st.fails = 0; }
            st.fails++;
            if (st.fails >= threshold && now - st.lastAsk > askCooldown) {
                st.lastAsk = now;
                askForHelp(c, stage);
            }
        }
    }

    /** Re-derive the current stage from the REAL inventory + dimension (no stored counter). */
    private static QuestStage evaluate(CompanionEntity c) {
        Level lvl = c.level();
        if (lvl.dimension() == Level.END) {
            return dragonAlive(c) ? QuestStage.KILL_DRAGON : QuestStage.DONE;
        }
        // Once the portal has actually formed, stop re-entering the overworld progression.
        // The previous director consumed the eyes and then fell back to NETHER_PREP forever.
        if (lvl.getBlockState(c.blockPosition()).is(Blocks.END_PORTAL)
                || nearbyBlock(c, Blocks.END_PORTAL, 12) != null) {
            return QuestStage.THE_END;
        }
        boolean inNether = lvl.dimension() == Level.NETHER;
        int blazeRods = QuestItems.countContains(c, "blaze_rod");
        int pearls = QuestItems.countContains(c, "ender_pearl");
        int eyes = QuestItems.countContains(c, "ender_eye") + QuestItems.countContains(c, "eye_of_ender");

        if (inNether) {
            if (blazeRods < 2) return QuestStage.NETHER_BLAZE;
            return QuestStage.ENDER_PEARLS;
        }

        boolean hasStonePick = QuestItems.has(c, "stone_pickaxe", 1) || QuestItems.has(c, "iron_pickaxe", 1) || QuestItems.has(c, "diamond_pickaxe", 1);
        boolean hasIronPick = QuestItems.has(c, "iron_pickaxe", 1) || QuestItems.has(c, "diamond_pickaxe", 1);
        boolean hasDiamondGear = QuestItems.has(c, "diamond_pickaxe", 1) && QuestItems.has(c, "diamond_sword", 1);
        boolean tableAround = blockNearby(c, Blocks.CRAFTING_TABLE, 4);
        boolean furnaceAround = blockNearby(c, Blocks.FURNACE, 6);
        int woodUnits = QuestItems.planks(c) + QuestItems.logs(c) * 4;

        // OVERWORLD progression: earliest unmet gate first. Wood is only topped up as far as the
        // next crafts really need (12 units early on, one table's worth afterwards) so the
        // companion does not run back to the surface for trees after every craft.
        if (woodUnits < (hasStonePick ? 4 : 12) && !hasDiamondGear && !tableAround)
            return QuestStage.GET_WOOD;
        if (woodUnits < 12 && !hasStonePick)
            return QuestStage.GET_WOOD;
        // A crafting table must be within 4 blocks for every 3x3 recipe until the diamond gear exists.
        if (!tableAround && !hasDiamondGear)
            return QuestStage.CRAFTING_TABLE;
        if (QuestItems.sticks(c) < 4 && !hasIronPick)
            return QuestStage.STICKS;
        if (!QuestItems.hasSuffix(c, "_pickaxe"))
            return QuestStage.WOODEN_TOOLS;
        if (QuestItems.countContains(c, "cobblestone") < 20 && !hasStonePick)
            return QuestStage.GET_STONE;
        if ((!hasStonePick || (!furnaceAround && QuestItems.countContains(c, "furnace") < 1 && !hasIronPick)))
            return QuestStage.STONE_GEAR;
        if (QuestItems.countContains(c, "coal") < 6 && !QuestItems.has(c, "torch", 8))
            return QuestStage.GET_COAL;
        // Food: keep a small stock of cooked food, hunting if there is none at all.
        if (rawFood(c) + cookedFood(c) < 4 && !hasIronPick)
            return QuestStage.GET_FOOD;
        if (rawFood(c) > 0 && cookedFood(c) < 4 && furnaceAround)
            return QuestStage.GET_FOOD;
        int ironAll = QuestItems.countContains(c, "raw_iron") + QuestItems.countContains(c, "iron_ingot");
        if (ironAll < IRON_NEEDED && !hasIronPick)
            return QuestStage.GET_IRON;
        if (QuestItems.countContains(c, "raw_iron") > 0 && QuestItems.countContains(c, "iron_ingot") < IRON_NEEDED)
            return QuestStage.SMELT_IRON;
        if (!QuestItems.has(c, "iron_pickaxe", 1) && !QuestItems.has(c, "diamond_pickaxe", 1) || !QuestItems.has(c, "iron_sword", 1) && !QuestItems.has(c, "diamond_sword", 1))
            return QuestStage.IRON_GEAR;
        if (QuestItems.countContains(c, "diamond") < 5 && !QuestItems.has(c, "diamond_pickaxe", 1))
            return QuestStage.GET_DIAMONDS;
        if (!QuestItems.has(c, "diamond_pickaxe", 1) || !QuestItems.has(c, "diamond_sword", 1))
            return QuestStage.DIAMOND_GEAR;
        if (eyes >= 12) return QuestStage.STRONGHOLD;
        if (pearls >= 12 && blazeRods >= 2) return QuestStage.EYES_OF_ENDER;
        if (blazeRods >= 2) return QuestStage.ENDER_PEARLS;
        return QuestStage.NETHER_PREP;
    }

    /** Fire ONE existing tool to progress the given stage. Returns the tool's raw result string. */
    private static String act(CompanionEntity c, ServerPlayer owner, QuestStage stage, State st, long now) {
        switch (stage) {
            case GET_WOOD: {
                if (isUnderground(c)) return returnToOwner(c, owner);
                String r = InternalToolExecutor.invoke(c, owner, "gather_wood", "{\"count\":8}", "internal-" + java.util.UUID.randomUUID()).result();
                return r != null && r.startsWith("FAILED") ? wander(c, st) : r;
            }
            case CRAFTING_TABLE: {
                // Walk-away bug fix: if a crafting table we already placed still exists nearby but we
                // drifted out of the 4-block reach, walk BACK to it instead of crafting/placing a
                // brand new one (which previously failed for lack of wood and spammed help-asks).
                if (!blockNearby(c, Blocks.CRAFTING_TABLE, 4)) {
                    BlockPos known = nearbyBlock(c, Blocks.CRAFTING_TABLE, 12);
                    if (known != null) {
                        if (TaskManager.isActive(c) && known.equals(st.tablePos)) {
                            return "STARTED walking_back_to_table target=" + known; // already en route, don't re-path every tick
                        }
                        st.tablePos = known;
                        com.yaseen.aicore.debug.Trace.log("QUEST", "CRAFTING_TABLE walk-back to existing table at {} (self={})", known, c.blockPosition());
                        return TaskManager.move(c, known);
                    }
                }
                if (QuestItems.countContains(c, "crafting_table") < 1) {
                    String crafted = InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"crafting_table\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                    if (crafted == null || !crafted.startsWith("SUCCESS")) return withQuestHint(crafted, "صناعة طاولة العمل");
                }
                // Never place in the same decision as a failed craft. The next tick sees the
                // real inventory and can retry placement without consuming a phantom item.
                if (QuestItems.countContains(c, "crafting_table") < 1) return "FAILED crafting_table_not_in_inventory_after_craft";
                String placed = InternalToolExecutor.invoke(c, owner, "place_block", "{\"block\":\"crafting_table\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                BlockPos np = nearbyBlock(c, Blocks.CRAFTING_TABLE, 4);
                if (np != null) st.tablePos = np;
                com.yaseen.aicore.debug.Trace.log("QUEST", "CRAFTING_TABLE place result={} tablePos={}", com.yaseen.aicore.debug.Trace.clip(placed), st.tablePos);
                return placed;
            }
            case STICKS: {
                if (QuestItems.planks(c) < 2) {
                    String planks = InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"planks\",\"count\":4}", "internal-" + java.util.UUID.randomUUID()).result();
                    if (planks == null || !planks.startsWith("SUCCESS")) return withQuestHint(planks, "صناعة الألواح اللازمة للعصي");
                }
                String sticks = InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"stick\",\"count\":8}", "internal-" + java.util.UUID.randomUUID()).result();
                return sticks == null ? "FAILED stick_craft_returned_null" : sticks;
            }
            case WOODEN_TOOLS: {
                String pick = InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"wooden_pickaxe\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                String axe = InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"wooden_axe\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                String sword = InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"wooden_sword\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                if (QuestItems.hasSuffix(c, "_pickaxe")) return "SUCCESS wooden_tools pick=" + pick + " axe=" + axe + " sword=" + sword;
                return "FAILED wooden_tools pick=" + pick + " axe=" + axe + " sword=" + sword;
            }
            case GET_STONE:
                // "=stone" = exact block id, so the player's stone bricks / slabs are never touched.
                return gatherOrDig(c, owner, st, "=stone", 24, Integer.MIN_VALUE);
            case STONE_GEAR:
                if (!QuestItems.has(c, "stone_pickaxe", 1) && !QuestItems.has(c, "iron_pickaxe", 1))
                    InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"stone_pickaxe\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                if (!QuestItems.has(c, "stone_sword", 1) && !QuestItems.has(c, "iron_sword", 1))
                    InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"stone_sword\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                if (QuestItems.has(c, "furnace", 1) || blockNearby(c, Blocks.FURNACE, 6)) {
                    autoEquipBest(c);
                    return blockNearby(c, Blocks.FURNACE, 6) ? "SUCCESS stone_gear" : placeFurnace(c, owner, st);
                }
                return placeFurnace(c, owner, st);
            case GET_COAL:
                return gatherOrDig(c, owner, st, "coal_ore", 10, 45);
            case GET_FOOD: {
                if (rawFood(c) == 0) {
                    // Villages are a useful real-world resource hub during the beat-the-game run.
                    // Search the generated structure first; if none is close, fall back to hunting.
                    BlockPos village = StructureSearchTool.find(c, "village", 128);
                    if (village != null && c.position().distanceToSqr(Vec3.atCenterOf(village)) > 18 * 18) return TaskManager.move(c, village);
                    return hunt(c, st);
                }
                if (!blockNearby(c, Blocks.FURNACE, 6)) return placeFurnace(c, owner, st);
                String target = countExact(c, "beef") > 0 ? "cooked_beef"
                        : countExact(c, "porkchop") > 0 ? "cooked_porkchop"
                        : countExact(c, "chicken") > 0 ? "cooked_chicken"
                        : countExact(c, "mutton") > 0 ? "cooked_mutton" : "baked_potato";
                String r = InternalToolExecutor.invoke(c, owner, "cook_food", "{\"output\":\"" + target + "\"}", "internal-" + java.util.UUID.randomUUID()).result();
                if (r != null && r.startsWith("STARTED")) st.waitUntil = now + 230L; // ~11 s per item
                return r;
            }
            case GET_IRON:
                return gatherOrDig(c, owner, st, "iron_ore", 12, 16);
            case SMELT_IRON: {
                if (!blockNearby(c, Blocks.FURNACE, 6)) return placeFurnace(c, owner, st);
                String r = InternalToolExecutor.invoke(c, owner, "cook_food", "{\"output\":\"iron_ingot\"}", "internal-" + java.util.UUID.randomUUID()).result();
                if (r != null && r.startsWith("STARTED")) st.waitUntil = now + 230L;
                return r;
            }
            case IRON_GEAR:
                InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"iron_pickaxe\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"iron_sword\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"shield\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"iron_chestplate\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"iron_helmet\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"iron_leggings\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"iron_boots\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                autoEquipBest(c);
                return QuestItems.has(c, "iron_pickaxe", 1) ? "SUCCESS iron_gear" : "FAILED iron_gear missing_materials";
            case GET_DIAMONDS:
                return gatherOrDig(c, owner, st, "diamond_ore", 5, -54);
            case DIAMOND_GEAR:
                InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"diamond_pickaxe\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"diamond_sword\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                autoEquipBest(c);
                return QuestItems.has(c, "diamond_pickaxe", 1) ? "SUCCESS diamond_gear" : "FAILED diamond_gear missing_materials";
            case NETHER_PREP: {
                autoEquipBest(c);
                BlockPos portal = nearbyBlock(c, Blocks.NETHER_PORTAL, 32);
                if (portal != null) return TaskManager.move(c, portal);

                // First exploit a nearby ruined portal instead of asking the owner. Ruined portals
                // are a deterministic world resource and can supply the obsidian needed to finish
                // the portal automatically.
                if (countItem(c, Items.OBSIDIAN) < 10 && c.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    BlockPos ruined = sl.findNearestMapStructure(StructureTags.RUINED_PORTAL, c.blockPosition(), 48, false);
                    if (ruined != null && c.distanceToSqr(ruined.getX(), ruined.getY(), ruined.getZ()) > 10 * 10) {
                        return TaskManager.move(c, ruined);
                    }
                }
                if (countItem(c, Items.OBSIDIAN) < 10) {
                    String obsidian = InternalToolExecutor.invoke(c, owner, "gather_block", "{\"block\":\"=obsidian\",\"count\":10}", "internal-" + java.util.UUID.randomUUID()).result();
                    if (obsidian != null && obsidian.startsWith("STARTED")) return obsidian;
                }
                if (countItem(c, Items.FLINT) < 1 && countItem(c, Items.FLINT_AND_STEEL) < 1) {
                    String gravel = InternalToolExecutor.invoke(c, owner, "gather_block", "{\"block\":\"=gravel\",\"count\":12}", "internal-" + java.util.UUID.randomUUID()).result();
                    if (gravel != null && gravel.startsWith("STARTED")) return gravel;
                }
                if (countItem(c, Items.FLINT_AND_STEEL) < 1) {
                    String lighter = InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"flint_and_steel\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
                    if (lighter != null && !lighter.startsWith("SUCCESS")) return lighter;
                }
                String built = buildNetherPortal(c, owner);
                if (built.startsWith("SUCCESS")) return built;
                return built;
            }
            case NETHER_BLAZE: {
                autoEquipBest(c);
                LivingEntity blaze = nearestLiving(c, net.minecraft.world.entity.monster.Blaze.class, 64.0D);
                if (blaze != null) {
                    c.setFriendlyCombat(true);
                    c.setTarget(blaze);
                    return "STARTED hunt_blaze";
                }
                BlockPos fortressTarget = StructureSearchTool.find(c, "fortress", 256);
                if (fortressTarget != null && c.position().distanceToSqr(Vec3.atCenterOf(fortressTarget)) > 16 * 16) return TaskManager.move(c, fortressTarget);
                BlockPos fortressBlock = nearestLoadedBlock(c, Blocks.NETHER_BRICKS, 48, 24);
                if (fortressBlock != null) return TaskManager.move(c, fortressBlock);
                // Do not immediately ask the owner. Explore the Nether in deterministic sectors so
                // a naturally spawned blaze can eventually be found.
                return netherSearch(c, owner, st);
            }
            case ENDER_PEARLS: {
                autoEquipBest(c);
                if (QuestItems.countContains(c, "ender_pearl") >= 12) {
                    BlockPos portal = nearbyBlock(c, Blocks.NETHER_PORTAL, 32);
                    return portal == null ? netherSearch(c, owner, st) : TaskManager.move(c, portal);
                }
                LivingEntity enderman = nearestLiving(c, net.minecraft.world.entity.monster.EnderMan.class, 64.0D);
                if (enderman != null) {
                    c.setFriendlyCombat(true);
                    c.setTarget(enderman);
                    return "STARTED hunt_enderman";
                }
                return netherSearch(c, owner, st);
            }
            case EYES_OF_ENDER: {
                String r = InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"ender_eye\",\"count\":12}", "internal-" + java.util.UUID.randomUUID()).result();
                return r == null ? "FAILED craft_ender_eye_null" : r;
            }
            case STRONGHOLD: {
                autoEquipBest(c);
                if (st.strongholdTarget == null) {
                    st.strongholdTarget = StructureSearchTool.find(c, "stronghold", 10000);
                    if (st.strongholdTarget == null) return "FAILED stronghold_not_found";
                    // Long-distance travel is handled by StructureTravelController through the same
                    // search/navigation system used by direct player commands.
                    return StructureTravelController.start(c, st.strongholdTarget, "stronghold");
                }
                if (c.distanceToSqr(st.strongholdTarget.getX(), st.strongholdTarget.getY(), st.strongholdTarget.getZ()) > 18 * 18) {
                    if (!StructureTravelController.active(c)) return StructureTravelController.start(c, st.strongholdTarget, "stronghold");
                    return "STARTED traveling_to_stronghold target=" + st.strongholdTarget;
                }
                return activateEndPortal(c, owner);
            }
            case THE_END: {
                BlockPos portal = nearbyBlock(c, Blocks.END_PORTAL, 16);
                if (portal != null) return TaskManager.move(c, portal);
                // If the portal was just activated but the block is not in the loaded scan yet,
                // return to the last known stronghold area rather than abandoning the quest.
                if (st.strongholdTarget != null) return TaskManager.move(c, st.strongholdTarget);
                return "FAILED end_portal_not_loaded";
            }
            case KILL_DRAGON: {
                autoEquipBest(c);
                LivingEntity dragon = nearestDragon(c);
                if (dragon != null) {
                    // Explicit combat bypasses the fair-fight gate (a dragon always "looks" unfair) but the
                    // retreat-and-heal logic stays active, so it still backs off when nearly dead.
                    c.setFriendlyCombat(true);
                    c.setTarget(dragon);
                    return "STARTED kill_dragon";
                }
                return "WAIT_OWNER";
            }
            default:
                return "SUCCESS";
        }
    }

    private static BlockPos parsePosition(String result) {
        if (result == null) return null;
        int i=result.indexOf("position="); if(i<0) return null;
        int a=i+9, e=result.indexOf(' ',a); if(e<0)e=result.length();
        String[] p=result.substring(a,e).split(","); if(p.length<3)return null;
        try{return new BlockPos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));}catch(Exception ex){return null;}
    }

    /** Preserve the real tool error while making the blocked quest step understandable. */
    private static String withQuestHint(String result, String step) {
        if (result == null || result.isBlank()) return "FAILED " + step + " returned no result";
        return result + " quest_step=" + step;
    }

    /** Craft (topping up cobblestone first if needed) and place a furnace next to the companion. */
    private static String placeFurnace(CompanionEntity c, ServerPlayer owner, State st) {
        if (QuestItems.countContains(c, "furnace") < 1) {
            if (QuestItems.countContains(c, "cobblestone") < 8) return gatherOrDig(c, owner, st, "=stone", 16, Integer.MIN_VALUE);
            InternalToolExecutor.invoke(c, owner, "craft_item", "{\"output\":\"furnace\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
        }
        return InternalToolExecutor.invoke(c, owner, "place_block", "{\"block\":\"furnace\",\"count\":1}", "internal-" + java.util.UUID.randomUUID()).result();
    }

    /** Mine the nearest reachable block; when none is exposed, dig a safe tunnel towards the right depth. */
    private static String gatherOrDig(CompanionEntity c, ServerPlayer owner, State st, String block, int count, int targetY) {
        String r = InternalToolExecutor.invoke(c, owner, "gather_block", "{\"block\":\"" + block + "\",\"count\":" + count + "}", "internal-" + java.util.UUID.randomUUID()).result();
        if (r != null && r.startsWith("STARTED")) { st.digSteps = 0; return r; }
        if (r != null && r.startsWith("DENIED")) return r;
        if (st.digSteps > 160) {      // dug a long way without finding anything: change area
            st.digSteps = 0;
            st.dir = (st.dir + 1) % 4;
            return "FAILED no_" + block + "_found_after_digging";
        }
        // Stone: just dig down until rock is exposed. Ores: go to the right depth first, then tunnel sideways.
        boolean down = targetY == Integer.MIN_VALUE || c.getBlockY() > targetY;
        String t = TaskManager.tunnel(c, st.dir, 6, down);
        if (t != null && t.startsWith("STARTED")) st.digSteps += 6;
        return t;
    }

    private static boolean isUnderground(CompanionEntity c) {
        BlockPos p = c.blockPosition();
        if (!c.level().canSeeSky(p)) {
            // Not under open sky: only treat as "underground" when well below the surface height.
            int surface = c.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
            return p.getY() < surface - 6;
        }
        return false;
    }

    private static String returnToOwner(CompanionEntity c, ServerPlayer owner) {
        if (owner.level() != c.level()) return "FAILED owner_in_other_dimension";
        return TaskManager.move(c, owner.blockPosition());
    }

    /** Walk ~24 blocks in the current direction on the surface to find new trees / animals. */
    private static String wander(CompanionEntity c, State st) {
        BlockPos base = c.blockPosition();
        net.minecraft.core.Direction[] dirs = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST};
        BlockPos probe = base.relative(dirs[st.dir % 4], 24);
        st.dir = (st.dir + 1) % 4;
        if (!c.level().hasChunkAt(probe)) return "FAILED wander_unloaded";
        int y = c.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe.getX(), probe.getZ());
        return TaskManager.move(c, new BlockPos(probe.getX(), y, probe.getZ()));
    }

    /** Hunt the nearest cow/pig/sheep/chicken using explicit combat (the fair-fight gate is skipped). */
    private static String hunt(CompanionEntity c, State st) {
        var animals = c.level().getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,
                c.getBoundingBox().inflate(32.0D),
                a -> a.isAlive() && !a.isBaby() && !a.hasCustomName() && !a.isLeashed()
                        && (a instanceof net.minecraft.world.entity.animal.Cow || a instanceof net.minecraft.world.entity.animal.Pig
                        || a instanceof net.minecraft.world.entity.animal.Sheep || a instanceof net.minecraft.world.entity.animal.Chicken));
        net.minecraft.world.entity.animal.Animal best = null;
        for (var a : animals) if (best == null || c.distanceToSqr(a) < c.distanceToSqr(best)) best = a;
        if (best == null) return wander(c, st);
        c.setFriendlyCombat(true);
        c.setTarget(best);
        return "STARTED hunt target=" + best.getType().builtInRegistryHolder().key().location().getPath();
    }

    private static BlockPos nearestLoadedBlock(CompanionEntity c, Block block, int horizontal, int vertical) {
        BlockPos base = c.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dy = -vertical; dy <= vertical; dy++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > horizontal) continue;
                    BlockPos p = base.offset(dx, dy, dz);
                    if (!c.level().hasChunkAt(p) || !c.level().getBlockState(p).is(block)) continue;
                    double d = c.distanceToSqr(p.getX() + .5, p.getY() + .5, p.getZ() + .5);
                    if (d < bestDist) { bestDist = d; best = p.immutable(); }
                }
            }
        }
        return best;
    }

    private static String netherSearch(CompanionEntity c, ServerPlayer owner, State st) {
        BlockPos base = c.blockPosition();
        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        Direction d = dirs[Math.floorMod(st.dir, dirs.length)];
        BlockPos probe = base.relative(d, 28);
        st.dir = (st.dir + 1) % dirs.length;
        if (c.level().hasChunkAt(probe)) {
            return TaskManager.move(c, new BlockPos(probe.getX(), Math.max(5, probe.getY()), probe.getZ()));
        }
        if (owner != null && owner.level() == c.level()) return TaskManager.move(c, owner.blockPosition());
        return "FAILED nether_search_unloaded";
    }

    private static LivingEntity nearestDragon(CompanionEntity c) {
        if (!(c.level() instanceof net.minecraft.server.level.ServerLevel sl)) return null;
        LivingEntity best = null;
        for (var e : sl.getAllEntities())
            if (e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon d && d.isAlive()
                    && (best == null || c.distanceToSqr(d) < c.distanceToSqr(best))) best = d;
        return best != null && c.distanceToSqr(best) < 80.0D * 80.0D ? best : null;
    }
    private static String buildNetherPortal(CompanionEntity c, ServerPlayer owner) {
        if (countItem(c, Items.OBSIDIAN) < 10) return "FAILED need_obsidian=10";
        if (countItem(c, Items.FLINT_AND_STEEL) < 1) return "FAILED need_flint_and_steel";
        Direction side = Direction.EAST;
        BlockPos p = c.blockPosition().relative(c.getDirection(), 2);
        int y = c.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        BlockPos base = new BlockPos(p.getX(), y, p.getZ());
        java.util.List<BlockPos> frame = new java.util.ArrayList<>();
        for (int yy = 0; yy < 5; yy++) for (int xx = 0; xx < 4; xx++) {
            BlockPos pos = base.relative(side, xx).above(yy);
            boolean edge = (yy == 0 || yy == 4 || xx == 0 || xx == 3);
            boolean corner = (yy == 0 || yy == 4) && (xx == 0 || xx == 3);
            // A valid portal needs only the 10 edge blocks; corners are optional in vanilla.
            // The old code placed all 14 frame blocks but checked/consumed only 10 obsidian,
            // so it either silently over-placed or desynced the inventory. Skip the 4 corners
            // to keep the frame exactly 10 obsidian, matching the requirement check above.
            if (edge && !corner) frame.add(pos);
            else if (!edge && !c.level().getBlockState(pos).canBeReplaced()) return "FAILED portal_space_blocked";
        }
        for (BlockPos pos : frame) if (!WorldActionSecurity.canPlaceBlock(c, owner, pos)) return "DENIED portal_protected";
        for (BlockPos pos : frame) c.level().setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
        removeItem(c, Items.OBSIDIAN, 10);
        for (int yy = 1; yy <= 3; yy++) for (int xx = 1; xx <= 2; xx++) {
            BlockPos pos = base.relative(side, xx).above(yy);
            // The frame is built along side=EAST, so its width runs on the X axis and the
            // portal plane is the X-Y plane: the portal blocks MUST carry AXIS=X. The old
            // AXIS=Z did not match the frame, so on the first neighbour update
            // NetherPortalBlock.updateShape() built a PortalShape along Z, found it
            // incomplete, and replaced every portal block with air -> the portal silently
            // self-destructed and could never be used. AXIS=X matches the EAST-aligned frame.
            c.level().setBlock(pos, Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X), 3);
        }
        removeItem(c, Items.FLINT_AND_STEEL, 1);
        c.getCompanionState().positiveEvent();
        return "SUCCESS nether_portal_built at=" + base;
    }
    private static String throwEye(CompanionEntity c, BlockPos target, State st) {
        if (countItem(c, Items.ENDER_EYE) < 1) return "FAILED need_ender_eye";
        EyeOfEnder eye = new EyeOfEnder(c.level(), c.getX(), c.getEyeY(), c.getZ());
        eye.setItem(new ItemStack(Items.ENDER_EYE)); eye.signalTo(target); c.level().addFreshEntity(eye);
        removeItem(c, Items.ENDER_EYE, 1);
        st.nextEyeThrow = c.level().getGameTime() + 80L;
        c.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return "STARTED threw_ender_eye target=" + target;
    }
    private static String activateEndPortal(CompanionEntity c, ServerPlayer owner) {
        BlockPos frame = nearbyBlock(c, Blocks.END_PORTAL_FRAME, 32);
        if (frame == null) return "FAILED portal_room_not_found_at_stronghold";
        int placed = 0;
        for (int dx = -16; dx <= 16; dx++) for (int dy = -8; dy <= 8; dy++) for (int dz = -16; dz <= 16; dz++) {
            BlockPos pos = frame.offset(dx, dy, dz);
            if (!c.level().getBlockState(pos).is(Blocks.END_PORTAL_FRAME)) continue;
            var state = c.level().getBlockState(pos);
            if (state.getValue(EndPortalFrameBlock.HAS_EYE)) continue;
            if (countItem(c, Items.ENDER_EYE) < 1) return placed > 0 ? "SUCCESS placed_eyes=" + placed : "FAILED need_ender_eye";
            if (!WorldActionSecurity.canPlaceBlock(c, owner, pos)) return "DENIED portal_frame_protected";
            c.level().setBlock(pos, state.setValue(EndPortalFrameBlock.HAS_EYE, true), 3);
            c.level().updateNeighborsAt(pos, Blocks.END_PORTAL_FRAME);
            removeItem(c, Items.ENDER_EYE, 1); placed++;
        }
        return placed > 0 ? "SUCCESS placed_eyes=" + placed : "FAILED no_empty_portal_frames";
    }
    private static int countItem(CompanionEntity c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getCompanionInventory().getContainerSize(); i++) if (c.getCompanionInventory().getItem(i).is(item)) n += c.getCompanionInventory().getItem(i).getCount();
        return n;
    }
    private static void removeItem(CompanionEntity c, net.minecraft.world.item.Item item, int count) {
        for (int i = 0; i < c.getCompanionInventory().getContainerSize() && count > 0; i++) {
            ItemStack s = c.getCompanionInventory().getItem(i); if (!s.is(item)) continue;
            int n = Math.min(count, s.getCount()); s.shrink(n); count -= n;
            if (s.isEmpty()) c.getCompanionInventory().setItem(i, ItemStack.EMPTY);
        }
        c.getCompanionInventory().setChanged();
    }

    private static LivingEntity nearestLiving(CompanionEntity c, Class<? extends LivingEntity> type, double radius) {
        var list = c.level().getEntitiesOfClass(type, c.getBoundingBox().inflate(radius), e -> e.isAlive());
        LivingEntity best = null;
        for (LivingEntity e : list) if (best == null || c.distanceToSqr(e) < c.distanceToSqr(best)) best = e;
        return best;
    }
    private static BlockPos nearbyBlock(CompanionEntity c, Block block, int radius) {
        BlockPos base = c.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) for (int dy = -6; dy <= 6; dy++)
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos p = base.offset(dx, dy, dz);
                if (c.level().hasChunkAt(p) && c.level().getBlockState(p).is(block)) return p;
            }
        return null;
    }

    /** Honest, stage-specific request for owner help (used mainly for the dimensional end game). */
    private static void askForHelp(CompanionEntity c, QuestStage stage) {
        String msg;
        switch (stage) {
            case NETHER_PREP:
                msg = "\u062c\u0647\u0651\u0632\u062a \u0639\u062f\u062a\u064a. \u0645\u062d\u062a\u0627\u062c \u0646\u0628\u0646\u064a \u0628\u0648\u0627\u0628\u0629 \u0646\u0630\u0631 (\u0623\u0648\u0628\u0633\u064a\u062f\u064a\u0627\u0646 + \u0632\u0646\u0627\u062f). \u062a\u0642\u062f\u0631 \u062a\u0633\u0627\u0639\u062f\u0646\u064a \u0646\u0628\u0646\u064a\u0647\u0627 \u0648\u0646\u062f\u062e\u0644 \u0633\u0648\u0629\u061f";
                break;
            case NETHER_BLAZE:
                msg = "\u0625\u062d\u0646\u0627 \u0628\u0627\u0644\u0646\u0630\u0631. \u062e\u0644\u0651\u064a\u0646\u0627 \u0646\u0644\u0642\u0649 \u0642\u0644\u0639\u0629 \u0646\u0630\u0631 \u0648\u0646\u062c\u0645\u0639 \u0639\u0635\u064a \u0628\u0644\u064a\u0632\u060c \u0627\u0628\u0642\u0649 \u0642\u0631\u064a\u0628 \u0645\u0646\u064a.";
                break;
            case ENDER_PEARLS:
                msg = "\u0645\u062d\u062a\u0627\u062c\u064a\u0646 \u0644\u0624\u0644\u0624 \u0625\u0646\u062f\u0631 (\u0635\u064a\u062f \u0625\u0646\u062f\u0631\u0645\u0627\u0646 \u0623\u0648 \u0645\u0642\u0627\u064a\u0636\u0629 \u0628\u0627\u0644\u0630\u0647\u0628). \u0631\u0627\u0641\u0642\u0646\u064a \u0644\u0646\u062c\u0645\u0639\u0647\u0627.";
                break;
            case EYES_OF_ENDER:
                msg = "\u0639\u062f\u0646\u0627 \u0628\u0644\u064a\u0632 \u0648\u0644\u0624\u0644\u0624. \u0631\u0627\u062d \u0623\u0635\u0646\u0639 \u0639\u064a\u0648\u0646 \u0625\u0646\u062f\u0631 \u0648\u0646\u062a\u0628\u0639\u0647\u0627 \u0644\u0644\u062d\u0635\u0646.";
                break;
            case STRONGHOLD:
                msg = "\u0639\u0646\u062f\u064a \u0639\u064a\u0648\u0646 \u0625\u0646\u062f\u0631. \u062e\u0644\u0651\u064a\u0646\u0627 \u0646\u062a\u0628\u0639\u0647\u0627 \u0633\u0648\u0629 \u0644\u0646\u0644\u0642\u0649 \u0627\u0644\u062d\u0635\u0646 \u0648\u0628\u0648\u0627\u0628\u0629 \u0627\u0644\u0646\u0647\u0627\u064a\u0629.";
                break;
            case THE_END:
                msg = "\u0648\u0635\u0644\u0646\u0627 \u0628\u0648\u0627\u0628\u0629 \u0627\u0644\u0646\u0647\u0627\u064a\u0629. \u0646\u062d\u0637 \u0627\u0644\u0639\u064a\u0648\u0646 \u0648\u0646\u062f\u062e\u0644 \u0633\u0648\u0629 \u0644\u0642\u062a\u0644 \u0627\u0644\u062a\u0646\u064a\u0646.";
                break;
            default:
                msg = "\u0639\u0644\u0642\u062a \u0628\u0627\u0644\u0645\u0631\u062d\u0644\u0629: " + stage.arabic() + ". \u0645\u0645\u0643\u0646 \u062a\u0633\u0627\u0639\u062f\u0646\u064a\u061f";
        }
        c.say(msg);   // NOT c.ask(): ask() feeds the text to the LLM as if the player had said it
    }

    // ---------- helpers ----------

    private static boolean blockNearby(CompanionEntity c, Block block, int r) {
        BlockPos base = c.blockPosition();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -3; dy <= 3; dy++)
                for (int dz = -r; dz <= r; dz++)
                    if (c.level().getBlockState(base.offset(dx, dy, dz)).is(block)) return true;
        return false;
    }

    private static boolean dragonAlive(CompanionEntity c) {
        if (!(c.level() instanceof net.minecraft.server.level.ServerLevel sl)) return true;
        for (var e : sl.getAllEntities())
            if (e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon d && d.isAlive()) return true;
        return false;
    }

    private static void autoEquipBest(CompanionEntity c) {
        var inv = c.getCompanionInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) CompanionEquipmentManager.autoEquip(c, s);
        }
    }

    /** Exact-path item count (so "beef" does NOT match "cooked_beef"). */
    private static int countExact(CompanionEntity c, String... paths) {
        var inv = c.getCompanionInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String p = QuestItems.idPath(s.getItem());
            for (String q : paths) if (p.equals(q)) { n += s.getCount(); break; }
        }
        return n;
    }

    private static int rawFood(CompanionEntity c) {
        return countExact(c, "beef", "porkchop", "chicken", "mutton", "potato", "cod", "salmon");
    }

    private static int cookedFood(CompanionEntity c) {
        return countExact(c, "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton",
                "baked_potato", "bread", "cooked_cod", "cooked_salmon");
    }
}
