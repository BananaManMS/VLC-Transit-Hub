# Plan de Saneamiento y Corrección de Referencias

Plan detallado para eliminar llamadas inventadas, resolver clases duplicadas y corregir referencias rotas en el código de transporte de Valencia, preservando intactas las llamadas a APIs reales y la persistencia local.

### User Review & Critical Decisions

> [!IMPORTANT]
> Confirmaciones obtenidas para la ejecución de este plan:
> - **Llamadas inventadas (`deleteAllStations`, `insertAll`, etc.)**: Se eliminarán directamente en lugar de inventar métodos vacíos o tocar la base de datos Room, restaurando el comportamiento original.
> - **Clases / Objetos duplicados (`AccessibilityNoticeFormatter`, `PenultimateStopArrivalMatcher`)**: Se comparará el contenido de ambas implementaciones antes de eliminar los duplicados para no perder ninguna lógica útil.
> - **Metodología de ejecución**: Se procederá por lotes organizados por tipo de error, ejecutando una verificación de compilación tras cada lote para evitar roturas en cascada.
> - **Blindaje de APIs y Datos Estáticos**: Cero modificaciones en `MetrobusRepository`, `EmtRepository`, servicios de red o GeoJSON/JSON locales.

---

### 1. Overview & Core Concept

- **Objetivo**: Sanear el código fuente de Kotlin para que vuelva a compilar con éxito (`BUILD SUCCESSFUL`) sin introducir métodos ficticios, sin vaciar modelos y sin romper la integración existente entre el mapa (osmdroid), las APIs en tiempo real y los datos en assets.
- **Enfoque**: Corrección quirúrgica y conservadora. En lugar de adaptar el resto de la app a errores generados por la IA, se limpian las llamadas espurias que nunca debieron existir.

---

### 2. Metodología por Lotes (Batch by Error Type)

```
┌─────────────────────────────────────────────────────────────┐
│                       LOTE 1                                │
│       Comparación y Limpieza de Clases Duplicadas           │
│  • AccessibilityNoticeFormatter  • PenultimateStopMatcher   │
└──────────────────────────────┬──────────────────────────────┘
                               │ Verificación Gradle
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                       LOTE 2                                │
│        Eliminación de Llamadas Inventadas en ViewModel       │
│  • MetroViewModel (eliminar deleteAllStations, insertAll)   │
└──────────────────────────────┬──────────────────────────────┘
                               │ Verificación Gradle
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                       LOTE 3                                │
│    Alineación de Referencias a Modelos Reales Existentes    │
│  • MetroStationHeader & SelectedStationInfoCard             │
│    (conectar con propiedades reales: getLineInfo, etc.)     │
│  • OsmdroidMapView (llamada a sync manager real)            │
└──────────────────────────────┬──────────────────────────────┘
                               │ Verificación Final
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                       ESTADO SANO                           │
│   Compilación Verde + APK actualizado en Preview            │
└─────────────────────────────────────────────────────────────┘
```

---

### 3. Detalle de los Lotes de Trabajo

#### Lote 1: Comparar y Desduplicar Clases Repetidas
* **`AccessibilityNoticeFormatter`**:
  * Comparar la versión aislada en `util/AccessibilityNoticeFormatter.kt` frente a la que quedó incrustada al final de `util/StationAccessibilityHelper.kt`.
  * Mantener la versión más completa y eliminar la duplicada para resolver el error `Redeclaration`.
* **`PenultimateStopArrivalMatcher`**:
  * Comparar la versión en `util/PenultimateStopArrivalMatcher.kt` con la incrustada al final de `util/BoardedDriftReconciler.kt`.
  * Retirar la redeclaración redundante preservando el objeto único.
* **Verificación**: Comprobar que desaparecen los 4 errores de `Redeclaration`.

#### Lote 2: Limpieza de Métodos Inventados en ViewModels
* **`MetroViewModel.kt`**:
  * Revisar líneas 241-245: eliminar la llamada a `metroStationDao.deleteAllStations()` y `insertAll()`.
  * Revisar línea 265: eliminar el parámetro inventado `description` que no existe en el constructor original de la entidad.
  * Mantener inalterado todo el flujo de carga reactivo (`StateFlow`, consultas a Room existentes y llamadas a servicios).
* **Verificación**: Comprobar que el ViewModel queda libre de llamadas ficticias.

#### Lote 3: Alineación de Referencias a Modelos Reales
* **`MetroStationHeader.kt` y `SelectedStationInfoCard.kt`**:
  * Verificar en `MetroModels.kt` qué método existe realmente para obtener la información de una línea (por ejemplo `ValenciaMetroData.getLineInfo(lineId)` en lugar del inventado `getLine(lineId)`).
  * Conectar las propiedades reales del modelo (como `lineColor` o el enum correspondiente) sin crear capas intermedias vacías.
* **`OsmdroidMapView.kt`**:
  * Revisar la llamada a `syncIfNeeded`: comprobar si el gestor de sincronización real tiene `sync()` o si la llamada no es requerida en el renderizado del mapa.
* **Verificación**: Ejecutar `compile_applet` para confirmar compilación limpia y despliegue del nuevo APK sin fallos.

---

### 4. Criterios de Éxito y Seguridad
1. **Cero regresiones en red**: No se tocan URLs, endpoints de la EMT ni parseos de GeoJSON de Metrovalencia/Cercanías.
2. **Cero métodos huecos**: No se añadirán stubs con `return emptyList()` ni implementaciones vacías.
3. **Compilación exitosa**: `gradle compileDebugKotlin` finaliza con código 0.
4. **Actualización de la preview**: El emulador pasa a ejecutar el código fuente real saneado.
