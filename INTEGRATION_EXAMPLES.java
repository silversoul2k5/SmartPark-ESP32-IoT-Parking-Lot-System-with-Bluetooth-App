// Example Integration in TerminalFragment.java

// Method to set parking lot priority
// Add this to TerminalFragment class:

/**
 * Set the preferred parking slot priority (1-4)
 * Car will navigate to this slot or the next available one if occupied
 * 
 * @param slotNumber 1-4, the priority slot to navigate to
 */
public void setParkingPriority(int slotNumber) {
    if (parkingMapView != null && slotNumber >= 1 && slotNumber <= 4) {
        parkingMapView.setPreferredSlot(slotNumber);
    }
}

// Usage Examples:

// Example 1: Set priority from UI button click
// In your activity/fragment:
findViewById(R.id.btn_slot_1).setOnClickListener(v -> {
    terminalFragment.setParkingPriority(1); // Navigate to Slot 1 or next available
});

findViewById(R.id.btn_slot_2).setOnClickListener(v -> {
    terminalFragment.setParkingPriority(2); // Navigate to Slot 2 or next available
});

// Example 2: Auto-set priority based on availability
// When receiving data from server:
public void onParkingDataReceived(ParkingSnapshot snapshot) {
    // Update slot states
    parkingMapView.setSlotStates(snapshot.getSlots());
    
    // Find first available slot
    List<ParkingState.SlotState> slots = snapshot.getSlots();
    for (int i = 0; i < slots.size(); i++) {
        if (slots.get(i) == ParkingState.SlotState.FREE) {
            parkingMapView.setPreferredSlot(i + 1);
            break;
        }
    }
}

// Example 3: Sequential priority assignment
// For demonstration/testing:
private int currentPriority = 1;

public void cycleSlotPriority() {
    parkingMapView.setPreferredSlot(currentPriority);
    currentPriority = (currentPriority % 4) + 1; // Cycle through 1,2,3,4
}

// Example 4: Voice command or sensor-based priority
public void setPriorityFromSensor(int sensorSlotId) {
    // Convert sensor ID (if different) to UI slot number
    int uiSlotNumber = convertSensorToUISlot(sensorSlotId);
    parkingMapView.setPreferredSlot(uiSlotNumber);
}

// Example 5: Bluetooth command from parking system
public void onBluetoothPriorityCommand(String command) {
    // Parse command like "PRIORITY:2"
    if (command.startsWith("PRIORITY:")) {
        int slot = Integer.parseInt(command.substring(9));
        parkingMapView.setPreferredSlot(slot);
    }
}

