# Project Notes & Developer Guidelines

## Stability & Model Contract Rules
1. **Mandatory Read-Before-Write**: ALWAYS use `view_file` on the target model/entity class file (`DatabaseModels.kt`, `MetroModels.kt`, etc.) BEFORE writing, modifying, or instantiating data classes. NEVER guess property names, types, or constructor argument order.
2. **Contract Integrity**: Maintain parameter defaults on data classes and repository functions to guarantee backward compatibility with calling components. Always use NAMED ARGUMENTS when instantiating data classes with multiple fields (e.g. `ForecastHour(time = "12:00", tempCelsius = 22.0)`).
3. **Atomic Upgrades**: Any modification to data layer signatures (DAOs, Repositories, Domain Models) MUST be paired with corresponding updates across ViewModels and UI Composables in the same edit cycle.
4. **No Ghost Helper Classes**: NEVER reference or call helper classes/objects (`StationAccessibilityHelper`, `MetroDepotFilterHelper`, `MetrobusDataSyncManager`, etc.) without implementing or verifying their actual existence in `com.example.util` or `com.example.data`.
5. **Database Migration Safety**: Room Database builders must specify `fallbackToDestructiveMigration()` or safe schema migrations to prevent cache corruption upon model changes.
6. **Language & Identifier Discipline**: Do NOT translate variable names between Spanish and English. Keep the canonical identifiers defined in the code:
   - Metro Stations: `MetroStation(id, name, lines, zone, latitude, longitude, accessibility, isFavorite)`
   - Cercanías Entities: `CercaniasStationEntity(stop_id, nombre, lat, lon, lines, zone, isFavorite)`
   - Metrobus / Geoportal Stops: `id_parada`, `denominacion`, `lineas`, `suprimida`
   - Active Trip: `ActiveTripEntity(tripId, originName, destinationName, routeDataJson, status, currentLegIndex, lastLegScheduledArrivalTimeMillis, startTimestamp, lastUpdatedTimestamp)`

## Map Marker Recycling Strategy (osmdroid)
To avoid memory leaks, heavy GC pauses, and invisible/disappearing markers during map pan and zoom operations in `MapView`:
1. **Never create new `Marker` instances dynamically on every render frame.**
2. **Use centralized index-based pool recycling** (`recycledMarkers` lists passed to renderers).
3. **Index-Based Active Count**:
   - Iterate through items to render.
   - For index `i` from `0` to `activeCount - 1`: grab `recycledMarkers[i]` (or instantiate and append to list if `i >= recycledMarkers.size`).
   - Configure marker properties (`position`, `icon`, `title`, `snippet`, `anchor`, `setVisible(true)`, `isEnabled = true`, listeners).
   - For any leftover markers from `activeCount` to `recycledMarkers.size - 1`: hide them explicitly:
     ```kotlin
     recycledMarkers[i].apply {
         setVisible(false)
         isEnabled = false
         setOnMarkerClickListener(null)
     }
     ```
4. **Do NOT use key-based lookup (`HashSet` or `.find { m.id == key }`)** across map overlays, as `mapView.overlays` iterations cause massive frame drops and state desynchronization.
