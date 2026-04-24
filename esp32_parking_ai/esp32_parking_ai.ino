#include <BluetoothSerial.h>
#include <ESP32Servo.h>

BluetoothSerial SerialBT;
Servo gateServo;

// Pins
#define TRIG_PIN 5
#define ECHO_PIN 18
#define SERVO_PIN 13
#define RED_LED 25
#define GREEN_LED 26

// Settings
#define OPEN_ANGLE 90
#define CLOSE_ANGLE 0
#define THRESHOLD_CM 15
#define SENSOR_INTERVAL_MS 200
#define STATUS_INTERVAL_MS 3000

// The Android app expects 0 = free, 1 = occupied.
bool isOccupied = false;
bool gateOpen = false;
unsigned long lastSensorCheck = 0;
unsigned long lastStatusSend = 0;

void setup() {
  Serial.begin(115200);
  SerialBT.begin("ESP32_Parking");

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  pinMode(RED_LED, OUTPUT);
  pinMode(GREEN_LED, OUTPUT);

  gateServo.attach(SERVO_PIN);
  closeGate(false);

  setLED(isOccupied);

  Serial.println("System Ready");
  sendFullStatus();
}

void loop() {
  handleBluetoothCommands();

  unsigned long now = millis();
  if (now - lastSensorCheck >= SENSOR_INTERVAL_MS) {
    lastSensorCheck = now;
    updateParkingState();
  }

  if (now - lastStatusSend >= STATUS_INTERVAL_MS) {
    lastStatusSend = now;
    sendFullStatus();
  }
}

void handleBluetoothCommands() {
  while (SerialBT.available()) {
    char cmd = SerialBT.read();

    if (cmd == '1') {
      openGate(true);
    } else if (cmd == '0') {
      closeGate(true);
    }
  }
}

void updateParkingState() {
  long distance = getDistance();
  bool currentState = distance > 0 && distance < THRESHOLD_CM;

  if (currentState != isOccupied) {
    isOccupied = currentState;
    setLED(isOccupied);
    sendParkingStatus();

    if (isOccupied) {
      Serial.println("Occupied");
    } else {
      Serial.println("Available");
    }
  }
}

void openGate(bool notifyApp) {
  gateServo.write(OPEN_ANGLE);
  gateOpen = true;
  Serial.println("Gate Open");

  if (notifyApp) {
    sendGateStatus();
  }
}

void closeGate(bool notifyApp) {
  gateServo.write(CLOSE_ANGLE);
  gateOpen = false;
  Serial.println("Gate Close");

  if (notifyApp) {
    sendGateStatus();
  }
}

void setLED(bool occupied) {
  if (occupied) {
    digitalWrite(RED_LED, LOW);   // ON for common-anode LEDs
    digitalWrite(GREEN_LED, HIGH);
  } else {
    digitalWrite(RED_LED, HIGH);
    digitalWrite(GREEN_LED, LOW);
  }
}

void sendFullStatus() {
  sendGateStatus();
  sendParkingStatus();
}

void sendGateStatus() {
  if (gateOpen) {
    SerialBT.println("GATE:OPEN");
    Serial.println("BT -> GATE:OPEN");
  } else {
    SerialBT.println("GATE:CLOSED");
    Serial.println("BT -> GATE:CLOSED");
  }
}

void sendParkingStatus() {
  if (isOccupied) {
    SerialBT.println("PARKING:FULL");
    SerialBT.println("SLOTS:1");
    Serial.println("BT -> PARKING:FULL");
    Serial.println("BT -> SLOTS:1");
  } else {
    SerialBT.println("PARKING:AVAILABLE");
    SerialBT.println("SLOTS:0");
    Serial.println("BT -> PARKING:AVAILABLE");
    Serial.println("BT -> SLOTS:0");
  }
}

long getDistance() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);

  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 25000);
  if (duration == 0) {
    return -1;
  }

  return duration * 0.034 / 2;
}
