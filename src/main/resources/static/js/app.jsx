const API_BASE = 'http://localhost:8080/api';

// Utility: Decode JWT Claims (extract userId & role from token)
function parseJwtClaims(token) {
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

// ============================================================================
// ROLE-BASED ROUTE GUARD COMPONENT
// Enforces that only users with allowed roles can mount the child component
// ============================================================================
function RoleGuard({ user, allowedRoles, children }) {
    if (!user || !allowedRoles.includes(user.role)) {
        return (
            <div style={{ padding: '60px', textAlignment: 'center', color: '#ef4444' }}>
                <h2>🔒 Access Denied</h2>
                <p>Your user role ({user ? user.role : 'GUEST'}) does not have permission to view this portal.</p>
            </div>
        );
    }
    return children;
}

// Main Application Component
function App() {
    const [currentUser, setCurrentUser] = React.useState(() => {
        const saved = localStorage.getItem('currentUser');
        if (!saved) return null;
        try {
            const parsed = JSON.parse(saved);
            // Validate JWT claims server-side embedded role
            const claims = parseJwtClaims(parsed.token);
            if (claims && claims.role) {
                parsed.role = claims.role; // Enforce role from JWT token
                parsed.id = LongOrNum(claims.sub);
            }
            return parsed;
        } catch (e) {
            return null;
        }
    });

    const [showAuthModal, setShowAuthModal] = React.useState(false);
    const [authMode, setAuthMode] = React.useState('signin'); // 'signin' or 'register'

    function LongOrNum(val) {
        return val ? parseInt(val) : null;
    }

    const logout = () => {
        localStorage.removeItem('currentUser');
        setCurrentUser(null);
    };

    // Render Portal based strictly on JWT-decoded User Role
    const renderRolePortal = () => {
        if (!currentUser) {
            return <UnauthenticatedLanding openAuth={() => setShowAuthModal(true)} />;
        }

        switch (currentUser.role) {
            case 'SENDER':
                return (
                    <RoleGuard user={currentUser} allowedRoles={['SENDER']}>
                        <SenderPortal user={currentUser} logout={logout} />
                    </RoleGuard>
                );
            case 'TRAVELER':
                return (
                    <RoleGuard user={currentUser} allowedRoles={['TRAVELER']}>
                        <CaptainPortal user={currentUser} logout={logout} />
                    </RoleGuard>
                );
            case 'RIDER':
                return (
                    <RoleGuard user={currentUser} allowedRoles={['RIDER']}>
                        <RiderPortal user={currentUser} logout={logout} />
                    </RoleGuard>
                );
            case 'ADMIN':
                return (
                    <RoleGuard user={currentUser} allowedRoles={['ADMIN']}>
                        <AdminPortal user={currentUser} logout={logout} />
                    </RoleGuard>
                );
            default:
                return <div>Invalid Role</div>;
        }
    };

    return (
        <div>
            {/* Header Navigation Bar */}
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
                    {currentUser ? (
                        <div class="user-profile-btn" onClick={logout}>
                            <div class="user-avatar">{currentUser.fullName ? currentUser.fullName.charAt(0).toUpperCase() : 'U'}</div>
                            <div class="user-details">
                                <span class="user-name">{currentUser.fullName}</span>
                                <span class="user-role-badge">{currentUser.role} (Sign Out)</span>
                            </div>
                        </div>
                    ) : (
                        <button class="user-profile-btn" onClick={() => setShowAuthModal(true)}>
                            <div class="user-avatar">🔑</div>
                            <div class="user-details">
                                <span class="user-name">Sign In / Register</span>
                                <span class="user-role-badge">BCrypt + JWT Protected</span>
                            </div>
                        </button>
                    )}
                </div>
            </header>

            {/* Main Wrapper */}
            <main class="main-wrapper">
                {renderRolePortal()}
            </main>

            {/* Auth Modal */}
            {showAuthModal && (
                <AuthModal 
                    mode={authMode} 
                    setMode={setAuthMode} 
                    onClose={() => setShowAuthModal(false)}
                    onSuccess={(user) => {
                        setCurrentUser(user);
                        localStorage.setItem('currentUser', JSON.stringify(user));
                        setShowAuthModal(false);
                    }}
                />
            )}
        </div>
    );
}

// ----------------------------------------------------------------------------
// 1. SENDER PORTAL (Only rendered for SENDER role)
// ----------------------------------------------------------------------------
function SenderPortal({ user, logout }) {
    const [trips, setTrips] = React.useState([]);
    const [selectedTrip, setSelectedTrip] = React.useState(null);
    const [declaredVal, setDeclaredVal] = React.useState(15000);
    const [quote, setQuote] = React.useState(null);

    React.useEffect(() => {
        fetch(`${API_BASE}/trips`)
            .then(res => res.json())
            .then(data => setTrips(data || []))
            .catch(err => console.error(err));
    }, []);

    React.useEffect(() => {
        fetch(`${API_BASE}/parcels/quote?declaredValue=${declaredVal}&distanceKm=350`)
            .then(res => res.json())
            .then(data => setQuote(data))
            .catch(err => console.error(err));
    }, [declaredVal]);

    return (
        <div>
            <div class="hero-card">
                <div class="hero-header">
                    <div class="hero-subtitle">📦 Parcel Sender Portal (Standard Customer)</div>
                    <h1 class="hero-title">Search Available Captain Trips & Book Crowd-Shipping</h1>
                </div>
            </div>

            <h2 class="section-title" style={{ marginBottom: '20px' }}>Available Inter-City Captain Trips</h2>
            <div class="cards-grid">
                {trips.map(trip => (
                    <div class="route-card" key={trip.id}>
                        <div class="card-top">
                            <div class="driver-profile">
                                <div class="driver-avatar">C{trip.travelerId}</div>
                                <div class="driver-info">
                                    <span class="driver-name">Captain Traveler #{trip.travelerId}</span>
                                    <span class="driver-meta">⭐ 5.0 Rating</span>
                                </div>
                            </div>
                            <span class="verified-badge">VERIFIED CAPTAIN</span>
                        </div>
                        <div class="route-timeline">
                            <div class="timeline-row">
                                <span class="city-label">{trip.source}</span>
                                <span class="duration-tag">➔ ~6 Hrs ➔</span>
                                <span class="city-label">{trip.destination}</span>
                            </div>
                        </div>
                        <div class="card-footer">
                            <span class="price-amount">₹150 Base</span>
                            <button class="btn-book" onClick={() => setSelectedTrip(trip)}>Book Delivery</button>
                        </div>
                    </div>
                ))}
            </div>

            {selectedTrip && quote && (
                <div class="modal-backdrop show">
                    <div class="modal-box">
                        <div class="modal-head">
                            <h3>📦 Book Parcel Delivery</h3>
                            <button class="btn-close" onClick={() => setSelectedTrip(null)}>✕</button>
                        </div>
                        <div class="form-group" style={{ marginBottom: '14px' }}>
                            <label class="form-label">Declared Value (₹ INR)</label>
                            <input 
                                type="number" 
                                class="form-control" 
                                value={declaredVal} 
                                onChange={e => setDeclaredVal(e.target.value)} 
                            />
                        </div>
                        <div id="fare-breakdown-box" style={{ background: '#172033', padding: '14px', borderRadius: '12px', marginBottom: '20px' }}>
                            <div style={{ fontWeight: 700, color: '#06b6d4' }}>💰 Transparent Fare Calculation (INR)</div>
                            <div>Base Fare: ₹{quote.baseFareInr}</div>
                            <div>Distance Fare: ₹{quote.distanceFareInr}</div>
                            <div>{quote.categoryTierLabel}: ₹{quote.categorySurchargeInr}</div>
                            <div style={{ fontWeight: 800, color: '#10b981', marginTop: '6px' }}>Total Escrow: ₹{quote.totalFareInr} INR</div>
                        </div>
                        <button class="btn-search" style={{ width: '100%' }} onClick={() => { alert(`Parcel Booked for ₹${quote.totalFareInr} INR!`); setSelectedTrip(null); }}>
                            Pay Escrow & Book Parcel
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}

// ----------------------------------------------------------------------------
// 2. CAPTAIN / TRAVELER PORTAL (Only rendered for TRAVELER role)
// ----------------------------------------------------------------------------
function CaptainPortal({ user, logout }) {
    const [kycStatus, setKycStatus] = React.useState(user.kycStatus || 'NOT_SUBMITTED');

    return (
        <div>
            <div class="hero-card">
                <div class="hero-header">
                    <div class="hero-subtitle">🚗 Captain / Traveler Driver Portal</div>
                    <h1 class="hero-title">Publish Inter-City Routes & Carry Parcels</h1>
                </div>
            </div>

            {kycStatus !== 'APPROVED' ? (
                <div class="route-card" style={{ maxWidth: '600px', margin: '0 auto' }}>
                    <h2>🪪 Driver KYC Verification Required</h2>
                    <p style={{ color: '#94a3b8', margin: '16px 0' }}>
                        Per platform safety rules, Captains must submit Aadhaar, PAN, DL, and Vehicle RC to unlock route publishing.
                    </p>
                    <button class="btn-search" style={{ width: '100%', background: '#10b981' }} onClick={() => { setKycStatus('APPROVED'); alert('KYC Submitted & Approved by Admin for demo!'); }}>
                        Submit KYC Documents & Verification
                    </button>
                </div>
            ) : (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
                    <div class="route-card">
                        <h2>📍 Publish New Inter-City Route</h2>
                        <form onSubmit={e => { e.preventDefault(); alert('Trip Published!'); }}>
                            <div class="form-group" style={{ marginBottom: '14px' }}>
                                <label class="form-label">Origin City</label>
                                <input type="text" class="form-control" defaultValue="Bengaluru" required />
                            </div>
                            <div class="form-group" style={{ marginBottom: '14px' }}>
                                <label class="form-label">Destination City</label>
                                <input type="text" class="form-control" defaultValue="Hyderabad" required />
                            </div>
                            <button type="submit" class="btn-search" style={{ width: '100%' }}>Publish Route</button>
                        </form>
                    </div>
                    <div class="route-card">
                        <h2>📡 Live Telemetry GPS Broadcaster</h2>
                        <p style={{ color: '#94a3b8' }}>Broadcast real-time vehicle speed and GPS location during active transit.</p>
                        <button class="btn-search" style={{ width: '100%', marginTop: '20px' }} onClick={() => alert('GPS Ping Broadcasted!')}>Broadcast Location</button>
                    </div>
                </div>
            )}
        </div>
    );
}

// ----------------------------------------------------------------------------
// 3. PASSENGER RIDER PORTAL (Only rendered for RIDER role)
// ----------------------------------------------------------------------------
function RiderPortal({ user, logout }) {
    return (
        <div>
            <div class="hero-card">
                <div class="hero-header">
                    <div class="hero-subtitle">🚖 Passenger Carpooling Portal</div>
                    <h1 class="hero-title">Book Inter-City Seats & Enable Safety Mode</h1>
                </div>
            </div>
            <div class="route-card">
                <h2>💺 Passenger Seat Booking & Emergency Contacts</h2>
                <p style={{ color: '#94a3b8', margin: '16px 0' }}>Search carpool rides with 3-Stage Safety Ladder & Trusted Contact notifications.</p>
                <button class="btn-search" onClick={() => alert('Ride Requested!')}>Search Carpool Seats</button>
            </div>
        </div>
    );
}

// ----------------------------------------------------------------------------
// 4. ADMIN PORTAL (Only rendered for ADMIN role)
// ----------------------------------------------------------------------------
function AdminPortal({ user, logout }) {
    return (
        <div>
            <div class="hero-card">
                <div class="hero-header">
                    <div class="hero-subtitle">🛡️ Platform Safety & Governance Console</div>
                    <h1 class="hero-title">Review KYC Queue & Escrow Disputes</h1>
                </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
                <div class="route-card">
                    <h2>🪪 Pending Captain KYC Approvals</h2>
                    <p style={{ color: '#94a3b8', margin: '14px 0' }}>Captain Bob (Aadhaar: 1234-5678-9012, RC: KA-01-AB-1234)</p>
                    <button class="btn-search" style={{ background: '#10b981' }} onClick={() => alert('KYC Approved!')}>Approve Captain KYC</button>
                </div>
                <div class="route-card">
                    <h2>⚖️ Escrow Dispute Console</h2>
                    <p style={{ color: '#94a3b8', margin: '14px 0' }}>Dispute #104 (Damaged Packaging)</p>
                    <button class="btn-search" style={{ background: '#ef4444' }} onClick={() => alert('Refunded to Sender!')}>Refund Sender Escrow</button>
                </div>
            </div>
        </div>
    );
}

// Unauthenticated Landing Screen
function UnauthenticatedLanding({ openAuth }) {
    return (
        <div class="hero-card" style={{ textAlign: 'center', padding: '60px 40px' }}>
            <h1 class="hero-title" style={{ marginBottom: '16px' }}>Peer-to-Peer Crowd-Shipping & Ride-Sharing</h1>
            <p style={{ color: '#94a3b8', marginBottom: '32px' }}>Please sign in to access your role-specific dashboard.</p>
            <button class="btn-search" style={{ margin: '0 auto' }} onClick={openAuth}>Sign In / Create Account</button>
        </div>
    );
}

// Auth Modal Dialog
function AuthModal({ mode, setMode, onClose, onSuccess }) {
    const [mobile, setMobile] = React.useState('9876543210');
    const [password, setPassword] = React.useState('password123');
    const [name, setName] = React.useState('Stefan Salvatore');
    const [role, setRole] = React.useState('SENDER');

    const handleSubmit = async (e) => {
        e.preventDefault();
        const endpoint = mode === 'signin' ? `${API_BASE}/auth/login` : `${API_BASE}/auth/register`;
        const body = mode === 'signin' 
            ? { mobileNumber: mobile, password: password }
            : { fullName: name, mobileNumber: mobile, email: `${mobile}@app.com`, password: password, role: role };

        try {
            const res = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            if (!res.ok) {
                const errData = await res.json();
                alert(`Error: ${errData.message || errData.error || 'Authentication failed'}`);
                return;
            }
            const data = await res.json();
            onSuccess(data);
        } catch (err) {
            alert('Authentication Error');
        }
    };

    return (
        <div class="modal-backdrop show">
            <div class="modal-box">
                <div class="modal-head">
                    <div style={{ display: 'flex', gap: '12px' }}>
                        <button class={`service-btn ${mode === 'signin' ? 'active' : ''}`} onClick={() => setMode('signin')}>Sign In</button>
                        <button class={`service-btn ${mode === 'register' ? 'active' : ''}`} onClick={() => setMode('register')}>Create Account</button>
                    </div>
                    <button class="btn-close" onClick={onClose}>✕</button>
                </div>
                <form onSubmit={handleSubmit}>
                    {mode === 'register' && (
                        <div class="form-group" style={{ marginBottom: '14px' }}>
                            <label class="form-label">Full Name</label>
                            <input type="text" class="form-control" value={name} onChange={e => setName(e.target.value)} required />
                        </div>
                    )}
                    <div class="form-group" style={{ marginBottom: '14px' }}>
                        <label class="form-label">Mobile Number</label>
                        <input type="text" class="form-control" value={mobile} onChange={e => setMobile(e.target.value)} required />
                    </div>
                    <div class="form-group" style={{ marginBottom: '14px' }}>
                        <label class="form-label">Password</label>
                        <input type="password" class="form-control" value={password} onChange={e => setPassword(e.target.value)} required />
                    </div>
                    {mode === 'register' && (
                        <div class="form-group" style={{ marginBottom: '20px' }}>
                            <label class="form-label">Account Role (ADMIN blocked server-side)</label>
                            <select class="form-control" value={role} onChange={e => setRole(e.target.value)}>
                                <option value="SENDER">📦 Parcel Sender</option>
                                <option value="TRAVELER">🚗 Captain / Traveler</option>
                                <option value="RIDER">🚖 Passenger Rider</option>
                            </select>
                        </div>
                    )}
                    <button type="submit" class="btn-search" style={{ width: '100%' }}>
                        {mode === 'signin' ? 'Sign In with JWT' : 'Create Account & Sign In'}
                    </button>
                </form>
            </div>
        </div>
    );
}

// Render React App into DOM
const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<App />);
