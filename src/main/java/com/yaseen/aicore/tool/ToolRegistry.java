package com.yaseen.aicore.tool;

import com.yaseen.aicore.tool.impl.InspectInventoryTool;
import com.yaseen.aicore.tool.impl.InspectNearbyEntitiesTool;
import com.yaseen.aicore.tool.impl.InspectLookBlockTool;
import com.yaseen.aicore.tool.impl.LocationTool;
import com.yaseen.aicore.tool.impl.InspectRegistryTool;
import com.yaseen.aicore.tool.impl.RememberTool;
import com.yaseen.aicore.tool.impl.FollowOwnerTool;
import com.yaseen.aicore.tool.impl.MoveToTool;
import com.yaseen.aicore.tool.impl.MineBlockTool;
import com.yaseen.aicore.tool.impl.TaskStatusTool;
import com.yaseen.aicore.tool.impl.InspectCompanionInventoryTool;
import com.yaseen.aicore.tool.impl.InspectContainerTool;
import com.yaseen.aicore.tool.impl.DepositItemTool;
import com.yaseen.aicore.tool.impl.TakeFromContainerTool;
import com.yaseen.aicore.tool.impl.FindNearbyContainerTool;
import com.yaseen.aicore.tool.impl.InspectRecipesTool;
import com.yaseen.aicore.tool.impl.CraftItemTool;
import com.yaseen.aicore.tool.impl.SmeltItemTool;
import com.yaseen.aicore.tool.impl.InspectEquipmentTool;
import com.yaseen.aicore.tool.impl.InspectEnergyTool;
import com.yaseen.aicore.tool.impl.CombatStatusTool;
import com.yaseen.aicore.tool.impl.InspectVillagerTradesTool;
import com.yaseen.aicore.tool.impl.TradeWithVillagerTool;
import com.yaseen.aicore.tool.impl.SmartVeinMiningTool;
import com.yaseen.aicore.tool.impl.BuildPatternTool;
import com.yaseen.aicore.tool.impl.BuildBlueprintTool;
import com.yaseen.aicore.tool.impl.WorldMemoryTool;
import com.yaseen.aicore.tool.impl.GoalTool;
import com.yaseen.aicore.tool.impl.BodyguardTool;
import com.yaseen.aicore.tool.impl.ExploreTool;
import com.yaseen.aicore.tool.impl.StructureSearchTool;
import com.yaseen.aicore.tool.impl.BaseSiteTool;
import com.yaseen.aicore.tool.impl.RescueStatusTool;
import com.yaseen.aicore.tool.impl.SmartNavigateTool;
import com.yaseen.aicore.tool.impl.ResourceLedgerTool;
import com.yaseen.aicore.tool.impl.ReserveResourceTool;
import com.yaseen.aicore.tool.impl.LoadoutTool;
import com.yaseen.aicore.tool.impl.MaintenanceTool;
import com.yaseen.aicore.tool.impl.AutonomyTool;
import com.yaseen.aicore.tool.impl.TeamTool;
import com.yaseen.aicore.tool.impl.DailyReportTool;
import com.yaseen.aicore.tool.impl.PlanAlternativeTool;
import com.yaseen.aicore.tool.impl.FarmAreaTool;
import com.yaseen.aicore.tool.impl.GatherWoodTool;
import com.yaseen.aicore.tool.impl.GatherBlockTool;
import com.yaseen.aicore.tool.impl.PlaceBlockTool;
import com.yaseen.aicore.tool.impl.LightAreaTool;
import com.yaseen.aicore.tool.impl.CookFoodTool;
import com.yaseen.aicore.tool.impl.TillAndPlantTool;
import com.yaseen.aicore.tool.impl.GatherResourcesTool;
import com.yaseen.aicore.tool.impl.CombatTargetTool;
import com.yaseen.aicore.tool.impl.GiveItemTool;
import com.yaseen.aicore.tool.impl.ExperimentTool;
import com.yaseen.aicore.tool.impl.PermissionTool;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolRegistry {
    private static final Map<String, AITool> TOOLS = new LinkedHashMap<>();
    static {
        register(new InspectInventoryTool());
        register(new com.yaseen.aicore.tool.impl.QuestControlTool());
        register(new InspectNearbyEntitiesTool());
        register(new InspectLookBlockTool());
        register(new LocationTool());
        register(new InspectRegistryTool());
        register(new RememberTool());
        register(new FollowOwnerTool());
        register(new MoveToTool());
        register(new MineBlockTool());
        register(new TaskStatusTool());
        register(new InspectCompanionInventoryTool());
        register(new InspectContainerTool());
        register(new DepositItemTool());
        register(new TakeFromContainerTool());
        register(new FindNearbyContainerTool());
        register(new InspectRecipesTool());
        register(new CraftItemTool());
        register(new SmeltItemTool());
        register(new InspectEquipmentTool());
        register(new InspectEnergyTool());
        register(new CombatStatusTool());
        register(new InspectVillagerTradesTool());
        register(new TradeWithVillagerTool());
        register(new SmartVeinMiningTool());
        register(new BuildPatternTool());
        register(new BuildBlueprintTool());
        register(new WorldMemoryTool());
        register(new GoalTool());
        register(new BodyguardTool());
        register(new ExploreTool());
        register(new StructureSearchTool());
        register(new BaseSiteTool());
        register(new RescueStatusTool());
        register(new SmartNavigateTool());
        register(new ResourceLedgerTool());
        register(new ReserveResourceTool());
        register(new LoadoutTool());
        register(new MaintenanceTool());
        register(new AutonomyTool());
        register(new TeamTool());
        register(new DailyReportTool());
        register(new PlanAlternativeTool());
        register(new FarmAreaTool());
        register(new GatherWoodTool());
        register(new GatherBlockTool());
        register(new PlaceBlockTool());
        register(new LightAreaTool());
        register(new CookFoodTool());
        register(new TillAndPlantTool());
        register(new GatherResourcesTool());
        register(new CombatTargetTool());
        register(new GiveItemTool());
        register(new ExperimentTool());
        register(new PermissionTool());
    }

    private static void register(AITool tool) { TOOLS.put(tool.name(), tool); }
    /**
     * Arabic and colloquial aliases. Built into a plain map so a duplicate key is tolerated
     * (last definition wins) instead of crashing the whole mod at class-load time: the old
     * Map.ofEntries() throws IllegalArgumentException on any duplicate, which turned a harmless
     * alias typo into a fatal error that prevented the game from starting.
     */
    private static final Map<String, String> ALIASES = buildAliases();
    private static Map<String, String> buildAliases() {
        Map<String, String> m = new java.util.HashMap<>();
        m.put("قاتل", "combat_target");
        m.put("هاجم", "combat_target");
        m.put("حارب", "combat_target");
        m.put("قاتلني", "combat_target");
        m.put("قاتل_زومبي", "combat_target");
        m.put("دافع_عني", "bodyguard_mode");
        m.put("اتركه", "combat_target");
        m.put("توقف", "combat_target");
        m.put("توقف_عن_القتال", "combat_target");
        m.put("بارزني", "combat_target");
        m.put("تعال_نتبارز", "combat_target");
        m.put("تعال", "follow_owner");
        m.put("تعال_عندي", "follow_owner");
        m.put("اجمع_خشب", "gather_wood");
        m.put("اقطع_الشجرة", "gather_wood");
        m.put("جمع_خشب", "gather_wood");
        m.put("mine", "mine_block");
        m.put("اعطني", "give_item_to_owner");
        m.put("اعطيني", "give_item_to_owner");
        m.put("اعطني_الماس", "give_item_to_owner");
        m.put("اعطيني_الماس", "give_item_to_owner");
        m.put("اعطني_ماس", "give_item_to_owner");
        m.put("اعطيني_ماس", "give_item_to_owner");
        m.put("give_me", "give_item_to_owner");
        m.put("explore", "explore_mode");
        m.put("استكشف", "explore_mode");
        m.put("ابحث_عن_قرية", "search_structure");
        m.put("ابحث_عن_حصن", "search_structure");
        m.put("ابحث_عن_دنجن", "search_structure");
        m.put("ابحث_عن_قلعة", "search_structure");
        m.put("ابحث_عن_سترونغهولد", "search_structure");
        m.put("ابحث_عن_منجم", "search_structure");
        m.put("loadout", "prepare_loadout");
        m.put("تجهيز_العتاد", "prepare_loadout");
        m.put("world_memory", "inspect_world_memory");
        m.put("ذاكرة_العالم", "inspect_world_memory");
        return java.util.Collections.unmodifiableMap(m);
    }

    public static AITool get(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        AITool direct = TOOLS.get(trimmed);
        if (direct != null) return direct;
        String mapped = ALIASES.get(trimmed.toLowerCase(java.util.Locale.ROOT));
        return mapped == null ? null : TOOLS.get(mapped);
    }
    public static Collection<AITool> all() { return TOOLS.values(); }

    /** Compact tool surface for small local models. Keeping irrelevant tools out of the prompt
     * materially reduces prompt evaluation and makes tool selection much more reliable. */
    public static String definitionsFor(String message) {
        String s = com.yaseen.aicore.ai.InputNormalizer.normalize(message == null ? "" : message).toLowerCase(java.util.Locale.ROOT);
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا');
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        if (s.matches(".*(قرية|قرى|دنجن|دنجنات|بنايه|بناية|بنايات|مبنى|منجم|مناجم|سترونغهولد|قلعه|قلعة|حصن|حصون|معبد|هرم|مدينة قديمه|مدينة قديمة|قصر|سفينه|سفينة|بوابه مدمره|بوابة مدمرة|غرف التجارب|trial|structure|village|stronghold|dungeon).*")) {
            names.add("search_structure"); names.add("smart_navigate"); names.add("inspect_world_memory");
        }
        if (s.matches(".*(قرية|قرى|دنجن|دنجنات|حصن|قلعة|قلعه|باستيون|مدينة قديمة|مدينة النهاية|قصر|معبد|سفينة|حطام|بوابة مدمرة|برج نهب|منجم|مناجم|بنايات|بنايات|هياكل|structure|structures|village|stronghold|mineshaft|dungeon|fortress|bastion|end city|ancient city|mansion|monument|temple|shipwreck|ruined portal|outpost).*")) {
            names.add("search_structure"); names.add("smart_navigate"); names.add("inspect_world_memory");
        }
        if (s.matches(".*(وين|مكان|موقع|اين|أين|حولك|حولك|المكان|إحداث|احداث|coordinate|location|where).*")) {
            names.add("describe_location"); names.add("inspect_nearby_entities"); names.add("search_structure"); names.add("inspect_world_memory");
        }
        if (s.matches(".*(قاتل|اقتل|هاجم|عدو|وحش|زومبي|سكيليتون|قتال|بارز|دافع|احمني|احميني|حمايه|حماية).*")) {
            names.add("combat_target"); names.add("combat_status"); names.add("bodyguard_mode"); names.add("inspect_equipment"); names.add("inspect_nearby_entities");
        }
        if (s.matches(".*(خشب|شجر|اجمع|جمع|معدن|خام|كسر|اكسر|عدن|تعدين|mine|gather).*")) {
            names.add("gather_wood"); names.add("mine_block"); names.add("smart_vein_mining"); names.add("inspect_look_block"); names.add("inspect_companion_inventory");
        }
        if (s.matches(".*(شنطة|حقيبة|مخزون|اغراض|أغراض|درع|سلاح|قوس|سيف|ادوات|أدوات|ارمي|ارم|تخلص|احتاج|احتياجات).*")) {
            names.add("inspect_companion_inventory"); names.add("inspect_equipment"); names.add("prepare_loadout");
        }
        if (s.matches(".*(ابني|بناء|بيت|build).*")) {
            names.add("build_pattern"); names.add("build_blueprint"); names.add("inspect_companion_inventory");
        }
        if (s.matches(".*(تعال|اتبع|وقف|خلك|تحرك|اذهب|روح|مكانك|استكشف|استكشاف).*")) {
            names.add("follow_owner"); names.add("move_to"); names.add("smart_navigate"); names.add("explore_mode");
        }
        if (s.matches(".*(اعطني|اعطيني|اعطي|خذ|هات|اعط).*")) {
            names.add("inspect_companion_inventory"); names.add("give_item_to_owner");
        }
        if (s.matches(".*(اصنع|صنع|صناعه|صناعة|كرافت|craft|وصفه|وصفة|recipe|طاولة|فرن|صهر|اذب|اذابة|smelt|طبخ|اطبخ).*")) {
            names.add("inspect_recipes"); names.add("craft_item"); names.add("smelt_item");
            names.add("find_nearby_container"); names.add("inspect_companion_inventory");
        }
        if (s.matches(".*(صندوق|صناديق|حاويه|حاوية|خزن|خزنه|خزنة|مخزن|deposit|take|اسحب|خذ من الصندوق|حط بالصندوق).*")) {
            names.add("find_nearby_container"); names.add("inspect_container");
            names.add("take_from_container"); names.add("deposit_item");
        }
        if (s.matches(".*(تاجر|تجاره|تجارة|قروي|villager|بيع|شراء|تبادل|trade).*")) {
            names.add("inspect_villager_trades"); names.add("trade_with_villager");
        }
        if (s.matches(".*(مزرعه|مزرعة|زرع|محصول|حصاد|farm|ازرع|اسقي).*")) {
            names.add("farm_area"); names.add("inspect_companion_inventory");
        }
        // Compound/colloquial requests often contain no exact noun covered by the
        // filters above (for example: "ساعدني أجهز نفسي"). Give the planner a bounded,
        // executable action surface instead of diagnostics only.
        if (s.matches(".*(ساعد|نفذ|نفّذ|سوي|سو|اريد|أريد|خلي|خليني|جهز|جهّز|ممكن|please|help|do|prepare).*")) {
            names.add("inspect_companion_inventory"); names.add("inspect_equipment");
            names.add("inspect_recipes"); names.add("craft_item"); names.add("smelt_item");
            names.add("gather_wood"); names.add("gather_block"); names.add("mine_block");
            names.add("smart_vein_mining"); names.add("place_block"); names.add("farm_area");
            names.add("gather_resources"); names.add("cook_food"); names.add("follow_owner");
            names.add("smart_navigate"); names.add("combat_target"); names.add("prepare_loadout");
            names.add("task_status"); names.add("quest_control");
        }
        if (s.matches(".*(هدف|هدف طويل|خطه|خطة|plan|goal|استقلال|autonom|روتين|routine|موارد|مورد|resource).*")) {
            names.add("manage_long_term_goal"); names.add("inspect_resource_ledger");
            names.add("reserve_resource"); names.add("manage_autonomy");
        }
        if (s.matches(".*(فريق|فريقنا|رفيق|team|صديق|مرافق|ساعدني).*")) {
            names.add("manage_companion_team"); names.add("bodyguard_mode"); names.add("follow_owner");
        }
        if (s.contains("[autonomous_cycle]") || s.matches(".*(استقلال|autonomous|دورة مستقلة|مهمة مفيدة|راقب العالم).*")) {
            // Autonomous cycles need real action tools, not only diagnostic tools. This is
            // intentionally broader than normal chat but still excludes destructive/admin tools.
            names.add("describe_location"); names.add("inspect_nearby_entities");
            names.add("inspect_companion_inventory"); names.add("inspect_equipment");
            names.add("follow_owner"); names.add("smart_navigate"); names.add("explore_mode");
            names.add("gather_wood"); names.add("mine_block"); names.add("smart_vein_mining");
            names.add("farm_area"); names.add("craft_item"); names.add("smelt_item");
            names.add("inspect_recipes"); names.add("find_nearby_container");
            names.add("inspect_container"); names.add("take_from_container"); names.add("deposit_item");
            names.add("prepare_loadout"); names.add("inspect_base_maintenance"); names.add("bodyguard_mode");
            names.add("manage_long_term_goal"); names.add("inspect_world_memory");
            names.add("inspect_resource_ledger"); names.add("reserve_resource");
        }
        if (s.matches(".*(ختم|ختمة|تنين|dragon|quest|لعبه|لعبة|ماينكرافت|ماين كرافت|minecraft|beat|finish|مهمه|مهمة|وصلت).*")) {
            names.add("quest_control"); names.add("task_status");
        }
        // Ambiguous requests / chit-chat: diagnostics PLUS the core action tools. Before, an unmatched sentence got
        // only read-only tools, so the model could not DO anything and just answered "I don't understand".
        if (names.isEmpty()) {
            names.add("describe_location"); names.add("inspect_nearby_entities"); names.add("inspect_companion_inventory");
            names.add("inspect_equipment"); names.add("combat_status");
            names.add("follow_owner"); names.add("gather_wood"); names.add("craft_item"); names.add("combat_target");
            names.add("give_item_to_owner"); names.add("quest_control");
        }
        StringBuilder b = new StringBuilder();
        for (AITool t : TOOLS.values()) {
            if (!names.contains(t.name())) continue;
            b.append("TOOL ").append(t.name()).append('\n');
            b.append(t.description()).append('\n');
            b.append("ARGUMENTS ").append(t.schema()).append("\n\n");
        }
        return b.toString();
    }

    /** Native tool declarations for the small, relevant surface. The complete 55-tool registry remains authoritative;
     * the model sees only the tools relevant to this request, following current agent/tool-use guidance. */
    public static com.google.gson.JsonArray jsonToolDefinitionsFor(String message) {
        String defs = definitionsFor(message);
        java.util.Set<String> selected = new java.util.LinkedHashSet<>();
        for (String line : defs.split("\\n")) {
            if (line.startsWith("TOOL ")) selected.add(line.substring(5).trim());
        }
        com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        for (AITool t : TOOLS.values()) {
            if (!selected.contains(t.name())) continue;
            com.google.gson.JsonObject fn = new com.google.gson.JsonObject();
            fn.addProperty("name", t.name()); fn.addProperty("description", t.description()); fn.add("parameters", ToolContract.toJsonSchema(t)); fn.addProperty("strict", false);
            com.google.gson.JsonObject wrapper = new com.google.gson.JsonObject(); wrapper.addProperty("type", "function"); wrapper.add("function", fn); out.add(wrapper);
        }
        return out;
    }

    /** Provider-neutral JSON Schema tool declarations for strict function/tool calling. */
    public static com.google.gson.JsonArray jsonToolDefinitions() {
        com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        for (AITool t : TOOLS.values()) {
            com.google.gson.JsonObject fn = new com.google.gson.JsonObject();
            fn.addProperty("name", t.name());
            fn.addProperty("description", t.description());
            fn.add("parameters", ToolContract.toJsonSchema(t)); fn.addProperty("strict", false);
            com.google.gson.JsonObject wrapper = new com.google.gson.JsonObject();
            wrapper.addProperty("type", "function");
            wrapper.add("function", fn);
            out.add(wrapper);
        }
        return out;
    }

    /** Compact catalog used by small models before a detailed tool call is emitted. */
    public static String compactCatalog() {
        StringBuilder b = new StringBuilder();
        for (AITool t : TOOLS.values()) {
            b.append(t.name()).append(" :: ").append(t.description()).append(" :: ").append(t.schema()).append('\n');
        }
        return b.toString();
    }

    public static String definitions() {
        StringBuilder b = new StringBuilder();
        for (AITool t : TOOLS.values()) {
            b.append("TOOL ").append(t.name()).append('\n');
            b.append(t.description()).append('\n');
            b.append("ARGUMENTS ").append(t.schema()).append("\n\n");
        }
        return b.toString();
    }
    private ToolRegistry() {}
}
