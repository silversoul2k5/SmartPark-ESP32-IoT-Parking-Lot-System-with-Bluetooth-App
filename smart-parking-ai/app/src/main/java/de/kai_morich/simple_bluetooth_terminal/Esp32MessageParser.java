package de.kai_morich.simple_bluetooth_terminal;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Esp32MessageParser {

    private static final Pattern GATE_PATTERN = Pattern.compile("(?i)gate\\s*[:=]\\s*([a-z0-9_]+)");
    private static final Pattern LOT_PATTERN = Pattern.compile("(?i)(parking|lot|availability)\\s*[:=]\\s*([a-z0-9_ ]+)");
    private static final Pattern SLOTS_PATTERN = Pattern.compile("(?i)slots?\\s*[:=]\\s*([^;\\n]+)");
    private static final Pattern FREE_PATTERN = Pattern.compile("(?i)free\\s*[:=]\\s*([0-9, ]+)");
    private static final Pattern OCCUPIED_PATTERN = Pattern.compile("(?i)occupied\\s*[:=]\\s*([0-9, ]+)");

    private Esp32MessageParser() {}

    static boolean applyLine(String line, ParkingState state) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        String trimmed = line.trim();
        boolean changed = false;

        if (trimmed.equalsIgnoreCase("A")) {
            List<ParkingState.SlotState> slots = new ArrayList<>();
            slots.add(ParkingState.SlotState.FREE);
            state.setSlots(slots);
            state.setLotState(ParkingState.LotState.AVAILABLE);
            return true;
        }

        if (trimmed.equalsIgnoreCase("O")) {
            List<ParkingState.SlotState> slots = new ArrayList<>();
            slots.add(ParkingState.SlotState.OCCUPIED);
            state.setSlots(slots);
            state.setLotState(ParkingState.LotState.FULL);
            return true;
        }

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            changed |= applyJson(trimmed, state);
        }
        changed |= applyPattern(trimmed, GATE_PATTERN, matcher ->
                state.setGateState(ParkingState.GateState.fromToken(matcher.group(1))));
        changed |= applyPattern(trimmed, LOT_PATTERN, matcher ->
                state.setLotState(ParkingState.LotState.fromToken(matcher.group(2))));
        changed |= applyPattern(trimmed, SLOTS_PATTERN, matcher ->
                state.setSlots(parseSlotStates(matcher.group(1))));
        changed |= applyPattern(trimmed, FREE_PATTERN, matcher ->
                state.setFreeSlotsByIndex(parseIntegerList(matcher.group(1))));
        changed |= applyPattern(trimmed, OCCUPIED_PATTERN, matcher ->
                state.setOccupiedSlotsByIndex(parseIntegerList(matcher.group(1))));

        if (!changed) {
            ParkingState.GateState gateState = ParkingState.GateState.fromToken(trimmed);
            if (gateState != ParkingState.GateState.UNKNOWN) {
                state.setGateState(gateState);
                changed = true;
            }
            ParkingState.LotState lotState = ParkingState.LotState.fromToken(trimmed);
            if (lotState != ParkingState.LotState.UNKNOWN) {
                state.setLotState(lotState);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean applyJson(String line, ParkingState state) {
        try {
            JSONObject json = new JSONObject(line);
            boolean changed = false;
            if (json.has("gate")) {
                state.setGateState(ParkingState.GateState.fromToken(json.optString("gate")));
                changed = true;
            }
            if (json.has("gateOpen")) {
                state.setGateState(json.optBoolean("gateOpen")
                        ? ParkingState.GateState.OPEN
                        : ParkingState.GateState.CLOSED);
                changed = true;
            }
            if (json.has("parking")) {
                state.setLotState(ParkingState.LotState.fromToken(json.optString("parking")));
                changed = true;
            }
            if (json.has("availability")) {
                state.setLotState(ParkingState.LotState.fromToken(json.optString("availability")));
                changed = true;
            }
            if (json.has("slots")) {
                JSONArray slotsArray = json.optJSONArray("slots");
                if (slotsArray != null) {
                    state.setSlots(parseSlotStates(slotsArray));
                    changed = true;
                }
            }
            return changed;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean applyPattern(String line, Pattern pattern, MatchAction action) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) {
            return false;
        }
        action.apply(matcher);
        return true;
    }

    private static List<ParkingState.SlotState> parseSlotStates(JSONArray array) {
        List<ParkingState.SlotState> parsed = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            parsed.add(ParkingState.SlotState.fromToken(String.valueOf(array.opt(i))));
        }
        return parsed;
    }

    private static List<ParkingState.SlotState> parseSlotStates(String rawValue) {
        String cleaned = rawValue.replace("[", "").replace("]", "").trim();
        List<ParkingState.SlotState> parsed = new ArrayList<>();
        if (cleaned.matches("[01?]+")) {
            for (char value : cleaned.toCharArray()) {
                parsed.add(ParkingState.SlotState.fromToken(String.valueOf(value)));
            }
            return parsed;
        }
        String[] tokens = cleaned.split("[,\\s]+");
        for (String token : tokens) {
            if (!token.isEmpty()) {
                parsed.add(ParkingState.SlotState.fromToken(token));
            }
        }
        return parsed;
    }

    private static List<Integer> parseIntegerList(String rawValue) {
        List<Integer> parsed = new ArrayList<>();
        String[] tokens = rawValue.split("[,\\s]+");
        for (String token : tokens) {
            if (!token.isEmpty()) {
                try {
                    parsed.add(Integer.parseInt(token));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed slot indices instead of failing the whole update.
                }
            }
        }
        return parsed;
    }

    private interface MatchAction {
        void apply(Matcher matcher);
    }
}
