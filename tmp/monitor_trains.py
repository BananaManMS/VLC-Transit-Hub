import time
import json
import urllib.request
import ssl

def fetch_flota():
    url = f"https://tiempo-real.renfe.com/renfe-visor/flota.json?v={int(time.time())}"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://tiempo-real.renfe.com/"
    }
    req = urllib.request.Request(url, headers=headers)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as e:
        print(f"Error fetching: {e}")
        return None

def monitor():
    print("Starting 3-minute live monitor of Renfe Valencia trains at 20s intervals...")
    history = {}
    
    # Run 10 samples (3 minutes total, 180 seconds, every 20s is 0, 20, 40, 60, 80, 100, 120, 140, 160, 180)
    for sample in range(10):
        print(f"\n--- SAMPLE {sample + 1}/10 (Time: +{sample * 20}s) ---")
        data = fetch_flota()
        if not data:
            time.sleep(20)
            continue
            
        trains = [t for t in data.get("trenes", []) if t.get("nucleo") == "40"]
        print(f"Active Valencia trains detected: {len(trains)}")
        
        for t in trains:
            ct = t.get("codTren")
            if not ct:
                continue
            if ct not in history:
                history[ct] = []
            history[ct].append({
                "time": sample * 20,
                "lat": t.get("latitud"),
                "lon": t.get("longitud"),
                "porAvanc": t.get("porAvanc"),
                "estAct": t.get("codEstAct"),
                "estSig": t.get("codEstSig"),
                "linea": t.get("codLinea")
            })
            
        time.sleep(20)
        
    print("\n================== FINAL MONITOR REPORT ==================")
    # Select up to 3 trains that appeared in the most samples to report their trajectory
    sorted_trains = sorted(history.items(), key=lambda x: len(x[1]), reverse=True)
    selected_trains = sorted_trains[:3]
    
    for ct, states in selected_trains:
        print(f"\n--- TRAIN {ct} (Line {states[0]['linea']}) ---")
        print(f"Recorded in {len(states)}/10 samples.")
        for s in states:
            print(f"  T+{s['time']}s: ({s['lat']}, {s['lon']}) | Progress: {s['porAvanc']} | Stations: {s['estAct']} -> {s['estSig']}")
            
        # Perform physical motion analysis
        if len(states) >= 2:
            first = states[0]
            last = states[-1]
            try:
                # Approximate distance calculation in meters
                import math
                dlat = (last['lat'] - first['lat']) * 111000
                dlon = (last['lon'] - first['lon']) * 111000 * math.cos(math.radians(first['lat']))
                distance = math.sqrt(dlat**2 + dlon**2)
                elapsed_time = last['time'] - first['time']
                if elapsed_time > 0:
                    speed_kmh = (distance / elapsed_time) * 3.6
                    print(f"  [Analysis] Total distance traveled: {distance:.2f} meters over {elapsed_time}s.")
                    print(f"  [Analysis] Average speed: {speed_kmh:.2f} km/h")
                    if speed_kmh > 150:
                        print("  [Analysis] WARNING: Speed exceeds 150 km/h (unrealistic telemetry jump detected!)")
                    elif speed_kmh < 1:
                        print("  [Analysis] Train is stationary (stopped at station or waiting).")
                    else:
                        print("  [Analysis] Smooth and realistic movement detected!")
            except Exception as e:
                print(f"  [Analysis Error]: {e}")

if __name__ == "__main__":
    monitor()
