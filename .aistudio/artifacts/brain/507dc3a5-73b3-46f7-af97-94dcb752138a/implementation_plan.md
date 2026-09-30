# Plan de Migración: Mapa de Cercanías en Vivo Independiente (Revisado)

Este plan detalla la migración de la funcionalidad de rastreo de trenes Cercanías hacia un nuevo mapa autónomo. Incorpora la optimización sugerida por el usuario para **aprovechar al máximo la memoria caché compartida del panel de salidas**, logrando un mapa de carga instantánea con cero peticiones de red duplicadas.

---

### Análisis de Integración con el Panel de Salidas (Optimización Clave)

Tras analizar los componentes internos de Renfe en la aplicación, descubrimos una excelente oportunidad de optimización utilizando **`GtfsCacheManager`** y **`RenfeRepository`**:

1. **Caché Unificada en Memoria (`GtfsCacheManager`)**:
   * Cuando el panel de salidas de Cercanías se refresca periódicamente (cada 20s-30s), descarga e interpreta tres feeds: `flota.json`, `trip_updates.json` y `vehicle_positions.json`.
   * Estos datos se guardan de forma segura bajo un **Mutex** en `GtfsCacheManager` con un tiempo de vida (TTL) de **15 segundos**.
2. **Carga Instantánea sin Latencia (Cero Segundos)**:
   * Al abrir el nuevo mapa de trenes, **no necesitamos esperar a una nueva llamada de red**. Podemos consumir inmediatamente `renfeRepository.getUniqueLiveVehicles()`, que retornará al instante todas las posiciones ya descargadas y parseadas por el panel de salidas.
3. **Detalles Completos del Tren (`LiveVehicleInfo`)**:
   * Cada registro en caché ya contiene información crucial del tren: `originName` (estación de origen), `destinationName` (dirección/cabecera), `trainNum` (número de servicio), `delayMinutes` (retraso acumulado), y la línea (`routeId`).
   * No es necesario realizar peticiones adicionales para rellenar la cabecera o la dirección del tren en la hoja inferior (`LiveTrainBottomSheet`), ya están pre-cargadas.

---

## 1. Concepto y Flujo de Usuario (UX)

### Flujo de Datos Inteligente y Cero Latencia:
```
                                PANEL DE SALIDAS (Activo en Pestaña)
                                                 │
                                                 ▼ (Cada 20-30s)
                                    ┌────────────────────────┐
                                    │   GtfsCacheManager     │ <── Memoria Caché Compartida (TTL 15s)
                                    └────────────┬───────────┘
                                                 │
                             Pulsar FAB          │ (Consumo Instantáneo)
                        ────────────────────────►│
                                                 ▼
                                    ┌────────────────────────┐
                                    │ CercaniasLiveMapDialog │ ──► Carga instantánea de trenes
                                    │ (Mapa Dedicado Limpio) │ ──► Sin llamadas de red redundantes
                                    └────────────────────────┘
```

1. El usuario está visualizando el panel de salidas de una estación. En segundo plano, `GtfsCacheManager` mantiene frescas las posiciones de toda la flota de Valencia.
2. El usuario pulsa el **Botón Flotante (FAB)** en la esquina inferior derecha.
3. Al instante se despliega el mapa dedicado y **pinta los trenes de forma inmediata** con los datos de la caché en memoria.
4. El mapa mantiene el bucle de refresco coordinado con la caché compartida, asegurando que las llamadas de red sigan el mismo ciclo de vida sin duplicarse.
5. Al pulsar un tren, el panel `LiveTrainBottomSheet` muestra de inmediato el número de tren, su origen, destino y minutos de demora extraídos directamente de `LiveVehicleInfo`.

---

## 2. Plan de Trabajo Paso a Paso

### Paso 1: Limpieza del Mapa Multi-Modal Principal
- **`MapViewModel.kt`**:
  - Remover el estado `liveCercaniasVehicles` y suspender el bucle de polling de flota de Renfe en `init` para que no consuma recursos del sistema en segundo plano.
- **`OsmdroidMapView.kt`**:
  - Eliminar el gestor de capas de trenes Cercanías de la pantalla principal.

### Paso 2: Exponer la Caché en `CercaniasViewModel.kt`
- Añadiremos un flujo dedicado a los trenes en vivo en `CercaniasViewModel` que consulte la caché compartida del repositorio:
  ```kotlin
  private val _liveTrains = MutableStateFlow<List<LiveVehicleInfo>>(emptyList())
  val liveTrains = _liveTrains.asStateFlow()
  ```
- Al abrir el mapa, invocamos `loadLiveTrainsFromCache()` para poblar el mapa al instante de manera síncrona.
- Iniciamos un loop de refresco ligero de 20s en segundo plano mientras el mapa esté abierto que invoque a `renfeRepository.getUniqueLiveVehicles()`.

### Paso 3: Crear el Diálogo Autónomo `CercaniasLiveMapDialog.kt`
- Diseñar el diálogo de pantalla completa con un `MapView` exclusivo para Cercanías.
- Pintar las líneas férreas y las estaciones de Cercanías de Valencia.
- Renderizar los trenes en vivo recuperados de `liveTrains` y aplicar la animación suave de movimiento en base a sus coordenadas.
- Al interactuar con el mapa:
  - Estaciones de tren: abren `CercaniasStationBottomSheet`.
  - Trenes en vivo: abren `LiveTrainBottomSheet` cargando la dirección y origen ya almacenados en el objeto de datos.

### Paso 4: Agregar el FAB de Mapa a la Pantalla de Cercanías
- Añadir un `FloatingActionButton` circular de Material 3 con el color identificativo de Cercanías en el archivo `CercaniasScreen.kt`.
- El botón abrirá el mapa instantáneamente activando el diálogo modal.

---

## 3. Arquitectura de Datos Unificada

```
┌────────────────────────────────────────────────────────────────────────┐
│                              CAPA DE DATOS                             │
│                                                                        │
│                       ┌────────────────────────┐                       │
│                       │    GtfsCacheManager    │                       │
│                       │   (Caché con Mutex)    │                       │
│                       └───────────┬────────────┘                       │
└───────────────────────────────────┼────────────────────────────────────┘
                                    │
                                    ▼ (Consultas Locales Sin Redundancia)
┌────────────────────────────────────────────────────────────────────────┐
│                           CAPA DE PRESENTACIÓN                         │
│                                                                        │
│  ┌────────────────────────────────┐    ┌────────────────────────────┐  │
│  │     Panel de Salidas           │    │  CercaniasLiveMapDialog    │  │
│  │  (Consume horarios y demoras)  │    │  (Consume flota de trenes) │  │
│  └────────────────────────────────┘    └────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Beneficios del Enfoque Optimizado

* **Cero Latencia en Apertura**: La pantalla se dibuja con trenes en tiempo real desde el milisegundo uno.
* **Consumo de Datos Eficiente**: Al reusar el gestor de caché con TTL de 15s, evitamos saturar las conexiones móviles del usuario y los servidores de Renfe.
* **Aislamiento Perfecto**: El mapa principal queda 100% desligado de la telemetría de Renfe, recuperando un rendimiento óptimo.
