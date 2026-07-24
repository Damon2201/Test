// BlaBla + Porter — Native Role-Guarded Application Engine

const API_BASE = (() => {
    const customBackend = localStorage.getItem('BACKEND_URL');
    if (customBackend) {
        return customBackend.replace(/\/$/, '') + '/api';
    }
    if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        return window.location.port === '8080' ? '/api' : 'http://localhost:8080/api';
    }
    return '/api';
})();

// In-App Toast Notification Engine
function showToast(message, type = 'info') {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast-message ${type}`;
    const icon = type === 'success' ? '✅' : type === 'error' ? '❌' : 'ℹ️';
    toast.innerHTML = `<span>${icon}</span> <span>${escapeHtml(message)}</span>`;

    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

// Decode JWT Claims (extract userId & role from server-signed JWT)
function parseJwtClaims(token) {
    if (!token) return null;
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(atob(base64).split('').map(c => {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
        return JSON.parse(jsonPayload);
    } catch (e) {
        return null;
    }
}

// Global App State
let currentUser = null;
let activeModal = null; // null, 'signin', 'register', 'book-parcel', 'verify-pickup', 'verify-delivery', 'simulated-razorpay', 'book-seat', 'ride-tracking'
let loadedTrips = [];
let selectedTripForBooking = null;
let selectedParcelForVerification = null;
let currentParcelQuote = null;
let pendingSimulatedPayment = null;
let selectedParcelForChat = null;
let selectedParcelForTracking = null;
let selectedTripForSeatBooking = null;
let currentSeatQuote = null;
let selectedRideForTracking = null;
let riderActiveSubTab = 'carpool';
let currentLocalTaxiQuote = null;
let localTaxiBookingMapInstance = null;
let localTaxiPickupMarker = null;
let localTaxiDropoffMarker = null;
let trackingMapInstance = null;
let trackingIntervalId = null;
let lastTrackingPingTimestamp = null;
let trackingAnimationFrameId = null;
let bookingMapInstance = null;
let pickupMarker = null;
let dropoffMarker = null;
let telemetryMapInstance = null;
let telemetryMarker = null;
let usersCache = [];

// ===== Live Tracking Animation Utilities =====
function createCaptainLiveIcon() {
    return L.divIcon({
        className: '',
        html: `<div class="captain-live-marker">
                   <div class="pulse-ring"></div>
                   <div class="pulse-ring pulse-ring-2"></div>
                   <div class="captain-dot"></div>
               </div>`,
        iconSize: [20, 20],
        iconAnchor: [10, 10],
        popupAnchor: [0, -12]
    });
}

function smoothMoveMarker(marker, newLatLng, durationMs) {
    if (!marker) return;
    const startLatLng = marker.getLatLng();
    const startLat = startLatLng.lat;
    const startLng = startLatLng.lng;
    const deltaLat = newLatLng[0] - startLat;
    const deltaLng = newLatLng[1] - startLng;
    if (Math.abs(deltaLat) < 0.00001 && Math.abs(deltaLng) < 0.00001) return;
    const startTime = performance.now();
    function animate(currentTime) {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / durationMs, 1);
        const eased = progress < 0.5 ? 2 * progress * progress : 1 - Math.pow(-2 * progress + 2, 2) / 2;
        const lat = startLat + deltaLat * eased;
        const lng = startLng + deltaLng * eased;
        marker.setLatLng([lat, lng]);
        if (progress < 1) {
            trackingAnimationFrameId = requestAnimationFrame(animate);
        }
    }
    if (trackingAnimationFrameId) cancelAnimationFrame(trackingAnimationFrameId);
    trackingAnimationFrameId = requestAnimationFrame(animate);
}

function updateSignalLostBanner(containerId, lastPingTime) {
    let banner = document.getElementById(containerId + '-signal-lost');
    if (!lastPingTime) {
        if (banner) banner.style.display = 'none';
        return;
    }
    const elapsed = (Date.now() - lastPingTime) / 1000;
    if (elapsed > 30) {
        if (!banner) {
            banner = document.createElement('div');
            banner.id = containerId + '-signal-lost';
            banner.className = 'signal-lost-banner';
            const infoBox = document.getElementById(containerId);
            if (infoBox) infoBox.parentElement.insertBefore(banner, infoBox);
        }
        banner.innerHTML = `⚠️ Signal lost — showing last known location (${Math.round(elapsed)}s ago)`;
        banner.style.display = 'flex';
    } else {
        if (banner) banner.style.display = 'none';
    }
}

async function loadUsersCache() {
    try {
        const res = await fetch(`${API_BASE}/auth/users`, { headers: getAuthHeaders() });
        if (res.ok) {
            usersCache = await res.json();
        }
    } catch (e) {
        console.error('Failed to load users cache', e);
    }
}

function getUserName(userId) {
    const u = usersCache.find(x => x.id === userId);
    return u ? u.fullName : `Captain Traveler #${userId}`;
}

document.addEventListener('DOMContentLoaded', () => {
    loadUserSession();
    renderApp();
});

function loadUserSession() {
    const saved = localStorage.getItem('currentUser');
    if (saved) {
        try {
            const parsed = JSON.parse(saved);
            const claims = parseJwtClaims(parsed.token);
            if (claims && claims.role) {
                parsed.role = claims.role; // Enforce role from JWT claim
                parsed.id = parseInt(claims.sub);
            }
            currentUser = parsed;
        } catch (e) {
            currentUser = null;
        }
    }
}

function getAuthHeaders() {
    const headers = { 'Content-Type': 'application/json' };
    if (currentUser && currentUser.token) {
        headers['Authorization'] = `Bearer ${currentUser.token}`;
    }
    return headers;
}

function logout() {
    localStorage.removeItem('currentUser');
    currentUser = null;
    showToast('Signed out successfully.', 'info');
    renderApp();
}

// Core App Renderer
function renderApp() {
    const root = document.getElementById('root');
    if (!root) return;

    let headerHtml = `
        <header class="navbar">
            <div class="nav-left">
                <div class="brand-logo">
                    <div class="logo-badge">BP</div>
                    <div class="brand-text">
                        <span class="brand-title">BlaBla + Porter</span>
                        <span class="brand-tag">ROLE-GUARDED FREIGHT PLATFORM</span>
                    </div>
                </div>
            </div>
            <div class="nav-right">
                ${currentUser ? `
                    <div class="user-profile-btn" onclick="logout()">
                        <div class="user-avatar">${currentUser.fullName ? currentUser.fullName.charAt(0).toUpperCase() : 'U'}</div>
                        <div class="user-details">
                            <span class="user-name">${escapeHtml(currentUser.fullName)}</span>
                            <span class="user-role-badge">${currentUser.role} (Sign Out)</span>
                        </div>
                    </div>
                ` : `
                    <button class="user-profile-btn" onclick="openAuthModal('signin')">
                        <div class="user-avatar">🔑</div>
                        <div class="user-details">
                            <span class="user-name">Sign In / Register</span>
                            <span class="user-role-badge">BCrypt + JWT Protected</span>
                        </div>
                    </button>
                `}
            </div>
        </header>
    `;

    let mainContentHtml = '';

    if (!currentUser) {
        mainContentHtml = renderUnauthenticatedLanding();
    } else {
        switch (currentUser.role) {
            case 'SENDER':
                mainContentHtml = renderSenderPortal();
                break;
            case 'TRAVELER':
                mainContentHtml = renderCaptainPortal();
                break;
            case 'RIDER':
                mainContentHtml = renderRiderPortal();
                break;
            case 'ADMIN':
                mainContentHtml = renderAdminPortal();
                break;
            default:
                mainContentHtml = `<div style="padding:40px; color:var(--danger);">Invalid User Role in JWT Token</div>`;
        }
    }

    let modalsHtml = renderActiveModal();

    root.innerHTML = `
        ${headerHtml}
        <main class="main-wrapper">
            ${mainContentHtml}
        </main>
        ${modalsHtml}
    `;

    bindPostRenderListeners();

    if (activeModal) {
        document.body.classList.add('modal-open');
    } else {
        document.body.classList.remove('modal-open');
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// ----------------------------------------------------------------------------
// 1. SENDER PORTAL (Rendered ONLY for SENDER role)
// ----------------------------------------------------------------------------
function renderSenderPortal() {
    return `
        <div class="hero-card">
            <div class="hero-header">
                <div class="hero-subtitle">📦 Parcel Sender Portal (Standard Customer)</div>
                <h1 class="hero-title">Search Available Captain Trips & Book Crowd-Shipping</h1>
                <button class="btn-search" style="margin-top:16px; background:var(--porter-teal); color:white; border:none; box-shadow:0 4px 15px rgba(6,182,212,0.3);" onclick="openGeneralBookingModal()">
                    📦 Post General Parcel Request (Auto-Match)
                </button>
            </div>
        </div>

        <div class="section-header">
            <h2 class="section-title">Available Inter-City Captain Deliveries</h2>
            <span class="section-tag">BlaBlaCar-Style Browse & Pick</span>
        </div>

        <div id="sender-trips-container" class="cards-grid">
            <div style="color:var(--text-body); padding:40px;">Loading active routes...</div>
        </div>

        <div class="section-header" style="margin-top:40px;">
            <h2 class="section-title">My Booked Parcels & Escrows</h2>
            <span class="section-tag">Track Status & Handover OTPs</span>
        </div>

        <div id="sender-parcels-container" class="cards-grid">
            <div style="color:var(--text-body); padding:40px;">Loading bookings...</div>
        </div>
    `;
}

// ----------------------------------------------------------------------------
// 2. CAPTAIN / TRAVELER PORTAL (Rendered ONLY for TRAVELER role)
// ----------------------------------------------------------------------------
function renderCaptainPortal() {
    const kycStatus = currentUser.kycStatus || 'NOT_SUBMITTED';

    if (kycStatus !== 'APPROVED') {
        return `
            <div class="hero-card">
                <div class="hero-header">
                    <div class="hero-subtitle">🚗 Captain / Traveler Driver Portal</div>
                    <h1 class="hero-title">Driver KYC Document Verification</h1>
                </div>
            </div>
            <div class="route-card" style="max-width: 600px; margin: 0 auto;">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
                    <h2 style="font-size:20px; font-weight:800;">🪪 Driver Verification Required</h2>
                    <span class="verified-badge" style="background:rgba(245,158,11,0.15); color:var(--warning);">STATUS: ${kycStatus}</span>
                </div>
                <p style="font-size:13px; color:var(--text-body); margin-bottom:24px; line-height:1.5;">
                    ⚠️ Mandatory Driver Protocol: Captains must submit Aadhaar, PAN, Driving Licence, and Vehicle RC to obtain Admin approval before publishing trips.
                </p>
                <form id="captain-kyc-form">
                    <div class="form-group" style="margin-bottom:14px;">
                        <label class="form-label">Aadhaar Card (12 Digits)</label>
                        <input type="text" id="kyc-aadhaar" class="form-control" style="padding-left:16px;" value="1234-5678-9012" required />
                    </div>
                    <div class="form-group" style="margin-bottom:14px;">
                        <label class="form-label">PAN Card Number</label>
                        <input type="text" id="kyc-pan" class="form-control" style="padding-left:16px;" value="ABCDE1234F" required />
                    </div>
                    <div class="form-group" style="margin-bottom:14px;">
                        <label class="form-label">Driving Licence Number</label>
                        <input type="text" id="kyc-dl" class="form-control" style="padding-left:16px;" value="DL-12345-KAR" required />
                    </div>
                    <div class="form-group" style="margin-bottom:24px;">
                        <label class="form-label">Vehicle RC Number</label>
                        <input type="text" id="kyc-rc" class="form-control" style="padding-left:16px;" value="KA-01-AB-1234" required />
                    </div>
                    <button type="submit" class="btn-search" style="width:100%; background:var(--accent-green);">Submit KYC Documents</button>
                </form>
            </div>
        `;
    }

    return `
        <div class="hero-card">
            <div class="hero-header">
                <div class="hero-subtitle">🚗 Captain Driver Portal (KYC APPROVED)</div>
                <h1 class="hero-title">Publish Inter-City Routes & Broadcast GPS</h1>
            </div>
        </div>
        <div style="display:grid; grid-template-columns: 1fr 1fr; gap:24px; margin-bottom:40px;">
            <div class="route-card">
                <h2 style="font-size:20px; font-weight:800; margin-bottom:18px;">📍 Publish Inter-City Route</h2>
                <form id="captain-publish-form">
                    <div class="form-group" style="margin-bottom:14px; position:relative;">
                        <label class="form-label">Origin City</label>
                        <input type="text" id="pub-origin" class="form-control" style="padding-left:16px;" value="Bengaluru" autocomplete="off" required />
                        <div id="pub-origin-suggestions" style="position:absolute; top:100%; left:0; width:100%; max-height:180px; overflow-y:auto; background:var(--bg-surface); border:1px solid var(--border); border-radius:8px; z-index:9999; display:none; box-shadow:0 10px 25px rgba(0,0,0,0.5);"></div>
                    </div>
                    <div class="form-group" style="margin-bottom:14px; position:relative;">
                        <label class="form-label">Destination City</label>
                        <input type="text" id="pub-dest" class="form-control" style="padding-left:16px;" value="Hyderabad" autocomplete="off" required />
                        <div id="pub-dest-suggestions" style="position:absolute; top:100%; left:0; width:100%; max-height:180px; overflow-y:auto; background:var(--bg-surface); border:1px solid var(--border); border-radius:8px; z-index:9999; display:none; box-shadow:0 10px 25px rgba(0,0,0,0.5);"></div>
                    </div>
                    <div style="display:grid; grid-template-columns: 1fr 1fr; gap:14px; margin-bottom:20px;">
                        <div class="form-group">
                            <label class="form-label">Trunk Space (kg)</label>
                            <input type="number" id="pub-kg" class="form-control" style="padding-left:16px;" value="25.0" step="0.5" required />
                        </div>
                        <div class="form-group">
                            <label class="form-label">Seats</label>
                            <input type="number" id="pub-seats" class="form-control" style="padding-left:16px;" value="3" required />
                        </div>
                    </div>
                    <button type="submit" class="btn-search" style="width:100%;">Publish Route</button>
                </form>
            </div>
            <div class="route-card">
                <h2 style="font-size:20px; font-weight:800; margin-bottom:18px;">📡 Live Telemetry GPS Broadcaster</h2>
                <div id="telemetry-map" style="height:180px; border-radius:12px; margin-bottom:12px; border:1px solid var(--border); z-index:1;"></div>
                <form id="gps-broadcast-form">
                    <div style="display:grid; grid-template-columns: 1fr 1fr 1fr; gap:8px; margin-bottom:12px;">
                        <div>
                            <label class="form-label" style="font-size:10px; text-align:center; display:block;">Latitude</label>
                            <input type="number" id="gps-lat" class="form-control" style="padding:10px 6px; text-align:center; font-size:12px;" value="12.9716" step="0.0001" required />
                        </div>
                        <div>
                            <label class="form-label" style="font-size:10px; text-align:center; display:block;">Longitude</label>
                            <input type="number" id="gps-lng" class="form-control" style="padding:10px 6px; text-align:center; font-size:12px;" value="77.5946" step="0.0001" required />
                        </div>
                        <div>
                            <label class="form-label" style="font-size:10px; text-align:center; display:block;">Speed (km/h)</label>
                            <input type="number" id="gps-speed" class="form-control" style="padding:10px 6px; text-align:center; font-size:12px;" value="85.0" step="0.1" required />
                        </div>
                    </div>
                    <button type="button" id="btn-device-gps" class="btn-search" style="width:100%; margin-bottom:10px; background:var(--bg-surface); color:var(--text-title); border:1px solid var(--border);">📍 Use Device GPS</button>
                    <button type="submit" class="btn-search" style="width:100%; background:var(--porter-gradient);">Broadcast Live GPS Telemetry</button>
                </form>
            </div>
        </div>

        <div class="section-header">
            <h2 class="section-title">Manage Cargo Bookings & Handover Verification</h2>
            <span class="section-tag">Fulfill Accepted Deliveries</span>
        </div>

        <div id="captain-parcels-container" class="cards-grid" style="margin-bottom:32px;">
            <div style="color:var(--text-body); padding:40px;">Loading cargo list...</div>
        </div>

        <div class="section-header">
            <h2 class="section-title">🚖 Same-City Local Taxi Mode</h2>
            <span class="section-tag">On-Demand Same-City Rides</span>
        </div>
        <div class="route-card" style="margin-bottom:40px;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
                <h3 style="font-size:18px; font-weight:800; color:var(--text-white);">Toggle Local Availability</h3>
                <label style="display:flex; align-items:center; gap:8px; cursor:pointer;">
                    <input type="checkbox" id="local-taxi-available-toggle" onchange="toggleLocalTaxiAvailabilityBtn(this.checked)" style="transform:scale(1.2);" />
                    <span style="font-weight:700; font-size:13px; color:var(--danger);" id="local-taxi-toggle-label">OFFLINE</span>
                </label>
            </div>
            <div style="display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-bottom:16px;">
                <div>
                    <label class="form-label">Current Latitude</label>
                    <input type="number" id="local-gps-lat" class="form-control" value="12.9716" step="0.0001" onchange="updateLocalGpsCoordinates()" />
                </div>
                <div>
                    <label class="form-label">Current Longitude</label>
                    <input type="number" id="local-gps-lng" class="form-control" value="77.5946" step="0.0001" onchange="updateLocalGpsCoordinates()" />
                </div>
            </div>
            
            <h4 style="font-size:14px; font-weight:800; margin-top:24px; margin-bottom:12px; color:var(--porter-teal);">Active Local Taxi Assignments</h4>
            <div id="captain-local-bookings-container">
                <div style="color:var(--text-muted); font-size:13px;">No active local assignments. Make yourself available above.</div>
            </div>
        </div>
    `;
}

// ----------------------------------------------------------------------------
// 3. PASSENGER RIDER PORTAL (Rendered ONLY for RIDER role)
// ----------------------------------------------------------------------------
function renderRiderPortal() {
    return `
        <div class="hero-card">
            <div class="hero-header">
                <div class="hero-subtitle">🚖 Passenger Carpooling Portal</div>
                <h1 class="hero-title">Book Inter-City Seat & Manage Safety Contacts</h1>
            </div>
        </div>

        <div style="display:flex; gap:12px; margin-bottom:28px;">
            <button class="service-btn ${riderActiveSubTab === 'carpool' ? 'active' : ''}" onclick="setRiderSubTab('carpool')">🚗 Inter-City Carpool</button>
            <button class="service-btn ${riderActiveSubTab === 'taxi' ? 'active' : ''}" onclick="setRiderSubTab('taxi')">🚖 Same-City Local Taxi</button>
        </div>

        ${riderActiveSubTab === 'carpool' ? `
            <div class="section-header">
                <h2 class="section-title">🔍 Search Inter-City Carpool Seats</h2>
                <span class="section-tag">Find Drivers Sharing Route & Seats</span>
            </div>

            <div class="route-card" style="padding: 24px; margin-bottom: 24px;">
                <div style="display: grid; grid-template-columns: 1fr 1fr auto; gap: 16px; align-items: end;">
                    <div class="form-group">
                        <label class="form-label" for="rider-search-source">Leaving From</label>
                        <input type="text" id="rider-search-source" class="form-control" placeholder="e.g., Bengaluru" style="padding-left: 16px;" />
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="rider-search-destination">Going To</label>
                        <input type="text" id="rider-search-destination" class="form-control" placeholder="e.g., Chennai" style="padding-left: 16px;" />
                    </div>
                    <button class="btn-search" onclick="triggerRiderSearch()" style="height: 46px; padding: 0 32px;">Search Seats</button>
                </div>
            </div>

            <div id="rider-search-results" class="cards-grid" style="margin-bottom: 32px;">
                <div style="color:var(--text-muted); padding:20px;">Use search fields above to query routes.</div>
            </div>
        ` : `
            <div class="section-header">
                <h2 class="section-title">🔍 Same-City Local Taxi Booking</h2>
                <span class="section-tag">Instantly Match Nearby Available Captains</span>
            </div>

            <div class="route-card" style="padding: 24px; margin-bottom: 24px;">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
                    <h2 style="font-size:20px; font-weight:800;">🚖 Book Same-City Instant Taxi</h2>
                    <span class="verified-badge" style="background:rgba(6,182,212,0.15); color:var(--porter-teal);">ON-DEMAND MATCHING</span>
                </div>
                
                <form id="local-taxi-booking-form" onsubmit="submitLocalTaxiBookingForm(event)">
                    <div style="display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-bottom:16px;">
                        <div class="form-group" style="position:relative;">
                            <label class="form-label">Pickup Area Address</label>
                            <input type="text" id="local-taxi-pickup" class="form-control" style="padding-left:16px;" value="Koramangala, Bengaluru" autocomplete="off" required />
                            <div id="local-taxi-pickup-suggestions" style="position:absolute; top:100%; left:0; width:100%; max-height:180px; overflow-y:auto; background:var(--bg-surface); border:1px solid var(--border); border-radius:8px; z-index:9999; display:none; box-shadow:0 10px 25px rgba(0,0,0,0.5);"></div>
                            <input type="hidden" id="local-taxi-pickup-lat" value="12.9352" />
                            <input type="hidden" id="local-taxi-pickup-lng" value="77.6245" />
                        </div>
                        <div class="form-group" style="position:relative;">
                            <label class="form-label">Dropoff Area Address</label>
                            <input type="text" id="local-taxi-dropoff" class="form-control" style="padding-left:16px;" value="Indiranagar, Bengaluru" autocomplete="off" required />
                            <div id="local-taxi-dropoff-suggestions" style="position:absolute; top:100%; left:0; width:100%; max-height:180px; overflow-y:auto; background:var(--bg-surface); border:1px solid var(--border); border-radius:8px; z-index:9999; display:none; box-shadow:0 10px 25px rgba(0,0,0,0.5);"></div>
                            <input type="hidden" id="local-taxi-dropoff-lat" value="12.9719" />
                            <input type="hidden" id="local-taxi-dropoff-lng" value="77.6412" />
                        </div>
                    </div>

                    <div id="local-taxi-booking-map" style="height:220px; border-radius:12px; margin-bottom:16px; border:1px solid var(--border); z-index:1;"></div>

                    <div class="form-group" style="margin-bottom:16px; display:flex; align-items:center; gap:8px;">
                        <input type="checkbox" id="local-taxi-safety-mode" checked />
                        <label for="local-taxi-safety-mode" class="form-label" style="margin-bottom:0; cursor:pointer; font-weight:700;">
                            🚨 Enable 3-Stage Safety Mode (SOS, silent ping, check-ins)
                        </label>
                    </div>

                    <div id="local-taxi-fare-breakdown-box" style="background:var(--bg-surface); padding:16px; border-radius:12px; margin-bottom:20px; border:1px solid var(--border);">
                        <!-- Dynamic local fare breakdown will show here -->
                    </div>

                    <button type="submit" class="btn-search" style="width:100%; background:var(--porter-gradient); box-shadow:0 4px 15px rgba(6,182,212,0.3);">Book Local Taxi & Pay Escrow (INR ₹)</button>
                </form>
            </div>
        `}

        <div class="section-header">
            <h2 class="section-title">💺 Your Seat Bookings & Safety Escrow</h2>
            <span class="section-tag">Live tracking, Escrow control & Safety check-ins</span>
        </div>

        <div id="rider-rides-container" class="cards-grid" style="margin-bottom: 32px;">
            <div style="color:var(--text-muted); padding:20px;">Loading your bookings...</div>
        </div>

        <div class="section-header">
            <h2 class="section-title">🛡️ Emergency Safety Contacts</h2>
            <span class="section-tag">Trusted contacts for Stage-3 automatic escalations</span>
        </div>

        <div class="route-card" style="padding: 24px; margin-bottom: 32px;">
            <div style="display: grid; grid-template-columns: 1fr 1fr 1fr auto; gap: 16px; align-items: end; margin-bottom: 20px;">
                <div class="form-group">
                    <label class="form-label" for="rider-contact-name">Contact Name</label>
                    <input type="text" id="rider-contact-name" class="form-control" placeholder="e.g., John Doe" style="padding-left: 16px;" />
                </div>
                <div class="form-group">
                    <label class="form-label" for="rider-contact-phone">Mobile Number</label>
                    <input type="text" id="rider-contact-phone" class="form-control" placeholder="e.g., 9876543210" style="padding-left: 16px;" />
                </div>
                <div class="form-group">
                    <label class="form-label" for="rider-contact-relationship">Relationship</label>
                    <input type="text" id="rider-contact-relationship" class="form-control" placeholder="e.g., Brother" style="padding-left: 16px;" />
                </div>
                <button class="btn-search" onclick="addTrustedContactBtn()" style="height: 46px; padding: 0 24px; background: var(--porter-teal);">Add Contact</button>
            </div>
            
            <div id="rider-contacts-container">
                <div style="color:var(--text-muted); padding:10px;">Loading safety contacts...</div>
            </div>
        </div>
    `;
}

// ----------------------------------------------------------------------------
// 4. ADMIN PORTAL (Rendered ONLY for ADMIN role)
// ----------------------------------------------------------------------------
function renderAdminPortal() {
    return `
        <div class="hero-card">
            <div class="hero-header">
                <div class="hero-subtitle">🛡️ System Safety & Governance Console</div>
                <h1 class="hero-title">Review KYC Queue & Escrow Disputes</h1>
            </div>
        </div>
        <div style="display:grid; grid-template-columns: 1fr 1fr; gap:24px;">
            <div class="route-card">
                <h2 style="font-size:20px; font-weight:800; margin-bottom:16px;">🪪 Pending Captain KYC Approval Queue</h2>
                <div id="admin-kyc-queue-container">
                    <div style="color:var(--text-body); padding:16px;">Loading pending KYC requests...</div>
                </div>
            </div>
            <div class="route-card">
                <h2 style="font-size:20px; font-weight:800; margin-bottom:16px;">⚖️ Escrow Dispute Resolution Console</h2>
                <div style="background:var(--bg-surface); padding:16px; border-radius:12px; border:1px solid var(--border);">
                    <div style="font-weight:800; margin-bottom:6px;">Dispute #104 (Damaged Packaging)</div>
                    <div style="font-size:12px; color:var(--text-body); margin-bottom:14px;">Reporter: Stefan Salvatore | Traveler: Captain Bob</div>
                    <button class="btn-search" style="background:var(--danger); width:100%; padding:10px;" onclick="showToast('Refunded Escrow to Sender!', 'success')">Refund Sender Escrow</button>
                </div>
            </div>
        </div>
    `;
}

// Unauthenticated Landing Screen
function renderUnauthenticatedLanding() {
    return `
        <div class="hero-card" style="text-align:center; padding:60px 40px;">
            <div class="hero-subtitle">India's P2P Inter-City Platform</div>
            <h1 class="hero-title" style="margin: 12px 0 20px 0;">Peer-to-Peer Crowd-Shipping & Passenger Carpooling</h1>
            <p style="color:var(--text-body); max-width:600px; margin:0 auto 32px auto;">
                Please sign in with your mobile number and BCrypt password to access your role-specific dashboard.
            </p>
            <button class="btn-search" style="margin:0 auto;" onclick="openAuthModal('signin')">Sign In / Create Account</button>
        </div>
    `;
}

// Modals Renderer
function renderActiveModal() {
    if (!activeModal) return '';

    if (activeModal === 'signin' || activeModal === 'register') {
        const isSignIn = activeModal === 'signin';
        return `
            <div class="modal-backdrop show">
                <div class="modal-box">
                    <div class="modal-head">
                        <div style="display:flex; gap:12px;">
                            <button class="service-btn ${isSignIn ? 'active' : ''}" onclick="setAuthMode('signin')">Sign In</button>
                            <button class="service-btn ${!isSignIn ? 'active' : ''}" onclick="setAuthMode('register')">Create Account</button>
                        </div>
                        <button class="btn-close" onclick="closeModal()">✕</button>
                    </div>

                    ${isSignIn ? `
                        <form id="auth-signin-form">
                            <div class="form-group" style="margin-bottom:14px;">
                                <label class="form-label">Registered 10-Digit Mobile Number</label>
                                <input type="text" id="signin-mobile" class="form-control" style="padding-left:16px;" value="9876543213" required />
                            </div>
                            <div class="form-group" style="margin-bottom:20px;">
                                <label class="form-label">Password</label>
                                <input type="password" id="signin-password" class="form-control" style="padding-left:16px;" value="password123" required />
                            </div>
                            <button type="submit" class="btn-search" style="width:100%;">Sign In with BCrypt + JWT</button>
                        </form>
                    ` : `
                        <form id="auth-register-form">
                            <div class="form-group" style="margin-bottom:14px;">
                                <label class="form-label">Full Name</label>
                                <input type="text" id="reg-name" class="form-control" style="padding-left:16px;" value="Stefan Salvatore" required />
                            </div>
                            <div class="form-group" style="margin-bottom:14px;">
                                <label class="form-label">10-Digit Mobile Number</label>
                                <input type="text" id="reg-mobile" class="form-control" style="padding-left:16px;" value="9888811111" required />
                            </div>
                            <div class="form-group" style="margin-bottom:14px;">
                                <label class="form-label">Email Address</label>
                                <input type="email" id="reg-email" class="form-control" style="padding-left:16px;" value="stefan@mystic.com" required />
                            </div>
                            <div class="form-group" style="margin-bottom:14px;">
                                <label class="form-label">Password</label>
                                <input type="password" id="reg-password" class="form-control" style="padding-left:16px;" value="password123" required />
                            </div>
                            <div class="form-group" style="margin-bottom:14px;">
                                <label class="form-label">Account Role (ADMIN blocked server-side)</label>
                                <select id="reg-role" class="form-control" style="padding-left:16px;" onchange="toggleKycFields(this.value)" required>
                                    <option value="SENDER">📦 Parcel Sender (Standard Customer)</option>
                                    <option value="TRAVELER">🚗 Captain / Traveler (Driver - Mandatory KYC)</option>
                                    <option value="RIDER">🚖 Passenger Rider (Carpool Seats)</option>
                                </select>
                            </div>

                            <div id="kyc-fields-container" style="display:none; border:1px dashed var(--border); padding:14px; border-radius:12px; margin-bottom:14px; background:var(--bg-surface);">
                                <div style="font-weight:700; color:var(--porter-teal); margin-bottom:10px; font-size:13px;">🪪 Mandatory Captain KYC Documents</div>
                                <div class="form-group" style="margin-bottom:10px;">
                                    <label class="form-label">Aadhaar Card Number</label>
                                    <input type="text" id="reg-aadhaar" class="form-control" style="padding-left:16px;" value="123456789012" placeholder="12-digit Aadhaar" />
                                </div>
                                <div class="form-group" style="margin-bottom:10px;">
                                    <label class="form-label">PAN Card Number</label>
                                    <input type="text" id="reg-pan" class="form-control" style="padding-left:16px;" value="ABCDE1234F" placeholder="ABCDE1234F" />
                                </div>
                                <div class="form-group" style="margin-bottom:10px;">
                                    <label class="form-label">Driving License Number</label>
                                    <input type="text" id="reg-dl" class="form-control" style="padding-left:16px;" value="DL-1420110068745" placeholder="Driving License Number" />
                                </div>
                                <div class="form-group" style="margin-bottom:0px;">
                                    <label class="form-label">Vehicle Registration Certificate (RC)</label>
                                    <input type="text" id="reg-rc" class="form-control" style="padding-left:16px;" value="RC-9988-AA" placeholder="RC Number" />
                                </div>
                            </div>

                            <div id="otp-verification-container" style="display:none; border:1px solid var(--porter-teal); padding:14px; border-radius:12px; margin-bottom:14px; background:rgba(6,182,212,0.05);">
                                <div class="form-group" style="margin-bottom:0px;">
                                    <label class="form-label" style="color:var(--porter-teal); font-weight:700;">🔑 Enter 6-Digit SMS Verification OTP</label>
                                    <input type="text" id="reg-otp" class="form-control" style="padding-left:16px; text-align:center; font-size:18px; letter-spacing:4px;" placeholder="000000" maxlength="6" />
                                </div>
                            </div>

                            <button type="button" id="btn-send-otp" class="btn-search" style="width:100%; margin-bottom:10px;" onclick="sendRegistrationOtpBtn()">Send Verification OTP via SMS</button>
                            <button type="submit" id="btn-submit-register" class="btn-search" style="width:100%; display:none;">Verify OTP & Create Account</button>
                        </form>
                    `}
                </div>
            </div>
        `;
    }

    if (activeModal === 'book-parcel') {
        const q = currentParcelQuote || { baseFareInr: 150, distanceFareInr: 0, categorySurchargeInr: 0, totalFareInr: 150, categoryTierLabel: 'Standard Tier' };
        const pickupVal = selectedTripForBooking ? selectedTripForBooking.source : "Indiranagar, Bengaluru";
        const dropoffVal = selectedTripForBooking ? selectedTripForBooking.destination : "Banjara Hills, Hyderabad";
        return `
            <div class="modal-backdrop show">
                <div class="modal-box">
                    <div class="modal-head">
                        <h3>📦 Book P2P Crowd-Shipping Delivery</h3>
                        <button class="btn-close" onclick="closeModal()">✕</button>
                    </div>
                    <form id="parcel-booking-form">
                        <div class="form-group" style="margin-bottom:12px;">
                            <label class="form-label">Goods Description</label>
                            <input type="text" id="book-goods" class="form-control" style="padding-left:16px;" placeholder="e.g. Urgent Documents, Electronics, Laptop" value="Urgent Medical Documents & Supplies" required />
                        </div>
                        <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-bottom:12px;">
                            <div class="form-group">
                                <label class="form-label">Declared Value (INR ₹)</label>
                                <input type="number" id="book-value" class="form-control" style="padding-left:16px;" value="15000" oninput="updateFareQuote(this.value)" required />
                            </div>
                            <div class="form-group">
                                <label class="form-label">Weight (kg)</label>
                                <input type="number" id="book-weight" class="form-control" style="padding-left:16px;" value="4.0" step="0.1" oninput="updateFareQuote(document.getElementById('book-value').value)" required />
                            </div>
                        </div>
                        <div class="form-group" style="margin-bottom:12px; position:relative;">
                            <label class="form-label">Pickup Address</label>
                            <input type="text" id="book-pickup" class="form-control" style="padding-left:16px;" value="${escapeHtml(pickupVal)}" placeholder="Search pickup area..." autocomplete="off" required />
                            <div id="pickup-suggestions" style="position:absolute; top:100%; left:0; width:100%; max-height:180px; overflow-y:auto; background:var(--bg-surface); border:1px solid var(--border); border-radius:8px; z-index:9999; display:none; box-shadow:0 10px 25px rgba(0,0,0,0.5);"></div>
                            <input type="hidden" id="book-pickup-lat" value="12.9716" />
                            <input type="hidden" id="book-pickup-lng" value="77.5946" />
                        </div>
                        <div class="form-group" style="margin-bottom:12px; position:relative;">
                            <label class="form-label">Dropoff Address</label>
                            <input type="text" id="book-dropoff" class="form-control" style="padding-left:16px;" value="${escapeHtml(dropoffVal)}" placeholder="Search dropoff area..." autocomplete="off" required />
                            <div id="dropoff-suggestions" style="position:absolute; top:100%; left:0; width:100%; max-height:180px; overflow-y:auto; background:var(--bg-surface); border:1px solid var(--border); border-radius:8px; z-index:9999; display:none; box-shadow:0 10px 25px rgba(0,0,0,0.5);"></div>
                            <input type="hidden" id="book-dropoff-lat" value="13.0827" />
                            <input type="hidden" id="book-dropoff-lng" value="80.2707" />
                        </div>
                        <div id="booking-map" style="height:180px; border-radius:12px; margin-bottom:16px; border:1px solid var(--border); z-index:1;"></div>
                        <div id="fare-breakdown-box" style="background:var(--bg-surface); padding:16px; border-radius:12px; margin-bottom:20px; border:1px solid var(--border);">
                            <div style="font-weight:700; color:var(--porter-teal); margin-bottom:8px; font-size:13px;">💰 Transparent Fare Quote Breakdown</div>
                            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:4px;">
                                <span>Base Route Fare:</span>
                                <span>₹${q.baseFareInr}</span>
                            </div>
                            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:4px;">
                                <span>Distance Charge (Est. 350 Km):</span>
                                <span>₹${q.distanceFareInr}</span>
                            </div>
                            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:6px;">
                                <span>Value-Based Surcharge (${q.categoryTierLabel}):</span>
                                <span>₹${q.categorySurchargeInr}</span>
                            </div>
                            <div style="display:flex; justify-content:space-between; font-weight:800; font-size:14px; border-top:1px dashed var(--border); padding-top:6px; color:var(--accent-green);">
                                <span>Total Escrow Amount:</span>
                                <span>₹${q.totalFareInr} INR</span>
                            </div>
                        </div>
                        <button type="submit" class="btn-search" style="width:100%; background:var(--porter-gradient); box-shadow:0 4px 15px rgba(6,182,212,0.3);">Confirm Booking & Pay Escrow (INR ₹)</button>
                    </form>
                </div>
            </div>
        `;
    }

    if (activeModal === 'verify-pickup' && selectedParcelForVerification) {
        return `
            <div class="modal-backdrop show">
                <div class="modal-box">
                    <div class="modal-head">
                        <h3>📦 Verify Handover & Pickup</h3>
                        <button class="btn-close" onclick="closeModal()">✕</button>
                    </div>
                    <form id="pickup-verification-form">
                        <p style="font-size:13px; color:var(--text-body); margin-bottom:16px;">
                            Enter the 4-digit Pickup OTP provided by the Sender to authorize cargo release.
                        </p>
                        <div class="form-group" style="margin-bottom:14px;">
                            <label class="form-label">Pickup Verification OTP</label>
                            <input type="text" id="verify-pickup-otp" class="form-control" style="padding-left:16px; text-align:center; font-size:24px; letter-spacing:4px;" placeholder="0000" maxlength="4" required />
                        </div>
                        <div class="form-group" style="margin-bottom:20px;">
                            <label class="form-label">Handover Proof Photo URL</label>
                            <input type="text" id="verify-pickup-photo" class="form-control" style="padding-left:16px;" value="https://example.com/proofs/pickup_${selectedParcelForVerification.id}.jpg" required />
                        </div>
                        <button type="submit" class="btn-search" style="width:100%; background:var(--primary-gradient);">Confirm Pickup Verification</button>
                    </form>
                </div>
            </div>
        `;
    }

    if (activeModal === 'verify-delivery' && selectedParcelForVerification) {
        return `
            <div class="modal-backdrop show">
                <div class="modal-box">
                    <div class="modal-head">
                        <h3>📦 Verify Cargo Destination Delivery</h3>
                        <button class="btn-close" onclick="closeModal()">✕</button>
                    </div>
                    <form id="delivery-verification-form">
                        <p style="font-size:13px; color:var(--text-body); margin-bottom:16px;">
                            Enter the 4-digit Delivery OTP provided by the Sender to complete the delivery and release escrow funds.
                        </p>
                        <div class="form-group" style="margin-bottom:14px;">
                            <label class="form-label">Delivery Verification OTP</label>
                            <input type="text" id="verify-delivery-otp" class="form-control" style="padding-left:16px; text-align:center; font-size:24px; letter-spacing:4px;" placeholder="0000" maxlength="4" required />
                        </div>
                        <div class="form-group" style="margin-bottom:20px;">
                            <label class="form-label">Delivery Proof Photo URL</label>
                            <input type="text" id="verify-delivery-photo" class="form-control" style="padding-left:16px;" value="https://example.com/proofs/delivery_${selectedParcelForVerification.id}.jpg" required />
                        </div>
                        <button type="submit" class="btn-search" style="width:100%; background:var(--accent-green); box-shadow: 0 4px 15px var(--accent-glow);">Confirm Delivery & Release Escrow</button>
                    </form>
                </div>
            </div>
        `;
    }

    if (activeModal === 'simulated-razorpay' && pendingSimulatedPayment) {
        const order = pendingSimulatedPayment;
        return `
            <div class="modal-backdrop show" style="background: rgba(0, 0, 0, 0.95);">
                <div class="modal-box" style="border-color: var(--porter-teal); box-shadow: 0 0 40px rgba(6, 182, 212, 0.25);">
                    <div style="text-align:center; margin-bottom:20px;">
                        <div style="font-size: 32px; margin-bottom:10px;">💳</div>
                        <h3 style="font-size:20px; font-weight:800; color:var(--text-white);">Razorpay Test Mode Sandbox</h3>
                        <p style="font-size:12px; color:var(--porter-teal); font-weight:700; margin-top:4px;">SIMULATED WEB CHECKOUT</p>
                    </div>
                    
                    <div style="background:var(--bg-surface); padding:16px; border-radius:12px; margin-bottom:24px; border:1px solid var(--border); font-size:13px;">
                        <div style="display:flex; justify-content:space-between; margin-bottom:6px;">
                            <span style="color:var(--text-body);">Order ID:</span>
                            <span style="font-family:monospace; color:#fff;">${order.orderId}</span>
                        </div>
                        <div style="display:flex; justify-content:space-between; margin-bottom:6px;">
                            <span style="color:var(--text-body);">Goods:</span>
                            <span style="color:#fff;">${order.goodsDescription}</span>
                        </div>
                        <div style="display:flex; justify-content:space-between; margin-bottom:6px;">
                            <span style="color:var(--text-body);">Sender Name:</span>
                            <span style="color:#fff;">${order.senderName}</span>
                        </div>
                        <div style="display:flex; justify-content:space-between; margin-bottom:10px; border-bottom:1px solid var(--border); padding-bottom:10px;">
                            <span style="color:var(--text-body);">Key ID:</span>
                            <span style="font-family:monospace; color:var(--warning);">${order.keyId}</span>
                        </div>
                        <div style="display:flex; justify-content:space-between; font-weight:800; font-size:15px; color:var(--accent-green);">
                            <span>Amount to Pay:</span>
                            <span>₹${order.amount} INR</span>
                        </div>
                    </div>
                    
                    <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
                        <button class="btn-search" style="background:var(--accent-green); box-shadow:0 4px 15px var(--accent-glow);" onclick="completeSimulatedPayment(true)">Simulate Success</button>
                        <button class="btn-search" style="background:var(--danger); box-shadow:none;" onclick="completeSimulatedPayment(false)">Cancel Payment</button>
                    </div>
                </div>
            </div>
        `;
    }

    if (activeModal === 'chat' && selectedParcelForChat) {
        setTimeout(() => { loadChatMessages(selectedParcelForChat.id); }, 100);
        return `
            <div class="modal-backdrop show">
                <div class="modal-box" style="max-width:500px;">
                    <div class="modal-head">
                        <h3>💬 P2P Handshake Chat (Booking #${selectedParcelForChat.id})</h3>
                        <button class="btn-close" onclick="closeModal()">✕</button>
                    </div>
                    <div id="chat-messages-box" style="height:300px; overflow-y:auto; border:1px solid var(--border); border-radius:12px; padding:16px; margin-bottom:16px; background:var(--bg-surface);">
                        <div style="color:var(--text-muted); text-align:center; padding-top:100px;">Loading messages...</div>
                    </div>
                    <form id="chat-send-form" onsubmit="sendChatMessageBtn(event)">
                        <div style="display:flex; gap:12px;">
                            <input type="text" id="chat-input-text" class="form-control" placeholder="Type your message here..." style="padding-left:16px;" required />
                            <button type="submit" class="btn-search" style="padding:0 24px;">Send</button>
                        </div>
                    </form>
                </div>
            </div>
        `;
    }

    if (activeModal === 'tracking' && selectedParcelForTracking) {
        return `
            <div class="modal-backdrop show">
                <div class="modal-box" style="max-width:600px;">
                    <div class="modal-head">
                        <h3>🗺️ Live Telemetry Tracking (Booking #${selectedParcelForTracking.id})</h3>
                        <button class="btn-close" onclick="closeModal()">✕</button>
                    </div>
                    <div id="parcel-signal-lost-area"></div>
                    <div id="tracking-info-box" style="background:var(--bg-surface); padding:12px; border-radius:8px; border:1px solid var(--border); margin-bottom:12px; font-size:12px; line-height:1.5;">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
                            <span><b>Captain Status:</b></span>
                            <span style="display:flex; align-items:center; gap:8px;">
                                <span id="tracking-status" style="font-weight:700; color:var(--porter-teal);">LOADING...</span>
                                <span class="live-badge"><span class="live-dot"></span>LIVE</span>
                            </span>
                        </div>
                        <div style="display:flex; justify-content:space-between; margin-bottom:4px;">
                            <span><b>Distance Remaining:</b></span>
                            <span id="tracking-distance" style="font-weight:700; color:var(--accent-green);">--</span>
                        </div>
                        <div style="display:flex; justify-content:space-between;">
                            <span><b>Estimated Time Remaining (ETA):</b></span>
                            <span id="tracking-eta" style="font-weight:700; color:var(--accent-green);">--</span>
                        </div>
                    </div>
                    <div id="tracking-map" style="height:320px; border-radius:12px; border:1px solid var(--border); z-index:1;"></div>
                </div>
            </div>
        `;
    }

    if (activeModal === 'book-seat' && selectedTripForSeatBooking) {
        const q = currentSeatQuote || { baseFare: 50, distanceFare: 0, totalFare: 50 };
        const trip = selectedTripForSeatBooking;
        return `
            <div class="modal-backdrop show">
                <div class="modal-box">
                    <div class="modal-head">
                        <h3>💺 Book Passenger Co-Ride Seat</h3>
                        <button class="btn-close" onclick="closeModal()">✕</button>
                    </div>
                    <form id="seat-booking-form" onsubmit="submitRideBookingForm(event)">
                        <div class="form-group" style="margin-bottom:12px; position:relative;">
                            <label class="form-label">Pickup Address</label>
                            <input type="text" id="seat-pickup" class="form-control" style="padding-left:16px;" value="${escapeHtml(trip.source)}" placeholder="Search pickup area..." required />
                            <input type="hidden" id="seat-pickup-lat" value="12.9716" />
                            <input type="hidden" id="seat-pickup-lng" value="77.5946" />
                        </div>
                        <div class="form-group" style="margin-bottom:12px; position:relative;">
                            <label class="form-label">Dropoff Address</label>
                            <input type="text" id="seat-dropoff" class="form-control" style="padding-left:16px;" value="${escapeHtml(trip.destination)}" placeholder="Search dropoff area..." required />
                            <input type="hidden" id="seat-dropoff-lat" value="13.0827" />
                            <input type="hidden" id="seat-dropoff-lng" value="80.2707" />
                        </div>
                        <div id="seat-booking-map" style="height:180px; border-radius:12px; margin-bottom:16px; border:1px solid var(--border); z-index:1;"></div>
                        
                        <div class="form-group" style="margin-bottom:16px; display:flex; align-items:center; gap:8px;">
                            <input type="checkbox" id="seat-safety-mode" checked />
                            <label for="seat-safety-mode" class="form-label" style="margin-bottom:0; cursor:pointer;">
                                🚨 Enable 3-Stage Safety Mode (SOS alerts, silent ping, check-ins)
                            </label>
                        </div>

                        <div id="seat-fare-breakdown-box" style="background:var(--bg-surface); padding:16px; border-radius:12px; margin-bottom:20px; border:1px solid var(--border);">
                            <div style="font-weight:700; color:var(--porter-teal); margin-bottom:8px; font-size:13px;">💰 Transparent Fare Quote Breakdown</div>
                            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:4px;">
                                <span>Base Platform Fare:</span>
                                <span>₹${q.baseFare}</span>
                            </div>
                            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:6px;">
                                <span>Distance Charge:</span>
                                <span>₹${q.distanceFare}</span>
                            </div>
                            <div style="display:flex; justify-content:space-between; font-weight:800; font-size:14px; border-top:1px dashed var(--border); padding-top:6px; color:var(--accent-green);">
                                <span>Total Escrow Amount:</span>
                                <span>₹${q.totalFare} INR</span>
                            </div>
                        </div>
                        <button type="submit" class="btn-search" style="width:100%; background:var(--porter-gradient); box-shadow:0 4px 15px rgba(6,182,212,0.3);">Confirm Booking & Pay Escrow (INR ₹)</button>
                    </form>
                </div>
            </div>
        `;
    }

    if (activeModal === 'ride-tracking' && selectedRideForTracking) {
        const ride = selectedRideForTracking;
        return `
            <div class="modal-backdrop show">
                <div class="modal-box" style="max-width:600px;">
                    <div class="modal-head">
                        <h3>🗺️ Ride Live Telemetry & Safety Console</h3>
                        <button class="btn-close" onclick="closeModal()">✕</button>
                    </div>
                    <div id="ride-signal-lost-area"></div>
                    <div id="ride-tracking-info-box" style="background:var(--bg-surface); padding:12px; border-radius:8px; border:1px solid var(--border); margin-bottom:12px; font-size:12px; line-height:1.5;">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
                            <span><b>Ride Status:</b></span>
                            <span style="display:flex; align-items:center; gap:8px;">
                                <span id="ride-tracking-status" style="font-weight:700; color:var(--porter-teal);">${ride.status}</span>
                                <span class="live-badge"><span class="live-dot"></span>LIVE</span>
                            </span>
                        </div>
                        <div style="display:flex; justify-content:space-between; margin-bottom:4px;">
                            <span><b>Distance Remaining:</b></span>
                            <span id="ride-tracking-distance" style="font-weight:700; color:var(--accent-green);">-- km</span>
                        </div>
                        <div style="display:flex; justify-content:space-between;">
                            <span><b>Estimated Time Remaining (ETA):</b></span>
                            <span id="ride-tracking-eta" style="font-weight:700; color:var(--accent-green);">-- mins</span>
                        </div>
                    </div>
                    <div id="ride-tracking-map" style="height:260px; border-radius:12px; border:1px solid var(--border); margin-bottom:16px; z-index:1;"></div>

                    <div style="background:var(--bg-surface); padding:16px; border-radius:12px; border:1px solid var(--border);">
                        <h4 style="font-size:13px; font-weight:800; color:var(--danger); margin-bottom:10px; display:flex; align-items:center; gap:6px;">
                            🚨 3-Stage Emergency Safety Ladder
                        </h4>
                        <p style="font-size:11px; color:var(--text-body); margin-bottom:12px;">
                            If you feel unsafe during this journey, use the safety escalation console below.
                        </p>
                        <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-bottom:12px;">
                            <button class="btn-search" style="font-size:11px; background:var(--warning); color:black; height:36px; padding:0;" onclick="triggerSafetyEscalationBtn(${ride.id}, 'STAGE_1_SILENT_PING')">🔕 Stage 1: Silent Ping</button>
                            <button class="btn-search" style="font-size:11px; background:var(--warning); color:black; height:36px; padding:0;" onclick="triggerSafetyEscalationBtn(${ride.id}, 'STAGE_2_IN_APP_CHECKIN')">📱 Stage 2: Check-In Request</button>
                        </div>
                        <button class="btn-search" style="width:100%; font-size:12px; background:var(--danger); box-shadow: 0 4px 15px rgba(239,68,68,0.3); height:40px;" onclick="triggerSafetyEscalationBtn(${ride.id}, 'STAGE_3_TRUSTED_CONTACT_ALERT')">📢 Stage 3: SOS Emergency Warning SMS</button>
                        
                        <div id="active-checkin-area" style="margin-top:16px; padding-top:12px; border-top:1px dashed var(--border); display:none;">
                            <div style="background:rgba(245,158,11,0.1); border:1px solid var(--warning); border-radius:8px; padding:12px; text-align:center;">
                                <div style="font-weight:700; color:var(--warning); font-size:12px; margin-bottom:6px;">⚠️ Are you safe? A check-in alert was triggered!</div>
                                <div style="display:flex; justify-content:center; gap:12px;">
                                    <button class="btn-book" style="background:var(--accent-green);" onclick="submitSafetyCheckinResponse(true)">Yes, I am Safe</button>
                                    <button class="btn-book" style="background:var(--danger);" onclick="submitSafetyCheckinResponse(false)">No, I need help</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    return '';
}

// Modal Toggle Handlers
function openAuthModal(mode = 'signin') {
    activeModal = mode;
    renderApp();
}

function setAuthMode(mode) {
    activeModal = mode;
    renderApp();
}

function closeModal() {
    if (trackingIntervalId) {
        clearInterval(trackingIntervalId);
        trackingIntervalId = null;
    }
    if (trackingMapInstance) {
        try {
            trackingMapInstance.remove();
        } catch (e) {
            console.error("Error removing map instance:", e);
        }
        trackingMapInstance = null;
    }
    bookingMapInstance = null;
    pickupMarker = null;
    dropoffMarker = null;
    activeModal = null;
    renderApp();
}

// Open Booking Modal for a specific trip
async function openBookParcelModal(tripId) {
    const trip = loadedTrips.find(t => t.id === tripId);
    if (!trip) {
        showToast('Error: Selected trip not found.', 'error');
        return;
    }
    selectedTripForBooking = trip;
    activeModal = 'book-parcel';
    renderApp();
    
    // Trigger default quote calculation
    updateFareQuote(15000);

    // Dynamically geocode the trip source and destination to set the initial pins!
    try {
        const srcRes = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(trip.source)}&limit=1&countrycodes=in`, {
            headers: { 'Accept-Language': 'en' }
        });
        const destRes = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(trip.destination)}&limit=1&countrycodes=in`, {
            headers: { 'Accept-Language': 'en' }
        });
        
        if (srcRes.ok && destRes.ok) {
            const srcData = await srcRes.json();
            const destData = await destRes.json();
            
            let updated = false;
            if (srcData.length > 0) {
                const latEl = document.getElementById('book-pickup-lat');
                const lngEl = document.getElementById('book-pickup-lng');
                if (latEl && lngEl) {
                    latEl.value = srcData[0].lat;
                    lngEl.value = srcData[0].lon;
                    updated = true;
                }
            }
            if (destData.length > 0) {
                const latEl = document.getElementById('book-dropoff-lat');
                const lngEl = document.getElementById('book-dropoff-lng');
                if (latEl && lngEl) {
                    latEl.value = destData[0].lat;
                    lngEl.value = destData[0].lon;
                    updated = true;
                }
            }
            
            if (updated) {
                initBookingMap();
                updateFareQuote(15000);
            }
        }
    } catch (err) {
        console.error('Error geocoding initial trip locations', err);
    }
}

// Dynamic Fare Quote update from input
function calculateHaversineDistanceJs(lat1, lon1, lat2, lon2) {
    const R = 6371; // Earth radius in kilometers
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

async function updateFareQuote(declaredVal) {
    const val = parseFloat(declaredVal) || 0.0;
    
    // Calculate distance dynamically from inputs if available
    let distance = 350.0;
    const pickupLatEl = document.getElementById('book-pickup-lat');
    const pickupLngEl = document.getElementById('book-pickup-lng');
    const dropoffLatEl = document.getElementById('book-dropoff-lat');
    const dropoffLngEl = document.getElementById('book-dropoff-lng');
    
    if (pickupLatEl && pickupLngEl && dropoffLatEl && dropoffLngEl) {
        const lat1 = parseFloat(pickupLatEl.value);
        const lon1 = parseFloat(pickupLngEl.value);
        const lat2 = parseFloat(dropoffLatEl.value);
        const lon2 = parseFloat(dropoffLngEl.value);
        if (!isNaN(lat1) && !isNaN(lon1) && !isNaN(lat2) && !isNaN(lon2)) {
            distance = calculateHaversineDistanceJs(lat1, lon1, lat2, lon2);
        }
    }
    
    const displayDist = Math.round(distance * 10) / 10;
    const weightVal = parseFloat(document.getElementById('book-weight')?.value || '4.0');

    try {
        const res = await fetch(`${API_BASE}/parcels/quote?declaredValue=${val}&distanceKm=${distance}&weightKg=${weightVal}`, { headers: getAuthHeaders() });
        if (res.ok) {
            currentParcelQuote = await res.json();
            // Re-render modal details to display updated quote
            const box = document.getElementById('fare-breakdown-box');
            if (box) {
                box.innerHTML = `
                    <div style="font-weight:700; color:var(--porter-teal); margin-bottom:8px; font-size:13px;">💰 Transparent Fare Quote Breakdown</div>
                    <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:4px;">
                        <span>Base Route Fare:</span>
                        <span>₹${currentParcelQuote.baseFareInr}</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:4px;">
                        <span>Distance Charge (Est. ${displayDist} Km):</span>
                        <span>₹${Math.round(currentParcelQuote.distanceFareInr * 100) / 100}</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:4px;">
                        <span>Weight-Based Charge (${weightVal} kg):</span>
                        <span>₹${Math.round(currentParcelQuote.weightFareInr * 100) / 100}</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:6px;">
                        <span>Value-Based Surcharge (${currentParcelQuote.categoryTierLabel}):</span>
                        <span>₹${currentParcelQuote.categorySurchargeInr}</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; font-weight:800; font-size:14px; border-top:1px dashed var(--border); padding-top:6px; color:var(--accent-green);">
                        <span>Total Escrow Amount:</span>
                        <span>₹${Math.round(currentParcelQuote.totalFareInr * 100) / 100} INR</span>
                    </div>
                `;
            }
        }
    } catch (err) {
        console.error('Failed to update quote', err);
    }
}

// ----------------------------------------------------------------------------
// POST-RENDER EVENT BINDING
// ----------------------------------------------------------------------------
function bindPostRenderListeners() {
    // 1. Load active data depending on context
    const senderTripsContainer = document.getElementById('sender-trips-container');
    if (senderTripsContainer) {
        fetchTripsForSender();
    }

    const senderParcelsContainer = document.getElementById('sender-parcels-container');
    if (senderParcelsContainer) {
        fetchParcelsForSender();
    }

    const captainParcelsContainer = document.getElementById('captain-parcels-container');
    if (captainParcelsContainer) {
        fetchParcelsForCaptain();
    }

    const adminKycContainer = document.getElementById('admin-kyc-queue-container');
    if (adminKycContainer) {
        fetchPendingKycForAdmin();
    }

    const riderSearchResults = document.getElementById('rider-search-results');
    if (riderSearchResults) {
        fetchTripsForRider();
    }

    const riderRidesContainer = document.getElementById('rider-rides-container');
    if (riderRidesContainer) {
        fetchRidesForRider();
    }

    const riderContactsContainer = document.getElementById('rider-contacts-container');
    if (riderContactsContainer) {
        fetchTrustedContactsForRider();
    }

    const captainLocalBookingsContainer = document.getElementById('captain-local-bookings-container');
    if (captainLocalBookingsContainer) {
        fetchLocalCaptainStatus();
        fetchCaptainLocalBookings();
    }

    // 1.5 Initialize Maps if containers exist
    if (document.getElementById('booking-map')) {
        setTimeout(initBookingMap, 150);
    }
    if (document.getElementById('telemetry-map')) {
        setTimeout(initTelemetryMap, 150);
    }
    if (document.getElementById('tracking-map')) {
        setTimeout(initTrackingMap, 150);
    }
    if (document.getElementById('seat-booking-map')) {
        setTimeout(window.initSeatBookingMap, 150);
    }
    if (document.getElementById('local-taxi-booking-map')) {
        setTimeout(window.initLocalTaxiBookingMap, 150);
    }
    if (document.getElementById('ride-tracking-map')) {
        setTimeout(window.initRideTrackingMap, 150);
    }

    // 2. Form Event Listeners
    
    // Sign In Form Listener
    const signinForm = document.getElementById('auth-signin-form');
    if (signinForm) {
        signinForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const mobile = document.getElementById('signin-mobile').value;
            const password = document.getElementById('signin-password').value;

            try {
                const res = await fetch(`${API_BASE}/auth/login`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ mobileNumber: mobile, password: password })
                });

                if (!res.ok) {
                    const errData = await res.json();
                    showToast(`Sign In Failed: ${errData.message || errData.error || 'Invalid credentials'}`, 'error');
                    return;
                }
                const data = await res.json();
                currentUser = data;
                localStorage.setItem('currentUser', JSON.stringify(currentUser));
                activeModal = null;
                renderApp();
                showToast(`Authenticated! Logged in as ${currentUser.fullName} (${currentUser.role}).`, 'success');
            } catch (err) {
                console.error(err);
                showToast('Sign In Error', 'error');
            }
        });
    }

    // Registration Form Listener
    const regForm = document.getElementById('auth-register-form');
    if (regForm) {
        regForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const role = document.getElementById('reg-role').value;
            const payload = {
                fullName: document.getElementById('reg-name').value,
                mobileNumber: document.getElementById('reg-mobile').value,
                email: document.getElementById('reg-email').value,
                password: document.getElementById('reg-password').value,
                role: role,
                registrationOtp: document.getElementById('reg-otp').value
            };

            if (role === 'TRAVELER') {
                payload.aadhaarNumber = document.getElementById('reg-aadhaar').value;
                payload.panNumber = document.getElementById('reg-pan').value;
                payload.drivingLicenceNumber = document.getElementById('reg-dl').value;
                payload.rcNumber = document.getElementById('reg-rc').value;
            }

            try {
                const res = await fetch(`${API_BASE}/auth/register`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                if (!res.ok) {
                    const errData = await res.json();
                    showToast(`Registration failed: ${errData.message || errData.error}`, 'error');
                    return;
                }
                const data = await res.json();
                currentUser = data;
                localStorage.setItem('currentUser', JSON.stringify(currentUser));
                activeModal = null;
                renderApp();
                showToast(`Account Created! Logged in as ${currentUser.fullName} (${currentUser.role}).`, 'success');
            } catch (err) {
                console.error(err);
                showToast('Registration Error', 'error');
            }
        });
    }

    // Captain KYC Form Listener
    const kycForm = document.getElementById('captain-kyc-form');
    if (kycForm) {
        kycForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                userId: currentUser.id,
                aadhaarNumber: document.getElementById('kyc-aadhaar').value,
                panNumber: document.getElementById('kyc-pan').value,
                drivingLicenceNumber: document.getElementById('kyc-dl').value,
                rcNumber: document.getElementById('kyc-rc').value
            };

            try {
                const res = await fetch(`${API_BASE}/kyc/submit`, {
                    method: 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify(payload)
                });
                if (!res.ok) {
                    const errData = await res.json();
                    showToast(`KYC submission failed: ${errData.error || errData.message}`, 'error');
                    return;
                }
                currentUser.kycStatus = 'PENDING_APPROVAL';
                localStorage.setItem('currentUser', JSON.stringify(currentUser));
                renderApp();
                showToast('KYC Documents Submitted! Status updated to PENDING_APPROVAL.', 'success');
            } catch (err) {
                console.error(err);
            }
        });
    }

    // Captain Publish Route Listener
    const publishForm = document.getElementById('captain-publish-form');
    if (publishForm) {
        publishForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                travelerId: currentUser.id,
                source: document.getElementById('pub-origin').value,
                destination: document.getElementById('pub-dest').value,
                departureTime: new Date(Date.now() + 86400000).toISOString(), // 24 hrs from now
                availableCapacityKg: parseFloat(document.getElementById('pub-kg').value),
                availableSeats: parseInt(document.getElementById('pub-seats').value)
            };

            try {
                const res = await fetch(`${API_BASE}/trips`, {
                    method: 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify(payload)
                });
                if (res.ok) {
                    showToast('Inter-City Route Published Successfully!', 'success');
                    renderApp();
                } else {
                    const err = await res.json();
                    showToast(`Publish failed: ${err.message}`, 'error');
                }
            } catch (err) {
                showToast('Network error publishing trip', 'error');
            }
        });
        setupSimpleAutocomplete('pub-origin', 'pub-origin-suggestions');
        setupSimpleAutocomplete('pub-dest', 'pub-dest-suggestions');
    }

    // Captain GPS Broadcast Listener
    const gpsForm = document.getElementById('gps-broadcast-form');
    if (gpsForm) {
        gpsForm.addEventListener('submit', (e) => {
            e.preventDefault();
            showToast('GPS Telemetry Ping Broadcasted!', 'info');
        });
    }

    // Parcel Booking Form Submit
    const bookingForm = document.getElementById('parcel-booking-form');
    if (bookingForm) {
        bookingForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                senderId: currentUser.id,
                tripId: selectedTripForBooking ? selectedTripForBooking.id : null,
                goodsDescription: document.getElementById('book-goods').value,
                declaredValue: parseFloat(document.getElementById('book-value').value),
                estimatedWeightKg: parseFloat(document.getElementById('book-weight').value),
                pickupLocation: document.getElementById('book-pickup').value,
                dropoffLocation: document.getElementById('book-dropoff').value,
                pickupLatitude: parseFloat(document.getElementById('book-pickup-lat').value || '12.9716'),
                pickupLongitude: parseFloat(document.getElementById('book-pickup-lng').value || '77.5946'),
                dropoffLatitude: parseFloat(document.getElementById('book-dropoff-lat').value || '13.0827'),
                dropoffLongitude: parseFloat(document.getElementById('book-dropoff-lng').value || '80.2707')
            };

            try {
                const res = await fetch(`${API_BASE}/parcels/request`, {
                    method: 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify(payload)
                });

                if (res.ok) {
                    const parcel = await res.json();
                    showToast(`Parcel Request Created (Status: ${parcel.status})!`, 'success');
                    activeModal = null;
                    selectedTripForBooking = null;
                    currentParcelQuote = null;
                    renderApp();
                } else {
                    const err = await res.json();
                    showToast(`Booking failed: ${err.message || 'Error occurred'}`, 'error');
                }
            } catch (err) {
                console.error(err);
                showToast('Failed to create booking', 'error');
            }
        });
    }

    // Pickup Verification Form Submit
    const pickupForm = document.getElementById('pickup-verification-form');
    if (pickupForm) {
        pickupForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                parcelRequestId: selectedParcelForVerification.id,
                otp: document.getElementById('verify-pickup-otp').value,
                photoUrl: document.getElementById('verify-pickup-photo').value
            };

            try {
                const res = await fetch(`${API_BASE}/parcels/${selectedParcelForVerification.id}/verify-pickup`, {
                    method: 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify(payload)
                });

                if (res.ok) {
                    showToast('Cargo Handover & Pickup Verified Successfully!', 'success');
                    activeModal = null;
                    selectedParcelForVerification = null;
                    renderApp();
                } else {
                    const err = await res.json();
                    showToast(`Verification failed: ${err.message || 'Invalid OTP'}`, 'error');
                }
            } catch (err) {
                showToast('Network error during pickup verification', 'error');
            }
        });
    }

    // Delivery Verification Form Submit
    const deliveryForm = document.getElementById('delivery-verification-form');
    if (deliveryForm) {
        deliveryForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                parcelRequestId: selectedParcelForVerification.id,
                otp: document.getElementById('verify-delivery-otp').value,
                photoUrl: document.getElementById('verify-delivery-photo').value
            };

            try {
                const res = await fetch(`${API_BASE}/parcels/${selectedParcelForVerification.id}/verify-delivery`, {
                    method: 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify(payload)
                });

                if (res.ok) {
                    showToast('Cargo Destination Delivered! Escrow Released to Traveler.', 'success');
                    activeModal = null;
                    selectedParcelForVerification = null;
                    renderApp();
                } else {
                    const err = await res.json();
                    showToast(`Verification failed: ${err.message || 'Invalid OTP'}`, 'error');
                }
            } catch (err) {
                showToast('Network error during delivery verification', 'error');
            }
        });
    }
}

// ----------------------------------------------------------------------------
// DATA FETCHING FUNCTIONS
// ----------------------------------------------------------------------------

async function fetchTripsForSender() {
    const container = document.getElementById('sender-trips-container');
    if (!container) return;

    try {
        await loadUsersCache();
        const res = await fetch(`${API_BASE}/trips`, { headers: getAuthHeaders() });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const trips = await res.json();

        loadedTrips = trips;
        container.innerHTML = '';
        if (!trips || trips.length === 0) {
            container.innerHTML = `<div style="color:var(--text-muted); padding:40px;">No active Captain trips found.</div>`;
            return;
        }

        trips.forEach(trip => {
            if (trip.status !== 'PLANNED') return;
            const card = document.createElement('div');
            card.className = 'route-card';
            
            const driverName = getUserName(trip.travelerId);
            const avatarChar = driverName.charAt(0).toUpperCase();
            
            card.innerHTML = `
                <div class="card-top">
                    <div class="driver-profile">
                        <div class="driver-avatar">${escapeHtml(avatarChar)}</div>
                        <div class="driver-info">
                            <span class="driver-name">${escapeHtml(driverName)}</span>
                            <span class="driver-meta">⭐ 5.0 Rating • Verified Driver</span>
                        </div>
                    </div>
                    <span class="verified-badge">${escapeHtml(trip.status)}</span>
                </div>
                <div class="route-timeline">
                    <div class="timeline-row">
                        <span class="city-label">${escapeHtml(trip.source)}</span>
                        <span class="duration-tag">➔ ~6 Hrs ➔</span>
                        <span class="city-label">${escapeHtml(trip.destination)}</span>
                    </div>
                </div>
                <div class="capacity-row">
                    <div class="capacity-chip">🎒 Trunk Space: <b>${trip.availableCapacityKg} kg</b></div>
                    <div class="capacity-chip">💺 Seats: <b>${trip.availableSeats} Left</b></div>
                </div>
                <div class="card-footer">
                    <div class="price-container">
                        <span class="price-label">Fare Rate</span>
                        <span class="price-amount">₹150 Base</span>
                    </div>
                    <button class="btn-book" onclick="openBookParcelModal(${trip.id})">Book Parcel</button>
                </div>
            `;
            container.appendChild(card);
        });
    } catch (err) {
        console.error(err);
        container.innerHTML = `<div style="color:var(--danger); padding:40px;">Error loading trips</div>`;
    }
}

async function fetchParcelsForSender() {
    const container = document.getElementById('sender-parcels-container');
    if (!container) return;

    try {
        const res = await fetch(`${API_BASE}/parcels/sender/${currentUser.id}`, { headers: getAuthHeaders() });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const parcels = await res.json();

        container.innerHTML = '';
        if (!parcels || parcels.length === 0) {
            container.innerHTML = `<div style="color:var(--text-muted); padding:30px; text-align:center;">You have no active parcel bookings.</div>`;
            return;
        }

        parcels.forEach(p => {
            const card = document.createElement('div');
            card.className = 'route-card';
            
            let actionBtnHtml = '';
            let otpStatusHtml = '';

            if (p.status === 'CREATED') {
                actionBtnHtml = `<span style="font-weight:700; color:var(--warning); font-size:13px;">Awaiting Traveler Acceptance</span>`;
            } else if (p.status === 'ACCEPTED') {
                actionBtnHtml = `<button class="btn-book" style="background:var(--primary-gradient); box-shadow:0 4px 15px var(--primary-glow);" onclick="payEscrowRazorpay(${p.id})">Pay Escrow</button>`;
            } else if (p.status === 'PAID_ESCROW') {
                actionBtnHtml = `<span style="font-weight:700; color:var(--accent-green); font-size:13px;">🔒 Escrow Paid (Held)</span>`;
                otpStatusHtml = `
                    <div class="otp-grid" style="margin-top:16px;">
                        <div class="otp-box">
                            <div class="otp-title">Pickup OTP</div>
                            <div class="otp-code">${p.pickupOtp}</div>
                        </div>
                        <div class="otp-box delivery">
                            <div class="otp-title">Delivery OTP</div>
                            <div class="otp-code">${p.deliveryOtp}</div>
                        </div>
                    </div>
                `;
            } else if (p.status === 'PICKED_UP' || p.status === 'IN_TRANSIT') {
                actionBtnHtml = `
                    <div style="display:flex; flex-direction:column; gap:4px; align-items:flex-end;">
                        <span style="font-weight:700; color:var(--porter-teal); font-size:13px;">🚚 Transit In Progress</span>
                        <button class="btn-book" style="background:var(--porter-gradient);" onclick="openTrackingModal(${p.id}, ${p.tripId})">🗺️ Track</button>
                    </div>
                `;
                otpStatusHtml = `
                    <div class="otp-grid" style="margin-top:16px;">
                        <div class="otp-box delivery" style="grid-column: span 2;">
                            <div class="otp-title">Delivery OTP (Provide to Driver at destination)</div>
                            <div class="otp-code">${p.deliveryOtp}</div>
                        </div>
                    </div>
                `;
            } else if (p.status === 'DELIVERED') {
                actionBtnHtml = `<span style="font-weight:700; color:var(--accent-green); font-size:13px;">✅ Cargo Delivered & Funds Released</span>`;
            } else if (p.status === 'CANCELLED') {
                actionBtnHtml = `<span style="font-weight:700; color:var(--danger); font-size:13px;">❌ Cancelled & Refunded</span>`;
            }

            let chatBtnHtml = '';
            if (p.status !== 'CREATED' && p.status !== 'CANCELLED') {
                chatBtnHtml = `<button class="btn-book" style="background:var(--porter-teal); margin-right:8px;" onclick="openChatModalBtn(${p.id})">💬 Chat</button>`;
            }

            card.innerHTML = `
                <div class="card-top">
                    <div>
                        <span style="font-size:11px; color:var(--text-muted); font-weight:700;">BOOKING ID: #${p.id}</span>
                        <h3 style="font-size:16px; font-weight:800; margin-top:2px;">${escapeHtml(p.goodsDescription)}</h3>
                    </div>
                    <span class="verified-badge" style="text-transform:uppercase;">${escapeHtml(p.status)}</span>
                </div>
                <div style="font-size:12px; color:var(--text-body); margin-bottom:12px;">
                    <div><b>Route:</b> ${escapeHtml(p.pickupLocation)} ➔ ${escapeHtml(p.dropoffLocation)}</div>
                    <div><b>Weight:</b> ${p.estimatedWeightKg} kg | <b>Value:</b> ₹${p.declaredValue}</div>
                </div>
                ${otpStatusHtml}
                <div class="card-footer" style="margin-top:14px; padding-top:12px; display:flex; justify-content:space-between; align-items:center;">
                    <span class="price-amount" style="font-size:18px;">₹${p.calculatedFare}</span>
                    <div style="display:flex; align-items:center; gap:8px;">
                        ${chatBtnHtml}
                        ${actionBtnHtml}
                    </div>
                </div>
            `;
            container.appendChild(card);
        });
    } catch (err) {
        console.error(err);
        container.innerHTML = `<div style="color:var(--danger); padding:40px;">Error loading bookings</div>`;
    }
}

async function fetchParcelsForCaptain() {
    const container = document.getElementById('captain-parcels-container');
    if (!container) return;

    try {
        // Fetch all trips published by this traveler to query their parcels
        const tripsRes = await fetch(`${API_BASE}/trips`, { headers: getAuthHeaders() });
        if (!tripsRes.ok) throw new Error();
        const allTrips = await tripsRes.json();
        const captainTrips = allTrips.filter(t => t.travelerId === currentUser.id);

        container.innerHTML = '';
        if (captainTrips.length === 0) {
            container.innerHTML = `<div style="color:var(--text-muted); padding:30px; text-align:center;">Publish a route to see bookings.</div>`;
            return;
        }

        let bookingsFound = false;

        for (const trip of captainTrips) {
            const res = await fetch(`${API_BASE}/parcels/trip/${trip.id}`, { headers: getAuthHeaders() });
            if (!res.ok) continue;
            const parcels = await res.json();

            if (parcels && parcels.length > 0) {
                bookingsFound = true;
                parcels.forEach(p => {
                    const card = document.createElement('div');
                    card.className = 'route-card';

                    let actionBtnHtml = '';

                    if (p.status === 'CREATED') {
                        actionBtnHtml = `
                            <button class="btn-book" style="background:var(--primary-gradient); box-shadow:0 4px 15px var(--primary-glow); margin-right:8px;" onclick="acceptParcelBooking(${p.id})">Accept</button>
                            <button class="btn-book" style="background:var(--danger); box-shadow:0 4px 15px rgba(239,68,68,0.3);" onclick="rejectParcelBooking(${p.id})">Reject</button>
                        `;
                    } else if (p.status === 'ACCEPTED') {
                        actionBtnHtml = `
                            <span style="font-weight:700; color:var(--warning); font-size:13px; margin-right:8px;">Awaiting Sender Payment</span>
                            <button class="btn-book" style="background:var(--danger); box-shadow:0 4px 15px rgba(239,68,68,0.3);" onclick="rejectParcelBooking(${p.id})">Cancel</button>
                        `;
                    } else if (p.status === 'PAID_ESCROW') {
                        actionBtnHtml = `
                            <button class="btn-book" style="background:var(--primary-gradient); box-shadow:0 4px 15px var(--primary-glow); margin-right:8px;" onclick="openVerifyPickupModal(${p.id})">Verify Pickup</button>
                            <button class="btn-book" style="background:var(--danger); box-shadow:0 4px 15px rgba(239,68,68,0.3);" onclick="rejectParcelBooking(${p.id})">Cancel</button>
                        `;
                    } else if (p.status === 'PICKED_UP' || p.status === 'IN_TRANSIT') {
                        actionBtnHtml = `
                            <button class="btn-book" style="background:var(--accent-green); box-shadow:0 4px 15px var(--accent-glow); margin-right:8px;" onclick="openVerifyDeliveryModal(${p.id})">Verify Delivery</button>
                            <button class="btn-book" style="background:var(--danger); box-shadow:0 4px 15px rgba(239,68,68,0.3); margin-right:8px;" onclick="rejectParcelBooking(${p.id})">Cancel</button>
                            <button class="btn-book" style="background:var(--porter-gradient);" onclick="openTrackingModal(${p.id}, ${p.tripId})">🗺️ Track</button>
                        `;
                    } else if (p.status === 'DELIVERED') {
                        actionBtnHtml = `<span style="font-weight:700; color:var(--accent-green); font-size:13px;">✅ Fulfilling Completed</span>`;
                    } else if (p.status === 'CANCELLED') {
                        actionBtnHtml = `<span style="font-weight:700; color:var(--danger); font-size:13px;">❌ Cancelled</span>`;
                    }

                    let chatBtnHtml = '';
                    if (p.status !== 'CREATED' && p.status !== 'CANCELLED') {
                        chatBtnHtml = `<button class="btn-book" style="background:var(--porter-teal); margin-right:8px;" onclick="openChatModalBtn(${p.id})">💬 Chat</button>`;
                    }

                    card.innerHTML = `
                        <div class="card-top">
                            <div>
                                <span style="font-size:11px; color:var(--text-muted); font-weight:700;">CARGO ID: #${p.id}</span>
                                <h3 style="font-size:16px; font-weight:800; margin-top:2px;">${escapeHtml(p.goodsDescription)}</h3>
                            </div>
                            <span class="verified-badge" style="text-transform:uppercase;">${escapeHtml(p.status)}</span>
                        </div>
                        <div style="font-size:12px; color:var(--text-body); margin-bottom:12px;">
                            <div><b>Route:</b> ${escapeHtml(p.pickupLocation)} ➔ ${escapeHtml(p.dropoffLocation)}</div>
                            <div><b>Weight:</b> ${p.estimatedWeightKg} kg | <b>Earnings:</b> ₹${p.calculatedFare}</div>
                        </div>
                        <div class="card-footer" style="margin-top:14px; padding-top:12px; display:flex; justify-content:space-between; align-items:center;">
                            <span class="price-amount" style="font-size:18px;">₹${p.calculatedFare}</span>
                            <div style="display:flex; align-items:center; gap:8px;">
                                ${chatBtnHtml}
                                ${actionBtnHtml}
                            </div>
                        </div>
                    `;
                    container.appendChild(card);
                });
            }
        }

        if (!bookingsFound) {
            container.innerHTML = `<div style="color:var(--text-muted); padding:30px; text-align:center;">No pending cargos requested for your trips yet.</div>`;
        }
    } catch (err) {
        console.error(err);
        container.innerHTML = `<div style="color:var(--danger); padding:40px;">Error loading cargo list</div>`;
    }
}

async function acceptParcelBooking(parcelId) {
    try {
        const res = await fetch(`${API_BASE}/parcels/${parcelId}/accept?travelerId=${currentUser.id}`, {
            method: 'PUT',
            headers: getAuthHeaders()
        });

        if (res.ok) {
            showToast('Cargo Booking Accepted successfully!', 'success');
            renderApp();
        } else {
            showToast('Failed to accept booking', 'error');
        }
    } catch (err) {
        showToast('Network error accepting booking', 'error');
    }
}

function openVerifyPickupModal(parcelId) {
    selectedParcelForVerification = { id: parcelId };
    activeModal = 'verify-pickup';
    renderApp();
}

function openVerifyDeliveryModal(parcelId) {
    selectedParcelForVerification = { id: parcelId };
    activeModal = 'verify-delivery';
    renderApp();
}

// ----------------------------------------------------------------------------
// RAZORPAY INTEGRATION INTEGRITY FLOW
// ----------------------------------------------------------------------------

async function payEscrowRazorpay(parcelId) {
    showToast('Initializing payment gateway...', 'info');

    try {
        // Step 1: Create real order on backend
        const orderRes = await fetch(`${API_BASE}/parcels/${parcelId}/create-payment-order?senderId=${currentUser.id}`, {
            method: 'POST',
            headers: getAuthHeaders()
        });

        if (!orderRes.ok) {
            const err = await orderRes.json();
            showToast(`Gateway initialization failed: ${err.message}`, 'error');
            return;
        }

        const orderDetails = await orderRes.json();
        console.log('Razorpay Order Details received from backend:', orderDetails);

        // Step 2: Route dynamically based on Key ID (Simulated Sandbox vs Real Checkout)
        if (orderDetails.keyId && orderDetails.keyId.startsWith('rzp_test_mockkey')) {
            // Launch Sandbox Simulation Mode
            pendingSimulatedPayment = {
                parcelId: parcelId,
                orderId: orderDetails.orderId,
                amount: orderDetails.amount,
                goodsDescription: orderDetails.goodsDescription,
                keyId: orderDetails.keyId,
                senderName: orderDetails.senderName,
                senderMobile: orderDetails.senderMobile
            };
            activeModal = 'simulated-razorpay';
            renderApp();
        } else {
            // Launch Real Razorpay checkout popup
            const options = {
                "key": orderDetails.keyId,
                "amount": Math.round(orderDetails.amount * 100), // amount in paise
                "currency": orderDetails.currency,
                "name": "BlaBla + Porter Escrow",
                "description": orderDetails.goodsDescription,
                "order_id": orderDetails.orderId,
                "handler": function (response) {
                    verifyRazorpayPayment(parcelId, response.razorpay_order_id, response.razorpay_payment_id, response.razorpay_signature);
                },
                "prefill": {
                    "name": orderDetails.senderName,
                    "contact": orderDetails.senderMobile
                },
                "theme": {
                    "color": "#06b6d4"
                },
                "modal": {
                    "ondismiss": function() {
                        showToast('Payment checkout cancelled.', 'warning');
                    }
                }
            };

            const rzp = new Razorpay(options);
            rzp.on('payment.failed', function (response){
                showToast(`Payment failed: ${response.error.description}`, 'error');
            });
            rzp.open();
        }
    } catch (err) {
        console.error(err);
        showToast('Payment system communication error', 'error');
    }
}

// Verify payment signature
async function verifyRazorpayPayment(parcelId, orderId, paymentId, signature) {
    showToast('Verifying payment details...', 'info');

    const payload = {
        razorpayOrderId: orderId,
        razorpayPaymentId: paymentId,
        razorpaySignature: signature,
        senderId: currentUser.id
    };

    try {
        const res = await fetch(`${API_BASE}/parcels/${parcelId}/verify-payment`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showToast('Payment Verified Successfully! Escrow is locked.', 'success');
            activeModal = null;
            renderApp();
        } else {
            const err = await res.json();
            showToast(`Escrow validation failed: ${err.message || 'Signature mismatch'}`, 'error');
        }
    } catch (err) {
        showToast('Network error during payment verification', 'error');
    }
}

// Complete Simulated Payment in sandbox modal
function completeSimulatedPayment(isSuccess) {
    if (!isSuccess) {
        showToast('Payment checkout cancelled.', 'warning');
        activeModal = null;
        pendingSimulatedPayment = null;
        renderApp();
        return;
    }

    const order = pendingSimulatedPayment;
    const mockPaymentId = 'pay_mock_' + Date.now();
    const mockSignature = 'sig_mock_' + Math.random().toString(36).substring(7);

    // Call appropriate verify handler synchronously
    if (order.localTaxiBookingId) {
        verifyLocalTaxiRazorpayPayment(order.localTaxiBookingId, order.orderId, mockPaymentId, mockSignature);
    } else if (order.rideId) {
        verifyRideRazorpayPayment(order.rideId, order.orderId, mockPaymentId, mockSignature);
    } else {
        verifyRazorpayPayment(order.parcelId, order.orderId, mockPaymentId, mockSignature);
    }
    pendingSimulatedPayment = null;
}

// ----------------------------------------------------------------------------
// ADMIN PORTAL & KYC APPROVAL
// ----------------------------------------------------------------------------

async function fetchPendingKycForAdmin() {
    const container = document.getElementById('admin-kyc-queue-container');
    if (!container) return;

    try {
        const res = await fetch(`${API_BASE}/kyc/admin/pending`, { headers: getAuthHeaders() });
        if (!res.ok) {
            container.innerHTML = `<div style="color:var(--danger); padding:16px;">Failed to load pending KYC queue.</div>`;
            return;
        }
        const pendingUsers = await res.json();
        container.innerHTML = '';
        if (!pendingUsers || pendingUsers.length === 0) {
            container.innerHTML = `<div style="color:var(--text-muted); padding:16px;">No pending Captain KYC submissions.</div>`;
            return;
        }

        pendingUsers.forEach(u => {
            const div = document.createElement('div');
            div.style.cssText = 'background:var(--bg-surface); padding:16px; border-radius:12px; border:1px solid var(--border); margin-bottom:12px;';
            div.innerHTML = `
                <div style="font-weight:800; margin-bottom:6px;">${escapeHtml(u.fullName)} (Captain #${u.id})</div>
                <div style="font-size:12px; color:var(--text-body); margin-bottom:14px;">
                    Mobile: ${escapeHtml(u.mobileNumber)} | Aadhaar: ${escapeHtml(u.aadhaarNumber)} | RC: ${escapeHtml(u.rcNumber)}
                </div>
                <button class="btn-search" style="background:var(--accent-green); width:100%; padding:10px;" onclick="approveKycAdmin(${u.id})">Approve Captain KYC</button>
            `;
            container.appendChild(div);
        });
    } catch (err) {
        console.error(err);
        container.innerHTML = `<div style="color:var(--danger); padding:16px;">Error loading queue.</div>`;
    }
}

async function approveKycAdmin(userId) {
    try {
        const res = await fetch(`${API_BASE}/kyc/admin/${userId}/approve`, { method: 'POST', headers: getAuthHeaders() });
        if (!res.ok) {
            const errData = await res.json();
            showToast(`Approval failed: ${errData.error || errData.message}`, 'error');
            return;
        }
        const user = await res.json();
        showToast(`KYC APPROVED for Captain ${user.fullName}!`, 'success');
        renderApp();
    } catch (err) {
        console.error(err);
        showToast('Failed to approve KYC', 'error');
    }
}

// STAGE 1 Helper Functions
window.toggleKycFields = function(role) {
    const container = document.getElementById('kyc-fields-container');
    if (container) {
        if (role === 'TRAVELER') {
            container.style.display = 'block';
            document.getElementById('reg-aadhaar').required = true;
            document.getElementById('reg-pan').required = true;
            document.getElementById('reg-dl').required = true;
            document.getElementById('reg-rc').required = true;
        } else {
            container.style.display = 'none';
            document.getElementById('reg-aadhaar').required = false;
            document.getElementById('reg-pan').required = false;
            document.getElementById('reg-dl').required = false;
            document.getElementById('reg-rc').required = false;
        }
    }
};

window.sendRegistrationOtpBtn = async function() {
    const name = document.getElementById('reg-name').value.trim();
    const mobile = document.getElementById('reg-mobile').value.trim();
    const email = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-password').value.trim();
    const role = document.getElementById('reg-role').value;

    if (!name || !mobile || !email || !password) {
        showToast('Please fill out all personal info fields first!', 'error');
        return;
    }
    if (mobile.length !== 10 || isNaN(mobile)) {
        showToast('Mobile number must be exactly 10 digits!', 'error');
        return;
    }

    if (role === 'TRAVELER') {
        const aadhaar = document.getElementById('reg-aadhaar').value.trim();
        const pan = document.getElementById('reg-pan').value.trim();
        const dl = document.getElementById('reg-dl').value.trim();
        const rc = document.getElementById('reg-rc').value.trim();
        if (!aadhaar || !pan || !dl || !rc) {
            showToast('All KYC documents are required for Captain registration!', 'error');
            return;
        }
    }

    try {
        const res = await fetch(`${API_BASE}/auth/send-registration-otp`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ mobileNumber: mobile })
        });
        if (res.ok) {
            showToast('Verification OTP sent successfully via SMS!', 'success');
            document.getElementById('otp-verification-container').style.display = 'block';
            document.getElementById('btn-submit-register').style.display = 'block';
            document.getElementById('btn-send-otp').style.display = 'none';
        } else {
            const err = await res.json();
            showToast(`Failed to send OTP: ${err.message || 'Error'}`, 'error');
        }
    } catch (err) {
        showToast('Network error while sending OTP', 'error');
    }
};

window.openGeneralBookingModal = function() {
    selectedTripForBooking = null;
    activeModal = 'book-parcel';
    renderApp();
    updateFareQuote(15000);
};

window.openChatModalBtn = async function(id) {
    try {
        const res = await fetch(`${API_BASE}/parcels/${id}`, { headers: getAuthHeaders() });
        if (res.ok) {
            selectedParcelForChat = await res.json();
            activeModal = 'chat';
            renderApp();
        }
    } catch (e) {
        showToast('Error opening chat modal', 'error');
    }
};

window.loadChatMessages = async function(parcelRequestId) {
    const box = document.getElementById('chat-messages-box');
    if (!box) return;
    try {
        const res = await fetch(`${API_BASE}/chat/${parcelRequestId}?userId=${currentUser.id}`, {
            headers: getAuthHeaders()
        });
        if (!res.ok) {
            box.innerHTML = `<div style="color:var(--danger); text-align:center; padding-top:100px;">Failed to load chat.</div>`;
            return;
        }
        const messages = await res.json();
        box.innerHTML = '';
        if (!messages || messages.length === 0) {
            box.innerHTML = `<div style="color:var(--text-muted); text-align:center; padding-top:100px; font-size:13px;">No messages yet. Send a message to start coordinates exchange!</div>`;
            return;
        }
        messages.forEach(msg => {
            const isMe = msg.senderUserId === currentUser.id;
            const msgDiv = document.createElement('div');
            msgDiv.style.cssText = `margin-bottom:12px; display:flex; flex-direction:column; align-items:${isMe ? 'flex-end' : 'flex-start'};`;
            msgDiv.innerHTML = `
                <div style="font-size:10px; color:var(--text-muted); margin-bottom:2px;">
                    ${isMe ? 'You' : 'Participant'}
                </div>
                <div style="background:${isMe ? 'var(--porter-teal)' : 'var(--border)'}; color:${isMe ? 'white' : 'var(--text-title)'}; padding:8px 14px; border-radius:12px; max-width:80%; word-break:break-word; font-size:13px;">
                    ${escapeHtml(msg.message)}
                </div>
            `;
            box.appendChild(msgDiv);
        });
        box.scrollTop = box.scrollHeight;
    } catch (err) {
        console.error(err);
        box.innerHTML = `<div style="color:var(--danger); text-align:center; padding-top:100px;">Error loading messages.</div>`;
    }
};

window.sendChatMessageBtn = async function(e) {
    e.preventDefault();
    const input = document.getElementById('chat-input-text');
    if (!input || !input.value.trim() || !selectedParcelForChat) return;
    const content = input.value.trim();
    input.value = '';
    
    const payload = {
        senderUserId: currentUser.id,
        message: content
    };

    try {
        const res = await fetch(`${API_BASE}/chat/${selectedParcelForChat.id}/send`, {
            method: 'POST',
            headers: {
                ...getAuthHeaders(),
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            loadChatMessages(selectedParcelForChat.id);
        } else {
            showToast('Failed to send message', 'error');
        }
    } catch (err) {
        showToast('Network error while sending message', 'error');
    }
};

window.rejectParcelBooking = async function(id) {
    if (!confirm("Are you sure you want to reject/cancel this booking? It will be routed to the next traveler or refunded.")) return;
    try {
        const res = await fetch(`${API_BASE}/parcels/${id}/cancel?userId=${currentUser.id}`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        if (res.ok) {
            showToast('Booking rejected/cancelled successfully!', 'success');
            renderApp();
        } else {
            const err = await res.json();
            showToast(`Failed: ${err.message || 'Error'}`, 'error');
        }
    } catch (err) {
        showToast('Network error while cancelling booking', 'error');
    }
};

window.openTrackingModal = function(parcelId, tripId) {
    activeModal = 'tracking';
    selectedParcelForTracking = { id: parcelId, tripId: tripId };
    renderApp();
};

window.initBookingMap = function() {
    const mapDiv = document.getElementById('booking-map');
    if (!mapDiv) return;

    const lat1 = parseFloat(document.getElementById('book-pickup-lat').value || '12.9716');
    const lng1 = parseFloat(document.getElementById('book-pickup-lng').value || '77.5946');
    const lat2 = parseFloat(document.getElementById('book-dropoff-lat').value || '13.0827');
    const lng2 = parseFloat(document.getElementById('book-dropoff-lng').value || '80.2707');

    try {
        bookingMapInstance = L.map('booking-map').setView([lat1, lng1], 6);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; OpenStreetMap contributors'
        }).addTo(bookingMapInstance);

        pickupMarker = L.marker([lat1, lng1], { draggable: true }).addTo(bookingMapInstance);
        pickupMarker.bindPopup("<b>Pickup Location (Drag to fine-tune)</b>").openPopup();

        dropoffMarker = L.marker([lat2, lng2], { draggable: true }).addTo(bookingMapInstance);
        dropoffMarker.bindPopup("<b>Dropoff Location (Drag to fine-tune)</b>");

        const polyline = L.polyline([[lat1, lng1], [lat2, lng2]], { color: 'blue', dashArray: '5, 5' }).addTo(bookingMapInstance);

        const group = new L.featureGroup([pickupMarker, dropoffMarker]);
        bookingMapInstance.fitBounds(group.getBounds().pad(0.1));

        pickupMarker.on('dragend', function(e) {
            const position = pickupMarker.getLatLng();
            document.getElementById('book-pickup-lat').value = position.lat;
            document.getElementById('book-pickup-lng').value = position.lng;
            polyline.setLatLngs([position, dropoffMarker.getLatLng()]);
            reverseGeocode(position.lat, position.lng, 'book-pickup');
            updateFareQuote(document.getElementById('book-value').value);
        });

        dropoffMarker.on('dragend', function(e) {
            const position = dropoffMarker.getLatLng();
            document.getElementById('book-dropoff-lat').value = position.lat;
            document.getElementById('book-dropoff-lng').value = position.lng;
            polyline.setLatLngs([pickupMarker.getLatLng(), position]);
            reverseGeocode(position.lat, position.lng, 'book-dropoff');
            updateFareQuote(document.getElementById('book-value').value);
        });

        setupAutocomplete('book-pickup', 'pickup-suggestions', 'book-pickup-lat', 'book-pickup-lng', pickupMarker, polyline, 0);
        setupAutocomplete('book-dropoff', 'dropoff-suggestions', 'book-dropoff-lat', 'book-dropoff-lng', dropoffMarker, polyline, 1);

        setupBlurGeocoding('book-pickup', 'book-pickup-lat', 'book-pickup-lng', pickupMarker, polyline, 0);
        setupBlurGeocoding('book-dropoff', 'book-dropoff-lat', 'book-dropoff-lng', dropoffMarker, polyline, 1);
    } catch (err) {
        console.error('Failed to init booking map', err);
    }
};

function setupBlurGeocoding(inputId, latId, lngId, marker, polyline, type) {
    const input = document.getElementById(inputId);
    if (!input) return;

    input.addEventListener('blur', async function() {
        // Wait a small moment to let the click on suggestions execute first if selected
        setTimeout(async () => {
            const query = input.value.trim();
            if (query.length < 3) return;

            try {
                const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=1&countrycodes=in`, {
                    headers: { 'Accept-Language': 'en' }
                });
                if (res.ok) {
                    const data = await res.json();
                    if (data.length > 0) {
                        const lat = parseFloat(data[0].lat);
                        const lon = parseFloat(data[0].lon);
                        
                        document.getElementById(latId).value = lat;
                        document.getElementById(lngId).value = lon;
                        
                        marker.setLatLng([lat, lon]);
                        if (type === 0) {
                            polyline.setLatLngs([[lat, lon], dropoffMarker.getLatLng()]);
                        } else {
                            polyline.setLatLngs([pickupMarker.getLatLng(), [lat, lon]]);
                        }
                        
                        const group = new L.featureGroup([pickupMarker, dropoffMarker]);
                        bookingMapInstance.fitBounds(group.getBounds().pad(0.1));
                        
                        updateFareQuote(document.getElementById('book-value').value);
                    }
                }
            } catch (err) {
                console.error(err);
            }
        }, 200);
    });
}

function setupAutocomplete(inputId, suggestionsId, latId, lngId, marker, polyline, type) {
    const input = document.getElementById(inputId);
    const suggestionsBox = document.getElementById(suggestionsId);
    if (!input || !suggestionsBox) return;

    let debounceTimeout = null;

    input.addEventListener('input', function() {
        if (debounceTimeout) clearTimeout(debounceTimeout);
        const query = input.value.trim();
        if (query.length < 3) {
            suggestionsBox.style.display = 'none';
            return;
        }

        debounceTimeout = setTimeout(async () => {
            try {
                const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=5&countrycodes=in`, {
                    headers: { 'Accept-Language': 'en' }
                });
                if (!res.ok) return;
                const data = await res.json();
                
                suggestionsBox.innerHTML = '';
                if (data.length === 0) {
                    suggestionsBox.style.display = 'none';
                    return;
                }

                data.forEach(item => {
                    const div = document.createElement('div');
                    div.style.cssText = 'padding:10px 14px; cursor:pointer; font-size:12px; border-bottom:1px solid var(--border); color:var(--text-title); background:var(--bg-surface);';
                    div.textContent = item.display_name;
                    div.addEventListener('click', function() {
                        input.value = item.display_name;
                        suggestionsBox.style.display = 'none';
                        const lat = parseFloat(item.lat);
                        const lon = parseFloat(item.lon);
                        document.getElementById(latId).value = lat;
                        document.getElementById(lngId).value = lon;
                        
                        marker.setLatLng([lat, lon]);
                        if (type === 0) {
                            polyline.setLatLngs([[lat, lon], dropoffMarker.getLatLng()]);
                        } else {
                            polyline.setLatLngs([pickupMarker.getLatLng(), [lat, lon]]);
                        }
                        
                        const group = new L.featureGroup([pickupMarker, dropoffMarker]);
                        bookingMapInstance.fitBounds(group.getBounds().pad(0.1));
                        
                        updateFareQuote(document.getElementById('book-value').value);
                    });
                    suggestionsBox.appendChild(div);
                });
                suggestionsBox.style.display = 'block';
            } catch (err) {
                console.error(err);
            }
        }, 500);
    });

    document.addEventListener('click', function(e) {
        if (e.target !== input && e.target !== suggestionsBox) {
            suggestionsBox.style.display = 'none';
        }
    });
}

async function reverseGeocode(lat, lng, inputId) {
    try {
        const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`, {
            headers: { 'Accept-Language': 'en' }
        });
        if (res.ok) {
            const data = await res.json();
            const input = document.getElementById(inputId);
            if (input && data.display_name) {
                input.value = data.display_name;
            }
        }
    } catch (err) {
        console.error(err);
    }
}

window.initTelemetryMap = function() {
    const mapDiv = document.getElementById('telemetry-map');
    if (!mapDiv) return;

    const latInput = document.getElementById('gps-lat');
    const lngInput = document.getElementById('gps-lng');

    const initLat = parseFloat(latInput.value || '12.9716');
    const initLng = parseFloat(lngInput.value || '77.5946');

    try {
        telemetryMapInstance = L.map('telemetry-map').setView([initLat, initLng], 12);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; OpenStreetMap contributors'
        }).addTo(telemetryMapInstance);

        telemetryMarker = L.marker([initLat, initLng], { draggable: true }).addTo(telemetryMapInstance);
        telemetryMarker.bindPopup("<b>Broadcast Coordinates</b>").openPopup();

        telemetryMapInstance.on('click', function(e) {
            const lat = e.latlng.lat;
            const lng = e.latlng.lng;
            telemetryMarker.setLatLng([lat, lng]);
            latInput.value = lat.toFixed(6);
            lngInput.value = lng.toFixed(6);
        });

        telemetryMarker.on('dragend', function(e) {
            const position = telemetryMarker.getLatLng();
            latInput.value = position.lat.toFixed(6);
            lngInput.value = position.lng.toFixed(6);
        });

        const devGpsBtn = document.getElementById('btn-device-gps');
        if (devGpsBtn) {
            devGpsBtn.addEventListener('click', function() {
                if (navigator.geolocation) {
                    navigator.geolocation.getCurrentPosition(function(pos) {
                        const lat = pos.coords.latitude;
                        const lng = pos.coords.longitude;
                        telemetryMarker.setLatLng([lat, lng]);
                        telemetryMapInstance.setView([lat, lng], 14);
                        latInput.value = lat.toFixed(6);
                        lngInput.value = lng.toFixed(6);
                        showToast('Fetched device GPS successfully!', 'success');
                    }, function(err) {
                        showToast('Device GPS permission denied or unavailable.', 'error');
                    });
                } else {
                    showToast('Geolocation is not supported by your browser.', 'error');
                }
            });
        }

        const gpsForm = document.getElementById('gps-broadcast-form');
        if (gpsForm) {
            const newGpsForm = gpsForm.cloneNode(true);
            gpsForm.parentNode.replaceChild(newGpsForm, gpsForm);

            newGpsForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                
                try {
                    const tripsRes = await fetch(`${API_BASE}/trips`, { headers: getAuthHeaders() });
                    if (!tripsRes.ok) throw new Error();
                    const allTrips = await tripsRes.json();
                    const myTrips = allTrips.filter(t => t.travelerId === currentUser.id);

                    if (myTrips.length === 0) {
                        showToast('Cannot broadcast GPS: No published routes found!', 'error');
                        return;
                    }

                    const activeTrip = myTrips[myTrips.length - 1];

                    const payload = {
                        tripId: activeTrip.id,
                        travelerId: currentUser.id,
                        latitude: parseFloat(latInput.value),
                        longitude: parseFloat(lngInput.value),
                        speedKmh: parseFloat(document.getElementById('gps-speed').value || '85.0'),
                        headingDegrees: 0.0,
                        batteryLevel: 100
                    };

                    const pingRes = await fetch(`${API_BASE}/tracking/ping`, {
                        method: 'POST',
                        headers: {
                            ...getAuthHeaders(),
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify(payload)
                    });

                    if (pingRes.ok) {
                        showToast(`GPS Telemetry Ping Broadcasted for Trip #${activeTrip.id}!`, 'success');
                    } else {
                        const err = await pingRes.json();
                        showToast(`Broadcast failed: ${err.message || 'Error'}`, 'error');
                    }
                } catch (err) {
                    console.error(err);
                    showToast('Error broadcasting GPS telemetry', 'error');
                }
            });
        }
    } catch (err) {
        console.error('Failed to init telemetry map', err);
    }
};

window.initTrackingMap = function() {
    const mapDiv = document.getElementById('tracking-map');
    if (!mapDiv) return;

    const parcelId = selectedParcelForTracking.id;
    const tripId = selectedParcelForTracking.tripId;

    let pMarker = null;
    let dMarker = null;
    let cMarker = null;
    let routeLine = null;

    const pollTrackingData = async () => {
        try {
            const parcelRes = await fetch(`${API_BASE}/parcels/${parcelId}`, { headers: getAuthHeaders() });
            if (!parcelRes.ok) return;
            const parcel = await parcelRes.json();

            const pLat = parcel.pickupLatitude || 12.9716;
            const pLng = parcel.pickupLongitude || 77.5946;
            const dLat = parcel.dropoffLatitude || 13.0827;
            const dLng = parcel.dropoffLongitude || 80.2707;

            const liveRes = await fetch(`${API_BASE}/tracking/live/${tripId}`, { headers: getAuthHeaders() });
            if (!liveRes.ok) return;
            const tracking = await liveRes.json();

            const cLat = tracking.currentLatitude;
            const cLng = tracking.currentLongitude;

            const statusEl = document.getElementById('tracking-status');
            const distEl = document.getElementById('tracking-distance');
            const etaEl = document.getElementById('tracking-eta');

            if (statusEl) statusEl.textContent = tracking.tripStatus || parcel.status;
            if (distEl) distEl.textContent = `${tracking.distanceRemainingKm} km`;
            if (etaEl) etaEl.textContent = `${tracking.estimatedMinutesRemaining} mins`;

            lastTrackingPingTimestamp = Date.now();

            if (!trackingMapInstance) {
                trackingMapInstance = L.map('tracking-map').setView([cLat, cLng], 8);
                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    attribution: '&copy; OpenStreetMap contributors'
                }).addTo(trackingMapInstance);

                pMarker = L.marker([pLat, pLng]).addTo(trackingMapInstance);
                pMarker.bindPopup(`<b>Pickup:</b> ${parcel.pickupLocation}`);

                dMarker = L.marker([dLat, dLng]).addTo(trackingMapInstance);
                dMarker.bindPopup(`<b>Dropoff:</b> ${parcel.dropoffLocation}`);

                cMarker = L.marker([cLat, cLng], { icon: createCaptainLiveIcon() }).addTo(trackingMapInstance);
                cMarker.bindPopup("<b>Captain Current Position</b>").openPopup();

                routeLine = L.polyline([[pLat, pLng], [cLat, cLng], [dLat, dLng]], { color: 'teal', weight: 4, dashArray: '8 4' }).addTo(trackingMapInstance);

                const group = new L.featureGroup([pMarker, dMarker, cMarker]);
                trackingMapInstance.fitBounds(group.getBounds().pad(0.1));
                setTimeout(() => {
                    if (trackingMapInstance) trackingMapInstance.invalidateSize();
                }, 250);
            } else {
                if (cMarker) smoothMoveMarker(cMarker, [cLat, cLng], 2000);
                setTimeout(() => {
                    if (routeLine) routeLine.setLatLngs([[pLat, pLng], [cLat, cLng], [dLat, dLng]]);
                }, 2100);
            }

            updateSignalLostBanner('parcel-signal-lost-area', lastTrackingPingTimestamp);
        } catch (err) {
            console.error(err);
            updateSignalLostBanner('parcel-signal-lost-area', lastTrackingPingTimestamp);
        }
    };

    pollTrackingData();
    trackingIntervalId = setInterval(pollTrackingData, 5000);
};

// Rider Portal Functions
async function fetchTripsForRider() {
    const resultsBox = document.getElementById('rider-search-results');
    if (!resultsBox) return;

    try {
        await loadUsersCache();
        const res = await fetch(`${API_BASE}/trips`, { headers: getAuthHeaders() });
        if (!res.ok) throw new Error();
        const trips = await res.json();
        loadedTrips = trips;

        renderFilteredRiderTrips();
    } catch (e) {
        console.error(e);
        resultsBox.innerHTML = `<div style="color:var(--danger); padding:20px;">Failed to load available carpool seats.</div>`;
    }
}

function renderFilteredRiderTrips() {
    const resultsBox = document.getElementById('rider-search-results');
    if (!resultsBox) return;

    const sourceInput = document.getElementById('rider-search-source');
    const destInput = document.getElementById('rider-search-destination');
    const sourceQuery = sourceInput ? sourceInput.value.trim().toLowerCase() : '';
    const destQuery = destInput ? destInput.value.trim().toLowerCase() : '';

    const filtered = loadedTrips.filter(t => {
        if (!t.availableSeats || t.availableSeats <= 0) return false;
        if (t.status !== 'PLANNED' && t.status !== 'ACTIVE') return false;

        const matchesSrc = !sourceQuery || t.source.toLowerCase().includes(sourceQuery);
        const matchesDest = !destQuery || t.destination.toLowerCase().includes(destQuery);
        return matchesSrc && matchesDest;
    });

    resultsBox.innerHTML = '';
    if (filtered.length === 0) {
        resultsBox.innerHTML = `<div style="color:var(--text-muted); padding:20px; grid-column: span 3; text-align:center;">No matching seats found. Try different locations.</div>`;
        return;
    }

    filtered.forEach(t => {
        const driverName = getUserName(t.travelerId);
        const avatarChar = driverName.charAt(0).toUpperCase();

        let distance = 300.0;
        const src = t.source.toLowerCase();
        const dest = t.destination.toLowerCase();
        if ((src.includes('bengaluru') && dest.includes('chennai')) || (src.includes('chennai') && dest.includes('bengaluru'))) {
            distance = 290.0;
        } else if ((src.includes('bengaluru') && dest.includes('hyderabad')) || (src.includes('hyderabad') && dest.includes('bengaluru'))) {
            distance = 562.0;
        }
        
        let displayFare = 50;
        if (distance <= 100) {
            displayFare += distance * 1.50;
        } else {
            displayFare += (100 * 1.50) + (distance - 100) * 1.00;
        }

        const card = document.createElement('div');
        card.className = 'route-card';
        card.innerHTML = `
            <div class="card-top">
                <div class="driver-profile">
                    <div class="driver-avatar">${escapeHtml(avatarChar)}</div>
                    <div class="driver-info">
                        <span class="driver-name">${escapeHtml(driverName)}</span>
                        <span class="driver-meta">⭐ 4.9 Rating • Verified Captain</span>
                    </div>
                </div>
                <span class="verified-badge">${escapeHtml(t.status)}</span>
            </div>
            <div class="route-timeline" style="margin: 16px 0;">
                <div class="timeline-row">
                    <span class="city-label">${escapeHtml(t.source)}</span>
                    <span class="duration-tag">➔ ~${Math.round(distance/60) || 5} Hrs ➔</span>
                    <span class="city-label">${escapeHtml(t.destination)}</span>
                </div>
            </div>
            <div class="capacity-row" style="margin-bottom:16px;">
                <div class="capacity-chip">💺 Seats Available: <b>${t.availableSeats}</b></div>
            </div>
            <div class="card-footer" style="display:flex; justify-content:space-between; align-items:center;">
                <div class="price-container">
                    <span class="price-label">Estimated Fare</span>
                    <span class="price-amount">₹${Math.round(displayFare)}</span>
                </div>
                <button class="btn-book" onclick="openBookSeatModal(${t.id})">Book Seat</button>
            </div>
        `;
        resultsBox.appendChild(card);
    });
}

function triggerRiderSearch() {
    renderFilteredRiderTrips();
}

async function fetchRidesForRider() {
    const container = document.getElementById('rider-rides-container');
    if (!container) return;

    try {
        const res = await fetch(`${API_BASE}/rides/rider/${currentUser.id}`, { headers: getAuthHeaders() });
        if (!res.ok) throw new Error();
        const rides = await res.json();

        // Also fetch Same-City Local Taxi bookings
        let localTaxis = [];
        try {
            const taxiRes = await fetch(`${API_BASE}/taxi/rider/${currentUser.id}`, { headers: getAuthHeaders() });
            if (taxiRes.ok) {
                localTaxis = await taxiRes.json();
            }
        } catch (e) {
            console.error("Failed to load local taxi bookings: ", e);
        }

        container.innerHTML = '';
        if (rides.length === 0 && localTaxis.length === 0) {
            container.innerHTML = `<div style="color:var(--text-muted); padding:20px; grid-column: span 3; text-align:center;">You have no active seat bookings or taxi rides.</div>`;
            return;
        }

        // Render Inter-City Rides
        rides.forEach(ride => {
            const card = document.createElement('div');
            card.className = 'route-card';

            let actionHtml = '';
            if (ride.status === 'REQUESTED') {
                actionHtml = `
                    <button class="btn-book" style="background:var(--primary-gradient); box-shadow:0 4px 15px var(--primary-glow); margin-right:8px;" onclick="payEscrowForRide(${ride.id})">💳 Pay Escrow</button>
                    <button class="btn-book" style="background:var(--danger); box-shadow:none;" onclick="cancelRideBooking(${ride.id})">❌ Cancel</button>
                `;
            } else if (ride.status === 'ACCEPTED') {
                actionHtml = `
                    <button class="btn-book" style="background:var(--porter-teal); margin-right:8px;" onclick="openRideTrackingModal(${ride.id})">🗺️ Track Captain</button>
                    <button class="btn-book" style="background:var(--danger); box-shadow:none;" onclick="cancelRideBooking(${ride.id})">❌ Cancel</button>
                `;
            } else if (ride.status === 'IN_PROGRESS') {
                actionHtml = `
                    <button class="btn-book" style="background:var(--porter-gradient); margin-right:8px;" onclick="openRideTrackingModal(${ride.id})">🛡️ Emergency Console</button>
                `;
            } else if (ride.status === 'COMPLETED') {
                actionHtml = `<span style="font-weight:700; color:var(--accent-green); font-size:13px;">✅ Completed</span>`;
            } else if (ride.status === 'CANCELLED') {
                actionHtml = `<span style="font-weight:700; color:var(--danger); font-size:13px;">❌ Cancelled & Refunded</span>`;
            }

            card.innerHTML = `
                <div class="card-top">
                    <div>
                        <span style="font-size:11px; color:var(--text-muted); font-weight:700;">BOOKING ID: #${ride.id}</span>
                        <h3 style="font-size:16px; font-weight:800; margin-top:2px;">Co-Ride Booking</h3>
                    </div>
                    <span class="verified-badge">🚗 Inter-City</span>
                </div>
                <div style="font-size:12px; color:var(--text-body); margin:12px 0;">
                    <div><b>Route:</b> ${escapeHtml(ride.pickupLocation)} ➔ ${escapeHtml(ride.dropoffLocation)}</div>
                    <div><b>Safety Mode:</b> ${ride.safetyModeEnabled ? '🚨 Enabled' : '❌ Disabled'}</div>
                </div>
                <div class="card-footer" style="display:flex; justify-content:space-between; align-items:center; padding-top:12px; border-top:1px solid var(--border);">
                    <span class="price-amount">₹${Math.round(ride.calculatedFare)}</span>
                    <div style="display:flex; align-items:center;">
                        ${actionHtml}
                    </div>
                </div>
            `;
            container.appendChild(card);
        });

        // Render Same-City Local Taxis
        localTaxis.forEach(taxi => {
            const card = document.createElement('div');
            card.className = 'route-card';

            let actionHtml = '';
            if (taxi.status === 'REQUESTED' || taxi.status === 'MATCHED') {
                actionHtml = `
                    <button class="btn-book" style="background:var(--primary-gradient); box-shadow:0 4px 15px var(--primary-glow); margin-right:8px;" onclick="payEscrowForLocalTaxi(${taxi.id})">💳 Pay Escrow</button>
                    <button class="btn-book" style="background:var(--danger); box-shadow:none;" onclick="cancelLocalTaxiBooking(${taxi.id})">❌ Cancel</button>
                `;
            } else if (taxi.status === 'PAID') {
                actionHtml = `
                    <button class="btn-book" style="background:var(--porter-teal); margin-right:8px;" onclick="openLocalTaxiTrackingModal(${taxi.id})">🗺️ Track Captain</button>
                    <button class="btn-book" style="background:var(--danger); box-shadow:none;" onclick="cancelLocalTaxiBooking(${taxi.id})">❌ Cancel</button>
                `;
            } else if (taxi.status === 'IN_PROGRESS') {
                actionHtml = `
                    <button class="btn-book" style="background:var(--porter-gradient); margin-right:8px;" onclick="openLocalTaxiTrackingModal(${taxi.id})">🛡️ Emergency Console</button>
                `;
            } else if (taxi.status === 'COMPLETED') {
                actionHtml = `<span style="font-weight:700; color:var(--accent-green); font-size:13px;">✅ Completed</span>`;
            } else if (taxi.status === 'CANCELLED') {
                actionHtml = `<span style="font-weight:700; color:var(--danger); font-size:13px;">❌ Cancelled & Refunded</span>`;
            }

            card.innerHTML = `
                <div class="card-top">
                    <div>
                        <span style="font-size:11px; color:var(--text-muted); font-weight:700;">TAXI ID: #${taxi.id}</span>
                        <h3 style="font-size:16px; font-weight:800; margin-top:2px;">Same-City Local Taxi</h3>
                    </div>
                    <span class="verified-badge" style="background:rgba(6,182,212,0.15); color:var(--porter-teal);">🚖 Same-City</span>
                </div>
                <div style="font-size:12px; color:var(--text-body); margin:12px 0;">
                    <div><b>Route:</b> ${escapeHtml(taxi.pickupLocation)} ➔ ${escapeHtml(taxi.dropoffLocation)}</div>
                    <div><b>Safety Mode:</b> ${taxi.safetyModeEnabled ? '🚨 Enabled' : '❌ Disabled'}</div>
                </div>
                <div class="card-footer" style="display:flex; justify-content:space-between; align-items:center; padding-top:12px; border-top:1px solid var(--border);">
                    <span class="price-amount">₹${Math.round(taxi.calculatedFare)}</span>
                    <div style="display:flex; align-items:center;">
                        ${actionHtml}
                    </div>
                </div>
            `;
            container.appendChild(card);
        });

    } catch (e) {
        console.error(e);
        container.innerHTML = `<div style="color:var(--danger); padding:20px;">Failed to load bookings.</div>`;
    }
}

async function fetchTrustedContactsForRider() {
    const container = document.getElementById('rider-contacts-container');
    if (!container) return;

    try {
        const res = await fetch(`${API_BASE}/auth/trusted-contacts/${currentUser.id}`, { headers: getAuthHeaders() });
        if (!res.ok) throw new Error();
        const contacts = await res.json();

        container.innerHTML = '';
        if (contacts.length === 0) {
            container.innerHTML = `<div style="color:var(--text-muted); padding:10px; font-size:13px;">No emergency contacts registered yet.</div>`;
            return;
        }

        const table = document.createElement('table');
        table.style.width = '100%';
        table.style.fontSize = '13px';
        table.style.borderCollapse = 'collapse';
        table.innerHTML = `
            <thead>
                <tr style="border-bottom:1px solid var(--border); text-align:left; color:var(--text-muted);">
                    <th style="padding:8px 0;">Name</th>
                    <th style="padding:8px 0;">Phone Number</th>
                    <th style="padding:8px 0;">Relationship</th>
                </tr>
            </thead>
            <tbody>
                ${contacts.map(c => `
                    <tr style="border-bottom:1px dashed var(--border);">
                        <td style="padding:8px 0; color:var(--text-white); font-weight:700;">${escapeHtml(c.contactName)}</td>
                        <td style="padding:8px 0; color:var(--text-body); font-family:monospace;">${escapeHtml(c.contactPhoneNumber)}</td>
                        <td style="padding:8px 0; color:var(--porter-teal); font-weight:700;">${escapeHtml(c.relationship)}</td>
                    </tr>
                `).join('')}
            </tbody>
        `;
        container.appendChild(table);
    } catch (e) {
        console.error(e);
        container.innerHTML = `<div style="color:var(--danger); padding:10px;">Failed to load contacts.</div>`;
    }
}

async function addTrustedContactBtn() {
    const nameInput = document.getElementById('rider-contact-name');
    const phoneInput = document.getElementById('rider-contact-phone');
    const relInput = document.getElementById('rider-contact-relationship');

    const name = nameInput.value.trim();
    const phone = phoneInput.value.trim();
    const rel = relInput.value.trim();

    if (!name || !phone || !rel) {
        showToast('Please fill all trusted contact fields.', 'error');
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/auth/trusted-contacts?userId=${currentUser.id}&contactName=${encodeURIComponent(name)}&contactPhoneNumber=${encodeURIComponent(phone)}&relationship=${encodeURIComponent(rel)}`, {
            method: 'POST',
            headers: getAuthHeaders()
        });

        if (res.ok) {
            showToast('Trusted Contact registered successfully!', 'success');
            nameInput.value = '';
            phoneInput.value = '';
            relInput.value = '';
            fetchTrustedContactsForRider();
        } else {
            showToast('Failed to register trusted contact.', 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('Connection error adding contact.', 'error');
    }
}

async function openBookSeatModal(tripId) {
    const trip = loadedTrips.find(t => t.id === tripId);
    if (!trip) {
        showToast('Error: Selected trip not found.', 'error');
        return;
    }
    selectedTripForSeatBooking = trip;
    activeModal = 'book-seat';
    renderApp();

    updateSeatFareQuote();

    try {
        const srcRes = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(trip.source)}&limit=1&countrycodes=in`, {
            headers: { 'Accept-Language': 'en' }
        });
        const destRes = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(trip.destination)}&limit=1&countrycodes=in`, {
            headers: { 'Accept-Language': 'en' }
        });

        if (srcRes.ok && destRes.ok) {
            const srcData = await srcRes.json();
            const destData = await destRes.json();

            let updated = false;
            if (srcData.length > 0) {
                const latEl = document.getElementById('seat-pickup-lat');
                const lngEl = document.getElementById('seat-pickup-lng');
                if (latEl && lngEl) {
                    latEl.value = srcData[0].lat;
                    lngEl.value = srcData[0].lon;
                    updated = true;
                }
            }
            if (destData.length > 0) {
                const latEl = document.getElementById('seat-dropoff-lat');
                const lngEl = document.getElementById('seat-dropoff-lng');
                if (latEl && lngEl) {
                    latEl.value = destData[0].lat;
                    lngEl.value = destData[0].lon;
                    updated = true;
                }
            }

            if (updated) {
                if (window.initSeatBookingMap) {
                    window.initSeatBookingMap();
                }
            }
        }
    } catch (e) {
        console.error("Geocoding failed for trip: ", e);
    }
}

function updateSeatFareQuote() {
    const lat1El = document.getElementById('seat-pickup-lat');
    const lng1El = document.getElementById('seat-pickup-lng');
    const lat2El = document.getElementById('seat-dropoff-lat');
    const lng2El = document.getElementById('seat-dropoff-lng');

    if (!lat1El || !lng1El || !lat2El || !lng2El) return;

    const lat1 = parseFloat(lat1El.value);
    const lng1 = parseFloat(lng1El.value);
    const lat2 = parseFloat(lat2El.value);
    const lng2 = parseFloat(lng2El.value);

    const distance = calculateDistanceKm(lat1, lng1, lat2, lng2);
    
    let baseFare = 50;
    let distanceFare = 0;
    if (distance <= 100) {
        distanceFare = distance * 1.50;
    } else {
        distanceFare = (100 * 1.50) + (distance - 100) * 1.00;
    }
    const totalFare = baseFare + distanceFare;

    currentSeatQuote = {
        distance: distance,
        baseFare: baseFare,
        distanceFare: distanceFare,
        totalFare: totalFare
    };

    const breakdownBox = document.getElementById('seat-fare-breakdown-box');
    if (breakdownBox) {
        breakdownBox.innerHTML = `
            <div style="font-weight:700; color:var(--porter-teal); margin-bottom:8px; font-size:13px;">💰 Transparent Fare Quote Breakdown</div>
            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:4px;">
                <span>Base Platform Fare:</span>
                <span>₹${baseFare}</span>
            </div>
            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:6px;">
                <span>Distance Charge (Est. ${distance.toFixed(1)} Km):</span>
                <span>₹${distanceFare.toFixed(2)}</span>
            </div>
            <div style="display:flex; justify-content:space-between; font-weight:800; font-size:14px; border-top:1px dashed var(--border); padding-top:6px; color:var(--accent-green);">
                <span>Total Escrow Amount:</span>
                <span>₹${totalFare.toFixed(2)} INR</span>
            </div>
        `;
    }
}

function calculateDistanceKm(lat1, lon1, lat2, lon2) {
    const R = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLon/2) * Math.sin(dLon/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    return R * c;
}

window.initSeatBookingMap = function() {
    const mapDiv = document.getElementById('seat-booking-map');
    if (!mapDiv) return;

    const lat1 = parseFloat(document.getElementById('seat-pickup-lat').value || '12.9716');
    const lng1 = parseFloat(document.getElementById('seat-pickup-lng').value || '77.5946');
    const lat2 = parseFloat(document.getElementById('seat-dropoff-lat').value || '13.0827');
    const lng2 = parseFloat(document.getElementById('seat-dropoff-lng').value || '80.2707');

    try {
        bookingMapInstance = L.map('seat-booking-map').setView([lat1, lng1], 6);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; OpenStreetMap contributors'
        }).addTo(bookingMapInstance);

        pickupMarker = L.marker([lat1, lng1], { draggable: true }).addTo(bookingMapInstance);
        pickupMarker.bindPopup("<b>Pickup Location (Drag to fine-tune)</b>").openPopup();

        dropoffMarker = L.marker([lat2, lng2], { draggable: true }).addTo(bookingMapInstance);
        dropoffMarker.bindPopup("<b>Dropoff Location (Drag to fine-tune)</b>");

        const polyline = L.polyline([[lat1, lng1], [lat2, lng2]], { color: 'blue', dashArray: '5, 5' }).addTo(bookingMapInstance);

        const group = new L.featureGroup([pickupMarker, dropoffMarker]);
        bookingMapInstance.fitBounds(group.getBounds().pad(0.1));

        pickupMarker.on('dragend', function(e) {
            const position = pickupMarker.getLatLng();
            document.getElementById('seat-pickup-lat').value = position.lat;
            document.getElementById('seat-pickup-lng').value = position.lng;
            polyline.setLatLngs([position, dropoffMarker.getLatLng()]);
            reverseGeocode(position.lat, position.lng, 'seat-pickup');
            updateSeatFareQuote();
        });

        dropoffMarker.on('dragend', function(e) {
            const position = dropoffMarker.getLatLng();
            document.getElementById('seat-dropoff-lat').value = position.lat;
            document.getElementById('seat-dropoff-lng').value = position.lng;
            polyline.setLatLngs([pickupMarker.getLatLng(), position]);
            reverseGeocode(position.lat, position.lng, 'seat-dropoff');
            updateSeatFareQuote();
        });

        setupSeatBlurGeocoding('seat-pickup', 'seat-pickup-lat', 'seat-pickup-lng', pickupMarker, polyline, 0);
        setupSeatBlurGeocoding('seat-dropoff', 'seat-dropoff-lat', 'seat-dropoff-lng', dropoffMarker, polyline, 1);

    } catch (err) {
        console.error("Map initialization failed: ", err);
    }
};

function setupSeatBlurGeocoding(inputId, latId, lngId, marker, polyline, type) {
    const input = document.getElementById(inputId);
    if (!input) return;

    input.addEventListener('blur', function() {
        setTimeout(async () => {
            const val = input.value.trim();
            if (val.length < 3) return;

            try {
                const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(val)}&limit=1&countrycodes=in`, {
                    headers: { 'Accept-Language': 'en' }
                });
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.length > 0) {
                        const lat = parseFloat(data[0].lat);
                        const lon = parseFloat(data[0].lon);
                        
                        document.getElementById(latId).value = lat;
                        document.getElementById(lngId).value = lon;
                        
                        marker.setLatLng([lat, lon]);
                        if (type === 0) {
                            polyline.setLatLngs([[lat, lon], dropoffMarker.getLatLng()]);
                        } else {
                            polyline.setLatLngs([pickupMarker.getLatLng(), [lat, lon]]);
                        }
                        
                        const group = new L.featureGroup([pickupMarker, dropoffMarker]);
                        bookingMapInstance.fitBounds(group.getBounds().pad(0.1));
                        
                        updateSeatFareQuote();
                    }
                }
            } catch (err) {
                console.error(err);
            }
        }, 200);
    });
}

async function payEscrowForRide(rideId) {
    try {
        showToast('Initializing payment gateway...', 'info');

        const orderRes = await fetch(`${API_BASE}/rides/${rideId}/create-payment-order?riderId=${currentUser.id}`, {
            method: 'POST',
            headers: getAuthHeaders()
        });

        if (!orderRes.ok) {
            const err = await orderRes.json();
            showToast(`Gateway initialization failed: ${err.message}`, 'error');
            return;
        }

        const orderDetails = await orderRes.json();

        if (orderDetails.keyId && orderDetails.keyId.startsWith('rzp_test_mockkey')) {
            pendingSimulatedPayment = {
                rideId: rideId,
                orderId: orderDetails.orderId,
                amount: orderDetails.amount,
                goodsDescription: orderDetails.goodsDescription,
                keyId: orderDetails.keyId,
                senderName: orderDetails.senderName,
                senderMobile: orderDetails.senderMobile
            };
            activeModal = 'simulated-razorpay';
            renderApp();
        } else {
            const options = {
                "key": orderDetails.keyId,
                "amount": Math.round(orderDetails.amount * 100),
                "currency": orderDetails.currency,
                "name": "BlaBla + Porter Ride Escrow",
                "description": orderDetails.goodsDescription,
                "order_id": orderDetails.orderId,
                "handler": function (response) {
                    verifyRideRazorpayPayment(rideId, response.razorpay_order_id, response.razorpay_payment_id, response.razorpay_signature);
                },
                "prefill": {
                    "name": orderDetails.senderName,
                    "contact": orderDetails.senderMobile
                },
                "theme": {
                    "color": "#06b6d4"
                },
                "modal": {
                    "ondismiss": function() {
                        showToast('Payment checkout cancelled.', 'warning');
                    }
                }
            };
            const rzp = new Razorpay(options);
            rzp.open();
        }
    } catch (e) {
        console.error(e);
        showToast('Payment gateway error.', 'error');
    }
}

async function verifyRideRazorpayPayment(rideId, orderId, paymentId, signature) {
    showToast('Verifying ride payment details...', 'info');

    const payload = {
        razorpayOrderId: orderId,
        razorpayPaymentId: paymentId,
        razorpaySignature: signature,
        senderId: currentUser.id
    };

    try {
        const res = await fetch(`${API_BASE}/rides/${rideId}/verify-payment`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showToast('Escrow Paid Successfully! Your seat is booked.', 'success');
            activeModal = null;
            renderApp();
        } else {
            const err = await res.json();
            showToast(`Escrow validation failed: ${err.message || 'Signature mismatch'}`, 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('Verification failed.', 'error');
    }
}

async function cancelRideBooking(rideId) {
    if (!confirm('Are you sure you want to cancel this booking? This will refund your paid escrow.')) return;

    try {
        const res = await fetch(`${API_BASE}/rides/${rideId}/cancel?userId=${currentUser.id}`, {
            method: 'POST',
            headers: getAuthHeaders()
        });

        if (res.ok) {
            showToast('Ride booking cancelled and escrow refunded!', 'success');
            renderApp();
        } else {
            const err = await res.json();
            showToast(`Failed to cancel booking: ${err.message}`, 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('Connection error.', 'error');
    }
}

async function submitRideBookingForm(event) {
    event.preventDefault();

    const tripId = selectedTripForSeatBooking.id;
    const pickupLoc = document.getElementById('seat-pickup').value;
    const dropoffLoc = document.getElementById('seat-dropoff').value;
    const pickupLat = parseFloat(document.getElementById('seat-pickup-lat').value);
    const pickupLng = parseFloat(document.getElementById('seat-pickup-lng').value);
    const dropoffLat = parseFloat(document.getElementById('seat-dropoff-lat').value);
    const dropoffLng = parseFloat(document.getElementById('seat-dropoff-lng').value);
    const safetyMode = document.getElementById('seat-safety-mode').checked;

    const payload = {
        riderId: currentUser.id,
        tripId: tripId,
        pickupLocation: pickupLoc,
        dropoffLocation: dropoffLoc,
        pickupLatitude: pickupLat,
        pickupLongitude: pickupLng,
        dropoffLatitude: dropoffLat,
        dropoffLongitude: dropoffLng,
        safetyModeEnabled: safetyMode,
        estimatedDurationMinutes: 300
    };

    try {
        showToast('Saving co-ride request...', 'info');
        const res = await fetch(`${API_BASE}/rides/request`, {
            method: 'POST',
            headers: {
                ...getAuthHeaders(),
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            const ride = await res.json();
            showToast('Ride request created successfully!', 'success');
            payEscrowForRide(ride.id);
        } else {
            const err = await res.json();
            showToast(`Booking failed: ${err.message}`, 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('Connection error.', 'error');
    }
}

let activeCheckinAlertId = null;

async function openRideTrackingModal(rideId) {
    try {
        const res = await fetch(`${API_BASE}/rides/${rideId}`, { headers: getAuthHeaders() });
        if (res.ok) {
            selectedRideForTracking = await res.json();
            activeModal = 'ride-tracking';
            renderApp();
            
            // Auto check if any check-in was triggered for this ride request
            pollForActiveCheckin(rideId);
        }
    } catch (e) {
        showToast('Error fetching ride tracking data.', 'error');
    }
}

async function pollForActiveCheckin(rideId) {
    try {
        const res = await fetch(`${API_BASE}/rides/${rideId}`, { headers: getAuthHeaders() });
        if (res.ok) {
            // Find active Stage 2 checks in a robust way
        }
    } catch (e) {
        console.error(e);
    }
}

window.initRideTrackingMap = function() {
    const mapDiv = document.getElementById('ride-tracking-map');
    if (!mapDiv) return;

    const rideId = selectedRideForTracking.id;
    const tripId = selectedRideForTracking.tripId;

    let pMarker = null;
    let dMarker = null;
    let cMarker = null;
    let routeLine = null;

    const pollRideData = async () => {
        try {
            let ride;
            if (selectedRideForTracking.isLocalTaxi) {
                const rideRes = await fetch(`${API_BASE}/taxi/${rideId}`, { headers: getAuthHeaders() });
                if (!rideRes.ok) return;
                ride = await rideRes.json();
            } else {
                const rideRes = await fetch(`${API_BASE}/rides/${rideId}`, { headers: getAuthHeaders() });
                if (!rideRes.ok) return;
                ride = await rideRes.json();
            }

            const pLat = ride.pickupLatitude || 12.9716;
            const pLng = ride.pickupLongitude || 77.5946;
            const dLat = ride.dropoffLatitude || 13.0827;
            const dLng = ride.dropoffLongitude || 80.2707;

            const liveRes = await fetch(`${API_BASE}/tracking/live/${tripId}`, { headers: getAuthHeaders() });
            if (!liveRes.ok) return;
            const tracking = await liveRes.json();

            const cLat = tracking.currentLatitude;
            const cLng = tracking.currentLongitude;

            const statusEl = document.getElementById('ride-tracking-status');
            const distEl = document.getElementById('ride-tracking-distance');
            const etaEl = document.getElementById('ride-tracking-eta');

            if (statusEl) statusEl.textContent = ride.status;
            if (distEl) distEl.textContent = `${tracking.distanceRemainingKm.toFixed(1)} km`;
            if (etaEl) etaEl.textContent = `${tracking.estimatedMinutesRemaining} mins`;

            lastTrackingPingTimestamp = Date.now();

            if (!trackingMapInstance) {
                trackingMapInstance = L.map('ride-tracking-map').setView([cLat, cLng], 8);
                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    attribution: '&copy; OpenStreetMap contributors'
                }).addTo(trackingMapInstance);

                pMarker = L.marker([pLat, pLng]).addTo(trackingMapInstance);
                pMarker.bindPopup(`<b>Pickup:</b> ${ride.pickupLocation}`);

                dMarker = L.marker([dLat, dLng]).addTo(trackingMapInstance);
                dMarker.bindPopup(`<b>Dropoff:</b> ${ride.dropoffLocation}`);

                cMarker = L.marker([cLat, cLng], { icon: createCaptainLiveIcon() }).addTo(trackingMapInstance);
                cMarker.bindPopup("<b>Captain Position</b>").openPopup();

                routeLine = L.polyline([[pLat, pLng], [cLat, cLng], [dLat, dLng]], { color: 'indigo', weight: 4, dashArray: '8 4' }).addTo(trackingMapInstance);

                const group = new L.featureGroup([pMarker, dMarker, cMarker]);
                trackingMapInstance.fitBounds(group.getBounds().pad(0.1));
                setTimeout(() => {
                    if (trackingMapInstance) trackingMapInstance.invalidateSize();
                }, 250);
            } else {
                if (cMarker) smoothMoveMarker(cMarker, [cLat, cLng], 2000);
                setTimeout(() => {
                    if (routeLine) routeLine.setLatLngs([[pLat, pLng], [cLat, cLng], [dLat, dLng]]);
                }, 2100);
            }

            updateSignalLostBanner('ride-signal-lost-area', lastTrackingPingTimestamp);
        } catch (err) {
            console.error(err);
            updateSignalLostBanner('ride-signal-lost-area', lastTrackingPingTimestamp);
        }
    };

    pollRideData();
    trackingIntervalId = setInterval(pollRideData, 5000);
};

async function triggerSafetyEscalationBtn(rideId, stage) {
    try {
        showToast(`Triggering Stage safety escalation: ${stage}`, 'info');
        const res = await fetch(`${API_BASE}/rides/${rideId}/safety/trigger?stage=${stage}&lastKnownLocation=Current GPS Track`, {
            method: 'POST',
            headers: getAuthHeaders()
        });

        if (res.ok) {
            const alert = await res.json();
            showToast(`Safety Escalation Alert status: ${alert.status}`, 'success');

            if (stage === 'STAGE_2_IN_APP_CHECKIN') {
                activeCheckinAlertId = alert.id;
                const area = document.getElementById('active-checkin-area');
                if (area) area.style.display = 'block';
            }
        } else {
            const err = await res.json();
            showToast(`Failed to trigger safety: ${err.message}`, 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('Connection error triggering safety.', 'error');
    }
}

async function submitSafetyCheckinResponse(isSafe) {
    if (!activeCheckinAlertId) return;

    try {
        const res = await fetch(`${API_BASE}/rides/safety/checkin/${activeCheckinAlertId}?isSafe=${isSafe}`, {
            method: 'POST',
            headers: getAuthHeaders()
        });

        if (res.ok) {
            if (isSafe) {
                showToast('Glad you are safe! Safety alert resolved.', 'success');
            } else {
                showToast('Safety check-in acknowledged. Stage 3 automatic contacts warning initiated!', 'warning');
            }
            const area = document.getElementById('active-checkin-area');
            if (area) area.style.display = 'none';
            activeCheckinAlertId = null;
        } else {
            showToast('Failed to acknowledge check-in.', 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('Connection error.', 'error');
    }
}

// Same-City Local Taxi Helper Functions
function setRiderSubTab(tab) {
    riderActiveSubTab = tab;
    renderApp();
}

let localTaxiPolyline = null;

window.initLocalTaxiBookingMap = function() {
    const mapDiv = document.getElementById('local-taxi-booking-map');
    if (!mapDiv) return;

    const lat1 = parseFloat(document.getElementById('local-taxi-pickup-lat').value || '12.9352');
    const lng1 = parseFloat(document.getElementById('local-taxi-pickup-lng').value || '77.6245');
    const lat2 = parseFloat(document.getElementById('local-taxi-dropoff-lat').value || '12.9719');
    const lng2 = parseFloat(document.getElementById('local-taxi-dropoff-lng').value || '77.6412');

    try {
        localTaxiBookingMapInstance = L.map('local-taxi-booking-map').setView([lat1, lng1], 13);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; OpenStreetMap contributors'
        }).addTo(localTaxiBookingMapInstance);

        localTaxiPickupMarker = L.marker([lat1, lng1], { draggable: true }).addTo(localTaxiBookingMapInstance);
        localTaxiPickupMarker.bindPopup("<b>Pickup Point (Drag to refine)</b>").openPopup();

        localTaxiDropoffMarker = L.marker([lat2, lng2], { draggable: true }).addTo(localTaxiBookingMapInstance);
        localTaxiDropoffMarker.bindPopup("<b>Dropoff Point (Drag to refine)</b>");

        localTaxiPolyline = L.polyline([[lat1, lng1], [lat2, lng2]], { color: 'cyan', dashArray: '5, 5' }).addTo(localTaxiBookingMapInstance);

        const group = new L.featureGroup([localTaxiPickupMarker, localTaxiDropoffMarker]);
        localTaxiBookingMapInstance.fitBounds(group.getBounds().pad(0.2));

        localTaxiPickupMarker.on('dragend', function() {
            const pos = localTaxiPickupMarker.getLatLng();
            document.getElementById('local-taxi-pickup-lat').value = pos.lat;
            document.getElementById('local-taxi-pickup-lng').value = pos.lng;
            localTaxiPolyline.setLatLngs([pos, localTaxiDropoffMarker.getLatLng()]);
            reverseGeocode(pos.lat, pos.lng, 'local-taxi-pickup');
            updateLocalTaxiFareQuote();
        });

        localTaxiDropoffMarker.on('dragend', function() {
            const pos = localTaxiDropoffMarker.getLatLng();
            document.getElementById('local-taxi-dropoff-lat').value = pos.lat;
            document.getElementById('local-taxi-dropoff-lng').value = pos.lng;
            localTaxiPolyline.setLatLngs([localTaxiPickupMarker.getLatLng(), pos]);
            reverseGeocode(pos.lat, pos.lng, 'local-taxi-dropoff');
            updateLocalTaxiFareQuote();
        });

        setupLocalTaxiAutocomplete('local-taxi-pickup', 'local-taxi-pickup-suggestions', 'local-taxi-pickup-lat', 'local-taxi-pickup-lng', localTaxiPickupMarker, localTaxiPolyline, 0);
        setupLocalTaxiAutocomplete('local-taxi-dropoff', 'local-taxi-dropoff-suggestions', 'local-taxi-dropoff-lat', 'local-taxi-dropoff-lng', localTaxiDropoffMarker, localTaxiPolyline, 1);

        setupLocalBlurGeocoding('local-taxi-pickup', 'local-taxi-pickup-lat', 'local-taxi-pickup-lng', localTaxiPickupMarker, 0);
        setupLocalBlurGeocoding('local-taxi-dropoff', 'local-taxi-dropoff-lat', 'local-taxi-dropoff-lng', localTaxiDropoffMarker, 1);

        updateLocalTaxiFareQuote();

    } catch (e) {
        console.error("Local taxi map initialization failed: ", e);
    }
};

function setupLocalBlurGeocoding(inputId, latId, lngId, marker, type) {
    const input = document.getElementById(inputId);
    if (!input) return;

    input.addEventListener('blur', function() {
        setTimeout(async () => {
            const val = input.value.trim();
            if (val.length < 3) return;

            try {
                const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(val)}&limit=1&countrycodes=in`, {
                    headers: { 'Accept-Language': 'en' }
                });
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.length > 0) {
                        const lat = parseFloat(data[0].lat);
                        const lon = parseFloat(data[0].lon);

                        document.getElementById(latId).value = lat;
                        document.getElementById(lngId).value = lon;

                        marker.setLatLng([lat, lon]);
                        if (type === 0) {
                            localTaxiPolyline.setLatLngs([[lat, lon], localTaxiDropoffMarker.getLatLng()]);
                        } else {
                            localTaxiPolyline.setLatLngs([localTaxiPickupMarker.getLatLng(), [lat, lon]]);
                        }

                        const group = new L.featureGroup([localTaxiPickupMarker, localTaxiDropoffMarker]);
                        localTaxiBookingMapInstance.fitBounds(group.getBounds().pad(0.2));

                        updateLocalTaxiFareQuote();
                    }
                }
            } catch (e) {
                console.error(e);
            }
        }, 200);
    });
}

function updateLocalTaxiFareQuote() {
    const lat1El = document.getElementById('local-taxi-pickup-lat');
    const lng1El = document.getElementById('local-taxi-pickup-lng');
    const lat2El = document.getElementById('local-taxi-dropoff-lat');
    const lng2El = document.getElementById('local-taxi-dropoff-lng');

    if (!lat1El || !lng1El || !lat2El || !lng2El) return;

    const lat1 = parseFloat(lat1El.value);
    const lng1 = parseFloat(lng1El.value);
    const lat2 = parseFloat(lat2El.value);
    const lng2 = parseFloat(lng2El.value);

    const distance = calculateDistanceKm(lat1, lng1, lat2, lng2);
    const duration = distance * 3.0; // 3 mins per km

    let baseFare = 20;
    let distanceFare = 0;
    if (distance > 2.0) {
        distanceFare = (distance - 2.0) * 10.0;
    }
    const durationFare = duration * 1.00;
    const platformFee = 5;
    const totalFare = baseFare + distanceFare + durationFare + platformFee;

    currentLocalTaxiQuote = {
        distance: distance,
        duration: duration,
        baseFare: baseFare,
        distanceFare: distanceFare,
        durationFare: durationFare,
        platformFee: platformFee,
        totalFare: totalFare
    };

    const box = document.getElementById('local-taxi-fare-breakdown-box');
    if (box) {
        box.innerHTML = `
            <div style="font-weight:700; color:var(--porter-teal); margin-bottom:8px; font-size:13px;">💰 Transparent Fare Quote Breakdown</div>
            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:4px;">
                <span>Base Platform Fare (includes first 2km):</span>
                <span>₹${baseFare}</span>
            </div>
            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:4px;">
                <span>Distance Charge (${distance.toFixed(2)} km @ ₹10/km):</span>
                <span>₹${distanceFare.toFixed(2)}</span>
            </div>
            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:4px;">
                <span>Time/Duration Charge (Est. ${duration.toFixed(0)} min @ ₹1/min):</span>
                <span>₹${durationFare.toFixed(2)}</span>
            </div>
            <div style="display:flex; justify-content:space-between; font-size:12px; margin-bottom:6px;">
                <span>Flat Platform Fee:</span>
                <span>₹${platformFee}</span>
            </div>
            <div style="display:flex; justify-content:space-between; font-weight:800; font-size:14px; border-top:1px dashed var(--border); padding-top:6px; color:var(--accent-green);">
                <span>Total Escrow Amount:</span>
                <span>₹${totalFare.toFixed(2)} INR</span>
            </div>
        `;
    }
}

async function submitLocalTaxiBookingForm(event) {
    event.preventDefault();

    const pickup = document.getElementById('local-taxi-pickup').value;
    const dropoff = document.getElementById('local-taxi-dropoff').value;
    const pLat = parseFloat(document.getElementById('local-taxi-pickup-lat').value);
    const pLng = parseFloat(document.getElementById('local-taxi-pickup-lng').value);
    const dLat = parseFloat(document.getElementById('local-taxi-dropoff-lat').value);
    const dLng = parseFloat(document.getElementById('local-taxi-dropoff-lng').value);
    const safetyMode = document.getElementById('local-taxi-safety-mode').checked;

    const payload = {
        riderId: currentUser.id,
        pickupLocation: pickup,
        dropoffLocation: dropoff,
        pickupLatitude: pLat,
        pickupLongitude: pLng,
        dropoffLatitude: dLat,
        dropoffLongitude: dLng,
        safetyModeEnabled: safetyMode
    };

    try {
        showToast('Finding nearest active local Captain...', 'info');
        const res = await fetch(`${API_BASE}/taxi/book`, {
            method: 'POST',
            headers: {
                ...getAuthHeaders(),
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            const booking = await res.json();
            showToast('Instant match found! Proceeding to payment...', 'success');
            payEscrowForLocalTaxi(booking.id);
        } else {
            const err = await res.json();
            showToast(`Match failed: ${err.message || 'No Captains online in your 20km radius.'}`, 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('Connection error booking taxi.', 'error');
    }
}

async function payEscrowForLocalTaxi(bookingId) {
    try {
        showToast('Initializing local payment gateway...', 'info');

        const orderRes = await fetch(`${API_BASE}/taxi/${bookingId}/create-payment-order?riderId=${currentUser.id}`, {
            method: 'POST',
            headers: getAuthHeaders()
        });

        if (!orderRes.ok) {
            const err = await orderRes.json();
            showToast(`Gateway initialization failed: ${err.message}`, 'error');
            return;
        }

        const orderDetails = await orderRes.json();

        if (orderDetails.keyId && orderDetails.keyId.startsWith('rzp_test_mockkey')) {
            pendingSimulatedPayment = {
                localTaxiBookingId: bookingId,
                orderId: orderDetails.orderId,
                amount: orderDetails.amount,
                goodsDescription: orderDetails.goodsDescription,
                keyId: orderDetails.keyId,
                senderName: orderDetails.senderName,
                senderMobile: orderDetails.senderMobile
            };
            activeModal = 'simulated-razorpay';
            renderApp();
        } else {
            const options = {
                "key": orderDetails.keyId,
                "amount": Math.round(orderDetails.amount * 100),
                "currency": orderDetails.currency,
                "name": "BlaBla + Porter Same-City Escrow",
                "description": orderDetails.goodsDescription,
                "order_id": orderDetails.orderId,
                "handler": function (response) {
                    verifyLocalTaxiRazorpayPayment(bookingId, response.razorpay_order_id, response.razorpay_payment_id, response.razorpay_signature);
                },
                "prefill": {
                    "name": orderDetails.senderName,
                    "contact": orderDetails.senderMobile
                },
                "theme": {
                    "color": "#06b6d4"
                },
                "modal": {
                    "ondismiss": function() {
                        showToast('Payment checkout cancelled.', 'warning');
                    }
                }
            };
            const rzp = new Razorpay(options);
            rzp.open();
        }
    } catch (e) {
        console.error(e);
        showToast('Payment gateway error.', 'error');
    }
}

async function verifyLocalTaxiRazorpayPayment(bookingId, orderId, paymentId, signature) {
    showToast('Verifying same-city ride payment details...', 'info');

    const payload = {
        razorpayOrderId: orderId,
        razorpayPaymentId: paymentId,
        razorpaySignature: signature,
        senderId: currentUser.id
    };

    try {
        const res = await fetch(`${API_BASE}/taxi/${bookingId}/verify-payment`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showToast('Escrow Paid Successfully! Same-city ride accepted.', 'success');
            activeModal = null;
            renderApp();
        } else {
            const err = await res.json();
            showToast(`Escrow validation failed: ${err.message || 'Signature mismatch'}`, 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('Verification failed.', 'error');
    }
}

async function cancelLocalTaxiBooking(bookingId) {
    if (!confirm('Are you sure you want to cancel this local taxi booking? This will refund your paid escrow.')) return;

    try {
        const res = await fetch(`${API_BASE}/taxi/${bookingId}/status?userId=${currentUser.id}&status=CANCELLED`, {
            method: 'POST',
            headers: getAuthHeaders()
        });

        if (res.ok) {
            showToast('Local taxi booking cancelled and escrow refunded!', 'success');
            renderApp();
        } else {
            const err = await res.json();
            showToast(`Failed to cancel booking: ${err.message}`, 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('Connection error.', 'error');
    }
}

async function openLocalTaxiTrackingModal(bookingId) {
    try {
        const res = await fetch(`${API_BASE}/taxi/${bookingId}`, { headers: getAuthHeaders() });
        if (res.ok) {
            const b = await res.json();
            selectedRideForTracking = {
                id: b.id,
                tripId: b.tripId,
                status: b.status,
                pickupLocation: b.pickupLocation,
                dropoffLocation: b.dropoffLocation,
                pickupLatitude: b.pickupLatitude,
                pickupLongitude: b.pickupLongitude,
                dropoffLatitude: b.dropoffLatitude,
                dropoffLongitude: b.dropoffLongitude,
                isLocalTaxi: true
            };
            activeModal = 'ride-tracking';
            renderApp();
        }
    } catch (e) {
        showToast('Error fetching ride tracking data.', 'error');
    }
}

let localTaxiAvailable = false;
async function fetchLocalCaptainStatus() {
    try {
        const res = await fetch(`${API_BASE}/taxi/captain/status/${currentUser.id}`, { headers: getAuthHeaders() });
        if (res.ok) {
            const status = await res.json();
            localTaxiAvailable = status.available;

            const toggle = document.getElementById('local-taxi-available-toggle');
            const label = document.getElementById('local-taxi-toggle-label');
            const latEl = document.getElementById('local-gps-lat');
            const lngEl = document.getElementById('local-gps-lng');

            if (toggle) toggle.checked = localTaxiAvailable;
            if (label) {
                label.textContent = localTaxiAvailable ? 'ACTIVE & ONLINE' : 'OFFLINE';
                label.style.color = localTaxiAvailable ? 'var(--accent-green)' : 'var(--danger)';
            }
            if (latEl && status.currentLatitude && document.activeElement !== latEl) latEl.value = status.currentLatitude;
            if (lngEl && status.currentLongitude && document.activeElement !== lngEl) lngEl.value = status.currentLongitude;
        }
    } catch (e) {
        console.error(e);
    }
}

async function toggleLocalTaxiAvailabilityBtn(isChecked, isCoordinateUpdateOnly = false) {
    const latVal = parseFloat(document.getElementById('local-gps-lat').value || '12.9716');
    const lngVal = parseFloat(document.getElementById('local-gps-lng').value || '77.5946');

    try {
        const res = await fetch(`${API_BASE}/taxi/captain/status?captainId=${currentUser.id}&available=${isChecked}&latitude=${latVal}&longitude=${lngVal}`, {
            method: 'POST',
            headers: getAuthHeaders()
        });

        if (res.ok) {
            if (isCoordinateUpdateOnly) {
                showToast('GPS coordinates updated!', 'success');
            } else {
                showToast(isChecked ? 'You are now online for local taxi rides!' : 'You went offline.', 'success');
            }
            fetchLocalCaptainStatus();
            fetchCaptainLocalBookings();
        } else {
            showToast('Failed to update status.', 'error');
        }
    } catch (e) {
        console.error(e);
    }
}

async function updateLocalGpsCoordinates() {
    toggleLocalTaxiAvailabilityBtn(localTaxiAvailable, true);
}

async function fetchCaptainLocalBookings() {
    const container = document.getElementById('captain-local-bookings-container');
    if (!container) return;

    try {
        const res = await fetch(`${API_BASE}/taxi/captain-bookings/${currentUser.id}`, { headers: getAuthHeaders() });
        if (res.ok) {
            const bookings = await res.json();
            container.innerHTML = '';

            const active = bookings.filter(b => b.status !== 'COMPLETED' && b.status !== 'CANCELLED');
            if (active.length === 0) {
                container.innerHTML = `<div style="color:var(--text-muted); font-size:13px;">No active local assignments.</div>`;
                return;
            }

            active.forEach(b => {
                let actionHtml = '';
                if (b.status === 'PAID') {
                    actionHtml = `<button class="btn-book" style="background:var(--accent-green); margin-right:8px;" onclick="transitionLocalTaxiStatus(${b.id}, 'IN_PROGRESS')">🚖 Start Ride</button>`;
                } else if (b.status === 'IN_PROGRESS') {
                    actionHtml = `<button class="btn-book" style="background:var(--porter-teal); margin-right:8px;" onclick="transitionLocalTaxiStatus(${b.id}, 'COMPLETED')">🏁 Complete Ride</button>`;
                }

                actionHtml += `<button class="btn-book" style="background:var(--danger); box-shadow:none;" onclick="transitionLocalTaxiStatus(${b.id}, 'CANCELLED')">❌ Cancel</button>`;

                const div = document.createElement('div');
                div.className = 'route-card';
                div.style.background = 'var(--bg-body)';
                div.style.border = '1px solid var(--border)';
                div.style.marginTop = '12px';
                div.innerHTML = `
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="font-weight:700; font-size:13px; color:var(--porter-teal);">Booking #${b.id} (${b.status})</span>
                        <span style="font-weight:800; color:var(--text-white);">₹${Math.round(b.calculatedFare)}</span>
                    </div>
                    <div style="font-size:12px; color:var(--text-body); margin:8px 0;">
                        <div><b>Pickup:</b> ${escapeHtml(b.pickupLocation)}</div>
                        <div><b>Dropoff:</b> ${escapeHtml(b.dropoffLocation)}</div>
                    </div>
                    <div style="display:flex; align-items:center; margin-top:8px;">
                        ${actionHtml}
                    </div>
                `;
                container.appendChild(div);
            });
        }
    } catch (e) {
        console.error(e);
    }
}

async function transitionLocalTaxiStatus(bookingId, status) {
    try {
        const res = await fetch(`${API_BASE}/taxi/${bookingId}/status?userId=${currentUser.id}&status=${status}`, {
            method: 'POST',
            headers: getAuthHeaders()
        });

        if (res.ok) {
            showToast(`Ride status updated to ${status}`, 'success');
            fetchCaptainLocalBookings();
        } else {
            showToast('Failed to update ride status.', 'error');
        }
    } catch (e) {
        console.error(e);
    }
}

function setupLocalTaxiAutocomplete(inputId, suggestionsId, latId, lngId, marker, polyline, type) {
    console.log(`[local-taxi-autocomplete] setup called for: ${inputId}`);
    const input = document.getElementById(inputId);
    const suggestionsBox = document.getElementById(suggestionsId);
    if (!input || !suggestionsBox) {
        console.warn(`[local-taxi-autocomplete] elements not found: input=${!!input}, box=${!!suggestionsBox}`);
        return;
    }

    let debounceTimeout = null;

    input.addEventListener('input', function() {
        console.log(`[local-taxi-autocomplete] input event triggered on ${inputId}: "${input.value}"`);
        if (debounceTimeout) clearTimeout(debounceTimeout);
        const query = input.value.trim();
        if (query.length < 3) {
            console.log(`[local-taxi-autocomplete] query too short (<3 chars), hiding box.`);
            suggestionsBox.style.display = 'none';
            return;
        }

        debounceTimeout = setTimeout(async () => {
            console.log(`[local-taxi-autocomplete] starting fetch for: "${query}"`);
            try {
                const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=5&countrycodes=in`, {
                    headers: { 'Accept-Language': 'en' }
                });
                if (!res.ok) {
                    console.error(`[local-taxi-autocomplete] fetch failed with status: ${res.status}`);
                    return;
                }
                const data = await res.json();
                console.log(`[local-taxi-autocomplete] fetch successful, data length: ${data.length}`);
                
                suggestionsBox.innerHTML = '';
                if (data.length === 0) {
                    suggestionsBox.style.display = 'none';
                    return;
                }

                data.forEach(item => {
                    const div = document.createElement('div');
                    div.style.cssText = 'padding:10px 14px; cursor:pointer; font-size:12px; border-bottom:1px solid var(--border); color:var(--text-title); background:var(--bg-surface);';
                    div.textContent = item.display_name;
                    div.addEventListener('click', function() {
                        console.log(`[local-taxi-autocomplete] suggestion clicked: "${item.display_name}"`);
                        input.value = item.display_name;
                        suggestionsBox.style.display = 'none';
                        const lat = parseFloat(item.lat);
                        const lon = parseFloat(item.lon);
                        document.getElementById(latId).value = lat;
                        document.getElementById(lngId).value = lon;
                        
                        marker.setLatLng([lat, lon]);
                        if (type === 0) {
                            polyline.setLatLngs([[lat, lon], localTaxiDropoffMarker.getLatLng()]);
                        } else {
                            polyline.setLatLngs([localTaxiPickupMarker.getLatLng(), [lat, lon]]);
                        }
                        
                        const group = new L.featureGroup([localTaxiPickupMarker, localTaxiDropoffMarker]);
                        localTaxiBookingMapInstance.fitBounds(group.getBounds().pad(0.2));
                        
                        updateLocalTaxiFareQuote();
                    });
                    suggestionsBox.appendChild(div);
                });
                suggestionsBox.style.display = 'block';
                console.log(`[local-taxi-autocomplete] suggestions box visible (display = block)`);
            } catch (err) {
                console.error("[local-taxi-autocomplete] fetch catch error: ", err);
            }
        }, 500);
    });

    document.addEventListener('click', function(e) {
        if (e.target !== input && e.target !== suggestionsBox) {
            suggestionsBox.style.display = 'none';
        }
    });
}

function setupSimpleAutocomplete(inputId, suggestionsId) {
    const input = document.getElementById(inputId);
    const suggestionsBox = document.getElementById(suggestionsId);
    if (!input || !suggestionsBox) return;

    let debounceTimeout = null;

    input.addEventListener('input', function() {
        if (debounceTimeout) clearTimeout(debounceTimeout);
        const query = input.value.trim();
        if (query.length < 3) {
            suggestionsBox.style.display = 'none';
            return;
        }

        debounceTimeout = setTimeout(async () => {
            try {
                const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=5&countrycodes=in`, {
                    headers: { 'Accept-Language': 'en' }
                });
                if (!res.ok) return;
                const data = await res.json();
                
                suggestionsBox.innerHTML = '';
                if (data.length === 0) {
                    suggestionsBox.style.display = 'none';
                    return;
                }

                data.forEach(item => {
                    const div = document.createElement('div');
                    div.style.cssText = 'padding:10px 14px; cursor:pointer; font-size:12px; border-bottom:1px solid var(--border); color:var(--text-white); background:var(--bg-surface);';
                    div.textContent = item.display_name;
                    div.addEventListener('click', function() {
                        const parts = item.display_name.split(',');
                        const cityName = parts[0].trim();
                        input.value = cityName;
                        suggestionsBox.style.display = 'none';
                    });
                    suggestionsBox.appendChild(div);
                });
                suggestionsBox.style.display = 'block';
            } catch (err) {
                console.error(err);
            }
        }, 500);
    });

    document.addEventListener('click', function(e) {
        if (e.target !== input && e.target !== suggestionsBox) {
            suggestionsBox.style.display = 'none';
        }
    });
}

