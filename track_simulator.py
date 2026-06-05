import time
import requests

# Targets
ENDPOINTS = [
    "https://vajra-production.up.railway.app/api/track-telemetry",
    "https://vajra-bei6.onrender.com/api/track-telemetry"
]

# The "Playbook" (Loops forever)
SCENARIO = [
    {"status": "HEALTHY", "stress": 75.0, "temp": 30.0},
    {"status": "ESCALATING", "stress": 120.0, "temp": 45.0},
    {"status": "CRITICAL", "stress": 165.0, "temp": 58.0},
    {"status": "RECOVERING", "stress": 90.0, "temp": 35.0}
]

def run_forever():
    print("Vajra Telemetry Engine: Online 24/7.")
    while True:
        for state in SCENARIO:
            for url in ENDPOINTS:
                try:
                    # Short timeout so one slow domain doesn't hang the whole script
                    requests.post(url, json=state, timeout=2)
                except Exception:
                    # If one fails, the loop continues to the next domain
                    continue
            time.sleep(5)

if __name__ == "__main__":
    run_forever()
