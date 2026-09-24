# Plan Maestro de Sincronización y Restauración por Micropasos

Este documento actúa como la **bitácora y base de control** para sincronizar y reparar de manera 100% segura y gradual cada desajuste identificado entre el código fuente restaurado y el binario funcional verificado en el APK (`app-debug.apk`).

---

## Reglas de Ejecución de Cada Micropaso
1. **Un solo archivo / responsabilidad a la vez**: No se tocan múltiples módulos simultáneamente.
2. **Cero efectos colaterales**: Cada micropaso solo añade o sincroniza interfaces y datos ya existentes en la versión estable del APK.
3. **Validación continua**: Tras cada cambio se registra el estado aquí y se comprueba la reducción de errores.

---

## Registro de Fases y Micropasos

### 🟢 FASE 1: Modelos y Enums Base (Sin Lógica de Negocio / Riesgo Cero)
- [x] **Paso 1.1**: Definir enum `SchedulePhase` en `com/example/data/model/routing/RoutingDomainModels.kt` con valores exactos del APK:
  - `THEORETICAL_AWAITING_RADAR`
  - `LIVE_ACQUIRED`
  - `VERIFIED_LIVE_TRACKING`
  - `SCHEDULED_ON_TIME`
  - `DELAYED`
  - `CANCELLED`
- [x] **Paso 1.2**: Ajustar data class `NominatimResult` (`com/example/data/model/NominatimResult.kt`):
  - Incorporar campos opcionales `stopId: String? = null` y `stopType: String? = null` para compatibilidad de constructores.
- [x] **Paso 1.3**: Sincronizar constantes y campos de `ActiveTripEntity` (`com/example/data/database/DatabaseModels.kt`) y `ActiveTripDao` (`com/example/data/database/AppDatabase.kt`):
  - Constantes: `ACTIVE_TRIP_ID = "SINGLETON_ACTIVE_TRIP"`, `STATUS_IN_PROGRESS = "IN_PROGRESS"`, `EXPIRATION_GRACE_PERIOD_MILLIS = 1800000L`.
  - Campos: `originName`, `routeDataJson`, `currentLegIndex`, `lastLegScheduledArrivalTimeMillis`, `startTimestamp`, `lastUpdatedTimestamp`.
  - Métodos DAO: `insertOrUpdateActiveTrip`, `deleteActiveTrip`, `updateLegIndex`, `updateStatus`.

---

### 🟡 FASE 2: Sincronizadores y Clases de Soporte de Red / GTFS
- [x] **Paso 2.1**: Restaurar `MetrobusDataSyncManager` en `com/example/data/repository/metrobus/MetrobusDataSyncManager.kt`:
  - Métodos: `syncIfNeeded()`, `getLocalShapesIndexFile()`, `loadShapesIndex()`.
  - Añadir `getLocalShapesFile()` en `EmtDataSyncManager.kt`.
- [x] **Paso 2.2**: Restaurar `GtfsNetworkDataSource` en `com/example/data/repository/renfe/GtfsNetworkDataSource.kt`:
  - Métodos: `fetchGtfsData()`, `fetchTripUpdates()`.
  - Sincronizar `GtfsCacheManager.kt` y `EmtScheduledRepository.kt`.

---

### 🟠 FASE 3: Utilidades de Reconciliación y Formateo
- [x] **Paso 3.1**: Restaurar `PenultimateStopArrivalMatcher` en `com/example/util/PenultimateStopArrivalMatcher.kt`.
- [x] **Paso 3.2**: Restaurar `AccessibilityNoticeFormatter` en `com/example/util/AccessibilityNoticeFormatter.kt` (soporte para limpieza y deduplicación de avisos de accesibilidad de Metro).
- [x] **Paso 3.3**: Restaurar banner composable `UnifiedDivertedAlertBanner` en `com/example/ui/transit/UnifiedDivertedAlertBanner.kt`.

---

### 🔵 FASE 4: Repositorios y DAOs
- [x] **Paso 4.1**: Sincronizar métodos de `ActiveTripDao` (`insertOrUpdateActiveTrip`, `deleteActiveTrip`, `updateLegIndex`, `updateStatus`).
- [x] **Paso 4.2**: Ajustar llamadas en `ActiveTripRepository.kt`.
- [x] **Paso 4.3**: Ajustar `MetroScheduleRepository.kt` y `MetroRepository.kt` (métodos de consulta de horarios y mapeo de estaciones).
- [x] **Paso 4.4**: Ajustar `MetroViewModel.kt` y `BusViewModel.kt` (alinear llamadas a repositorios).

---

### 🟣 FASE 5: Componentes de Dashboard y UI
- [x] **Paso 5.1**: Corregir llamadas de propiedades meteorológicas en `DashboardComponents.kt`.
- [x] **Paso 5.2**: Corregir `DashboardCalendarManager.kt`.
- [x] **Paso 5.3**: Limpiar y tipar vistas de incidencias en `MetroAlertsTab.kt`.
- [x] **Paso 5.4**: Completar ramas de transporte en `TripUIStateFormatter.kt`.
- [x] **Paso 5.5**: Alinear offsets de renderizado en `MapMarkersManager.kt`.

---

## Estado Actual de Compilación
* **Fase en curso**: ¡Todas las fases de restauración completadas exitosamente!
* **Última verificación**: **BUILD SUCCEEDED** (Compilación correcta de la aplicación).
