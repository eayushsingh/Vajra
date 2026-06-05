#!/usr/bin/env python3
"""
============================================================
COMPONENT A: Virtual Track Telemetry Simulator
Project: CWR Stress & Broken Rail Anomaly Detection Engine
Section: NDLS-AGRA-SEC1 | Kilometer Marker: 412
Author: RailTech AI Initiative
============================================================

Simulates a track-mounted IoT sensor array streaming live
stress and temperature telemetry to the Spring Boot API.
Injects critical structural anomalies at ~15% probability
to simulate CWR buckling / rail-fracture events.
"""

import requests
import time
import random
import math
import json
import sys
from datetime import datetime, timezone

# ─────────────────────────────────────────────────────────────────────────────
# CONFIGURATION
# ─────────────────────────────────────────────────────────────────────────────

API_ENDPOINT      = "http://localhost:8080/api/track-telemetry"
SECTION_ID        = "NDLS-AGRA-SEC1"
KM_MARKER         = 412
INTERVAL_SECONDS  = 1.0          # Stream frequency
ANOMALY_CHANCE    = 0.15         # 15% probability of injecting a critical event
REQUEST_TIMEOUT   = 3            # HTTP timeout in seconds

# Nominal operating envelope (healthy CWR ranges for Indian summer)
BASELINE_TEMP_C   = 32.0         # Typical ambient rail temp in °C
TEMP_FLUCTUATION  = 4.0          # ± natural thermal drift in °C
BASELINE_STRESS   = 68.0         # MPa — within safe compressive range
STRESS_FLUCTUATION = 12.0        # ± MPa natural variation from train load

# ─────────────────────────────────────────────────────────────────────────────
# HELPERS
# ─────────────────────────────────────────────────────────────────────────────

def print_banner():
    banner = """
╔══════════════════════════════════════════════════════════════╗
║  🚆  CWR TRACK SENSOR — VIRTUAL TELEMETRY SIMULATOR         ║
║  Section : NDLS-AGRA-SEC1  |  KM Marker : 412               ║
║  Target  : {endpoint:<44} ║
║  Interval: {interval}s  |  Anomaly Prob: {prob}%                       ║
╚══════════════════════════════════════════════════════════════╝
""".format(
        endpoint=API_ENDPOINT,
        interval=INTERVAL_SECONDS,
        prob=int(ANOMALY_CHANCE * 100)
    )
    print(banner)


def simulate_train_vibration_noise(t: float) -> float:
    """
    Compose multi-frequency sinusoidal noise to mimic real track vibration
    from passing trains (wheel-flange harmonics, bogie resonance, track joints).
    Returns a ± MPa jitter value.
    """
    # Bogie frequency ~2 Hz, wheel harmonic ~11 Hz, track mode ~0.3 Hz
    noise = (
        3.2 * math.sin(2 * math.pi * 2.0  * t + random.uniform(0, 1)) +
        1.1 * math.sin(2 * math.pi * 11.0 * t + random.uniform(0, 1)) +
        4.8 * math.sin(2 * math.pi * 0.3  * t + random.uniform(0, 1))
    )
    return noise


def generate_healthy_reading(t: float) -> dict:
    """
    Generate a realistic healthy telemetry packet with natural
    environmental drift and train-induced vibration noise.
    """
    # Thermal drift: slow sinusoidal variation over the day
    diurnal_temp = BASELINE_TEMP_C + TEMP_FLUCTUATION * math.sin(2 * math.pi * t / 3600)
    temp_noise   = random.gauss(0, 0.8)
    rail_temp    = round(diurnal_temp + temp_noise, 2)

    # Stress: correlated to temperature (thermal expansion) + vibration + Gaussian noise
    thermal_stress  = BASELINE_STRESS + (rail_temp - BASELINE_TEMP_C) * 0.9
    vibration_stress = simulate_train_vibration_noise(t)
    gaussian_noise   = random.gauss(0, 2.0)
    stress_mpa       = round(thermal_stress + vibration_stress + gaussian_noise, 2)

    # Clamp to physical plausibility (can't be negative or exceed normal ops max)
    stress_mpa = max(30.0, min(stress_mpa, 118.0))
    rail_temp  = max(20.0, min(rail_temp, 49.0))

    return {
        "sectionId":      SECTION_ID,
        "kmMarker":       KM_MARKER,
        "railTemperature": rail_temp,
        "stressMPa":      stress_mpa,
        "anomalyInjected": False,
        "timestamp":       datetime.now(timezone.utc).isoformat()
    }


def generate_critical_anomaly(t: float) -> dict:
    """
    Inject a simulated CWR failure event:
    — Rail Buckling (summer): extreme temp spike + compressive stress surge
    — Rail Fracture (winter): sudden tensile stress overload
    Both exceed the emergency thresholds defined in IS:3443 / UIC 60 standards.
    """
    event_type = random.choice(["BUCKLING", "FRACTURE"])

    if event_type == "BUCKLING":
        # Thermal buckling: rail heats beyond neutral temperature, lateral displacement
        rail_temp  = round(random.uniform(51.0, 68.0), 2)  # Exceeds 50°C threshold
        stress_mpa = round(random.uniform(122.0, 185.0), 2) # Compressive surge
        mode       = "THERMAL_BUCKLING"
    else:
        # Rail fracture: sudden tensile stress from contraction / wheel impact
        rail_temp  = round(random.uniform(35.0, 49.5), 2)  # Near-normal temp
        stress_mpa = round(random.uniform(143.0, 210.0), 2) # Tensile overload
        mode       = "TENSILE_FRACTURE"

    return {
        "sectionId":       SECTION_ID,
        "kmMarker":        KM_MARKER,
        "railTemperature": rail_temp,
        "stressMPa":       stress_mpa,
        "anomalyInjected": True,
        "anomalyMode":     mode,
        "timestamp":       datetime.now(timezone.utc).isoformat()
    }


def colorize(text: str, color_code: str) -> str:
    """ANSI terminal color wrapper."""
    return f"\033[{color_code}m{text}\033[0m"


def log_packet(packet: dict, http_status: int, elapsed_ms: float):
    """Structured console log for each transmitted packet."""
    ts       = packet["timestamp"][11:23]  # Extract HH:MM:SS.mmm
    temp     = packet["railTemperature"]
    stress   = packet["stressMPa"]
    injected = packet.get("anomalyInjected", False)
    mode     = packet.get("anomalyMode", "NOMINAL")

    if injected:
        status_icon  = colorize("⚠  ANOMALY  ⚠", "1;31")
        temp_str     = colorize(f"{temp:>6.2f}°C", "1;31")
        stress_str   = colorize(f"{stress:>7.2f} MPa", "1;31")
        mode_str     = colorize(f"[{mode}]", "1;33")
    elif stress >= 90:
        status_icon  = colorize("⚡  WARNING ", "1;33")
        temp_str     = colorize(f"{temp:>6.2f}°C", "1;33")
        stress_str   = colorize(f"{stress:>7.2f} MPa", "1;33")
        mode_str     = colorize("[ELEVATED]", "0;33")
    else:
        status_icon  = colorize("✓  HEALTHY ", "0;32")
        temp_str     = colorize(f"{temp:>6.2f}°C", "0;32")
        stress_str   = colorize(f"{stress:>7.2f} MPa", "0;32")
        mode_str     = colorize("[NOMINAL]", "0;32")

    http_str = colorize(f"HTTP {http_status}", "0;36") if http_status == 200 else colorize(f"HTTP {http_status}", "0;31")
    elapsed_str = colorize(f"{elapsed_ms:.0f}ms", "0;90")

    print(
        f"  [{ts}] {status_icon}  "
        f"Temp: {temp_str}  Stress: {stress_str}  "
        f"{mode_str}  {http_str} {elapsed_str}"
    )


# ─────────────────────────────────────────────────────────────────────────────
# MAIN STREAMING LOOP
# ─────────────────────────────────────────────────────────────────────────────

def stream_telemetry():
    print_banner()
    print(colorize("  Starting telemetry stream. Press Ctrl+C to stop.\n", "0;90"))

    t = 0.0  # Simulated time counter (seconds)
    packet_count = 0
    anomaly_count = 0

    while True:
        loop_start = time.time()

        # Decide: healthy packet or injected anomaly?
        if random.random() < ANOMALY_CHANCE:
            packet = generate_critical_anomaly(t)
            anomaly_count += 1
        else:
            packet = generate_healthy_reading(t)

        packet_count += 1

        # Transmit to Spring Boot API
        http_status = 0
        elapsed_ms  = 0.0
        try:
            send_start = time.time()
            response   = requests.post(
                API_ENDPOINT,
                json=packet,
                headers={"Content-Type": "application/json"},
                timeout=REQUEST_TIMEOUT
            )
            elapsed_ms  = (time.time() - send_start) * 1000
            http_status = response.status_code

        except requests.exceptions.ConnectionError:
            http_status = 0
            print(colorize(
                f"  [ERROR] Cannot reach {API_ENDPOINT} — Is Spring Boot running?",
                "1;31"
            ))
        except requests.exceptions.Timeout:
            http_status = 408
            print(colorize("  [ERROR] Request timed out after 3 seconds.", "1;31"))
        except Exception as ex:
            http_status = -1
            print(colorize(f"  [ERROR] Unexpected: {ex}", "1;31"))

        log_packet(packet, http_status, elapsed_ms)

        # Print rolling summary every 20 packets
        if packet_count % 20 == 0:
            rate = (anomaly_count / packet_count) * 100
            print(colorize(
                f"\n  ── Summary: {packet_count} packets sent | "
                f"{anomaly_count} anomalies ({rate:.1f}%) ──\n",
                "0;90"
            ))

        # Precise sleep to maintain 1-second cadence (compensate for processing time)
        elapsed_loop = time.time() - loop_start
        sleep_time   = max(0, INTERVAL_SECONDS - elapsed_loop)
        time.sleep(sleep_time)
        t += INTERVAL_SECONDS


# ─────────────────────────────────────────────────────────────────────────────
# ENTRY POINT
# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    try:
        stream_telemetry()
    except KeyboardInterrupt:
        print(colorize("\n\n  ✓ Telemetry simulator stopped by operator.\n", "0;33"))
        sys.exit(0)
