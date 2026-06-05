# 🚆 CWR Anomaly Detection Engine
### Real-Time Predictive Anomaly Engine for Continuous Welded Rail Stress Management
*Indian Railways · RailTech Policy 2026 · RDSO Prototype*

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                     3-TIER REACTIVE PIPELINE                        │
│                                                                     │
│  ┌─────────────┐   POST /api/track-telemetry   ┌────────────────┐  │
│  │  COMPONENT A │  ────────────────────────────▶│  COMPONENT B   │  │
│  │             │   JSON every 1 second          │                │  │
│  │  Python     │                                │  Spring Boot   │  │
│  │  Simulator  │                                │  REST API      │  │
│  │             │                                │                │  │
│  │ Simulates   │                                │ • Evaluates    │  │
│  │ CWR sensor  │                                │   stress thres │  │
│  │ at KM 412   │                                │ • ConcurrentHM │  │
│  │             │                                │   (no DB)      │  │
│  └─────────────┘                                │ • Classifies:  │  │
│                                                  │   HEALTHY /    │  │
│  ┌─────────────┐   GET /api/track-status        │   WARNING /    │  │
│  │  COMPONENT C │  ◀──────────────────────────── │   CRITICAL     │  │
│  │             │   JSON every 800ms              └────────────────┘  │
│  │  HTML5      │                                                     │
│  │  Dashboard  │                                                     │
│  │             │  • Track line: Green / Amber / Neon Crimson        │
│  │  localhost  │  • Emergency alert banner                           │
│  │  :8080/     │  • Live metric cards                                │
│  └─────────────┘  • Telemetry log table                             │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

| Tool          | Version        | Install Command                                               |
|---------------|----------------|---------------------------------------------------------------|
| Java JDK      | 17+            | `brew install openjdk@17`                                    |
| Apache Maven  | 3.8+           | `brew install maven`                                          |
| Python        | 3.9+           | Pre-installed on macOS / `brew install python3`              |
| pip package   | `requests`     | `pip3 install requests`                                       |

---

## File Placement Reference

```
rail-anomaly-engine/
│
├── python-simulator/
│   └── track_simulator.py              ← COMPONENT A
│
└── spring-api/
    ├── pom.xml
    └── src/
        └── main/
            ├── java/
            │   └── com/railways/anomaly/
            │       ├── AnomalyEngineApplication.java
            │       ├── controller/
            │       │   └── TrackTelemetryController.java   ← COMPONENT B (API)
            │       ├── model/
            │       │   ├── TelemetryPacket.java
            │       │   └── TrackStatusResponse.java
            │       └── service/
            │           └── TrackEvaluationService.java     ← COMPONENT B (Logic)
            └── resources/
                ├── application.properties
                └── static/
                    └── index.html                          ← COMPONENT C
```

---

## Step-by-Step Setup (Single MacBook Air, One Day)

### Step 1 — Build and Start the Spring Boot API

```bash
# Navigate to the Spring Boot project
cd rail-anomaly-engine/spring-api

# Build the JAR (first time downloads dependencies ~2 min)
mvn clean package -DskipTests

# Start the server on port 8080
mvn spring-boot:run
```

**Expected output:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
...
Started AnomalyEngineApplication in 2.341 seconds
Tomcat started on port(s): 8080
```

**Verify API is running:**
```bash
curl http://localhost:8080/api/health
# → {"status":"UP","service":"CWR Anomaly Detection Engine",...}
```

---

### Step 2 — Open the Dashboard

Open your browser and navigate to:
```
http://localhost:8080
```

The dashboard will load showing "Waiting for first telemetry packet…" — this is correct. The track line segments will be dim until the simulator starts sending data.

---

### Step 3 — Start the Python Telemetry Simulator

Open a **new terminal tab** (keep Spring Boot running in the first):

```bash
# Install the requests library if not present
pip3 install requests

# Navigate to the simulator directory
cd rail-anomaly-engine/python-simulator

# Start streaming
python3 track_simulator.py
```

**Expected console output:**
```
╔══════════════════════════════════════════════════════════════╗
║  🚆  CWR TRACK SENSOR — VIRTUAL TELEMETRY SIMULATOR         ║
║  Section : NDLS-AGRA-SEC1  |  KM Marker : 412               ║
╚══════════════════════════════════════════════════════════════╝

  [14:22:31.012] ✓  HEALTHY    Temp:  33.41°C  Stress:   71.83 MPa  [NOMINAL]  HTTP 200  12ms
  [14:22:32.014] ✓  HEALTHY    Temp:  32.87°C  Stress:   69.20 MPa  [NOMINAL]  HTTP 200  9ms
  [14:22:33.016] ⚠  ANOMALY  ⚠  Temp:  58.23°C  Stress:  167.45 MPa  [THERMAL_BUCKLING]  HTTP 200  11ms
  [14:22:34.018] ✓  HEALTHY    Temp:  33.11°C  Stress:   72.90 MPa  [NOMINAL]  HTTP 200  10ms
```

---

## What Happens in Real Time

| Simulator State       | Spring Boot Logs      | Dashboard Response                                               |
|-----------------------|-----------------------|------------------------------------------------------------------|
| Healthy packet        | `[HEALTHY] ...`       | Track line stays **green**, metrics update smoothly             |
| Warning (90–120 MPa)  | `[WARNING] ...`       | Track line turns **amber**, warning pill shown in segment list  |
| Critical anomaly      | `[CRITICAL] ...`      | Track line turns **neon crimson**, emergency banner flashes,    |
|                       |                       | sensor node pulses, Emergency Brake Command displayed           |

---

## API Reference

### `POST /api/track-telemetry`
Ingest a telemetry packet.

**Request body:**
```json
{
  "sectionId":      "NDLS-AGRA-SEC1",
  "kmMarker":       412,
  "railTemperature": 34.52,
  "stressMPa":      78.91,
  "anomalyInjected": false,
  "timestamp":       "2026-06-05T14:22:31.000Z"
}
```

**Response (200 OK):**
```json
{
  "sectionId":             "NDLS-AGRA-SEC1",
  "kmMarker":              412,
  "railTemperature":       34.52,
  "stressMPa":             78.91,
  "riskLevel":             "HEALTHY",
  "action":                "All parameters within safe operating bounds...",
  "statusLabel":           "HEALTHY",
  "lastUpdated":           "2026-06-05T14:22:31.000Z",
  "evaluatedAt":           "2026-06-05T14:22:31.120Z",
  "totalPacketsProcessed": 42,
  "totalAnomaliesDetected": 6
}
```

### `GET /api/track-status`
Retrieve the latest evaluated status for dashboard polling.
Returns `204 No Content` if no packets received yet.

### `GET /api/health`
Liveness check.

---

## Risk Thresholds (RDSO / IS:3443 / UIC 60)

| Risk Level   | Condition                                      | Action Prescribed                              |
|--------------|------------------------------------------------|------------------------------------------------|
| ✅ HEALTHY   | Stress < 90 MPa                                | Normal operations continue                     |
| ⚡ WARNING   | Stress 90 – 120 MPa                            | Schedule USFD inspection · Reduce speed to 60 km/h |
| 🚨 CRITICAL  | Stress > 120 MPa **OR** Temperature > 50°C    | Emergency Brake Command · Halt all trains · Dispatch P-Way gang to KM 412 |

---

## Anomaly Simulation Modes

| Mode              | Trigger Condition                                           |
|-------------------|-------------------------------------------------------------|
| `THERMAL_BUCKLING`| Rail temp > 51°C + Compressive stress surge (122–185 MPa) |
| `TENSILE_FRACTURE`| Near-normal temp + Tensile overload (143–210 MPa)          |

Anomaly injection probability: **15% per packet** (configurable in `track_simulator.py`).

---

## Customization

**Change anomaly probability:**
Edit `ANOMALY_CHANCE` in `track_simulator.py`:
```python
ANOMALY_CHANCE = 0.30   # 30% for demo purposes
```

**Change stress thresholds:**
Edit constants in `TrackEvaluationService.java`:
```java
private static final double STRESS_WARNING_THRESHOLD  = 90.0;
private static final double STRESS_CRITICAL_THRESHOLD = 120.0;
private static final double TEMP_CRITICAL_THRESHOLD   = 50.0;
```

**Change poll frequency:**
Edit `POLL_INTERVAL_MS` in `index.html`:
```javascript
const POLL_INTERVAL_MS = 500;  // 500ms for faster refresh
```

---

*Built for Indian Railways Innovation Policy / StartUps for Railways · RailTech Policy 2026*
*Prototype demonstrates zero-cost, zero-database, high-throughput telemetry pipeline.*
