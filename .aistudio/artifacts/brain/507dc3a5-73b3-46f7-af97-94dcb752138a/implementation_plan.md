# Seguimiento Rápido de Metro y Notificaciones en Vivo (Pin & Alerta de Bajada)

Sistema de seguimiento ligero e instantáneo para viajeros habituales de Metrovalencia. Permite monitorizar un tren en tiempo real o fijar una alerta de bajada en destino con una pulsación prolongada, sin configurar rutas ni transbordos, mediante notificaciones continuas silenciosas y soporte para pastillas activas en la barra de estado de Android 16+ (Rich Ongoing Notifications).

---

## Decisiones Críticas y Confirmadas

> [!IMPORTANT]
> El diseño se ha refinado para maximizar la simplicidad (cero texto innecesario) y evitar truncamientos en la barra de estado del sistema operativo.

* **Activación por Pulsación Larga (Cero Fricción)**: Al mantener pulsada cualquier tarjeta de salida de metro (tiempo real o programada), se despliega una hoja inferior minimalista con un único selector vertical de estaciones.
* **Lista Unificada de Destino de 1 Toque**: 
  * Primer elemento: `📌 Salida en andén actual` (ej. *Xàtiva · en 4 min*).
  * Siguientes elementos: Las paradas directas del tren ordenadas cronológicamente (ej. *Colón · 6 min*, *Facultats · 11 min*).
* **Diseño en Dos Capas para Android 16+ (Evita Truncado de Texto)**:
  * *Pastilla colapsada (Status Bar Chip)*: Micro-texto ultra-compacto de máx. 5–7 caracteres (`[ 🚇 4 min ]` o `[ L3 · 8m ]`).
  * *Notificación expandida (Cortina del sistema)*: Detalle completo con línea, estación de destino, hora prevista y botón «Descartar».
* **Alerta Háptica de Bajada (Independiente de GPS)**: Durante el viaje, la notificación permanece en silencio absoluto; solo emite una vibración distintiva (doble pulso) al llegar a la penúltima estación o 1–2 minutos antes del destino. Funciona 100% bajo tierra gracias a la sincronización con el horario oficial y el radar en vivo.
* **Transición Automática Programado $\rightarrow$ Tiempo Real**: Si el tren seleccionado sale en más de 15 minutos, la notificación arranca con la cuenta atrás programada oficial y conmuta suavemente a telemetría en tiempo real en cuanto el tren entra en el radar de FGV.

---

## 1. Visión General y Concepto

### ¿Qué soluciona?
Los viajeros diarios que conocen perfectamente su recorrido no necesitan instrucciones paso a paso, mapas ni alertas de transbordo. Solo necesitan resolver dos preguntas básicas:
1. *¿Cuántos minutos le faltan a mi tren para llegar a mi estación mientras termino el café o bajo las escaleras?*
2. *¿Cuándo tengo que levantar la vista del móvil para no pasarme de mi parada mientras escucho música o leo?*

### Público Objetivo
El usuario habitual de Metrovalencia (*commuter*) que busca la máxima inmediatez con el mínimo número de toques posible.

---

## 2. Experiencia de Usuario y Flujos

```
┌────────────────────────────────────────────────────────┐
│ PASO 1: En la pantalla de estación                     │
│ Mantener pulsado un tren (Long Click con haptic tick) │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│ PASO 2: Hoja inferior ultra-simple                     │
│                                                        │
│  🚇 L3 Rafelbunyol                                     │
│  Selecciona aviso:                                     │
│  ────────────────────────────────────────────────────  │
│  📌 Aquí (Xàtiva)              En 4 min               │
│  ○  Colón                      En 6 min               │
│  ○  Alameda                    En 8 min               │
│  ○  Facultats                  En 11 min              │
│  ○  Benimaclet                 En 13 min              │
└──────────────────────────┬─────────────────────────────┘
                           │
             ┌─────────────┴─────────────┐
             ▼                           ▼
      [ Elige "Aquí" ]            [ Elige Destino ]
             │                           │
             ▼                           ▼
┌─────────────────────────┐ ┌─────────────────────────┐
│ NOTIFICACIÓN SALIDA     │ │ NOTIFICACIÓN EN RUTA    │
│ Chip: [ 🚇 4 min ]      │ │ Chip: [ L3 · 11m ]      │
│ Cortina: Pasa por       │ │ Cortina: Hacia          │
│ Xàtiva en 4 min         │ │ Facultats (en 11 min)   │
│                         │ │                         │
│ Al partir el tren:      │ │ En penúltima estación:  │
│ Se autodestruye sola.   │ │ 📳 "Próxima Facultats"   │
└─────────────────────────┘ └─────────────────────────┘
```

### Comportamiento de Notificaciones
1. **Canal Silencioso Continuo (`IMPORTANCE_LOW` / `Ongoing`)**: Sin alertas audibles ni vibraciones cada vez que se actualiza el minutero.
2. **Canal de Alarma de Bajada (`IMPORTANCE_HIGH`)**: Se dispara exclusivamente al entrar en el tramo de aviso de la estación de destino.
3. **Acción de Cancelación Inmediata**: Botón «Descartar» directamente en la notificación para apagar el seguimiento en cualquier momento.

---

## 3. Arquitectura Técnica y Estrategia de Datos

```
┌─────────────────────────────────────────────────────────────────┐
│                          CAPA UI                                │
│  MetroArrivalsCard / MetroTimetableItem                         │
│   └── Modifier.combinedClickable(onLongClick = { showSheet() }) │
│         └── QuickTrainTrackerBottomSheet                        │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    GESTIÓN DE SEGUIMIENTO                       │
│  QuickVehicleTrackerManager (Singleton / StateFlow)             │
│   ├── activeTrackingState: StateFlow<QuickTrackedVehicle?>      │
│   ├── startTracking(vehicle, targetStop)                        │
│   └── stopTracking()                                            │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                  SERVICIO EN SEGUNDO PLANO                      │
│  QuickVehicleTrackingService (ForegroundService ligero)         │
│   ├── Ticker local cada segundo (interpolación de cuenta atrás) │
│   ├── Sincronización API cada 30-45s con MetroRepository        │
│   ├── Detección de parada previa (cálculo de tiempo/estación)   │
│   └── Actualización de NotificationCompat.Builder               │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                   SISTEMA DE NOTIFICACIONES                     │
│  - Android 16+: Notification.ProgressStyle / Ongoing Chip       │
│  - Android 8-15: NotificationCompat.Builder (Ongoing, Low)      │
│  - Alarma en destino: VibratorHelper (patrón doble pulso)      │
└─────────────────────────────────────────────────────────────────┘
```

### Componentes a Crear / Integrar:
1. **`QuickTrackedVehicle` (Modelo de Datos)**:
   * `lineId`: Identificador de línea (ej. "3").
   * `destinationName`: Nombre de cabecera (ej. "Rafelbunyol").
   * `originStationId` y `originStationName`: Estación donde se fijó el tren.
   * `targetStationId` y `targetStationName`: Estación seleccionada (`null` si solo se sigue la salida).
   * `scheduledDepartureEpochMs` y `liveMinutesRemaining`: Tiempos calculados en zona `Europe/Madrid`.
   * `downstreamStops`: Lista de paradas intermedias con sus desfases temporales.
2. **`QuickVehicleTrackerManager`**:
   * Coordina el ciclo de vida del seguimiento. Si se fija un nuevo tren, cancela limpiamente el anterior.
3. **`QuickVehicleTrackingService`**:
   * Foreground Service de bajo impacto que mantiene el proceso activo cuando el usuario bloquea el móvil o sale de la app.
4. **`QuickTrainTrackerBottomSheet`**:
   * Composable estilizado con Material Design 3, bordes redondeados, pastilla con el color oficial de la línea de Metrovalencia y lista de paradas táctiles con altura mínima accesible de 48dp.

---

## 4. Verificación y Pruebas

1. **Pruebas de Cálculo Temporal y Zona Horaria**:
   * Verificar que la cuenta atrás de la estación seleccionada utiliza siempre la hora oficial de España (`Europe/Madrid`).
2. **Pruebas de Transición Automática**:
   * Simular el cambio de estado de espera en andén a trayecto en marcha cuando el tiempo en origen llega a 0 min.
3. **Pruebas de Alarma de Bajada**:
   * Verificar que el evento de vibración se activa únicamente en la estación inmediatamente anterior a la de destino.
4. **Compatibilidad Visual de Notificaciones**:
   * Validar que los textos del chip colapsado no superen los 7 caracteres en Android 16+.
   * Validar el funcionamiento del botón de descartar y la autodestrucción tras completar el viaje.
