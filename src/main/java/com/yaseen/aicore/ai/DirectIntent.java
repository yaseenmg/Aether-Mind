package com.yaseen.aicore.ai;

import com.yaseen.aicore.agent.InternalToolExecutor;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.task.TaskManager;
import net.minecraft.server.level.ServerPlayer;
import com.yaseen.aicore.tool.impl.LocationTool;
import com.yaseen.aicore.tool.impl.StructureSearchTool;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Minimal deterministic emergency guardrail.
 * It intentionally does NOT contain a command dictionary for ordinary actions.
 * All natural-language intent, questions, negations, and multi-step requests are
 * delegated to AgentEngine so the LLM can reason from meaning and live context.
 */
public final class DirectIntent {
    public static String tryExecute(CompanionEntity c, ServerPlayer p, String raw) {
        String s = canonical(raw);
        if (s.isBlank()) return null;

        if (isLocationQuestion(s)) {
            String result = InternalToolExecutor.invoke(c, p, "location", "{}", "internal-" + java.util.UUID.randomUUID()).result();
            return locationReply(result);
        }

        String structure = structureFromCommand(s);
        if (structure != null) {
            String result = InternalToolExecutor.invoke(c, p, "search_structure", "{\"structure\":\"" + structure + "\",\"go\":true,\"radius\":10000}", "internal-" + java.util.UUID.randomUUID()).result();
            return structureReply(result);
        }

        if (isStop(s)) {
            stopEverything(c);
            return "توقفت. سأبقى هنا حتى تطلب مني شيئًا آخر.";
        }

        // Do not turn ordinary Arabic into a keyword dictionary. Combat, gathering,
        // mining, questions, requests, negations, and multi-step sentences are interpreted
        // by AgentEngine/LLM from their meaning and the live world context. The local layer
        // only handles the unambiguous emergency/stop controls above.

        // Everything else intentionally falls through to the real agent. This is
        // what lets "كيف حالك؟", long Arabic sentences and modpack-specific tasks
        // reach the LLM instead of being rejected by a keyword table.
        return null;
    }


    private static String structureFromCommand(String s) {
        if (!(s.contains("ابحث") || s.contains("دور") || s.contains("لقى") || s.contains("لقِ") || s.contains("وين") || s.contains("اين"))) return null;
        if (s.contains("قرية") || s.contains("قرى")) return "minecraft:village";
        if (s.contains("سترونغهولد") || s.contains("ستروغ") || s.contains("قلعة النهاية") || s.contains("حصن النهاية")) return "minecraft:stronghold";
        if (s.contains("منجم") || s.contains("مناجم")) return "minecraft:mineshaft";
        if (s.contains("مدينة قديمة") || s.contains("المدينة القديمة")) return "minecraft:ancient_city";
        if (s.contains("هرم")) return "minecraft:desert_pyramid";
        if (s.contains("معبد الغابة")) return "minecraft:jungle_temple";
        if (s.contains("كوخ الساحرة")) return "minecraft:swamp_hut";
        if (s.contains("برج النهب") || s.contains("معسكر النهب")) return "minecraft:pillager_outpost";
        if (s.contains("سفينة")) return "minecraft:shipwreck";
        if (s.contains("قصر الغابة")) return "minecraft:woodland_mansion";
        if (s.contains("قصر المحيط") || s.contains("معبد المحيط")) return "minecraft:monument";
        if (s.contains("بوابة مدمرة")) return "minecraft:ruined_portal";
        if (s.contains("غرف التجارب") || s.contains("غرفة التجارب")) return "minecraft:trial_chambers";
        if (s.contains("اطلال المسار") || s.contains("أطلال المسار")) return "minecraft:trail_ruins";
        if (s.contains("حصن النذر") || s.contains("حصن") || s.contains("فورتريس")) return "minecraft:fortress";
        if (s.contains("باسشن") || s.contains("باستيون")) return "minecraft:bastion_remnant";
        if (s.contains("مدينة النهاية")) return "minecraft:end_city";
        if (s.contains("كل البنايات") || s.contains("كل المباني") || s.contains("كل الهياكل") || s.contains("كل الستركتشر")) return "all";
        return null;
    }

    private static String structureReply(String result) {
        if (result == null) return "لم أستطع البحث عن البناء.";
        if (result.startsWith("SUCCESS structure=")) {
            String id=value(result,"structure="); String pos=value(result,"position=");
            return "لقيت " + id + " عند " + pos + " وبدأت أروح له.";
        }
        if (result.startsWith("STARTED structure_discovery")) return "ما عندي إحداثيات مسبقة لهذا البناء. راح أستكشف المنطقة بنفسي، وأفحص المباني التي تظهر في العالم، ثم أروح له إذا اكتشفته.";
        if (result.startsWith("SUCCESS structure_scan")) return "بدأت فحص البنايات المسجلة في العالم.\n" + result;
        return "ما لقيت البناء المطلوب حاليًا. " + result;
    }

    private static boolean isLocationQuestion(String s) {
        return isExact(s, "وينك", "وين موقعك", "وين مكانك", "اين انت", "أين انت", "اين مكانك", "أين مكانك", "حدد موقعك", "حدد مكانك", "اعطني موقعك", "عطني موقعك", "وين انت");
    }

    private static String locationReply(String result) {
        String pos = value(result, "exact_block_position=");
        String dim = value(result, "dimension=");
        String biome = value(result, "biome=");
        String weather = value(result, "weather=");
        String surface = value(result, "surface_block=");
        String nearest = value(result, "nearest_living=");
        if (pos.isBlank()) return "لم أتمكن من تحديد موقعي بدقة.";
        String extra = surface.isBlank() ? "" : " والكتلة أسفل قدمي هي " + surface.split(",", 2)[0] + ".";
        if (!nearest.isBlank()) extra += " وأقرب كائن حي هو: " + nearest + ".";
        return "موقعي الآن هو " + pos + " في " + dim + "، والمنطقة هي " + biome + "، والطقس هو " + weather + "." + extra;
    }

    private static String value(String s, String key) {
        int i=s.indexOf(key); if(i<0)return ""; int a=i+key.length(), e=s.indexOf('\n',a); if(e<0)e=s.length(); return s.substring(a,e).trim();
    }

    private static boolean isStop(String s) {
        return isExact(s,
                "توقف", "توقف هنا", "قف", "وقف", "خلاص وقف", "خلاص",
                "لا تتحرك", "ابق مكانك", "ابقى مكانك", "خلك هنا", "لا تسوي شي",
                "stop", "stay here", "do not move");
    }

















    private static void stopEverything(CompanionEntity c) {
        TaskManager.cancelCurrentTask(c);
        c.setFollowEnabled(false);
        c.setFriendlyCombat(false);
        c.setTarget(null);
        c.getNavigation().stop();
    }





    private static String canonical(String input) {
        if (input == null) return "";
        String s = InputNormalizer.normalize(input);
        s = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        s = s.replaceAll("[\\u064B-\\u065F\\u0670]", "");
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي')
                .replace('ة', 'ه');
        s = s.replaceAll("[،,:;!?؟.!،\\-_/]+", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static boolean isExact(String s, String... terms) {
        for (String term : terms) {
            if (s.equals(canonical(term))) return true;
        }
        return false;
    }

    private DirectIntent() {}
}
