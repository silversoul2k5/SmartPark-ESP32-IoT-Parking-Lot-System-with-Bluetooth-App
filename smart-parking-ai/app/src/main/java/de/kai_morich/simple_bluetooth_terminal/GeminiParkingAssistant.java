package de.kai_morich.simple_bluetooth_terminal;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class GeminiParkingAssistant {

    interface Callback {
        void onDecision(AiDecision decision);
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String MODEL_NAME = "gemini-2.5-flash";
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/"
            + MODEL_NAME + ":generateContent?key=";

    private final String apiKey;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    GeminiParkingAssistant(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    boolean isGeminiConfigured() {
        return !apiKey.isEmpty();
    }

    void shutdown() {
        executor.shutdownNow();
    }

    void interpret(String userText, ParkingState.Snapshot snapshot, Callback callback) {
        executor.execute(() -> {
            AiDecision decision;
            if (isGeminiConfigured()) {
                try {
                    decision = sanitizeDecision(requestGemini(userText, snapshot), userText, snapshot);
                } catch (Exception e) {
                    decision = buildFallbackDecision(
                            userText,
                            snapshot,
                            getGeminiFailurePrefix(e));
                }
            } else {
                decision = buildFallbackDecision(userText, snapshot, "");
            }
            AiDecision finalDecision = decision;
            mainHandler.post(() -> callback.onDecision(finalDecision));
        });
    }

    private AiDecision requestGemini(String userText, ParkingState.Snapshot snapshot) throws Exception {
        JSONObject requestBody = new JSONObject();
        requestBody.put("systemInstruction", new JSONObject()
                .put("parts", new JSONArray().put(new JSONObject()
                        .put("text", buildSystemInstruction(snapshot)))));
        requestBody.put("contents", new JSONArray().put(new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject()
                        .put("text", "User request: " + userText)))));
        requestBody.put("generationConfig", new JSONObject()
                .put("temperature", 0.2)
                .put("responseMimeType", "application/json"));

        Request request = new Request.Builder()
                .url(ENDPOINT + apiKey)
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) {
                throw new IOException("Online AI request failed with code " + response.code());
            }
            String payload = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException(readGeminiError(response.code(), payload));
            }
            return parseDecision(payload, snapshot);
        }
    }

    private AiDecision parseDecision(String payload, ParkingState.Snapshot snapshot) throws Exception {
        JSONObject root = new JSONObject(payload);
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new IOException("Online AI returned no response");
        }
        JSONObject firstCandidate = candidates.getJSONObject(0);
        JSONObject content = firstCandidate.optJSONObject("content");
        if (content == null) {
            throw new IOException("Online AI returned empty content");
        }
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.length() == 0) {
            throw new IOException("Online AI returned no content parts");
        }
        String rawText = stripJsonDecorators(parts.getJSONObject(0).optString("text", ""));
        if (rawText.isEmpty()) {
            throw new IOException("Online AI returned empty text");
        }
        JSONObject decisionJson = new JSONObject(rawText);
        AiDecision.Action action = AiDecision.Action.fromValue(decisionJson.optString("action", "NONE"));
        String command = emptyToNull(decisionJson.optString("command", ""));
        String reply = decisionJson.optString("reply", "").trim();
        if (reply.isEmpty()) {
            reply = defaultReply(action, snapshot);
        }
        return new AiDecision(action, command, reply);
    }

    private AiDecision sanitizeDecision(AiDecision decision, String userText, ParkingState.Snapshot snapshot) {
        AiDecision.Action action = decision.getAction();
        String normalized = userText.toLowerCase(Locale.US);
        boolean userAskedToOpen = isOpenIntent(normalized);
        boolean userAskedToClose = isCloseIntent(normalized);
        if (userAskedToOpen && action == AiDecision.Action.NONE) {
            action = AiDecision.Action.OPEN_GATE;
        } else if (userAskedToClose && action == AiDecision.Action.NONE) {
            action = AiDecision.Action.CLOSE_GATE;
        }

        String reply = decision.getReply();
        String command = null;

        if (!snapshot.isBluetoothConnected()
                && (action == AiDecision.Action.OPEN_GATE || action == AiDecision.Action.CLOSE_GATE)) {
            return new AiDecision(
                    AiDecision.Action.NONE,
                    null,
                    "Bluetooth is not connected yet, so I cannot move the gate. " + snapshot.getParkingSpeech());
        }
        if (action == AiDecision.Action.OPEN_GATE) {
            command = "1";
            reply = snapshot.getOpenSpeech();
        } else if (action == AiDecision.Action.CLOSE_GATE) {
            command = "0";
            reply = snapshot.getCloseSpeech();
        }
        return new AiDecision(action, command, reply);
    }

    private AiDecision buildFallbackDecision(String userText, ParkingState.Snapshot snapshot, String prefix) {
        String normalized = userText.toLowerCase(Locale.US);
        boolean wantsOpen = isOpenIntent(normalized);
        boolean wantsClose = isCloseIntent(normalized);
        boolean wantsStatus = containsAny(normalized,
                "slot", "parking", "available", "free", "occupied", "status", "how many", "which");

        if (wantsOpen) {
            if (snapshot.isBluetoothConnected()) {
                return new AiDecision(AiDecision.Action.OPEN_GATE, "1", prefix + snapshot.getOpenSpeech());
            }
            return new AiDecision(AiDecision.Action.NONE, null,
                    prefix + "Bluetooth is not connected yet, so I cannot open the gate. " + snapshot.getParkingSpeech());
        }
        if (wantsClose) {
            if (snapshot.isBluetoothConnected()) {
                return new AiDecision(AiDecision.Action.CLOSE_GATE, "0", prefix + snapshot.getCloseSpeech());
            }
            return new AiDecision(AiDecision.Action.NONE, null,
                    prefix + "Bluetooth is not connected yet, so I cannot close the gate. " + snapshot.getParkingSpeech());
        }
        if (wantsStatus) {
            return new AiDecision(AiDecision.Action.STATUS, null, prefix + snapshot.getStatusSpeech());
        }
        return new AiDecision(
                AiDecision.Action.NONE,
                null,
                prefix + "I can open or close the gate and explain parking availability. Try saying: I am in front of the gate.");
    }

    private String buildSystemInstruction(ParkingState.Snapshot snapshot) {
        return "You are the voice assistant inside a smart parking Android app.\n"
                + "Your job is to understand a driver's sentence, decide whether the gate should open or close, "
                + "and respond using the live parking state.\n"
                + "Return only JSON with exactly these keys: action, command, reply.\n"
                + "Valid action values: OPEN_GATE, CLOSE_GATE, STATUS, NONE.\n"
                + "Valid command values: \"1\" for opening the gate, \"0\" for closing the gate, or \"\" when no command is needed.\n"
                + "If Bluetooth is disconnected, do not send a command.\n"
                + "Opening or closing the gate does not require slot data.\n"
                + "If the user says they are in front of the gate, at the gate, or wants to enter, use OPEN_GATE with command \"1\" even when slot data is unknown.\n"
                + "Do not refuse or delay gate movement because slot details are missing.\n"
                + "If the user asks about parking, answer using the slot information.\n"
                + "Keep reply natural and concise.\n\n"
                + "Live app context:\n"
                + snapshot.toAssistantContext();
    }

    private static boolean isOpenIntent(String normalized) {
        return containsAny(normalized,
                "open gate", "open the gate", "at the gate", "front of the gate", "in front of the gate",
                "let me in", "allow entry", "enter the parking", "open barrier");
    }

    private static boolean isCloseIntent(String normalized) {
        return containsAny(normalized,
                "close gate", "close the gate", "shut gate", "shut the gate", "close barrier");
    }

    private static boolean containsAny(String value, String... options) {
        for (String option : options) {
            if (value.contains(option)) {
                return true;
            }
        }
        return false;
    }

    private static String stripJsonDecorators(String rawText) {
        String cleaned = rawText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```json", "");
            cleaned = cleaned.replaceFirst("^```", "");
            cleaned = cleaned.replaceFirst("```$", "");
        }
        return cleaned.trim();
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String readGeminiError(int responseCode, String payload) {
        try {
            JSONObject root = new JSONObject(payload);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "").trim();
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (Exception ignored) {
            // Fall back to a compact HTTP message below.
        }
        return "Online AI request failed with code " + responseCode;
    }

    private static String getGeminiFailurePrefix(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "Unknown online assistant error";
        }
        message = message.replaceAll("(?i)gemini", "online AI");
        return "Online assistant error: " + message + ". I used local parking logic instead. ";
    }

    private static String defaultReply(AiDecision.Action action, ParkingState.Snapshot snapshot) {
        switch (action) {
            case OPEN_GATE:
                return snapshot.getOpenSpeech();
            case CLOSE_GATE:
                return snapshot.getCloseSpeech();
            case STATUS:
                return snapshot.getStatusSpeech();
            default:
                return "I can help with the gate and parking availability.";
        }
    }
}
