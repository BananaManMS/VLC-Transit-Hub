# Project Notes & Developer Guidelines

## Stability & Model Contract Rules
1. **Contract Integrity**: Maintain parameter defaults on data classes and repository functions to guarantee backward compatibility with calling components.
2. **Atomic Upgrades**: Any modification to data layer signatures (DAOs, Repositories, Domain Models) MUST be paired with corresponding updates across ViewModels and UI Composables in the same edit cycle.
3. **Database Migration Safety**: Room Database builders must specify `fallbackToDestructiveMigration()` or safe schema migrations to prevent cache corruption upon model changes.

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
