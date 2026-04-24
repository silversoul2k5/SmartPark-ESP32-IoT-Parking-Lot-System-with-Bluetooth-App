package de.kai_morich.simple_bluetooth_terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

class ParkingState {

    enum GateState {
        OPEN("Open"),
        CLOSED("Closed"),
        UNKNOWN("Unknown");

        private final String label;

        GateState(String label) {
            this.label = label;
        }

        String getLabel() {
            return label;
        }

        static GateState fromToken(String token) {
            if (token == null) {
                return UNKNOWN;
            }
            String normalized = token.trim().toLowerCase(Locale.US);
            if (normalized.equals("1") || normalized.contains("open")) {
                return OPEN;
            }
            if (normalized.equals("0") || normalized.contains("close")) {
                return CLOSED;
            }
            return UNKNOWN;
        }
    }

    enum LotState {
        AVAILABLE,
        FULL,
        UNKNOWN;

        static LotState fromToken(String token) {
            if (token == null) {
                return UNKNOWN;
            }
            String normalized = token.trim().toLowerCase(Locale.US);
            if (normalized.contains("available") || normalized.equals("free") || normalized.contains("space")) {
                return AVAILABLE;
            }
            if (normalized.contains("full") || normalized.contains("occupied")) {
                return FULL;
            }
            return UNKNOWN;
        }
    }

    enum SlotState {
        FREE("Free"),
        OCCUPIED("Occupied"),
        UNKNOWN("Waiting");

        private final String label;

        SlotState(String label) {
            this.label = label;
        }

        String getLabel() {
            return label;
        }

        static SlotState fromToken(String token) {
            if (token == null) {
                return UNKNOWN;
            }
            String normalized = token.trim().toLowerCase(Locale.US);
            if (normalized.isEmpty() || normalized.equals("?") || normalized.equals("unknown")) {
                return UNKNOWN;
            }
            if (normalized.equals("0") || normalized.equals("false") || normalized.equals("free") || normalized.equals("available") || normalized.equals("open")) {
                return FREE;
            }
            if (normalized.equals("1") || normalized.equals("true") || normalized.equals("occupied") || normalized.equals("busy") || normalized.equals("full") || normalized.equals("closed")) {
                return OCCUPIED;
            }
            return UNKNOWN;
        }
    }

    static final class Snapshot {
        private final boolean bluetoothConnected;
        private final GateState gateState;
        private final LotState lotState;
        private final List<SlotState> slots;

        Snapshot(boolean bluetoothConnected, GateState gateState, LotState lotState, List<SlotState> slots) {
            this.bluetoothConnected = bluetoothConnected;
            this.gateState = gateState;
            this.lotState = lotState;
            this.slots = Collections.unmodifiableList(slots);
        }

        boolean isBluetoothConnected() {
            return bluetoothConnected;
        }

        GateState getGateState() {
            return gateState;
        }

        List<SlotState> getSlots() {
            return slots;
        }

        String getGateLabel() {
            return gateState.getLabel();
        }

        String getHeadline() {
            switch (getEffectiveLotState()) {
                case AVAILABLE:
                    return "Parking Available";
                case FULL:
                    return "Parking Full";
                default:
                    return "Waiting For Sensor Data";
            }
        }

        String getSummary() {
            if (getKnownSlotCount() > 0) {
                List<String> freeSlots = getSlotLabels(SlotState.FREE);
                int unknownCount = getSlotLabels(SlotState.UNKNOWN).size();
                if (freeSlots.isEmpty()) {
                    if (unknownCount == 0) {
                        return "All " + slots.size() + " slots are occupied.";
                    }
                    return "No free slots confirmed yet. " + unknownCount + " slots are still waiting for sensor data.";
                }
                String summary = freeSlots.size() == 1
                        ? freeSlots.get(0) + " is free."
                        : joinLabels(freeSlots) + " are free.";
                if (unknownCount > 0) {
                    summary += " " + unknownCount + (unknownCount == 1 ? " slot is" : " slots are") + " still waiting for sensor data.";
                }
                return summary;
            }
            switch (lotState) {
                case AVAILABLE:
                    return "The lot has space available, but exact slot numbers have not arrived yet.";
                case FULL:
                    return "The lot reports full occupancy right now.";
                default:
                    return "The app is waiting for ESP32 updates about the lot.";
            }
        }

        String getParkingSpeech() {
            if (hasKnownSlotData()) {
                List<String> freeSlots = getSlotLabels(SlotState.FREE);
                if (freeSlots.isEmpty()) {
                    return "The parking lot is full.";
                }
                return freeSlots.size() == 1
                        ? "Parking is available. " + freeSlots.get(0) + " is free."
                        : "Parking is available. " + joinLabels(freeSlots) + " are free.";
            }
            switch (lotState) {
                case AVAILABLE:
                    return "Parking is available, but I do not know the exact slot numbers yet.";
                case FULL:
                    return "The parking lot is full.";
                default:
                    return "I have not received slot data from the ESP32 yet.";
            }
        }

        String getStatusSpeech() {
            return getParkingSpeech() + " " + getGateSpeech();
        }

        String getOpenSpeech() {
            return hasKnownSlotData()
                    ? "Opening the gate now. " + getParkingSpeech()
                    : "Opening the gate now.";
        }

        String getCloseSpeech() {
            return hasKnownSlotData()
                    ? "Closing the gate now. " + getParkingSpeech()
                    : "Closing the gate now.";
        }

        String toAssistantContext() {
            StringBuilder builder = new StringBuilder();
            builder.append("Bluetooth connected: ").append(bluetoothConnected ? "yes" : "no").append('\n');
            builder.append("Gate state: ").append(gateState.getLabel()).append('\n');
            builder.append("Parking summary: ").append(getSummary()).append('\n');
            builder.append("Parking speech summary: ").append(getParkingSpeech()).append('\n');
            builder.append("Slot list: ").append(getSlotContext()).append('\n');
            builder.append("Gate open command: 1").append('\n');
            builder.append("Gate close command: 0");
            return builder.toString();
        }

        private String getGateSpeech() {
            switch (gateState) {
                case OPEN:
                    return "The gate is open.";
                case CLOSED:
                    return "The gate is closed.";
                default:
                    return "The gate state is unknown.";
            }
        }

        boolean hasKnownSlotData() {
            return getKnownSlotCount() > 0;
        }

        private int getKnownSlotCount() {
            int knownCount = 0;
            for (SlotState state : slots) {
                if (state != SlotState.UNKNOWN) {
                    knownCount++;
                }
            }
            return knownCount;
        }

        private LotState getEffectiveLotState() {
            if (getKnownSlotCount() > 0) {
                return getSlotLabels(SlotState.FREE).isEmpty() ? LotState.FULL : LotState.AVAILABLE;
            }
            return lotState;
        }

        private List<String> getSlotLabels(SlotState targetState) {
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i) == targetState) {
                    labels.add("Slot " + (i + 1));
                }
            }
            return labels;
        }

        private String getSlotContext() {
            List<String> descriptions = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                descriptions.add("Slot " + (i + 1) + "=" + slots.get(i).getLabel());
            }
            return descriptions.isEmpty() ? "No slots configured" : joinLabels(descriptions);
        }
    }

    private final ArrayList<SlotState> slots = new ArrayList<>();
    private GateState gateState = GateState.UNKNOWN;
    private LotState lotState = LotState.UNKNOWN;

    ParkingState(int initialSlotCount) {
        ensureSlotCount(initialSlotCount);
    }

    void setGateState(GateState gateState) {
        if (gateState != null) {
            this.gateState = gateState;
        }
    }

    void setLotState(LotState lotState) {
        if (lotState != null) {
            this.lotState = lotState;
        }
    }

    void setSlots(List<SlotState> newSlots) {
        if (newSlots == null || newSlots.isEmpty()) {
            return;
        }
        slots.clear();
        slots.addAll(newSlots);
    }

    void setFreeSlotsByIndex(List<Integer> freeSlotIndices) {
        if (freeSlotIndices == null || freeSlotIndices.isEmpty()) {
            return;
        }
        int maxIndex = Collections.max(freeSlotIndices);
        ensureSlotCount(maxIndex);
        for (int i = 0; i < slots.size(); i++) {
            slots.set(i, freeSlotIndices.contains(i + 1) ? SlotState.FREE : SlotState.OCCUPIED);
        }
    }

    void setOccupiedSlotsByIndex(List<Integer> occupiedSlotIndices) {
        if (occupiedSlotIndices == null || occupiedSlotIndices.isEmpty()) {
            return;
        }
        int maxIndex = Collections.max(occupiedSlotIndices);
        ensureSlotCount(maxIndex);
        for (int i = 0; i < slots.size(); i++) {
            slots.set(i, occupiedSlotIndices.contains(i + 1) ? SlotState.OCCUPIED : SlotState.FREE);
        }
    }

    Snapshot snapshot(boolean bluetoothConnected) {
        return new Snapshot(bluetoothConnected, gateState, lotState, new ArrayList<>(slots));
    }

    private void ensureSlotCount(int count) {
        if (count <= 0) {
            return;
        }
        while (slots.size() < count) {
            slots.add(SlotState.UNKNOWN);
        }
    }

    private static String joinLabels(List<String> labels) {
        if (labels.isEmpty()) {
            return "";
        }
        if (labels.size() == 1) {
            return labels.get(0);
        }
        if (labels.size() == 2) {
            return labels.get(0) + " and " + labels.get(1);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) {
                builder.append(i == labels.size() - 1 ? ", and " : ", ");
            }
            builder.append(labels.get(i));
        }
        return builder.toString();
    }
}
