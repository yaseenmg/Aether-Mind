package com.yaseen.aicore.ai;

import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.agent.AgentToolRuntime;
import com.yaseen.aicore.task.TaskManager;
import com.yaseen.aicore.quest.DragonQuestDirector;
import com.yaseen.aicore.quest.QuestItems;
import net.minecraft.server.level.ServerPlayer;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Instant (no LLM) handling for short, unambiguous core phrases: follow, spar, stop fighting,
 * attack monsters, greetings. Only WHOLE-message matches are accepted, so negations or longer
 * sentences ("لا تتبعني", "تعال بعد ما تخلص") always fall through to the real agent.
 */
public final class FastIntent {
    private static final Set<String> FOLLOW = set("تعال", "تعال وياي", "تعال لي", "تعال عندي", "تعال هنا", "وينك تعال",
            "اتبعني", "تبعني", "خلك وياي", "خليك وياي", "follow me", "come", "come here");
    private static final Set<String> SPAR = set("قاتلني", "قاتلني الآن", "تعال قاتلني", "تعال نتبارز", "نتبارز", "يلا نتبارز",
            "ضاربني", "تعال نضارب", "نضارب", "وريني قوتك", "هاجمني", "بارزني", "تبارز وياي");
    private static final Set<String> CEASE = set("اتركه", "اتركني", "توقف عن القتال", "وقف القتال", "وقف المبارزة",
            "كافي", "كفى", "بس كافي");
    private static final Set<String> STAY = set("وقف", "توقف", "اوقف", "قف", "خلك هنا", "خليك هنا", "خلك مكانك", "مكانك", "استنى", "استنا",
            "انتظر", "stay", "wait", "stop", "hold");
    private static final Set<String> INVENTORY = set("شنو عندك", "شنو معك", "شنو بالجنطه", "شنو بالحقيبه", "وريني الجنطه", "اعرض الجنطه",
            "شنو بجيبك", "inventory", "show inventory", "what do you have");
    private static final Set<String> PROTECT = set("احمني", "احميني", "احمي", "حمايه", "وضع الحمايه", "protect me", "guard me");
    private static final Set<String> UNPROTECT = set("بطل حمايه", "وقف الحمايه", "لا تحميني", "stop protecting");
    private static final Set<String> EXPLORE = set("استكشف", "استكشاف", "روح استكشف", "explore");
    private static final Set<String> UNEXPLORE = set("بطل استكشاف", "وقف الاستكشاف", "stop exploring");
    private static final Set<String> KILL = set("اقتل الوحوش", "اقتل زومبي", "اقتل الزومبي", "اقتل اقرب وحش",
            "اقتل العدو", "اهجم على الوحوش");
    // Chopping wood is the single most-requested action and the weak local model keeps failing to
    // emit the gather_wood tool call, so handle the unambiguous whole-message forms deterministically.
    private static final Set<String> WOOD = set("كسر خشب", "اكسر خشب", "كسر الخشب", "اكسر الخشب", "كسر شجر",
            "اكسر شجر", "كسر الشجر", "اقطع شجرة", "اقطع الشجر", "اقطع شجر", "قطع شجرة", "قطع الخشب", "قطع خشب",
            "جيب خشب", "جيب لي خشب", "اجمع خشب", "اجمع الخشب", "احتاج خشب", "سوي لي خشب", "chop wood",
            "gather wood", "get wood", "break wood", "cut wood", "cut a tree", "chop a tree");
    // Verbs that mean "destroy/break a block" (whole word, canon'd). Everything after the verb is
    // treated as the block name and mapped to an English keyword for the generic breaker.
    private static final Set<String> BREAK_VERBS = set("كسر", "اكسر", "حطم", "دمر", "اقطع", "قطع", "احفر", "حفر",
            "اجمع", "جمع", "جيب", "احصل", "gather", "get",
            "break", "mine", "dig", "destroy");
    // Verbs that mean "craft/make an item".
    private static final Set<String> CRAFT_VERBS = set("اصنع", "صنع", "اعمل", "سوي", "سو", "اصنعلي", "سويلي",
            "craft", "make", "build");
    // Verbs that mean "place/put a block from inventory".
    private static final Set<String> PLACE_VERBS = set("ضع", "حط", "حطه", "ضعه", "ركب", "ثبت", "ابني", "ابن",
            "place", "put", "set");
    // Arabic/colloquial block name -> English keyword understood by GatherBlockTool / registry ids.
    private static final Map<String, String> BLOCK_WORDS = new HashMap<>();
    private static final Map<String, String> GREET = new HashMap<>();

    private static void blk(String k, String v) { BLOCK_WORDS.put(canon(k), v); }

    static {
        put("هلا", "مرحبًا بك.");
        put("هلو", "مرحبًا بك.");
        put("مرحبا", "مرحبًا بك.");
        put("سلام", "مرحبًا بك.");
        put("السلام عليكم", "وعليكم السلام ورحمة الله.");
        put("شلونك", "أنا بخير، شكرًا لسؤالك. وكيف حالك؟");
        put("شخبارك", "أنا بخير، شكرًا لسؤالك. وكيف حالك؟");
    }

    static {
        blk("تراب", "dirt"); blk("الديرت", "dirt"); blk("ديرت", "dirt"); blk("التراب", "dirt"); blk("dirt", "dirt");
        blk("حجر", "stone"); blk("الحجر", "stone"); blk("صخر", "stone"); blk("الصخر", "stone"); blk("ستون", "stone"); blk("stone", "stone");
        blk("حجاره", "cobblestone"); blk("كوبلستون", "cobblestone"); blk("cobble", "cobblestone"); blk("cobblestone", "cobblestone");
        blk("رمل", "sand"); blk("الرمل", "sand"); blk("sand", "sand");
        blk("حصى", "gravel"); blk("الحصى", "gravel"); blk("جرفل", "gravel"); blk("gravel", "gravel");
        blk("زجاج", "glass"); blk("الزجاج", "glass"); blk("قزاز", "glass"); blk("glass", "glass");
        blk("طين", "clay"); blk("الطين", "clay"); blk("clay", "clay");
        blk("صوف", "wool"); blk("الصوف", "wool"); blk("وول", "wool"); blk("wool", "wool");
        blk("اوراق", "leaves"); blk("ورق شجر", "leaves"); blk("leaves", "leaves");
        blk("جليد", "ice"); blk("ثلج", "ice"); blk("ice", "ice");
        blk("خام", "ore"); blk("معدن", "ore"); blk("ore", "ore");
        blk("خشب", "wood"); blk("الخشب", "wood"); blk("شجر", "wood"); blk("الشجر", "wood"); blk("شجره", "wood");
        blk("جذع", "wood"); blk("wood", "wood"); blk("log", "wood");
    }

    private static void put(String k, String v) { GREET.put(canon(k), v); }

    private static Set<String> set(String... items) {
        Set<String> s = new HashSet<>();
        for (String i : items) s.add(canon(i));
        return s;
    }

    /** @return the reply to say, or null if the message must go to the LLM. */
    public static String tryExecute(CompanionEntity c, ServerPlayer p, String raw) {
        String s = canon(raw);
        com.yaseen.aicore.debug.Trace.log("FAST", "evaluate canon=\"{}\"", s);
        // Natural requests often include politeness, context, quantity and a destination.
        // The old cutoff silently sent those requests to a small model that often answered
        // without selecting a tool.
        if (s.isEmpty() || s.length() > 220) return null;

        // ---- Identity: the companion is always Steve ----
        if (s.equals(canon("ما اسمك")) || s.equals(canon("شنو اسمك")) || s.equals(canon("اسمك شنو"))
                || s.equals(canon("من انت")) || s.equals(canon("منو انت")) || s.equals(canon("what is your name"))
                || s.equals(canon("whats your name")) || s.equals(canon("who are you"))) {
            return "اسمي ستيف.";
        }

        // ---- Dragon questline ("beat the game") deterministic hooks ----
        // Returning "" (not null) means "handled, say nothing more": before, null made the SAME sentence continue to the
        // weak LLM, which cancelled tasks and answered "I didn't understand" right after the quest had started.
        if (isQuestStart(raw, s)) {
            if (DragonQuestDirector.isActive(c)) return "المهمة شغالة أصلاً. اكتب: وين وصلت.";
            DragonQuestDirector.start(c);
            return ""; // start() already announces in Arabic
        }
        if (isQuestStop(s) && DragonQuestDirector.isActive(c)) { DragonQuestDirector.stop(c); return ""; }
        if (isQuestStatus(s)) return DragonQuestDirector.status(c);

        // Deterministic structure locating: never send an unambiguous "find a village/fortress/etc."
        // request to the LLM where it may answer without actually moving.
        String structureRequest = structureRequest(s);
        if (structureRequest != null) {
            int radius = s.contains("بعيد") || s.contains("بعيده") || s.contains("بعيدة") ? 512 : 256;
            String r = runTool(c, p, "search_structure", "{\"structure\":\"" + structureRequest + "\",\"radius\":" + radius + ",\"go\":true}");
            if (r != null && r.startsWith("SUCCESS")) return "وجدتها. " + r.substring("SUCCESS".length()).trim();
            if (r != null && r.startsWith("STARTED structure_discovery")) return "ما عندي إحداثيات مسبقة. راح أستكشف المنطقة بنفسي وأفحص المباني اللي تنكشف، وإذا لقيت القرية أروح لها.";
            if (r != null && r.startsWith("FAILED")) return "ما لقيت " + structureArabic(structureRequest) + " حاليًا ضمن المنطقة المكتشفة.";
        }
        // "\u0627\u0639\u0637\u0646\u064a X": deterministic give from the companion's REAL inventory.
        if (s.startsWith("\u0627\u0639\u0637\u0646\u064a") || s.startsWith("\u0639\u0637\u0646\u064a") || s.startsWith("\u0627\u0646\u0637\u064a\u0646\u064a")
                || s.startsWith("\u0646\u0627\u0648\u0644\u0646\u064a") || s.startsWith("give me")) {
            String id = QuestItems.resolveGiveId(s);
            if (id != null) {
                int n = extractInt(s, 1);
                String r = runTool(c, p, "give_item_to_owner", "{\"item_id\":\"" + id + "\",\"count\":" + n + "}");
                if (r != null && r.startsWith("SUCCESS")) return "\u062a\u0641\u0636\u0644\u060c \u0646\u0637\u064a\u062a\u0643 \u0627\u0644\u0644\u064a \u0637\u0644\u0628\u062a\u0647.";
                if (r != null && r.startsWith("FAILED")) return "\u0645\u0627 \u0639\u0646\u062f\u064a \u0647\u0630\u0627 \u0627\u0644\u0634\u064a \u0628\u0627\u0644\u062c\u0646\u0637\u0629 \u062d\u0627\u0644\u064a\u0627\u064b.";
                return null;
            }
        }

        // Stop-follow must be checked BEFORE the positive FOLLOW match, because phrases like
        // "لا تتبعني" contain the follow keyword and would otherwise trigger following.
        if (mentionsFollow(s) && isNegated(s)) {
            c.setFollowEnabled(false);
            TaskManager.cancelCurrentTask(c);
            return "حسنًا، توقفت عن اتباعك وسأنتظرك في مكاني.";
        }
        if (mentionsFollow(s)) {
            c.setFriendlyCombat(false);
            c.setTarget(null);
            TaskManager.cancelCurrentTask(c);
            c.setFollowEnabled(true);
            return "حسنًا، سأتبعك.";
        }
        if (FOLLOW.contains(s) && DragonQuestDirector.isActive(c)) DragonQuestDirector.stop(c);   // explicit "come" cancels the quest
        if (STAY.contains(s)) {
            if (DragonQuestDirector.isActive(c)) DragonQuestDirector.stop(c);
            c.setTarget(null);
            TaskManager.cancelCurrentTask(c);
            c.setFollowEnabled(false);
            return "حسنًا، توقفت وسأبقى في مكاني.";
        }
        if (INVENTORY.contains(s)) {
            StringBuilder inv = new StringBuilder();
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            for (int i = 0; i < c.getCompanionInventory().getContainerSize(); i++) {
                var st = c.getCompanionInventory().getItem(i);
                if (!st.isEmpty()) counts.merge(st.getHoverName().getString(), st.getCount(), Integer::sum);
            }
            if (counts.isEmpty()) return "الجنطة فاضية.";
            int n = 0;
            for (var e : counts.entrySet()) { if (n++ >= 14) { inv.append("..."); break; } inv.append(e.getKey()).append(" x").append(e.getValue()).append(n < counts.size() ? "، " : ""); }
            return "عندي: " + inv;
        }
        if (PROTECT.contains(s)) {
            com.yaseen.aicore.advanced.BodyguardController.set(c, true);
            c.setFollowEnabled(true);
            return "حسنًا، سأحميك.";
        }
        if (UNPROTECT.contains(s)) { com.yaseen.aicore.advanced.BodyguardController.set(c, false); return "حسنًا، أوقفت وضع الحماية."; }
        if (EXPLORE.contains(s)) {
            if (DragonQuestDirector.isActive(c)) return "أنا في منتصف مهمة إنجاز اللعبة. قل: أوقف المهمة، إذا أردت مني الاستكشاف.";
            com.yaseen.aicore.advanced.ExplorationController.set(c, true);
            return "حسنًا، بدأت الاستكشاف.";
        }
        if (UNEXPLORE.contains(s)) { com.yaseen.aicore.advanced.ExplorationController.set(c, false); return "حسنًا، أوقفت الاستكشاف."; }
        if (FOLLOW.contains(s)) {
            c.setFriendlyCombat(false);
            c.setTarget(null);
            TaskManager.cancelCurrentTask(c);
            c.setFollowEnabled(true);
            return "حسنًا، سآتي معك.";
        }
        if (SPAR.contains(s)) {
            String r = runTool(c, p, "combat_target", "{\"mode\":\"owner\"}");
            return r != null && r.startsWith("STARTED") ? "حسنًا، لنبدأ المبارزة." : null;
        }
        if (CEASE.contains(s)) {
            runTool(c, p, "combat_target", "{\"mode\":\"stop\"}");
            return "حسنًا، أوقفت القتال.";
        }
        if (KILL.contains(s)) {
            String r = runTool(c, p, "combat_target", "{\"mode\":\"nearest_hostile\"}");
            return r != null && r.startsWith("STARTED") ? "حسنًا، سأهاجم أقرب عدو." : "لم أرَ أعداء قريبين.";
        }
        if (WOOD.contains(s)) {
            return chopWood(c, p);
        }

        // ---- High-level deterministic tasks (phrase-based, so word order is flexible) ----
        // "اجمع موارد" / "جيب موارد": autonomous gather batch.
        if (s.contains("موارد") && (s.contains("اجمع") || s.contains("جمع") || s.contains("جيب") || s.contains("روح") || s.contains("gather"))) {
            String r = runTool(c, p, "gather_resources", "{\"count\":10}");
            if (r != null && r.startsWith("STARTED")) return "حسنًا، سأجمع الموارد ثم أعود إليك.";
            if (r != null && r.startsWith("FAILED")) return "لا توجد موارد واضحة قريبة يمكنني جمعها الآن.";
            return null;
        }
        // "نوّر المكان" / "حط شموع/مشاعل كل 5 بلوكات".
        boolean lightWords = s.contains("شموع") || s.contains("شمع") || s.contains("مشعل") || s.contains("مشاعل")
                || s.contains("torch") || s.contains("candle") || s.contains("فوانيس") || s.contains("lantern");
        boolean lightVerb = s.contains("نور") || s.contains("ضوي") || s.contains("اضي") || s.contains("حط") || s.contains("ضع") || s.contains("light");
        if (lightWords && lightVerb) {
            int spacing = extractInt(s, 5);
            String r = runTool(c, p, "light_area", "{\"light\":\"torch\",\"spacing\":" + spacing + ",\"count\":10}");
            if (r != null && r.startsWith("SUCCESS")) return "تم، نوّرت المكان وحطيت مشاعل كل " + spacing + " بلوكات.";
            if (r != null && r.startsWith("FAILED no_light_source")) return "لا أملك مشاعل أو المواد اللازمة لصنعها؛ أحتاج إلى الفحم والعصي.";
            if (r != null && r.startsWith("FAILED no_valid_spot")) return "لم أجد مكانًا مناسبًا لوضع المشاعل.";
            return null;
        }
        // "اطبخ" / "اشوي".
        if (s.contains("اطبخ") || s.contains("طبخ") || s.contains("اشوي") || s.contains("اشويلي") || s.contains("cook")) {
            String r = runTool(c, p, "cook_food", "{\"output\":\"\"}");
            if (r != null && r.startsWith("STARTED")) return "حسنًا، وضعت الطعام في الفرن وبدأ طهيه.";
            if (r != null && r.startsWith("FAILED no_furnace")) return "لا يوجد فرن أو مدخنة قريبة مني.";
            if (r != null && r.contains("no real companion-inventory input")) return "لا أملك طعامًا يمكنني طهيه الآن.";
            if (r != null && r.contains("no real fuel")) return "لا أملك وقودًا، مثل الفحم أو الخشب، لتشغيل الفرن.";
            return null;
        }
        // "احصد" (harvest grown crops) vs "ازرع" (till + plant).
        if (s.contains("احصد") || s.contains("حصاد") || s.contains("harvest")) {
            String r = runTool(c, p, "farm_area", "{\"radius\":6,\"replant\":true}");
            if (r != null && r.startsWith("RESULT harvested=0")) return "لم أجد محصولًا ناضجًا قريبًا لحصاده الآن.";
            if (r != null && r.startsWith("RESULT")) return "تم، حصدت المحصول الناضج وأعدت الزراعة.";
            if (r != null && r.startsWith("DENIED")) return "لا أملك صلاحية الزراعة حاليًا.";
            return null;
        }
        if (s.contains("ازرع") || s.contains("زراع") || s.contains("ازرعلي") || s.contains("فلاح") || s.contains("plant") || s.contains("farm")) {
            String r = runTool(c, p, "till_and_plant", "{\"radius\":4}");
            if (r != null && r.startsWith("SUCCESS")) return "تم تجهيز الأرض وزراعة البذور المتاحة لدي.";
            if (r != null && r.startsWith("FAILED no_tillable")) return "لم أجد أرضًا صالحة للزراعة، أو لا أملك بذورًا.";
            return null;
        }

        // Generic verb + object parsing: "اكسر <block>", "اصنع <item>". Bypasses the weak
        // local model for the two most-requested gameplay actions.
        String[] tok = s.split(" ");
        int verbIndex = findActionVerb(tok);
        if (verbIndex >= 0 && verbIndex < tok.length - 1) {
            String verb = tok[verbIndex];
            StringBuilder tail = new StringBuilder();
            for (int i = verbIndex + 1; i < tok.length; i++) {
                if (tail.length() > 0) tail.append(' ');
                tail.append(tok[i]);
            }
            String rest = tail.toString().trim();
            if (PLACE_VERBS.contains(verb)) {
                String kw = resolveBlock(rest);
                if (kw != null) {
                    String r = runTool(c, p, "place_block", "{\"block\":\"" + kw + "\",\"count\":" + extractInt(rest, 1) + "}");
                    if (r != null && r.startsWith("SUCCESS")) return "تم، حطيت الـ" + rest + ".";
                    if (r != null && r.startsWith("FAILED no_block_in_inventory")) return "لا أملك " + rest + " في المخزون أحطه.";
                    if (r != null && r.startsWith("FAILED no_valid_spot")) return "لم أجد مكان مناسب أحط بيه الـ" + rest + ".";
                    return null;
                }
                return null;
            }
            if (BREAK_VERBS.contains(verb)) {
                String kw = resolveBlock(rest);
                if ("wood".equals(kw)) return chopWood(c, p);
                if (kw != null) {
                    TaskManager.cancelCurrentTask(c);
                    com.yaseen.aicore.debug.Trace.log("FAST", "intent=BREAK block={} -> tool=gather_block count=6", kw);
                    String r = runTool(c, p, "gather_block", "{\"block\":\"" + kw + "\",\"count\":6}");
                    com.yaseen.aicore.debug.Trace.log("FAST", "tool=gather_block result={}", com.yaseen.aicore.debug.Trace.clip(r));
                    if (r != null && r.startsWith("STARTED")) return "حسنًا، سأكسر الـ" + rest + " القريب.";
                    if (r != null && r.startsWith("FAILED no_loaded_block")) return "لا يوجد " + rest + " قريب مني الآن.";
                    if (r != null && r.startsWith("DENIED")) return "لا أملك صلاحية التكسير حاليًا.";
                    return null;
                }
                return null;
            }
            if (CRAFT_VERBS.contains(verb)) {
                // Version 60: crafting is a long-horizon work graph, not a one-shot local action.
                // Let AgentEngine own the deterministic craft loop so missing resources can be
                // gathered/smelted/crafted and the final item can be verified before we claim success.
                return null;
            }
        }
        return GREET.get(s);
    }
    /** Version 60 deterministic parser exposed to the server-side work planner. */
    public static String craftTargetForPlanner(String raw) {
        String s = canon(raw);
        if (s.isBlank()) return null;
        String[] tok = s.split(" ");
        int verbIndex = findActionVerb(tok);
        if (verbIndex < 0 || !CRAFT_VERBS.contains(tok[verbIndex]) || verbIndex >= tok.length - 1) return null;
        StringBuilder tail = new StringBuilder();
        for (int i = verbIndex + 1; i < tok.length; i++) {
            if (tail.length() > 0) tail.append(' ');
            tail.append(tok[i]);
        }
        return craftTarget(tail.toString().trim());
    }

    /** Whether this message is an unambiguous craft/make request owned by the work planner. */
    public static boolean isCraftRequest(String raw) {
        return craftTargetForPlanner(raw) != null;
    }

    /** Find the first gameplay verb, allowing forms such as "من فضلك اكسر الخشب القريب". */
    private static int findActionVerb(String[] tokens) {
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            if (BREAK_VERBS.contains(t) || CRAFT_VERBS.contains(t) || PLACE_VERBS.contains(t)) return i;
        }
        return -1;
    }

    /** All deterministic fast-path tool calls still pass through the same strict gateway as LLM calls. */
    private static String runTool(CompanionEntity c, ServerPlayer p, String toolName, String args) {
        AgentToolRuntime.Result r = AgentToolRuntime.invoke(c, p, toolName, args, "fast-" + java.util.UUID.randomUUID());
        return r.result();
    }

    private static String chopWood(CompanionEntity c, ServerPlayer p) {
        TaskManager.cancelCurrentTask(c);
        com.yaseen.aicore.debug.Trace.log("FAST", "intent=CHOP_WOOD -> tool=gather_wood count=6");
        String r = runTool(c, p, "gather_wood", "{\"count\":6}");
        com.yaseen.aicore.debug.Trace.log("FAST", "tool=gather_wood result={}", com.yaseen.aicore.debug.Trace.clip(r));
        if (r != null && r.startsWith("STARTED")) return "حاضر، بدأت تكسير الخشب القريب.";
        if (r != null && r.startsWith("FAILED no_loaded_logs")) return "لا يوجد جذوع خشب قريبة مني الآن.";
        if (r != null && r.startsWith("DENIED")) return "لا أملك صلاحية التكسير حاليًا.";
        return null;
    }

    private static boolean mentionsFollow(String s) {
        return s.contains("تتبعني") || s.contains("تبعني") || s.contains("تمشي وراي")
                || s.contains("تلحقني") || s.contains("following") || s.contains("follow");
    }

    private static boolean isNegated(String s) {
        return s.startsWith("لا") || s.contains(" لا ") || s.contains("ماكو") || s.contains(" ما ")
                || s.contains("بطل") || s.contains("وقف") || s.contains("توقف")
                || s.contains("stop") || s.contains("dont") || s.contains("do not") || s.contains("don t");
    }

    /** First integer found in the text (ASCII or Arabic-Indic digits), else the fallback. */
    private static int extractInt(String s, int fallback) {
        if (s == null) return fallback;
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int d = Character.digit(ch, 10);
            if (d >= 0) num.append((char) ('0' + d));
            else if (num.length() > 0) break;
        }
        if (num.length() == 0) return fallback;
        try { return Math.max(1, Math.min(64, Integer.parseInt(num.toString()))); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static String resolveBlock(String rest) {
        if (rest == null || rest.isBlank()) return null;
        if (BLOCK_WORDS.containsKey(rest)) return BLOCK_WORDS.get(rest);
        for (String w : rest.split(" ")) {
            if (BLOCK_WORDS.containsKey(w)) return BLOCK_WORDS.get(w);
        }
        String last = rest.replaceAll("[^a-z_]", "");
        return last.length() >= 3 ? last : null;
    }

    private static String craftTarget(String rest) {
        if (rest == null || rest.isBlank()) return null;
        String r = rest;
        if (r.contains("طاوله صناعه") || r.contains("طاوله") || r.contains("كرافتنج") || r.contains("crafting table") || r.contains("كرافتنك")) return "crafting_table";
        if (r.contains("عصا") || r.contains("عصي") || r.contains("stick")) return "stick";
        if (r.contains("الواح") || r.contains("خشب منشور") || r.contains("plank")) return "planks";
        if (r.contains("مشعل") || r.contains("torch")) return "torch";
        // Generic item names must resolve to a deterministic sensible tier instead of
        // scanning the registry and accidentally selecting diamond/netherite variants.
        // The companion can then improve the gear later through the quest planner.
        if (r.contains("سيف") || r.contains("sword")) return "minecraft:iron_sword";
        if (r.contains("فأس") || r.contains("فاس") || r.contains("pickaxe") || r.contains("معول")) return "minecraft:iron_pickaxe";
        if (r.contains("صندوق") || r.contains("chest")) return "chest";
        if (r.contains("فرن") || r.contains("furnace")) return "furnace";
        String latin = r.replaceAll("[^a-z_ ]", "").trim().replace(' ', '_');
        return latin.length() >= 3 ? latin : null;
    }

    private static boolean hasAny(String s, String... words) { for (String w : words) if (s.contains(canon(w))) return true; return false; }

    private static String structureRequest(String s) {
        // Avoid a large Unicode regex here. Besides being harder to audit, it can become
        // fragile when source files are moved between Windows encodings. The project is
        // compiled as UTF-8, but this path intentionally uses the same canonical token
        // matching used by the rest of FastIntent.
        boolean request = hasAny(s,
                "ابحث", "دور", "لق", "لقي", "روح", "اذهب", "وين", "اين", "مكان",
                "find", "locate", "search");
        if (!request) return null;

        if (hasAny(s, "قرية", "قرى", "village")) return "village";
        if (hasAny(s, "حصن", "stronghold")) return "stronghold";
        if (hasAny(s, "دنجن", "دنجنات", "منجم", "مناجم", "mineshaft")) return "mineshaft";
        if (hasAny(s, "قلعة", "قلعه", "fortress")) return "fortress";
        if (hasAny(s, "باستيون", "bastion")) return "bastion";
        if (hasAny(s, "مدينة قديمة", "ancient city")) return "ancient_city";
        if (hasAny(s, "مدينة النهاية", "end city")) return "end_city";
        if (hasAny(s, "قصر", "mansion")) return "mansion";
        if (hasAny(s, "نصب", "monument")) return "monument";
        if (hasAny(s, "معبد", "temple")) return "temple";
        if (hasAny(s, "سفينة", "حطام", "shipwreck")) return "shipwreck";
        if (hasAny(s, "بوابة مدمرة", "ruined portal")) return "ruined_portal";
        if (hasAny(s, "برج نهب", "outpost")) return "outpost";
        if (hasAny(s, "بنايات", "هياكل", "structures", "all")) return "all";
        return null;
    }

    private static String structureArabic(String id) {
        return switch (id) {
            case "village" -> "القرية"; case "stronghold" -> "الحصن"; case "mineshaft" -> "المنجم";
            case "fortress" -> "قلعة النذر"; case "bastion" -> "الباستيون"; case "ancient_city" -> "المدينة القديمة";
            case "end_city" -> "مدينة النهاية"; case "mansion" -> "قصر الغابة"; case "monument" -> "نصب المحيط";
            case "temple" -> "معبد"; case "shipwreck" -> "حطام سفينة"; case "ruined_portal" -> "البوابة المدمرة";
            case "outpost" -> "برج النهب"; default -> "البناية";
        };
    }

    private static boolean isQuestStart(String raw, String s) {
        if (raw != null && (raw.contains("؟") || raw.contains("?"))) return false;   // a question, not an order
        if (s.startsWith("لا ") || s.contains(" لا ") || hasAny(s, "ماكو", "لا تقتل", "لا تختم", "dont", "don t", "do not", "stop", "وقف", "توقف", "الغ", "بطل")) return false;
        boolean dragon = hasAny(s, "تنين", "dragon");
        boolean killVerb = hasAny(s, "اقتل", "نقتل", "اهزم", "قتل", "اذبح", "اقضي", "kill", "defeat", "slay");
        if (dragon && killVerb) return true;
        boolean game = hasAny(s, "اللعبه", "لعبه", "ماينكرافت", "ماين كرافت", "ماين", "minecraft", "the game");
        boolean finishVerb = hasAny(s, "ختم", "اختم", "نختم", "خلص", "خلصلي", "نخلص", "اكمل", "كمل", "نكمل", "انهي", "نهي", "finish", "beat", "complete");
        if (game && finishVerb) return true;
        return hasAny(s, "beat the game", "end the game", "ender dragon", "ابأقوم بـالرحله", "ابأقوم بـالمهمه الكبرى");
    }

    private static boolean isQuestStop(String s) {
        return hasAny(s, "وقف المهمه", "وقف ختم", "الغ المهمه", "الغي المهمه", "بطل المهمه", "خلص المهمه", "وقف الكويست", "stop quest", "cancel quest");
    }

    private static boolean isQuestStatus(String s) {
        return hasAny(s, "وين وصلت", "ما وصلت", "شكو بالمهمه", "حاله المهمه", "تقدم المهمه", "quest status", "quest progress");
    }

    private static String canon(String input) {
        if (input == null) return "";
        String s = InputNormalizer.normalize(input);
        s = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        s = s.replaceAll("[\\u064B-\\u065F\\u0670]", "");
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي')
                .replace('ة', 'ه');
        s = s.replaceAll("[،,:;!?؟.!\\-_/\"']+", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    private FastIntent() {}
}
