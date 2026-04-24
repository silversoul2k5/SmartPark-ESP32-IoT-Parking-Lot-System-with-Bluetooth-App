# Parking Map View - Quick Reference

## Class: ParkingMapView

### Key Instance Variables
```java
private int preferredSlot = 1;    // User's priority choice (1-4)
private int assignedSlot = 1;     // Actual slot to navigate to
private float carProgress = 0f;   // Animation progress (0 to 1)
private float carSpeed = 0.006f;  // Animation speed per frame
```

### Public Methods

#### `setPreferredSlot(int slot)`
Sets the desired parking slot priority (1-4).
- Validates slot number (1-4)
- Calls `updateAssignedSlot()`
- Resets animation (`carProgress = 0f`)
- Triggers redraw

```java
// Example
parkingMapView.setPreferredSlot(1);  // Prefer Slot 1
```

#### `setSlotStates(List<ParkingState.SlotState> states)`
Updates the state of all parking slots.
- Updates slot status (FREE, OCCUPIED, UNKNOWN)
- Calls `updateAssignedSlot()` automatically
- Triggers redraw

```java
// Example
List<ParkingState.SlotState> states = snapshot.getSlots();
parkingMapView.setSlotStates(states);
```

#### `setExpanded(boolean expanded)`
Toggles between compact and expanded map view.
- Expanded view: Full parking map visualization
- Compact view: Summary map

```java
parkingMapView.setExpanded(true);   // Show full map
```

### Private Methods

#### `updateAssignedSlot()`
**Core Logic**: Determines which slot car should navigate to.

Priority checking algorithm:
```
For i = 0 to 3:
  Check slot at index: (preferredSlot - 1 + i) % 4
  If FREE or UNKNOWN:
    → Set as assigned slot
    → Return
If all occupied:
  → Fallback to slot 1
```

#### `animateCar()`
**Animation**: Moves car along route from gate to assigned slot.
- Increments `carProgress` by `carSpeed` each frame
- Calls `getPosTan()` to get position and rotation on path
- When `carProgress >= 1f`: Keep at destination

### Drawing Methods

#### `drawSlots(Canvas, width, height)`
Renders parking slots with:
- Slot color based on state (green=FREE, red=OCCUPIED, gray=UNKNOWN)
- **Purple border** for assigned slot
- Slot number and status text
- "Priority" label if preferred ≠ assigned

#### `drawStartAndTarget(Canvas, width, height)`
Shows:
- Gate location (start point)
- Target slot location (purple pin)
- Route text with priority info

#### `drawMovingCar(Canvas, Path)`
Animates yellow car:
- Positioned along route based on `carProgress`
- Rotates to follow path direction
- Adds subtle jitter for visual interest

#### `buildRoute(int slotNumber, width, height)`
Creates Path object:
- Starts at: Gate (bottom center)
- Uses Bézier curves to navigate map lanes
- Ends at: Center of target slot

## Slot Position Map

```
    Slot 1          Slot 3
   [TOP LEFT]     [TOP RIGHT]
   
   
   
   
   Slot 2          Slot 4
  [BOT LEFT]     [BOT RIGHT]
      ↑
    GATE
```

## State Transitions

```
setPreferredSlot(X) or setSlotStates(states)
        ↓
   updateAssignedSlot()
        ↓
   Priority Check Loop
        ↓
   Valid Slot Found?
   ├── YES: assignedSlot = slot
   └── NO: assignedSlot = 1 (fallback)
        ↓
   invalidate() → onDraw()
        ↓
   buildRoute(assignedSlot)
   drawStartAndTarget()
   drawSlots() → purple border
   drawMovingCar() → animate along route
```

## Color Scheme

| Element | Color | Code |
|---------|-------|------|
| Background | Dark Gray | rgb(18, 20, 24) |
| FREE Slot | Green | R.color.slotFree |
| OCCUPIED Slot | Red | R.color.slotOccupied |
| UNKNOWN Slot | Gray | R.color.slotUnknown |
| Route Path | Purple | rgb(132, 124, 255) |
| Route Shadow | Semi-transparent Purple | argb(85, 125, 117, 255) |
| Car Body | Yellow | rgb(247, 196, 39) |
| Car Window | Dark Brown | rgb(40, 38, 35) |
| Pin | Purple | rgb(132, 124, 255) |
| Assigned Border | Purple | rgb(132, 124, 255) |
| Road | Dark Gray | rgb(34, 36, 43) |
| Road Line | Yellow | rgb(237, 192, 57) |

## Animation Parameters

| Parameter | Default | Usage |
|-----------|---------|-------|
| SLOT_COUNT | 4 | Number of parking slots |
| FRAME_DELAY_MS | 33 | Animation frame interval (≈30 FPS) |
| carSpeed | 0.006 | Pixels per frame along path |
| carProgress | 0.0 to 1.0 | Animation completion percentage |

## TouchEvent Handling

When map is expanded (`setExpanded(true)`):
- Tap on a slot → `setPreferredSlot(slot_number)`
- Tap outside slots → True (consumed but no action)
- When collapsed → False (event not consumed)

## DP and SP Conversions

Used for density-independent sizing:
- `dp(value)` = value × display density
- `sp(value)` = value × scaled density

Example:
```java
dp(15)  // 15 pixels at base density, scales on different screens
sp(12)  // 12 scaled pixels for text
```

## Typical Usage Pattern

```java
// 1. Initialize (automatic)
ParkingMapView mapView = findViewById(R.id.parking_map_view);

// 2. Update slot states (from server/Bluetooth)
mapView.setSlotStates(serverData.getSlots());

// 3. Set preferred slot (user clicks or system decides)
mapView.setPreferredSlot(1);

// 4. Animation runs automatically:
// - Route drawn to assigned slot
// - Car animates toward it
// - UI shows purple border on assigned slot

// 5. When user wants different slot
mapView.setPreferredSlot(3); // Restart animation
```

## Debugging Tips

**Car not moving?**
- Check `setSlotStates()` called before `setPreferredSlot()`
- Verify canvas width/height > 0
- Check `animating` flag is true

**Route wrong slot?**
- Verify `assignedSlot` matches intended target
- Check slot states (all OCCUPIED?)
- Confirm `buildRoute()` receives correct slot number

**Colors not showing?**
- Verify R.color.* resources exist in colors.xml
- Check Paint objects initialized in `init()`
- Confirm ContextCompat colors are defined

**Animation stuttering?**
- Check FRAME_DELAY_MS (should be ~33ms for 30 FPS)
- Monitor View hierarchy performance
- Verify no heavy operations in `onDraw()`

