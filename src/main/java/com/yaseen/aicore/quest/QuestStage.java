package com.yaseen.aicore.quest;

/**
 * Ordered stages of the "end the game / kill the Ender Dragon" questline. The {@link DragonQuestDirector}
 * does NOT store a mutable stage index; instead it RE-DERIVES the current stage every evaluation from
 * the companion's real inventory + current dimension. That makes the whole questline naturally
 * resumable after a world reload or a death, and it never relies on the weak local model to remember
 * "where we were".
 *
 * <p>Each stage carries a short Arabic label so progress can be announced to the owner in natural
 * language, and an English code used only for logs.</p>
 */
public enum QuestStage {
    GET_WOOD("أجمع الخشب", "get_wood"),
    CRAFTING_TABLE("أصنع طاولة صناعة", "crafting_table"),
    STICKS("أصنع عصي", "sticks"),
    WOODEN_TOOLS("أصنع أدوات خشب", "wooden_tools"),
    GET_STONE("أجمع الحجر", "get_stone"),
    STONE_GEAR("أصنع أدوات حجر وفرن", "stone_gear"),
    GET_COAL("أجمع الفحم", "get_coal"),
    GET_FOOD("أجمع وأطبخ طعام", "get_food"),
    GET_IRON("أجمع الحديد", "get_iron"),
    SMELT_IRON("أصهر الحديد", "smelt_iron"),
    IRON_GEAR("أصنع عدة حديد", "iron_gear"),
    GET_DIAMONDS("أجمع الألماس", "get_diamonds"),
    DIAMOND_GEAR("أصنع عدة ألماس", "diamond_gear"),
    NETHER_PREP("أجهز بوابة النذر", "nether_prep"),
    NETHER_BLAZE("اذهب للنذر واجمع عصي البليز", "nether_blaze"),
    ENDER_PEARLS("اجمع لؤلؤ الإندر", "ender_pearls"),
    EYES_OF_ENDER("اصنع عيون الإندر", "eyes_of_ender"),
    STRONGHOLD("ابحث عن الحصن والبوابة", "stronghold"),
    THE_END("ادخل عالم النهاية", "the_end"),
    KILL_DRAGON("اقتل التنين", "kill_dragon"),
    DONE("ختمت اللعبة", "done");

    private final String arabic;
    private final String code;

    QuestStage(String arabic, String code) { this.arabic = arabic; this.code = code; }

    public String arabic() { return arabic; }
    public String code() { return code; }

    /** True once the companion reaches dimensional/boss stages that genuinely need world exploration. */
    public boolean isAdvanced() { return ordinal() >= NETHER_PREP.ordinal() && this != DONE; }
}
