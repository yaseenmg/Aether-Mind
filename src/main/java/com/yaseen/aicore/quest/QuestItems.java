package com.yaseen.aicore.quest;

import com.yaseen.aicore.entity.CompanionEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only helpers over the companion's real 36-slot inventory. The quest director uses these to
 * DERIVE which stage it is on (e.g. "do we already own a stone pickaxe?"), so there is no fragile
 * stored progress counter to drift out of sync with the real items.
 */
public final class QuestItems {
    private QuestItems() {}

    /** Registry path of an item, e.g. "iron_pickaxe". */
    public static String idPath(Item item) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
        return rl == null ? "" : rl.getPath().toLowerCase(Locale.ROOT);
    }

    /** Total count of items whose registry path CONTAINS the keyword (e.g. "log", "iron_ingot"). */
    public static int countContains(CompanionEntity c, String keyword) {
        String k = keyword.toLowerCase(Locale.ROOT);
        Container inv = c.getCompanionInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (idPath(s.getItem()).contains(k)) n += s.getCount();
        }
        return n;
    }

    /** True when the inventory holds at least {@code min} items whose path contains the keyword. */
    public static boolean has(CompanionEntity c, String keyword, int min) {
        return countContains(c, keyword) >= min;
    }

    /** True when the inventory holds any item whose path ENDS WITH the suffix (e.g. "_pickaxe"). */
    public static boolean hasSuffix(CompanionEntity c, String suffix) {
        String k = suffix.toLowerCase(Locale.ROOT);
        Container inv = c.getCompanionInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (idPath(s.getItem()).endsWith(k)) return true;
        }
        return false;
    }

    /** Count of logs of any wood type (so "get wood" works for every tree). */
    public static int logs(CompanionEntity c) { return countContains(c, "_log") + countContains(c, "_stem"); }
    public static int planks(CompanionEntity c) { return countContains(c, "_planks"); }
    public static int sticks(CompanionEntity c) { return countContains(c, "stick"); }

    /**
     * Resolve a user-spoken item name (Arabic or English) to a concrete item_id for the give tool.
     * Returns null when nothing sensible matches, so the caller can fall back to a registry scan.
     */
    public static String resolveGiveId(String spoken) {
        if (spoken == null || spoken.isBlank()) return null;
        String s = spoken.toLowerCase(Locale.ROOT).trim();
        for (Map.Entry<String, String> e : GIVE_WORDS.entrySet()) {
            if (s.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static final Map<String, String> GIVE_WORDS = new LinkedHashMap<>();
    private static void g(String k, String id) { GIVE_WORDS.put(k, id); }
    static {
        g("الماس", "minecraft:diamond"); g("diamond", "minecraft:diamond");
        g("حديد", "minecraft:iron_ingot"); g("iron", "minecraft:iron_ingot");
        g("ذهب", "minecraft:gold_ingot"); g("gold", "minecraft:gold_ingot");
        g("زمرد", "minecraft:emerald"); g("emerald", "minecraft:emerald");
        g("فحم", "minecraft:coal"); g("coal", "minecraft:coal");
        g("خشب", "minecraft:oak_log"); g("wood", "minecraft:oak_log"); g("log", "minecraft:oak_log");
        g("حجر", "minecraft:cobblestone"); g("cobble", "minecraft:cobblestone"); g("stone", "minecraft:cobblestone");
        g("تفاحة ذهب", "minecraft:golden_apple"); g("golden apple", "minecraft:golden_apple");
        g("تفاح", "minecraft:apple"); g("apple", "minecraft:apple");
        g("خبز", "minecraft:bread"); g("bread", "minecraft:bread");
        g("لحم", "minecraft:cooked_beef"); g("steak", "minecraft:cooked_beef"); g("meat", "minecraft:cooked_beef");
        g("سيف", "minecraft:iron_sword"); g("sword", "minecraft:iron_sword");
        g("فأس", "minecraft:iron_pickaxe"); g("pickaxe", "minecraft:iron_pickaxe");
        g("سهم", "minecraft:arrow"); g("arrow", "minecraft:arrow");
        g("مشعل", "minecraft:torch"); g("torch", "minecraft:torch");
        g("لؤلؤ", "minecraft:ender_pearl"); g("pearl", "minecraft:ender_pearl");
    }
}
