package com.yaseen.aicore.tool.impl;

import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.quest.DragonQuestDirector;
import com.yaseen.aicore.tool.AITool;
import com.yaseen.aicore.tool.JsonArgs;
import net.minecraft.server.level.ServerPlayer;

/** Lets the LLM start/stop/query the deterministic "beat the game" questline for any phrasing of the request. */
public final class QuestControlTool implements AITool {
    public String name() { return "quest_control"; }
    public String description() {
        return "Start, stop or report the autonomous beat-the-game / kill-the-Ender-Dragon questline "
             + "(gathers wood, crafts, mines, smelts, equips gear, fights). Use action=start when the player asks to finish the game "
             + "or kill the dragon in ANY wording. After start the quest runs by itself; do not call other tools for it.";
    }
    public String schema() { return "{\"action\":\"start|stop|status\"}"; }
    public String execute(CompanionEntity c, ServerPlayer p, String a) {
        String action = JsonArgs.string(a, "action").trim().toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "stop":
                if (!DragonQuestDirector.isActive(c)) return "SUCCESS quest_not_active";
                DragonQuestDirector.stop(c);
                return "SUCCESS quest_stopped";
            case "status":
                return "RESULT " + DragonQuestDirector.status(c);
            case "start":
            default:
                if (DragonQuestDirector.isActive(c)) return "SUCCESS quest_already_active " + DragonQuestDirector.status(c);
                DragonQuestDirector.start(c);
                return "SUCCESS quest_started";
        }
    }
}
