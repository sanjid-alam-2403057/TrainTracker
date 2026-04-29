# 🚆 Bangladesh Train Tracker

A feature-rich **Java Swing desktop application** for real-time train tracking in Bangladesh. It displays live journey progress, station-by-station route visualization, estimated arrival times, and distance metrics — all updated every second from your system clock.

> Developed as a CSE project at RUET (Rajshahi University of Engineering & Technology), 2026.

---

## 📸 Features

- 🕐 **Live clock** — real-time display of current time and day of the week
- 🚉 **Train info panel** — shows train number, name, route, scheduled departure/arrival, total stops, and distance
- 📍 **Live tracking** — current station, next station, and ETA calculated dynamically
- 📊 **Stats dashboard** — covered km, remaining km, estimated time left, and average speed
- 🗺️ **Route progress bar** — visual track with color-coded dots (passed / current / upcoming stations)
- 📅 **Off-day banner** — automatically detects and notifies when a train does not operate today
- 🔐 **Admin panel** — password-protected login to add or delete trains
- 💾 **Persistent storage** — trains saved to and loaded from `trains.csv`; activity logged to `train_data.txt`
- ➕ **Add/Delete trains** — admin users can manage the full train list through a GUI dialog

---

## 🗂️ Project Structure

```
Hello.java
│
├── Transport          (abstract class — base for transport types)
├── Persistable        (interface — defines saveToFile contract)
├── Station            (data class — station name + scheduled departure)
├── Train              (core model — journey logic, progress, ETA calculation)
├── TrainStore         (file I/O — save/load trains from trains.csv)
├── RouteProgressPanel (custom JPanel — draws the animated route track)
├── LoginDialog        (modal dialog — admin authentication)
├── AddTrainDialog     (modal dialog — form to add a new train)
├── BackgroundPanel    (custom JPanel — optional watermark/background image)
└── Hello              (main JFrame — orchestrates entire UI and timer loop)
```

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 11+ |
| UI Framework | Java Swing (`javax.swing`) |
| Graphics | Java2D (`java.awt`) |
| Date & Time | `java.time` (LocalTime, LocalDate, LocalDateTime) |
| File I/O | `java.io` (BufferedReader, BufferedWriter, FileWriter) |
| Build | Plain `javac` / any Java IDE |

---

## ⚙️ Requirements

- **Java 11 or higher** (Java 17+ recommended)
- No external libraries or Maven/Gradle needed — pure Java SE

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/bangladesh-train-tracker.git
cd bangladesh-train-tracker
```

### 2. Compile

```bash
javac Hello.java
```

### 3. Run

```bash
java Hello
```

The application window will launch. If `trains.csv` exists in the same directory, previously saved trains will be loaded automatically.

---

## 🔐 Admin Access

To add or delete trains, log in as admin:

- Click **"Admin Login"** in the top-right corner
- Enter the password: `admin123`
- You will now see **Add** and **Delete** buttons

> ⚠️ **Security Note:** The admin password is hardcoded in `LoginDialog`. For any real-world or production deployment, replace this with a hashed credential stored in a config file or environment variable.

---

## 📁 Data Files

| File | Purpose |
|------|---------|
| `trains.csv` | Stores all train records (auto-created on first save) |
| `train_data.txt` | Append-only activity log with timestamps |

### `trains.csv` Format

Each train occupies a block separated by a blank line:

```
TrainNumber|TrainName|Origin|Destination|OffDay|DistanceKm|AvgSpeedKmh
StationName1|HH:mm
StationName2|HH:mm
...
```

**Example:**
```
700|Silk City Express|Dhaka|Rajshahi|Tuesday|262|70
Dhaka|07:40
Joydebpur|08:15
Tangail|09:10
Rajshahi|12:30
```

---

## 🧠 How Live Tracking Works

1. Every **1 second**, the app reads the current system time.
2. It computes **elapsed minutes** since the train's departure.
3. Using the total journey duration and distance, it calculates:
   - Current progress percentage
   - Kilometers covered and remaining
   - Live ETA to final destination (blended schedule + speed model)
   - Current and next station based on stop timestamps
4. The route panel repaints with an animated train marker (▲) on the track.

---

## 🎨 UI Theme

The interface uses a custom **dark theme**:

| Role | Color |
|------|-------|
| Background | `#1C1C24` (dark navy) |
| Card background | `#262632` |
| Accent / progress | Gold `#FFD700` |
| Passed stations | Green `#22C55E` |
| Upcoming stations | Red `#DC3232` |
| Text (main) | `#E6E6E6` |
| Text (muted) | `#8C8CA0` |

Font used: **Roboto** (falls back to system default if not installed).

---

## 🧩 Class Responsibilities

### `Train`
The core model class. Calculates:
- `progress(nowMin)` — journey completion ratio (0.0 to 1.0)
- `coveredKm()` / `remainingKm()` — distance metrics
- `liveArrivalETA()` — blended ETA using schedule + speed
- `currentStopIndex()` / `nextStationName()` — current position along route

### `TrainStore`
Handles all CSV persistence:
- `saveAll(List<Train>)` — overwrites `trains.csv`
- `loadAll()` — parses CSV back into `Train` objects on startup

### `RouteProgressPanel`
Custom-painted `JPanel` that draws:
- A gray background track
- A gold-filled progress bar
- Color-coded dots for each station
- A triangle marker (▲) for current position
- A legend (Passed / Current / Upcoming)

### `Hello` (Main Frame)
Extends `JFrame`. Manages:
- Top navigation bar (clock, admin login)
- Sidebar (train info cards)
- Center panel (route + live ETA)
- Stats row (covered/remaining km, time, speed)
- Swing `Timer` firing every 1000ms to refresh all data

---

## 📌 Known Limitations

- Admin password is **hardcoded** — not suitable for production without modification
- Train progress is **time-based only** — no GPS or real API integration
- No **network connectivity** — all data is local
- UI is **not resizable** (fixed layout for consistent rendering)

---

## 🤝 Contributing

Contributions are welcome! To contribute:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "Add your feature"`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## 📄 License

This project is intended for academic and educational use.  
© 2026 CSE Department, RUET. All rights reserved.

---

## 👨‍💻 Author

Developed by Sanjid, CSE Student at **Rajshahi University of Engineering & Technology (RUET)**.  
For questions or suggestions, open an issue on GitHub.
