import time
import random
import requests

ENDPOINTS = [
    "https://vajra-production.up.railway.app/api/track-telemetry",
    "https://vajra-bei6.onrender.com/api/track-telemetry"
]

def get_demo_data():
    # Randomly pick a state to simulate "real-world" variance
    states = ["HEALTHY", "WARNING", "CRITICAL"]
    weights = [0.70, 0.20, 0.10] # Mostly healthy, occasionally critical
    status = random.choices(states, weights=weights)[0]
    
    return {
        "status": status,
        "stress": round(random.uniform(70.0, 180.0), 2) if status != "HEALTHY" else round(random.uniform(60.0, 90.0), 2),
        "temp": round(random.uniform(40.0, 65.0), 2) if status != "HEALTHY" else round(random.uniform(20.0, 35.0), 2)
    }

def run_demo():
    print("Vajra Demo Mode: Active.")
    while True:
        data = get_demo_data()
        for url in ENDPOINTS:
            try:
                requests.post(url, json=data, timeout=2)
            except:
                pass
        # Faster updates for a better demo experience
        time.sleep(3) 

if __name__ == "__main__":
    run_demo()
