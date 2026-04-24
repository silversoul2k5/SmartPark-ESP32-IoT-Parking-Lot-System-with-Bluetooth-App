# Parking Lot Navigation System - Priority Slot Guide

## Overview
The parking lot system now includes intelligent slot-priority based navigation. When a car enters the parking lot, it will automatically navigate to available slots based on a priority order: **Slot 1 → Slot 2 → Slot 3 → Slot 4**.

## Key Features Implemented

### 1. **Priority-Based Slot Navigation**
- The car automatically navigates to the first available slot in priority order
- If Slot 1 is occupied, it goes to Slot 2
- If Slots 1 and 2 are occupied, it goes to Slot 3
- If Slots 1, 2, and 3 are occupied, it goes to Slot 4
- If all slots are occupied, the system shows Slot 1 as fallback

### 2. **Visual Indicators**

#### Route Animation
- **Purple Path**: Shows the current route to the assigned slot
- **Purple Pin**: Marks the target parking slot
- **Yellow Car**: Animates along the route to the assigned slot

#### Slot Highlighting
- **Blue/Purple Border**: The assigned slot (currently being routed to) has a special purple border
- **Slot Status**: Shows "Priority" status if a slot is preferred but occupied
- **Route Info**: Displays "Route to Slot X" with "(Preferred: Y)" if different

### 3. **User Interaction**

#### Touch Interaction
When the map is expanded:
- **Tap on any slot**: Sets that slot as the preferred priority
- The car will automatically navigate to that slot or the next available one

#### Example Usage
```
User taps on Slot 3
↓
System sets Preferred Slot = 3
↓
If Slot 3 is FREE: Navigate to Slot 3
If Slot 3 is OCCUPIED: Navigate to Slot 4 (or 1 if all occupied)
↓
Car animates along route to assigned slot
```

## Code Integration

### Using setPreferredSlot()
To programmatically set the priority slot:

```java
// In TerminalFragment.java or wherever you have parkingMapView reference
parkingMapView.setPreferredSlot(1); // Priority order starts from Slot 1
```

### Slot States Update
The system automatically recalculates the assigned slot when states change:

```java
// When slot states are updated from Bluetooth data
List<ParkingState.SlotState> slotStates = ...;
parkingMapView.setSlotStates(slotStates); // Triggers updateAssignedSlot()
```

## Slot State Flow

```
┌─────────────────────────────────────────────────┐
│  setPreferredSlot(X) or setSlotStates(states)   │
├─────────────────────────────────────────────────┤
│           updateAssignedSlot()                  │
│  (Check availability in priority order)         │
├─────────────────────────────────────────────────┤
│  For i = 0 to 3:                                │
│    slotIndex = (preferredSlot - 1 + i) % 4     │
│    If state is FREE or UNKNOWN:                 │
│      → assignedSlot = that slot                 │
│      → Start navigation                         │
│    Else if OCCUPIED:                            │
│      → Try next slot                            │
└─────────────────────────────────────────────────┘
```

## Animation Details

### Car Movement
- **Animation Speed**: Default 0.006 units per frame (adjustable via `carSpeed`)
- **Path**: Dynamic cubic Bézier curve from gate to assigned slot
- **Car Visual**: Yellow rectangle with window, rotates along path
- **Motion**: Smooth animation with optional jitter effect

### When Animation Completes
- Car stays at the assigned slot
- Display shows "Route to Slot X"
- Slot shows purple/blue border to indicate navigation complete

## Examples

### Example 1: Priority Queue in Action
```
Initial State:
- Slot 1: OCCUPIED
- Slot 2: FREE
- Slot 3: OCCUPIED
- Slot 4: FREE

setPreferredSlot(1) called
↓
updateAssignedSlot() checks priority order:
  - Slot 1: OCCUPIED → Skip
  - Slot 2: FREE → ASSIGN
↓
Car animates to Slot 2
Display: "Route to Slot 2 (Preferred: 1)"
```

### Example 2: User Selects Specific Slot
```
User taps on Slot 3
↓
setPreferredSlot(3) called
↓
Current states: [OCCUPIED, OCCUPIED, FREE, FREE]
  - Slot 3: FREE → ASSIGN
↓
Car animates to Slot 3
Display: "Route to Slot 3"
```

### Example 3: All Slots Occupied
```
All slots occupied
↓
updateAssignedSlot() finds no FREE slots
↓
Fallback to Slot 1
↓
Display: "Route to Slot 1"
```

## Slot Color Coding

| Status | Color | Meaning |
|--------|-------|---------|
| FREE | Green | Slot is available |
| OCCUPIED | Red | Slot is taken |
| UNKNOWN | Gray | State not yet determined |
| ASSIGNED | Purple Border | Currently being routed to |

## Advanced Features

### Resetting Navigation
```java
carProgress = 0f; // Reset animation from start
parkingMapView.setPreferredSlot(1); // Reset to Slot 1 priority
```

### Enabling Animation Loop (Optional)
To make the car continuously loop through slots, uncomment in `animateCar()`:
```java
carProgress = 0f;
updateAssignedSlot();
carSpeed = 0.0045f + random.nextFloat() * 0.0045f;
```

### Adjusting Car Speed
```java
carSpeed = 0.003f;  // Slower animation
carSpeed = 0.012f;  // Faster animation
```

## Integration Checklist

- [x] Priority slot selection (1 → 2 → 3 → 4)
- [x] Automatic fallback to next available slot
- [x] Visual slot highlighting for assigned slot
- [x] Route visualization with priority info
- [x] Touch interaction for slot selection
- [x] Animated car navigation
- [x] Dynamic slot state updates

## Troubleshooting

### Car not moving to correct slot
- Check `setSlotStates()` is being called with updated states
- Verify `setPreferredSlot()` is called before starting animation
- Ensure slot states are properly mapped (1-indexed)

### Route not showing
- Confirm `parkingMapView` is properly initialized
- Check that `setExpanded(true)` is called for expanded view
- Verify canvas dimensions are greater than 0

### Touch interaction not working
- Ensure `setExpanded(true)` is set
- Verify touch coordinates fall within expanded map bounds
- Check slot rectangles are calculated correctly

