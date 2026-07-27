import requests
import time
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.stdout.reconfigure(encoding='utf-8')

# Configurable Target Host
TARGET_HOST = "http://localhost:8080" # Change to live domain for live test

LOGIN_URL = f"{TARGET_HOST}/api/auth/login"
TRIPS_URL = f"{TARGET_HOST}/api/trips"
BOOKING_URL = f"{TARGET_HOST}/api/parcels/request"

# Seeded credentials
SENDER_MOBILE = "9876543210"
PASSWORD = "password123"

def authenticate():
    try:
        r = requests.post(LOGIN_URL, json={"mobileNumber": SENDER_MOBILE, "password": PASSWORD}, timeout=10)
        if r.status_code == 200:
            data = r.json()
            return data.get("token"), data.get("id")
    except Exception as e:
        print(f"Auth failure: {e}")
    return None, None

COORDINATE_PAIRS = [
    {"name": "Bengaluru", "lat": 12.9716, "lng": 77.6412},
    {"name": "Hyderabad", "lat": 17.4107, "lng": 78.4497},
    {"name": "Mumbai", "lat": 19.1136, "lng": 72.8697},
    {"name": "Delhi", "lat": 28.6304, "lng": 77.2177},
    {"name": "Chennai", "lat": 13.0418, "lng": 80.2341},
    {"name": "Kolkata", "lat": 22.5535, "lng": 88.3524},
    {"name": "Pune", "lat": 18.5362, "lng": 73.8940},
    {"name": "Ahmedabad", "lat": 23.0300, "lng": 72.5084},
    {"name": "Jaipur", "lat": 26.9124, "lng": 75.7873},
    {"name": "Lucknow", "lat": 26.8467, "lng": 80.9462},
    {"name": "Kochi", "lat": 9.9678, "lng": 76.2996},
    {"name": "Patna", "lat": 25.6112, "lng": 85.1276},
    {"name": "Bhopal", "lat": 23.2156, "lng": 77.4406},
    {"name": "Indore", "lat": 22.7533, "lng": 75.8937},
    {"name": "Chandigarh", "lat": 30.7398, "lng": 76.7827}
]

def simulate_user_flow(token, sender_id, user_num):
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    }
    
    metrics = {
        "user": user_num,
        "trips_status": None,
        "trips_time": 0.0,
        "book_status": None,
        "book_time": 0.0,
        "payment_status": None,
        "payment_time": 0.0
    }
    
    # 1. Fetch Trips (Browse)
    t0 = time.time()
    try:
        r = requests.get(TRIPS_URL, headers=headers, timeout=15)
        metrics["trips_status"] = r.status_code
    except Exception:
        metrics["trips_status"] = "TIMEOUT/ERROR"
    metrics["trips_time"] = time.time() - t0
    
    pickup = COORDINATE_PAIRS[(user_num - 1) % 15]
    dropoff = COORDINATE_PAIRS[user_num % 15]
    
    # 2. Book a Parcel (Auto-Match)
    payload = {
        "senderId": sender_id,
        "tripId": None,
        "goodsDescription": f"Load Test Goods User {user_num}",
        "declaredValue": 5000.0,
        "estimatedWeightKg": 2.5,
        "pickupLocation": f"{pickup['name']} Location",
        "dropoffLocation": f"{dropoff['name']} Location",
        "pickupLatitude": pickup["lat"],
        "pickupLongitude": pickup["lng"],
        "dropoffLatitude": dropoff["lat"],
        "dropoffLongitude": dropoff["lng"]
    }
    t0 = time.time()
    parcel_id = None
    try:
        r = requests.post(BOOKING_URL, json=payload, headers=headers, timeout=15)
        metrics["book_status"] = r.status_code
        if r.status_code == 200:
            parcel_id = r.json().get("id")
    except Exception:
        metrics["book_status"] = "TIMEOUT/ERROR"
    metrics["book_time"] = time.time() - t0
    
    # 3. Create Payment Order if booked
    if parcel_id:
        pay_url = f"{TARGET_HOST}/api/parcels/{parcel_id}/create-payment-order?senderId={sender_id}"
        t0 = time.time()
        try:
            r = requests.post(pay_url, headers=headers, timeout=15)
            metrics["payment_status"] = r.status_code
            if r.status_code != 200:
                print(f"Payment error for Parcel {parcel_id}: HTTP {r.status_code} - {r.text[:150]}")
        except Exception as e:
            metrics["payment_status"] = "TIMEOUT/ERROR"
            print(f"Payment exception for Parcel {parcel_id}: {e}")
        metrics["payment_time"] = time.time() - t0
        
    return metrics

def run_load_test(concurrency):
    print(f"\n==========================================")
    print(f"Starting Load Test: {concurrency} Concurrent Users")
    print(f"Target: {TARGET_HOST}")
    print(f"==========================================")
    
    token, sender_id = authenticate()
    if not token:
        print("Failed to authenticate test sender. Aborting.")
        return
        
    results = []
    t_start = time.time()
    
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(simulate_user_flow, token, sender_id, i) for i in range(1, concurrency + 1)]
        for fut in as_completed(futures):
            results.append(fut.result())
            
    t_end = time.time()
    total_duration = t_end - t_start
    
    # Compute aggregates
    trips_times = [r["trips_time"] for r in results]
    book_times = [r["book_time"] for r in results]
    pay_times = [r["payment_time"] for r in results if r["payment_status"] is not None]
    
    trips_success = sum(1 for r in results if r["trips_status"] == 200)
    book_success = sum(1 for r in results if r["book_status"] == 200)
    pay_success = sum(1 for r in results if r["payment_status"] == 200)
    
    print(f"\nTest Completed in {total_duration:.2f} seconds.")
    print(f"\n--- Aggregated Metrics ---")
    print(f"1. Fetch Trips (Browse):")
    print(f"   Success Rate: {trips_success}/{concurrency} ({trips_success/concurrency*100:.1f}%)")
    if trips_times:
        print(f"   Response Latency: Min={min(trips_times):.3f}s, Max={max(trips_times):.3f}s, Avg={sum(trips_times)/len(trips_times):.3f}s")
        
    print(f"2. Post Booking (Match):")
    print(f"   Success Rate: {book_success}/{concurrency} ({book_success/concurrency*100:.1f}%)")
    if book_times:
        print(f"   Response Latency: Min={min(book_times):.3f}s, Max={max(book_times):.3f}s, Avg={sum(book_times)/len(book_times):.3f}s")
        
    print(f"3. Create Razorpay Payment (Escrow Init):")
    print(f"   Success Rate: {pay_success}/{len(pay_times) if pay_times else concurrency}")
    if pay_times:
        print(f"   Response Latency: Min={min(pay_times):.3f}s, Max={max(pay_times):.3f}s, Avg={sum(pay_times)/len(pay_times):.3f}s")

if __name__ == "__main__":
    # If a host is passed as command argument, use it
    if len(sys.argv) > 1:
        TARGET_HOST = sys.argv[1].rstrip('/')
        LOGIN_URL = f"{TARGET_HOST}/api/auth/login"
        TRIPS_URL = f"{TARGET_HOST}/api/trips"
        BOOKING_URL = f"{TARGET_HOST}/api/parcels/request"
        
    concurrency_level = 50
    if len(sys.argv) > 2:
        concurrency_level = int(sys.argv[2])
        
    run_load_test(concurrency_level)
