#!/usr/bin/env python3
"""
============================================================
VAJRA — CWR Track Sensor Telemetry Simulator (State-Based)
Project: CWR Stress & Broken Rail Anomaly Detection Engine
Section: NDLS-AGRA-SEC1  |  Kilometer Marker: 412
============================================================

State Machine:
  NOMINAL  ──(1/300 chance)──▶  ESCALATING  ──▶  CRITICAL  ──▶  RECOVERING  ──▶  NOMINAL

- NOMINAL    : Healthy baseline. Stress 70–90 MPa, Temp 25–35°C.
- ESCALATING : Thermal pre-stress buildup over 4–6 ticks.
- CRITICAL   : Full structural anomaly. Stress >150 MPa. Lasts 5–8 ticks.
- RECOVERING : Gradual return to safe range over 5–8 ticks.

Connection handling: per-target exponential backoff retry (max 3 attempts).
"""

import requests
import time
import random
import math
import sys
from datetime import datetime, timezone
from enum import Enum, auto

# ─────────────────────────────────────────────────────────────────────────────
# CONFIGURATION
# ─────────────────────────────────────────────────────────────────────────────

TARGETS = {
    "localhost" : "http://localhost:8080/api/track-telemetry",
    "railway"   : "https://vajra-production.up.railway.app/api/track-telemetry",
    "render"    : "https://vajra-bei6.onrender.com/api/track-telemetry",
}

# Per-target timeouts: Railway = fast fail, Render = cold-start tolerant
TARGET_TIMEOUTS = {
    "localhost" : 3,
    "railway"   : 4,
    "render"    : 55,
}

SECTION_ID       = "NDLS-AGRA-SEC1"
KM_MARKER        = 412
INTERVAL_SECONDS = 1.0      # Stream cadence (seconds)

# Anomaly trigger: 1 event per ~300 NOMINAL packets (0.33% probability)
ANOMALY_TRIGGER_PROB = 1 / 300

# Healthy envelope
NOMINAL_STRESS_MIN = 70.0   # MPa
NOMINAL_STRESS_MAX = 90.0   # MPa
NOMINAL_TEMP_MIN   = 25.0   # °C
NOMINAL_TEMP_MAX   = 35.0   # °C

# Critical envelope (IS:3443 / UIC 60 breach thresholds)
CRITICAL_STRESS_MIN = 150.0
CRITICAL_STRESS_MAX = 210.0
CRITICAL_TEMP_MIN   = 51.0
CRITICAL_TEMP_MAX   = 72.0

MAX_RETRIES        = 3      # Per-target retry attempts on failure
RETRY_BASE_DELAY   = 0.4    # Seconds — doubles each attempt (exponential backoff)


# ─────────────────────────────────────────────────────────────────────────────
# STATE MACHINE
# ─────────────────────────────────────────────────────────────────────────────

class State(Enum):
    NOMINAL     = auto()
    ESCALATING  = auto()
    CRITICAL    = auto()
    RECOVERING  = auto()


class TrackStateMachine:
    """
    Manages the lifecycle of a single CWR sensor section.
    Produces physically plausible telemetry values for each state phase.
    """

    def __init__(self):
        self.state          = State.NOMINAL
        self.phase_ticks    = 0        # Ticks remaining in current phase
        self.escalate_steps = 0        # Total ticks in ESCALATING phase
        self.escalate_idx   = 0        # Current step index
        self.recover_steps  = 0        # Total ticks in RECOVERING phase
        self.recover_idx    = 0        # Current step index

        # Snapshot values at state boundaries for smooth interpolation
        self._peak_stress   = 0.0
        self._peak_temp     = 0.0
        self._last_stress   = random.uniform(NOMINAL_STRESS_MIN, NOMINAL_STRESS_MAX)
        self._last_temp     = random.uniform(NOMINAL_TEMP_MIN,   NOMINAL_TEMP_MAX)

    # ── public ──────────────────────────────────────────────────────────────

    def tick(self) -> dict:
        """Advance the state machine by one tick and return a telemetry dict."""
        if self.state == State.NOMINAL:
            return self._tick_nominal()
        elif self.state == State.ESCALATING:
            return self._tick_escalating()
        elif self.state == State.CRITICAL:
            return self._tick_critical()
        elif self.state == State.RECOVERING:
            return self._tick_recovering()

    # ── state handlers ───────────────────────────────────────────────────────

    def _tick_nominal(self) -> dict:
        # Slow random walk within healthy envelope
        stress = self._last_stress + random.gauss(0, 1.5)
        stress = max(NOMINAL_STRESS_MIN, min(stress, NOMINAL_STRESS_MAX))

        temp   = self._last_temp + random.gauss(0, 0.4)
        temp   = max(NOMINAL_TEMP_MIN, min(temp, NOMINAL_TEMP_MAX))

        self._last_stress = stress
        self._last_temp   = temp

        # Decide whether to trigger an anomaly sequence
        if random.random() < ANOMALY_TRIGGER_PROB:
            self._begin_escalation(stress, temp)

        return self._packet(stress, temp, anomaly=False)

    def _tick_escalating(self) -> dict:
        """Smooth linear ramp from nominal values toward critical peak."""
        frac   = (self.escalate_idx + 1) / self.escalate_steps
        stress = self._last_stress + frac * (self._peak_stress - self._last_stress)
        temp   = self._last_temp   + frac * (self._peak_temp   - self._last_temp)

        self.escalate_idx += 1
        if self.escalate_idx >= self.escalate_steps:
            self._begin_critical()

        return self._packet(stress, temp, anomaly=False)

    def _tick_critical(self) -> dict:
        """Peak anomaly — stress >150 MPa with realistic jitter."""
        stress = self._peak_stress + random.gauss(0, 3.0)
        stress = max(CRITICAL_STRESS_MIN, stress)

        temp   = self._peak_temp + random.gauss(0, 0.8)
        temp   = max(CRITICAL_TEMP_MIN, temp)

        self.phase_ticks -= 1
        if self.phase_ticks <= 0:
            self._begin_recovery(stress, temp)

        return self._packet(stress, temp, anomaly=True)

    def _tick_recovering(self) -> dict:
        """Smooth decay back toward healthy nominal values."""
        target_stress = random.uniform(NOMINAL_STRESS_MIN, NOMINAL_STRESS_MAX)
        target_temp   = random.uniform(NOMINAL_TEMP_MIN,   NOMINAL_TEMP_MAX)

        frac   = (self.recover_idx + 1) / self.recover_steps
        stress = self._peak_stress + frac * (target_stress - self._peak_stress)
        temp   = self._peak_temp   + frac * (target_temp   - self._peak_temp)

        self.recover_idx += 1
        if self.recover_idx >= self.recover_steps:
            self._last_stress = stress
            self._last_temp   = temp
            self.state        = State.NOMINAL

        return self._packet(stress, temp, anomaly=False)

    # ── transitions ──────────────────────────────────────────────────────────

    def _begin_escalation(self, from_stress: float, from_temp: float):
        self.state          = State.ESCALATING
        self.escalate_steps = random.randint(4, 7)
        self.escalate_idx   = 0
        self._peak_stress   = random.uniform(CRITICAL_STRESS_MIN, CRITICAL_STRESS_MAX)
        self._peak_temp     = random.uniform(CRITICAL_TEMP_MIN,   CRITICAL_TEMP_MAX)
        # Store current values as baseline for interpolation
        self._last_stress   = from_stress
        self._last_temp     = from_temp

    def _begin_critical(self):
        self.state       = State.CRITICAL
        self.phase_ticks = random.randint(5, 9)

    def _begin_recovery(self, from_stress: float, from_temp: float):
        self.state         = State.RECOVERING
        self.recover_steps = random.randint(5, 9)
        self.recover_idx   = 0
        self._peak_stress  = from_stress
        self._peak_temp    = from_temp

    # ── helpers ──────────────────────────────────────────────────────────────

    def _packet(self, stress: float, temp: float, anomaly: bool) -> dict:
        return {
            "sectionId"       : SECTION_ID,
            "kmMarker"        : KM_MARKER,
            "railTemperature" : round(temp,   2),
            "stressMPa"       : round(stress, 2),
            "anomalyInjected" : anomaly,
            "timestamp"       : datetime.now(timezone.utc).isoformat(),
        }


# ─────────────────────────────────────────────────────────────────────────────
# NETWORK — PER-TARGET EXPONENTIAL BACKOFF RETRY
# ─────────────────────────────────────────────────────────────────────────────

def send_with_retry(name: str, url: str, packet: dict) -> tuple:
    """
    POST packet to a single endpoint with exponential backoff retry.
    Returns (http_status: int, elapsed_ms: float).
    Never raises — failure is always returned as a status code.
    """
    timeout = TARGET_TIMEOUTS.get(name, 5)
    delay   = RETRY_BASE_DELAY

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            t0       = time.time()
            response = requests.post(
                url,
                json=packet,
                headers={"Content-Type": "application/json"},
                timeout=timeout,
            )
            elapsed_ms = (time.time() - t0) * 1000
            return response.status_code, elapsed_ms

        except requests.exceptions.ConnectionError:
            if attempt < MAX_RETRIES:
                time.sleep(delay)
                delay *= 2
                continue
            _warn(f"[{name}] unreachable after {MAX_RETRIES} attempts.")
            return 0, 0.0

        except requests.exceptions.Timeout:
            if attempt < MAX_RETRIES:
                time.sleep(delay)
                delay *= 2
                continue
            _warn(f"[{name}] timed out after {timeout}s × {MAX_RETRIES} attempts — cold start?")
            return 408, 0.0

        except Exception as ex:
            _err(f"[{name}] unexpected error: {ex}")
            return -1, 0.0

    return 0, 0.0


# ─────────────────────────────────────────────────────────────────────────────
# TERMINAL OUTPUT HELPERS
# ─────────────────────────────────────────────────────────────────────────────

def _c(text: str, code: str) -> str:
    """ANSI color wrapper."""
    return f"\033[{code}m{text}\033[0m"

def _warn(msg: str):
    print(_c(f"  [WARN]  {msg}", "0;33"))

def _err(msg: str):
    print(_c(f"  [ERROR] {msg}", "1;31"))


def print_banner(sensor: TrackStateMachine):
    target_lines = "\n".join(
        f"  │  [{name:10s}] {url}"
        for name, url in TARGETS.items()
    )
    print(f"""
╔══════════════════════════════════════════════════════════════╗
║  🚆  VAJRA — CWR STATE-BASED TELEMETRY SIMULATOR            ║
║  Section : {SECTION_ID}  |  KM : {KM_MARKER}               ║
║  Anomaly Probability : 1 per ~300 packets ({ANOMALY_TRIGGER_PROB*100:.2f}%)          ║
║  Fan-out targets ({len(TARGETS)}):                                       ║
{target_lines}
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
""")


def log_packet(packet: dict, primary_status: int, primary_elapsed: float,
               state: State, packet_count: int):
    """Render a compact, color-coded line per tick."""
    ts     = packet["timestamp"][11:23]
    temp   = packet["railTemperature"]
    stress = packet["stressMPa"]

    state_labels = {
        State.NOMINAL    : _c("● NOMINAL    ", "0;32"),
        State.ESCALATING : _c("▲ ESCALATING ", "1;33"),
        State.CRITICAL   : _c("⚠ CRITICAL   ", "1;31"),
        State.RECOVERING : _c("▼ RECOVERING ", "0;36"),
    }

    state_str  = state_labels.get(state, "  UNKNOWN    ")
    temp_str   = _c(f"{temp:6.2f}°C",  "1;31" if temp > 50 else "0;32")
    stress_str = _c(f"{stress:7.2f} MPa", "1;31" if stress > 120 else "0;32")
    http_str   = _c(f"HTTP {primary_status}", "0;36") if primary_status == 200 \
                 else _c(f"HTTP {primary_status}", "0;31")

    print(
        f"  [{ts}] #{packet_count:05d}  {state_str}  "
        f"T: {temp_str}  σ: {stress_str}  "
        f"{http_str}  {primary_elapsed:.0f}ms"
    )


# ─────────────────────────────────────────────────────────────────────────────
# MAIN LOOP
# ─────────────────────────────────────────────────────────────────────────────

def stream_telemetry():
    sensor       = TrackStateMachine()
    packet_count = 0
    anomaly_count = 0

    print_banner(sensor)
    print(_c("  Streaming started. Press Ctrl+C to stop.\n", "0;90"))

    while True:
        loop_start = time.time()

        packet       = sensor.tick()
        packet_count += 1
        if packet["anomalyInjected"]:
            anomaly_count += 1

        # Fan-out to all targets; first target drives the log line
        primary_status, primary_elapsed = 0, 0.0
        for idx, (name, url) in enumerate(TARGETS.items()):
            status, elapsed = send_with_retry(name, url, packet)
            if idx == 0:
                primary_status  = status
                primary_elapsed = elapsed

        log_packet(packet, primary_status, primary_elapsed,
                   sensor.state, packet_count)

        # Rolling summary every 50 packets
        if packet_count % 50 == 0:
            rate = (anomaly_count / packet_count) * 100
            print(_c(
                f"\n  ── Vajra Summary · {packet_count} packets · "
                f"{anomaly_count} anomaly ticks ({rate:.2f}%) · "
                f"{len(TARGETS)} targets ──\n",
                "0;90"
            ))

        # Precise 1-second cadence
        elapsed_loop = time.time() - loop_start
        time.sleep(max(0, INTERVAL_SECONDS - elapsed_loop))


# ─────────────────────────────────────────────────────────────────────────────
# ENTRY POINT
# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    try:
        stream_telemetry()
    except KeyboardInterrupt:
        print(_c("\n\n  ✓ Vajra simulator stopped by operator.\n", "0;33"))
        sys.exit(0)
