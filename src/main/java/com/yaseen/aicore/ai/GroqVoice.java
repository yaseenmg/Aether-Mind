package com.yaseen.aicore.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yaseen.aicore.Config;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.network.ModNetwork;
import com.yaseen.aicore.network.VoiceReplyPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GroqVoice {
    private static final HttpClient HTTP = HttpClient.newBuilder().build();
    private static final int MAX_STT_BYTES = ModNetwork.MAX_VOICE_UPLOAD_BYTES;
    private static final int MAX_TTS_CHARS = 190;

    public static void transcribeAndAsk(ServerPlayer player, byte[] wav) {
        String key = Config.GROQ_API_KEY.get();
        if (key == null || key.isBlank()) {
            player.sendSystemMessage(Component.literal("[AI] ضع مفتاح Groq أولاً."));
            return;
        }
        if (wav == null || wav.length == 0 || wav.length > MAX_STT_BYTES) {
            player.sendSystemMessage(Component.literal("[AI] التسجيل الصوتي غير صالح أو أكبر من حد الشبكة."));
            return;
        }

        // Do not spend Groq quota transcribing audio for a player who has no companion.
        if (findCompanion(player) == null) {
            player.sendSystemMessage(Component.literal("[AI] لا أجد رفيقك الآن (جرّب /aic respawn)."));
            return;
        }
        final var server = player.server;
        final UUID playerId = player.getUUID();
        CompletableFuture.supplyAsync(() -> transcribe(key, wav))
                .whenComplete((text, err) -> server.execute(() -> {
                    ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                    if (current == null) return;
                    if (err != null) {
                        Throwable root = err.getCause() != null ? err.getCause() : err;
                        current.sendSystemMessage(Component.literal("[AI] " + describeSttError(root)));
                        return;
                    }
                    if (text == null || text.isBlank()) {
                        current.sendSystemMessage(Component.literal("[AI] لم أفهم التسجيل الصوتي."));
                        return;
                    }
                    current.sendSystemMessage(Component.literal("[أنت 🎤] " + text));
                    CompanionEntity companion = findCompanion(current);
                    if (companion != null) companion.ask(text);
                    else current.sendSystemMessage(Component.literal("[AI] لا أجد رفيقك الآن."));
                }));
    }

    private static String transcribe(String key, byte[] wav) {
        String boundary = "----AICoreBoundary" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream(wav.length + 1024);
        try {
            writeField(body, boundary, "model", Config.STT_MODEL.get());
            writeField(body, boundary, "language", Config.STT_LANGUAGE.get());
            writeField(body, boundary, "response_format", "verbose_json");
            writeField(body, boundary, "temperature", "0");
            String sttPrompt = Config.STT_PROMPT.get();
            if (sttPrompt != null && !sttPrompt.isBlank()) writeField(body, boundary, "prompt", sttPrompt);
            body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write("Content-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\n".getBytes(StandardCharsets.UTF_8));
            body.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            body.write(wav);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
            body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest req = HttpRequest.newBuilder(URI.create(Config.GROQ_BASE_URL.get() + "/audio/transcriptions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() < 200 || r.statusCode() >= 300) throw new SttHttpException(r.statusCode());
            JsonObject obj = JsonParser.parseString(r.body()).getAsJsonObject();
            String text = obj.has("text") ? obj.get("text").getAsString() : null;
            if (text == null || text.isBlank()) return null;
            // Whisper invents text ("موسيقى", "اشتركوا في القناة"...) when it hears noise or silence.
            if (obj.has("segments") && obj.get("segments").isJsonArray()) {
                double noSpeech = 0, logprob = 0; int n = 0;
                for (var el : obj.getAsJsonArray("segments")) {
                    JsonObject sg = el.getAsJsonObject();
                    if (sg.has("no_speech_prob")) noSpeech += sg.get("no_speech_prob").getAsDouble();
                    if (sg.has("avg_logprob")) logprob += sg.get("avg_logprob").getAsDouble();
                    n++;
                }
                if (n > 0) {
                    double ns = noSpeech / n;
                    double lp = logprob / n;
                    // Whisper confidence is a guard, not a command parser. The old 0.80/-1.60
                    // gate rejected real short Arabic speech on ordinary microphones. Version 60
                    // uses a conservative threshold and allows recognizable Minecraft commands
                    // through even when the acoustic confidence is temporarily poor.
                    boolean lowConfidence = ns > 0.94 || lp < -2.20;
                    boolean commandLike = looksLikeMinecraftCommand(text);
                    com.yaseen.aicore.AICompanion.LOG.info("[AICore][STT] text='{}' segments={} noSpeech={} avgLogprob={} commandLike={} lowConfidence={}",
                            text, n, ns, lp, commandLike, lowConfidence);
                    if (lowConfidence && !commandLike) return null;
                }
            }
            if (isHallucination(text)) {
                com.yaseen.aicore.AICompanion.LOG.info("[AICore] STT rejected (hallucination): '{}'", text);
                return null;
            }
            com.yaseen.aicore.AICompanion.LOG.debug("[AICore] STT heard: '{}'", text);
            return text.trim();
        } catch (SttHttpException e) {
            throw e;
        } catch (Exception e) {
            com.yaseen.aicore.AICompanion.LOG.warn("[AICore] STT request failed: {}", e.toString());
            throw new IllegalStateException("network", e);
        }
    }

    private static final class SttHttpException extends RuntimeException {
        final int code;
        SttHttpException(int code) { super("HTTP " + code); this.code = code; }
    }

    private static String describeSttError(Throwable t) {
        if (t instanceof SttHttpException h) {
            if (h.code == 401 || h.code == 403) return "مفتاح Groq غير صالح (" + h.code + "). راجع groqApiKey في config/aicore-common.toml";
            if (h.code == 429) return "تجاوزت حد استخدام Groq (429). انتظر قليلاً ثم أعد المحاولة.";
            return "فشل Groq في تحويل الصوت (HTTP " + h.code + ").";
        }
        return "تعذر الاتصال بـ Groq (تحقق من الإنترنت).";
    }

    private static boolean looksLikeMinecraftCommand(String text) {
        if (text == null) return false;
        String n = InputNormalizer.normalize(text);
        String[] words = {
                "اصنع", "سوي", "اعمل", "كرافت", "صنع", "اكسر", "كسر", "احفر", "عدن", "عدّن",
                "اجمع", "جيب", "خذ", "اعطني", "اعطيني", "تعال", "اتبعني", "توقف", "وقف",
                "قاتل", "اقتل", "احمني", "استكشف", "ابحث", "ابني", "ضع", "حط", "ازرع", "احصد",
                "mine", "craft", "make", "gather", "follow", "attack", "kill", "build", "stop"
        };
        for (String w : words) if (n.contains(w)) return true;
        return false;
    }

    private static boolean isHallucination(String text) {
        String n = text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{Punct}،؛؟!\\[\\]()*♪♫🎵]+", " ").replaceAll("\\s+", " ").trim();
        if (n.isEmpty()) return true;
        if (n.equals("موسيقى") || n.equals("موسيقي") || n.equals("music") || n.equals("الموسيقى")) return true;
        for (String bad : new String[]{"اشتركوا", "اشترك في القناة", "ترجمة", "مترجم", "نانسي قنقر", "شكرا على المشاهدة",
                "شكرا لمشاهدتكم", "شكرا لك على المشاهدة", "لا تنسوا", "subtitles", "amara.org", "thanks for watching"}) {
            if (n.contains(bad)) return true;
        }
        // Do NOT reject text merely because it appears in STT_PROMPT. The prompt deliberately
        // contains the exact Minecraft commands we want Whisper to recognize (e.g. "قاتلني" or
        // "اصنع سيف"). Treating prompt vocabulary as hallucination caused real commands to be
        // discarded on the live server. The segment no_speech/avg_logprob checks above are the
        // primary noise gate; this method is only for well-known generic Whisper hallucinations.
        return false;
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    public static void speakAsync(ServerPlayer player, String text) {
        if (!Config.VOICE_REPLY.get() || text == null || text.isBlank()) return;
        String key = Config.GROQ_API_KEY.get();
        if (key == null || key.isBlank()) return;

        List<String> chunks = splitForTts(text, MAX_TTS_CHARS);
        final var server = player.server;
        final UUID playerId = player.getUUID();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String chunk : chunks) {
            chain = chain.thenCompose(ignored -> CompletableFuture.supplyAsync(() -> synthesize(key, chunk))
                    .thenAccept(wav -> {
                        if (wav == null || wav.length == 0 || wav.length > ModNetwork.MAX_VOICE_BYTES) return;
                        server.execute(() -> {
                            ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                            if (current != null) {
                                ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> current),
                                        new VoiceReplyPacket(wav));
                            }
                        });
                    }));
        }
    }

    private static List<String> splitForTts(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        String remaining = text.trim();
        while (remaining.length() > maxChars) {
            int cut = remaining.lastIndexOf(' ', maxChars);
            if (cut < maxChars / 2) cut = maxChars;
            chunks.add(remaining.substring(0, cut).trim());
            remaining = remaining.substring(cut).trim();
        }
        if (!remaining.isEmpty()) chunks.add(remaining);
        return chunks;
    }

    private static byte[] synthesize(String key, String text) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("model", Config.TTS_MODEL.get());
            obj.addProperty("voice", Config.TTS_VOICE.get());
            obj.addProperty("input", text);
            obj.addProperty("response_format", "wav");
            HttpRequest req = HttpRequest.newBuilder(URI.create(Config.GROQ_BASE_URL.get() + "/audio/speech"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(obj.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> r = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return r.statusCode() >= 200 && r.statusCode() < 300 ? r.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static CompanionEntity findCompanion(ServerPlayer player) {
        return com.yaseen.aicore.event.ServerEvents.findOwnedCompanion(player.server, player.getUUID());
    }

    private GroqVoice() {}
}
