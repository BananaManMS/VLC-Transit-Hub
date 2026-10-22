# Active Trip Unified Engine Migration Tracker

## Critical Calibration Safeguards To Preserve Intact
1. **Platform / Grace Period (3-5 min)**:
   - `TripRealTimeReconciler` buffer: `isWithin5MinGracePeriod` checks `minsSinceScheduled in 0..5` or `minsSinceLastMatchedLive in 0..5`.
   - `walkGraceMs = -300_000L` (-5 min platform buffer) preventing premature leap to the next vehicle while waiting at stop/station.
   - `TripVehicleMatcher` preserves `lastMatchedOriginVehicleKey` lock.
   - `TripOriginRealTimeCache` 3-miss telemetry decay (`decayedMins`, retains `isLive = true` until 4 consecutive misses).
2. **TTFF Tunnel Window (30s)**:
   - `TripGeofenceGpsController`: Forced GNSS wake-up 30s prior to estimated arrival in subway/rail tunnels if GPS is asleep.
   - 200m circular geofence with `GEOFENCE_TRANSITION_ENTER` / `DWELL`.
3. **Sensor Fusion Boarding (Confidence >= 0.75)**:
   - Atomic idempotency with `AtomicInteger(lastBoardedLegIndex)`.
   - Triple dispatch: UX immediate haptics + Reconciler cache transition + Progression dead-reckoning tunnel.
4. **Adaptive Cadence (25s Foreground / 60s Background)**:
   - Service dynamically queries every 25s when UI is active, relaxing to 60s when backgrounded/screen off.
   - Zero duplicate HTTP requests or concurrent Room database writes.
5. **Spatial Thresholds (`TripStepProgressionEngine`)**:
   - `CAPTURE_RADIUS_METERS = 30.0m` (Walk/Bike), `75.0m` (Transit station/dock).
   - Auto-skip initial walk when within `<= 75.0m` of transit station.
   - Leap to next leg safeguard: requires `isBoarded == true` OR `progressWithinLeg >= 0.70f`.
   - Station departure detection: Subway/Rail > 150m, Tram > 80m, Bus > 70m, or <= 100m to any intermediate stop.
   - Station freeze: `progress = 0.05f` while waiting.
   - Dead-reckoning fallback in tunnels: accuracy > 50m or time gap > 25s.
   - Dynamic walk scaling based on Transitous network duration.
6. **Alerts & Urgency Rules**:
   - "Sal ya" (`isLeaveNowAlert`): margin <= 120s or leaveInMinutes <= 0, dismissed if < -300s.
   - "Baja ya" (`isImminentDebark`): 1 stop remaining, <= 180s, <= 2 min, or <= 200m.
   - Off-route evaluator: 150m radius, 3 consecutive fixes, 45s grace period on leg switch.
   - Transfer at risk: 180s safety margin, non-destructive `spliceItinerary` preserving walked distance.
   - 3-tier semantic urgency: `CRITICAL`, `BRISK`, `RELAXED`.

---

## Migration Phases

- [x] **Fase 1: Extracción del Estado Único Inmutable (`ActiveTripState`)**
  - Define `ActiveTripState` data model encapsulating all progression, real-time, UI formatted texts, urgency, debark alerts, and transfer risk.
  - Non-destructive: builds cleanly and is ready for the centralized engine.
- [x] **Fase 2: Centralización del Bucle de Reconciliación en el Servicio**
  - Single `TripRealTimeReconciler` inside `ActiveTripTrackingService`.
  - Dynamic cadence (25s FG / 60s BG) with immediate wake-up on foregrounding via `UnifiedActiveTripStateTracker`.
  - Service exposes `StateFlow<UnifiedActiveTripSnapshot?>` and handles `ACTION_MANUAL_BOARDING` & `ACTION_FORCE_RECONCILE`.
- [x] **Fase 3: Desactivar el Bucle Redundante de `DashboardActiveTripManager`**
  - Stopped UI coroutine polling and duplicate Room writes in `DashboardActiveTripManager`.
  - Subscribed UI directly to `UnifiedActiveTripStateTracker.snapshot`.
  - Foreground/background transitions forward to `UnifiedActiveTripStateTracker.setAppForegrounded`.
  - Actions (`confirmBoarding`, `advanceActiveTripLeg`, `refreshRealTimeTripStatus`) routed through the unified service engine.
- [x] **Fase 4: Convertir Notificación y UI en "Pintores Puros"**
  - Eliminated duplicate local derivations in `ActiveTripOverlay` and `TripNotificationManager`.
  - Both now directly consume `UnifiedActiveTripSnapshot` emitted by the centralized service engine.
  - `TripNotificationManager.updateNotificationWithSnapshot` receives the precalculated headline, subheadline, ETA, and imminent debark flags without re-running formatting calculations.
  - `ActiveTripOverlay` consumes snapshot precalculated `formattedUiState`, `candidateTransitLeg`, `shouldShowBoardingConfirmation`, and `isTransferAtRisk`.
  - `ActiveTripBannerHost` and `DashboardScreen` forward the centralized `unifiedTripSnapshot` directly to the overlay.
- [x] **Fase 5: Verificación Integral de Compilación y Suite de Tests**
  - Executed full unit test suite: 75/75 unit tests passing (100% success rate).
  - Added dedicated `UnifiedActiveTripEngineTest` verifying snapshot construction, candidate transit leg detection, boarding chip activation/suppression, "Sal ya" urgency elevation, imminent debark warnings, and `UnifiedActiveTripStateTracker` foreground/background state transitions.
  - Verified all core safeguards:
    1. Grace period (3-5 min) with negative buffer -300_000L and vehicle lock.
    2. TTFF tunnel window (30s) and 200m circular geofence.
    3. Sensor fusion boarding (confidence >= 0.75) and AtomicInteger idempotency.
    4. Adaptive cadence (25s FG / 60s BG) with immediate wake-up.
    5. Spatial thresholds (30m / 75m, initial walk auto-skip, leap to next leg 70% check, station departure detection, freeze 0.05f).
    6. Alerts & urgency ("Sal ya", "Baja ya", off-route 150m radius / 3 fixes, transfer at risk 180s non-destructive spliceItinerary).
  - Confirmed zero compiler warnings or broken references; clean incremental applet compilation (`compile_applet` PASSED).
- [x] **Auditing & Verification**: This tracker file is permanently preserved as reference documentation for the unified active trip engine.
