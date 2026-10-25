package com.yaseen.aicore.agent;

import java.util.Locale;

/** High-confidence intent router. It complements, rather than replaces, LLM planning. */
public final class ToolSelectionRouter {
    public record Route(String tool, String arguments, String reason) {}

    public static Route first(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا');
        if (s.isBlank() || s.length() > 300 || s.contains("لا تجمع") || s.contains("لا تكسر") || s.contains("لا تصنع")) return null;
        if (s.contains("اجمع خشب") || s.contains("جمع خشب") || s.contains("قطع شجر") || s.contains("كسر خشب")
                || s.contains("gather wood") || s.contains("chop wood"))
            return new Route("gather_resources", "{\"resource\":\"wood\",\"count\":8}", "wood intent");
        if (s.contains("اجمع موارد") || s.contains("جمع موارد") || s.contains("gather resources"))
            return new Route("gather_resources", "{\"count\":8}", "resource intent");
        if (s.contains("احصد") || s.contains("حصاد") || s.contains("harvest"))
            return new Route("farm_area", "{\"radius\":6,\"replant\":true}", "harvest intent");
        if (s.contains("ازرع") || s.contains("زراعة") || s.contains("plant"))
            return new Route("till_and_plant", "{\"radius\":4}", "plant intent");
        if (s.contains("اطبخ") || s.contains("طبخ") || s.contains("اشو") || s.contains("cook"))
            return new Route("cook_food", "{\"output\":\"\"}", "food intent");
        if (s.equals("استكشف") || s.contains(" ابدأ الاستكشاف") || s.contains("explore"))
            return new Route("explore_mode", "{\"enabled\":true}", "exploration intent");
        if (s.contains("اتبعني") || s.contains("تعال معي") || s.contains("follow me"))
            return new Route("follow_owner", "{\"enabled\":true}", "follow intent");
        if (s.contains("احمني") || s.contains("احميني") || s.contains("guard me"))
            return new Route("bodyguard_mode", "{\"enabled\":true}", "guard intent");
        if (s.contains("اقتل الوحوش") || s.contains("اقتل الزومبي") || s.contains("attack mobs"))
            return new Route("combat_target", "{\"mode\":\"nearest_hostile\"}", "combat intent");
        return null;
    }

    private ToolSelectionRouter() {}
}
