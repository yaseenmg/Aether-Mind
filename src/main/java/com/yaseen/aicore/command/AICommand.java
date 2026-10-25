package com.yaseen.aicore.command;

import com.yaseen.aicore.agent.InternalToolExecutor;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.event.ServerEvents;
import com.yaseen.aicore.autonomy.AutonomyController;
import com.yaseen.aicore.autonomy.AutonomyState;
import com.yaseen.aicore.advanced.ExplorationController;
import com.yaseen.aicore.advanced.BodyguardController;
import com.yaseen.aicore.tool.impl.StructureSearchTool;
import com.yaseen.aicore.state.ActionPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class AICommand {

    private static CompanionEntity companionOf(ServerPlayer player) {
        return ServerEvents.findOwnedCompanion(player.server, player.getUUID());
    }

    private static void tell(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text));
    }

    /** Parse an Arabic/English mode word into the Mode enum, or null if unknown. */
    private static AutonomyState.Mode parseMode(String raw) {
        if (raw == null) return null;
        switch (raw.trim().toLowerCase()) {
            case "follow": case "متابعة": return AutonomyState.Mode.FOLLOW;
            case "bodyguard": case "حارس": case "حماية": return AutonomyState.Mode.BODYGUARD;
            case "explorer": case "explore": case "استكشاف": return AutonomyState.Mode.EXPLORER;
            case "engineer": case "بناء": return AutonomyState.Mode.ENGINEER;
            case "miner": case "mine": case "تعدين": return AutonomyState.Mode.MINER;
            case "farmer": case "farm": case "زراعة": return AutonomyState.Mode.FARMER;
            case "idle": case "خامل": return AutonomyState.Mode.IDLE;
            default: return null;
        }
    }

    /** Turn ON the deterministic low-level controller that matches the current mode so the
     *  companion acts even when the weak local model never emits tool JSON. */
    private static void activateModeController(CompanionEntity c) {
        switch (c.getAutonomy().mode()) {
            case EXPLORER -> ExplorationController.set(c, true);
            case BODYGUARD -> BodyguardController.set(c, true);
            default -> ExplorationController.set(c, false);
        }
        // Fire the first autonomous decision promptly instead of waiting a full ~30s cycle.
        AutonomyController.clear(c.getUUID());
    }

    private static String modeLabel(AutonomyState.Mode m) {
        switch (m) {
            case FOLLOW: return "متابعة (follow)";
            case BODYGUARD: return "حارس (bodyguard)";
            case EXPLORER: return "استكشاف (explorer)";
            case ENGINEER: return "بناء (engineer)";
            case MINER: return "تعدين (miner)";
            case FARMER: return "زراعة (farmer)";
            case IDLE: return "خامل (idle)";
            default: return m.name();
        }
    }

    private static ActionPermissions.Action parsePermission(String raw) {
        if (raw == null) return null;
        try { return ActionPermissions.Action.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private int applyAutonomy(CommandContext<CommandSourceStack> ctx, String modeArg) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CompanionEntity companion = companionOf(player);
        if (companion == null) { tell(player, "رفيقك غير محمّل الآن."); return 0; }
        boolean on = BoolArgumentType.getBool(ctx, "enabled");
        if (!on) {
            companion.getAutonomy().routine(false);
            ExplorationController.set(companion, false);
            tell(player, "تم إيقاف الاستقلالية.");
            return 1;
        }
        if (modeArg != null) {
            AutonomyState.Mode m = parseMode(modeArg);
            if (m == null) {
                tell(player, "وضع غير معروف. الأوضاع: follow, bodyguard, explorer, engineer, miner, farmer, idle");
                return 0;
            }
            companion.getAutonomy().mode(m);
        }
        companion.getAutonomy().routine(true);
        activateModeController(companion);
        AutonomyState.Mode cur = companion.getAutonomy().mode();
        boolean deterministic = cur == AutonomyState.Mode.EXPLORER || cur == AutonomyState.Mode.BODYGUARD;
        tell(player, "✅ تم تفعيل الاستقلالية | الوضع = " + modeLabel(cur)
                + " — سأبدأ التصرف خلال ثوانٍ ثم أتخذ قراراً جديداً كل ~30 ثانية.");
        if (!deterministic) {
            tell(player, "ℹ هذا الوضع يعتمد على النموذج (LLM). لو كان النموذج ضعيفاً (حتى مع النماذج الضعيفة) قد لا يتصرف بانتظام؛ جرّب وضع explorer أو bodyguard لضمان سلوك فعلي.");
        }
        return 1;
    }

    private int locate(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CompanionEntity companion = companionOf(player);
        if (companion == null) { tell(player, "رفيقك غير محمّل الآن."); return 0; }
        String structure = StringArgumentType.getString(ctx, "structure");
        String r = InternalToolExecutor.invoke(companion, player, "search_structure", "{\"structure\":\"" + structure + "\",\"radius\":" + radius + ",\"go\":true}", "internal-" + java.util.UUID.randomUUID()).result();
        tell(player, r);
        return r.startsWith("SUCCESS") ? 1 : 0;
    }

    @SubscribeEvent
    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("aic")
                        .then(Commands.literal("teach")
                                .then(Commands.argument("definition", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String definition = StringArgumentType.getString(ctx, "definition");
                                            String[] parts = definition.split("\\=>", 2);
                                            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                                                tell(player, "استخدم: /aic teach العبارة => التعليمات");
                                                return 0;
                                            }
                                            CompanionEntity companion = companionOf(player);
                                            if (companion == null) { tell(player, "رفيقك غير محمّل الآن. جرّب /aic respawn إن كان قد مات."); return 0; }
                                            companion.getTeachingMemory().teach(parts[0].trim(), parts[1].trim());
                                            tell(player, "تعلمت الاختصار: " + parts[0].trim());
                                            return 1;
                                        })))
                        .then(Commands.literal("chat")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String message = StringArgumentType.getString(ctx, "message");
                                            CompanionEntity companion = companionOf(player);
                                            if (companion == null) { tell(player, "رفيقك غير محمّل الآن. جرّب /aic respawn إن كان قد مات."); return 0; }
                                            companion.ask(message);
                                            return 1;
                                        })))
                        .then(Commands.literal("ask")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String message = StringArgumentType.getString(ctx, "message");
                                            CompanionEntity companion = companionOf(player);
                                            if (companion == null) { tell(player, "رفيقك غير محمّل الآن. جرّب /aic respawn إن كان قد مات."); return 0; }
                                            companion.ask(message);
                                            return 1;
                                        })))
                        // Autonomy is OFF by default: it calls the LLM on its own every ~30s and may mine/build/farm.
                        // Usage: /aic autonomy <true|false> [mode]  — e.g. /aic autonomy true explorer
                        .then(Commands.literal("autonomy")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity companion = companionOf(player);
                                    if (companion == null) { tell(player, "رفيقك غير محمّل الآن."); return 0; }
                                    tell(player, "الاستقلالية: " + (companion.getAutonomy().routine() ? "مفعّلة ✅" : "متوقفة ❌")
                                            + " | الوضع = " + modeLabel(companion.getAutonomy().mode()));
                                    tell(player, "الاستخدام: /aic autonomy true explorer");
                                    return 1;
                                })
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> applyAutonomy(ctx, null))
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .executes(ctx -> applyAutonomy(ctx, StringArgumentType.getString(ctx, "mode"))))))
                        // Change only the mode (keeps autonomy on/off as-is). e.g. /aic mode explorer
                        .then(Commands.literal("mode")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            CompanionEntity companion = companionOf(player);
                                            if (companion == null) { tell(player, "رفيقك غير محمّل الآن."); return 0; }
                                            AutonomyState.Mode m = parseMode(StringArgumentType.getString(ctx, "mode"));
                                            if (m == null) {
                                                tell(player, "وضع غير معروف. الأوضاع: follow, bodyguard, explorer, engineer, miner, farmer, idle");
                                                return 0;
                                            }
                                            companion.getAutonomy().mode(m);
                                            activateModeController(companion);
                                            tell(player, "تم تغيير الوضع إلى " + modeLabel(m)
                                                    + (companion.getAutonomy().routine() ? "" : " (الاستقلالية متوقفة — شغّلها بـ /aic autonomy true)"));
                                            return 1;
                                        })))
                        // Permission changes are deliberately owner-command-only. The LLM can
                        // inspect permissions, but it cannot silently enable world-changing or
                        // rare-item actions after a prompt/context mistake.
                        .then(Commands.literal("permission")
                                .then(Commands.literal("status").executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity companion = companionOf(player);
                                    if (companion == null) { tell(player, "رفيقك غير محمّل الآن."); return 0; }
                                    tell(player, "الصلاحيات: " + companion.getPermissions().summary());
                                    return 1;
                                }))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("permission", StringArgumentType.word())
                                                .then(Commands.argument("allowed", BoolArgumentType.bool())
                                                        .executes(ctx -> {
                                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                            CompanionEntity companion = companionOf(player);
                                                            if (companion == null) { tell(player, "رفيقك غير محمّل الآن."); return 0; }
                                                            ActionPermissions.Action permission = parsePermission(StringArgumentType.getString(ctx, "permission"));
                                                            if (permission == null) {
                                                                tell(player, "صلاحية غير معروفة: build, mine, farm, trade, craft, consume_rare, drop, destroy");
                                                                return 0;
                                                            }
                                                            boolean allowed = BoolArgumentType.getBool(ctx, "allowed");
                                                            companion.getPermissions().set(permission, allowed);
                                                            tell(player, "تم ضبط " + permission.name().toLowerCase() + " = " + allowed);
                                                            return 1;
                                                        })))))
                        .then(Commands.literal("locate")
                                .then(Commands.argument("structure", StringArgumentType.word())
                                        .executes(ctx -> locate(ctx, 256))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 1000))
                                                .executes(ctx -> locate(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .then(Commands.literal("quest")
                                .then(Commands.literal("start").executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity companion = companionOf(player);
                                    if (companion == null) { tell(player, "رفيقك غير محمّل الآن."); return 0; }
                                    if (!com.yaseen.aicore.quest.DragonQuestDirector.isActive(companion)) com.yaseen.aicore.quest.DragonQuestDirector.start(companion);
                                    return 1;
                                }))
                                .then(Commands.literal("stop").executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity companion = companionOf(player);
                                    if (companion == null) { tell(player, "رفيقك غير محمّل الآن."); return 0; }
                                    if (com.yaseen.aicore.quest.DragonQuestDirector.isActive(companion)) com.yaseen.aicore.quest.DragonQuestDirector.stop(companion);
                                    else tell(player, "المهمة غير مفعّلة.");
                                    return 1;
                                }))
                                .then(Commands.literal("status").executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity companion = companionOf(player);
                                    if (companion == null) { tell(player, "رفيقك غير محمّل الآن."); return 0; }
                                    tell(player, "[Quest] " + com.yaseen.aicore.quest.DragonQuestDirector.status(companion));
                                    return 1;
                                })))
                        .then(Commands.literal("tp")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity companion = companionOf(player);
                                    if (companion == null) { tell(player, "ستيف غير محمّل الآن."); return 0; }
                                    player.teleportTo(companion.getX(), companion.getY(), companion.getZ());
                                    tell(player, "تم الانتقال إلى Steve.");
                                    return 1;
                                }))
                        .then(Commands.literal("diag")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity companion = companionOf(player);
                                    tell(player, companion == null
                                            ? "[AI] الرفيق غير محمّل ❌ (جرّب /aic respawn)"
                                            : "[AI] الرفيق موجود ✅ صحته=" + Math.round(companion.getHealth()) + "/" + Math.round(companion.getMaxHealth()));
                                    com.yaseen.aicore.agent.AgentEngine.diagnose(player);
                                    return 1;
                                }))
                        .then(Commands.literal("respawn")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity existing = companionOf(player);
                                    if (existing != null) { tell(player, "رفيقك موجود بالفعل."); return 0; }
                                    if (ServerEvents.hasPendingRespawn(player)) {
                                        CompanionEntity restored = ServerEvents.spawnPendingCompanion(player);
                                        if (restored != null) { tell(player, "تمت إعادة رفيقك عند نقطة سبونك."); return 1; }
                                    }
                                    // A recorded UUID means the companion is alive but currently unloaded; never create a duplicate.
                                    if (ServerEvents.hasRecordedCompanion(player)) {
                                        tell(player, "الرفيق مسجل لكنه غير محمّل حاليًا. لا يمكن إنشاء نسخة ثانية؛ انتقل إلى بعد/منطقة الرفيق أو أعد تحميل العالم.");
                                        return 0;
                                    }
                                    CompanionEntity fresh = ServerEvents.spawnFor(player);
                                    if (fresh == null) { tell(player, "تعذر إنشاء الرفيق."); return 0; }
                                    fresh.say("عدت. أنا معك الآن.");
                                    return 1;
                                }))
        );
    }

    public AICommand() {}
}
