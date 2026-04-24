# 🚗 SmartPark-ESP32  
### AI-Powered IoT Parking Lot System with Bluetooth App & Voice Assistant

---

## 📊 Project Summary

| Category | Details |
|----------|--------|
| 📌 Description | Smart parking system using ESP32 with AI voice assistant and real-time app |
| 🎯 Use Case | Shopping malls / smart parking |
| ⚙️ Controller | ESP32 |
| 📱 App | Java |
| 🤖 AI | Gemini Voice Assistant |
| 🔗 Communication | Bluetooth |

---

## ⚙️ Features

| Feature | Description |
|--------|------------|
| 🚪 Automatic Gate | Opens when vehicle detected |
| 📡 Slot Detection | Ultrasonic sensor-based |
| 🚦 LED Indicator | Red = Occupied, Green = Available |
| 📱 App Control | Manual gate control |
| 🗺️ Mini Map | Real-time parking layout updates |
| 🎤 Voice Assistant | Control system using voice |
| 🤖 Gemini AI | Understands natural language |
| 🔄 Real-Time Sync | Hardware + App sync |

---

## 🤖 AI Voice Assistant (Gemini Integration)

| Capability | Description |
|-----------|------------|
| 🧠 Natural Language | Understands complex sentences |
| 🚪 Smart Gate Control | Opens/closes gate automatically |
| 📊 Status Response | Gives live parking status |
| 📍 Context Awareness | Responds based on user situation |

### 🗣️ Example Commands

| Command | Action |
|--------|--------|
| "I am in front of the parking lot" | Opens gate + shows status |
| "I just left the parking lot" | Closes gate |
| "Is parking available?" | Returns slot status |
| "Open the gate" | Manual open |

---

## 🗺️ Real-Time Parking Mini Map

| Feature | Description |
|--------|------------|
| 🧭 Layout View | Shows parking slots |
| 🟢 Green | Available |
| 🔴 Red | Occupied |
| 🔄 Live Update | Updates instantly |
| 📡 Sync | ESP32 → App |

---

## 🔧 Hardware + Connections

| Component | Connection |
|----------|------------|
| Ultrasonic VCC | 5V |
| Ultrasonic GND | GND |
| TRIG | GPIO 5 |
| ECHO | GPIO 18 (via voltage divider) |
| Servo Signal | GPIO 13 |
| Servo VCC | 5V (external recommended) |
| Servo GND | GND |
| LED Common (Anode) | 5V |
| LED Red | GPIO 25 via 220Ω |
| LED Green | GPIO 26 via 220Ω |

---

## 🔌 Voltage Divider

| From | To |
|-----|----|
| ECHO → 1kΩ → | GPIO 18 |
| GPIO 18 → 2kΩ → | GND |

---

## 📡 Bluetooth Communication

| Action | Data |
|------|------|
| Open Gate | `1` |
| Close Gate | `0` |
| Slot Occupied | `O` |
| Slot Available | `A` |

---

## 📱 SmartPark Mobile App – UI & Screenshots

<table>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/e978c16e-72da-4f3f-816b-8548cf64896e" width="250"/></td>
    <td><img src="https://github.com/user-attachments/assets/bdca32cf-332a-4349-bdf3-e0653ec425f1" width="250"/></td>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/92b56b2f-9e48-4d96-b857-46f626d26883" width="250"/></td>
    <td><img src="https://github.com/user-attachments/assets/add76d6a-8c58-4bce-a4dd-eea4c0cc4817" width="250"/></td>
  </tr>
</table>



---

## 📂 Project Files

| File | Description |
|------|------------|
| ESP32_Code/ | Microcontroller code |
| App/ | Mobile application |
| Circuit_Diagram/ | Wiring |
| Docs/ | Report |

---

## 📚 Reference (Bluetooth Testing Tool)

| Tool | Link |
|------|------|
| Serial Bluetooth Terminal | https://play.google.com/store/apps/details?id=de.kai_morich.serial_bluetooth_terminal |
| Source Code | https://github.com/kai-morich/SimpleBluetoothLeTerminal |

---

## 🚀 How to Run

| Step | Action |
|-----|-------|
| 1 | Upload ESP32 code |
| 2 | Power ESP32 |
| 3 | Pair Bluetooth → ESP32_Parking |
| 4 | Open app |
| 5 | Use buttons or voice assistant |

(Just open folder "smart-parking-ai" on android studio sync then run it )
---

## ❤️ Support This Project

<p align="center">

If you found this project useful, consider supporting 💖

<br><br>

💰 <b>UPI Support</b>  
<br>
Scan the QR below 👇

<br><br>

<img src="https://github.com/user-attachments/assets/740acb3b-98c4-48b5-9bf6-deedb33eeee3" width="220"/>

<br><br>

<sub>⚠️ Please do not send collect requests</sub>

<br><br>

⭐ <b>Star the repo</b> if you like this project!

</p>
## 👨‍💻 Authors

- Arjun C  
- Codex
- ChatGPT
- Claude

---

## 📄 License

Educational use only.
