# Smart Parking AI

An Android Studio project for an ESP32 parking-lot controller, built on top of the classic Bluetooth SPP sample from `SimpleBluetoothTerminal`.

This version keeps the paired-device connect flow from the original app, then adds:

- Voice input with Android speech recognition
- Gemini-powered intent handling for commands like `I am in front of the gate`
- Text-to-speech replies
- Manual `Open Gate` and `Close Gate` fallback buttons
- A parking dashboard that updates from ESP32 serial messages

## What It Uses

- Bluetooth Classic / SPP for ESP32 communication
- `1` to open the servo gate
- `0` to close the servo gate
- Gemini `gemini-2.5-flash` for natural-language decisions
- Local fallback logic when no Gemini key is configured

## Open In Android Studio

Open this folder in Android Studio:

`/home/arjun/Projects/Parkinglot_sys_AI/smart-parking-ai`

## Gemini Setup

Add your Gemini API key in:

`local.properties`

Example:

```properties
sdk.dir=/home/arjun/Android/Sdk
GEMINI_API_KEY=your_real_key_here
```

If `GEMINI_API_KEY` is blank, the app still works with local fallback rules for simple phrases like:

- `I am in front of the gate`
- `Open the gate`
- `Close the gate`
- `Which slots are free?`

## ESP32 Message Format

The app listens for text lines from the ESP32 and updates the dashboard when it sees formats like these:

```text
GATE:OPEN
GATE:CLOSED
PARKING:AVAILABLE
PARKING:FULL
SLOTS:0,1,0,1
SLOTS:0101
FREE:1,3
OCCUPIED:2,4
```

For slot messages in `SLOTS:...`, the app assumes:

- `0 = free`
- `1 = occupied`

It also accepts JSON:

```json
{"gate":"open","slots":[0,1,0,1]}
```

## Build

From the project folder:

```bash
./gradlew assembleDebug
```

The debug APK is created at:

`app/build/outputs/apk/debug/app-debug.apk`

## Notes

- This app is based on the classic Bluetooth project, not the BLE sample.
- That means your ESP32 should expose a classic Bluetooth serial connection compatible with SPP.
- If your hardware is using BLE instead, the UI and AI layer here can still be reused, but the transport layer should be switched to the BLE base project.
