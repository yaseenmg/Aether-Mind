package com.yaseen.aicore;

import net.minecraftforge.common.ForgeConfigSpec;

/** Single-provider configuration: the reasoning brain is Groq OpenAI-compatible API. */
public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_BASE_URL;
    public static final ForgeConfigSpec.IntValue GROQ_MAX_REQUESTS_PER_MINUTE;
    public static final ForgeConfigSpec.IntValue GROQ_MAX_REQUESTS_PER_DAY;
    public static final ForgeConfigSpec.IntValue GROQ_MAX_TOKENS_PER_DAY;
    public static final ForgeConfigSpec.ConfigValue<String> STT_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> STT_LANGUAGE;
    public static final ForgeConfigSpec.ConfigValue<String> STT_PROMPT;
    public static final ForgeConfigSpec.ConfigValue<String> TTS_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> TTS_VOICE;
    public static final ForgeConfigSpec.ConfigValue<String> LANGUAGE;
    public static final ForgeConfigSpec.BooleanValue VOICE_REPLY;
    public static final ForgeConfigSpec.BooleanValue AUTO_SPAWN;
    public static final ForgeConfigSpec.BooleanValue PERSISTENT_MEMORY;
    public static final ForgeConfigSpec.IntValue MEMORY_LIMIT;
    public static final ForgeConfigSpec.IntValue CONTEXT_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_NEARBY_ENTITIES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("ai");
        GROQ_API_KEY = b.define("groqApiKey", "");
        GROQ_MODEL = b.define("groqModel", "openai/gpt-oss-120b");
        GROQ_BASE_URL = b.define("groqBaseUrl", "https://api.groq.com/openai/v1");
        GROQ_MAX_REQUESTS_PER_MINUTE = b.defineInRange("groqMaxRequestsPerMinute", 6, 0, 60);
        GROQ_MAX_REQUESTS_PER_DAY = b.defineInRange("groqMaxRequestsPerDay", 500, 0, 10000);
        GROQ_MAX_TOKENS_PER_DAY = b.defineInRange("groqMaxTokensPerDay", 200000, 0, 10000000);
        b.pop();

        b.push("voice");
        STT_MODEL = b.define("sttModel", "whisper-large-v3");
        STT_LANGUAGE = b.define("sttLanguage", "ar");
        STT_PROMPT = b.define("sttPrompt", "أوامر Minecraft باللغة العربية الفصحى: قاتلني، توقف عن القتال، تعال معي، اتبعني، توقف، ابحث عن قرية، ابحث عن حصن، أعطني الماس، ابنِ بيتًا، اجمع موارد، احمني. انقل الكلام المنطوق كما هو ولا تعِد صياغته.");
        TTS_MODEL = b.define("ttsModel", "canopylabs/orpheus-arabic-saudi");
        TTS_VOICE = b.define("ttsVoice", "fahad");
        VOICE_REPLY = b.define("voiceReply", true);
        b.pop();

        b.push("companion");
        LANGUAGE = b.define("language", "ar");
        AUTO_SPAWN = b.define("autoSpawn", true);
        PERSISTENT_MEMORY = b.define("persistentMemory", true);
        MEMORY_LIMIT = b.defineInRange("memoryLimit", 100, 10, 1000);
        CONTEXT_RADIUS = b.defineInRange("contextRadius", 16, 4, 64);
        MAX_NEARBY_ENTITIES = b.defineInRange("maxNearbyEntities", 24, 4, 100);
        b.pop();
        SPEC = b.build();
    }

    private Config() {}
}
