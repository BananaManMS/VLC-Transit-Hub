# Viatgem — Design System (`design.md`)

Estándar visual y funcional objetivo para unificar **Viatgem** (Android / Jetpack Compose / Material Design 3).

---

## 1. Identidad visual y criterios anti-genéricos

### Dirección estética
**Viatgem** adopta una identidad de **señalética de transporte público valenciano moderna**:
- **Color de marca propio (Viatgem Red):** basado en el rojo carmesí del icono oficial de la app (`#EC1B4B`), evitando azules genéricos de plantilla.
- **Superficies oscuras neutras (carbón puro):** `#171717` / `#222222` / `#2C2C2C` con bordes sutiles `#333333` — sin mezclar fondos azulados (`#131824`, `#151821`, `#1E222B`, `#232630`).
- **Diferenciación por operador sobre una estructura común:** la pantalla de **Metrovalencia** actúa como plantilla estructural de referencia para los modos de transporte, manteniendo la identidad cromática y las características funcionales de cada operador (Metrovalencia, Rodalies Renfe, EMT, Metrobús, Valenbisi).

### Principios rectores
1. **Señalética antes que decoración:** el color comunica estado o línea de transporte; nunca es adorno gratuito.
2. **Respeto a los logos oficiales:** los logotipos de Metrovalencia, Rodalies, EMT, Metrobús y Valenbisi se muestran sin envolverlos en contenedores circulares artificiales cuando el propio logo ya define su forma.
3. **Semántica estricta de estado:**
   - **Operación normal / OK:** siempre verde semántico (`statusOk`), nunca `primary`.
   - **Aviso / Obras / Atención:** ámbar (`statusWarning`).
   - **Incidencia / Supresión / Error real:** rojo de error (`statusError`).
   - **Búsqueda vacía (sin rutas):** estado informativo neutro, nunca alerta roja de error crítico.
4. **Cero texto crudo de API:** los avisos de Renfe/FGV deben limpiarse de prefijos internos (`#AVISO#`, `#LÍNEA C6#`, bloques en mayúsculas) antes de renderizarse.

### Qué evitar (criterios anti-genéricos)
- ❌ No usar azules genéricos (`#4F8CFF`, `#2563EB`, `#60A5FA`) como color principal de marca o enlaces.
- ❌ No mezclar superficies oscuras azuladas (`#131824`, `#1E222B`, `#232630`) con el carbón neutro (`#171717` / `#222222`).
- ❌ No usar `FontFamily.Monospace` con colores arcoíris para encabezados de secciones en Avisos.
- ❌ No usar caracteres ASCII (`▲` / `▼`) en lugar de iconos vectoriales Material (`ExpandMore` animado).
- ❌ No envolver todos los logos en círculos o cajas redundantes.
- ❌ No competir con múltiples botones sólidos primarios en una misma vista (p. ej. permisos en Onboarding vs botón `Següent`).

---

## 2. Colores (tokens semánticos)

### Marca y acentos (`MaterialTheme.colorScheme`)

| Token | Dark Mode | Light Mode | Uso |
| :--- | :--- | :--- | :--- |
| `primary` | `#F43F5E` | `#EC1B4B` | Acciones primarias, pestaña activa, enlaces, foco |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` | Texto e iconos sobre `primary` |
| `primaryContainer` | `#3D1120` | `#FCE7EE` | Contenedores tonales activos, pills secundarias |
| `onPrimaryContainer` | `#FFE4E9` | `#9F1239` | Texto e iconos sobre `primaryContainer` |
| `secondary` | `#F43F5E` | `#E11D48` | Acento secundario coherente con marca |

### Superficies y texto

| Token | Dark Mode | Light Mode | Uso |
| :--- | :--- | :--- | :--- |
| `background` | `#171717` | `#FAFAFA` | Lienzo base de pantallas y bottom sheets |
| `surface` | `#222222` | `#FFFFFF` | Tarjetas principales, diálogos, modales |
| `surfaceVariant` | `#2C2C2C` | `#F1F5F9` | Controles secundarios, chips inactivos, contadores |
| `outlineVariant` | `#333333` | `#E2E8F0` | Bordes sutiles `1.dp` y divisores `0.8.dp` |
| `onSurface` | `#F8FAFC` | `#0F172A` | Texto principal |
| `onSurfaceVariant` | `#A3A3A3` | `#475569` | Texto secundario, subtítulos, metadatos |

### Estados semánticos (`AppThemeColors`)

| Token | Dark Mode | Light Mode | Uso |
| :--- | :--- | :--- | :--- |
| `statusOk` | `#10B981` | `#059669` | Servicio normal, sin incidencias, permiso concedido |
| `statusOkContainer` | `#102820` | `#ECFDF5` | Fondo de tarjetas "Xarxa sense incidències" |
| `statusWarning` | `#FBBF24` | `#D97706` | Obras, retrasos moderados, servidor no disponible |
| `statusWarningContainer` | `#2A2215` | `#FEF3C7` | Fondo de avisos de advertencia / error temporal |
| `statusError` | `#F87171` | `#DC2626` | Incidencias graves, supresiones, sin conexión |
| `statusErrorContainer` | `#33181A` | `#FEE2E2` | Fondo de tarjetas de incidencia crítica |
| `statusAccessibility` | `#38BDF8` | `#0284C7` | Sección y badges de accesibilidad / ascensores |

### Identidad por operador (preservada por dominio)
- **Metrovalencia:** colores oficiales L1–L10 (`TransitLogoUtils.kt`).
- **Rodalies Renfe:** colores oficiales C1–C6 (`TransitLogoUtils.kt`).
- **EMT València:** `#E53935`.
- **Metrobús:** `#F59E0B`.
- **Valenbisi:** `#38BDF8` (Dark) / `#0284C7` (Light).

---

## 3. Tipografía y escala

- **Títulos y métricas:** `SpaceGroteskFontFamily` (`space_grotesk_w300..w700`).
- **Cuerpo y etiquetas:** `PlusJakartaSansFontFamily` (`plus_jakarta_sans_w400..w800`).
- **Números en directo (`LiveTimerStyle`):** `SpaceGroteskFontFamily`, `ExtraBold`, `fontFeatureSettings = "tnum"`.

| Nivel | Familia | Tamaño | Peso | Uso |
| :--- | :--- | :--- | :--- | :--- |
| `displayLarge` | Space Grotesk | `44.sp` | Bold | Reloj principal en Inicio |
| `headlineSmall` | Space Grotesk | `22.sp` | Medium | Títulos de pantallas destacadas / Onboarding |
| `titleLarge` | Space Grotesk | `20.sp` | Bold | `ScreenHeader`, títulos de modales/sheets |
| `titleMedium` | Space Grotesk | `16.sp` | Medium/SemiBold | Títulos de widgets, filas de ajustes, destinos |
| `titleSmall` | Space Grotesk | `14.sp` | Medium/Bold | Pestañas (`UnifiedTabRow`) |
| `bodyLarge` | Plus Jakarta Sans | `15.sp` | Normal | Detalle completo de noticias y avisos |
| `bodyMedium` | Plus Jakarta Sans | `13.sp` | Normal | Descripciones de tarjetas e incidencias |
| `bodySmall` | Plus Jakarta Sans | `11–12.sp` | Normal | Metadatos, fechas, subtítulos secundarios |
| `labelLarge` | Plus Jakarta Sans | `14.sp` | SemiBold | Botones primarios y acciones |
| `labelMedium` | Plus Jakarta Sans | `12.sp` | Medium/Bold | Encabezados de sección en mayúsculas, badges |
| `labelSmall` | Plus Jakarta Sans | `10–11.sp` | Medium/Bold | Micro-etiquetas de categoría y contadores |

---

## 4. Espaciado, radios y elevación

- **Rejilla base:** múltiplos de `4.dp` / `8.dp`.
- **Margen horizontal de pantalla:** `16.dp` (o `12.dp` en listas dentro de contenedores con padding previo).
- **Separación vertical entre tarjetas:** `10–12.dp` en listas de salidas; `12–16.dp` entre bloques/secciones.
- **Padding interno de tarjeta:** `14.dp` (`UnifiedAppCard`) o `16.dp` (tarjetas de contenido/avisos).
- **Padding inferior con FAB:** mínimo `88.dp + activeTripBottomPadding` para evitar solapamientos.

| Elemento | Radio (`RoundedCornerShape`) | Borde (Dark / Light) | Elevación |
| :--- | :--- | :--- | :--- |
| Tarjetas estándar y avisos | `16.dp` | `1.dp` `#333333` / `#E2E8F0` (50%) | `0.dp` Dark / `1–2.dp` Light |
| Diálogos | `20–24.dp` | `1.dp` `#333333` (sin bordes neón) | `6.dp` |
| Bottom Sheets | `topStart/topEnd = 24.dp` | Sin borde lateral | Nativa M3 |
| Botones principales | `12.dp` | — | `0.dp` |
| Badges de línea / categoría | `6–8.dp` | — | `0.dp` |
| Pills / Contadores | `CircleShape` o `20.dp` | — | `0.dp` |

---

## 5. Catálogo de componentes

### 5.1 `UnifiedAppCard` & Tarjetas de Salida
- Estructura de 3 zonas: `[Badge de línea/operador] · [Destino + metadatos] · [Cuenta atrás tabular]`.
- Tinte sutil del color de línea/operador sobre `surface` (`10–14%` en Dark, `6–8%` en Light) siguiendo el patrón de **Metrovalencia**.

### 5.2 `UnifiedTabRow`
- Acepta `accentColor: Color = MaterialTheme.colorScheme.primary`.
- En **Bus**, adapta el indicador al sub-operador activo (EMT rojo, Metrobús ámbar, Valenbisi azul).

### 5.3 `DashboardBottomNavBar`
- Fondo negro puro `#000000` (Dark) / `#FFFFFF` (Light).
- Indicador activo `#262626` (Dark) / `#FCE7EE` (Light) con icono y texto en `primary`.
- **Oculta en `Ajustos`** para reforzar la jerarquía de pantalla secundaria con botón atrás.

### 5.4 `IncidenciasWidget` (Inicio)
- Usa los logotipos oficiales (`logo_metrovalencia`, `logo_cercanias`) sin contenedores artificiales.
- Muestra badges de líneas afectadas de forma coherente para ambos operadores cuando hay líneas concretas afectadas, o estado verde `statusOk` cuando no hay incidencias.

### 5.5 Pestañas de Avisos (Metrovalencia y Rodalies)
- **Sin barras de filtros rápidos:** navegación directa por secciones claras con `PullToRefreshBox`.
- **Banner compacto de estado arriba:** resume al instante si la red opera normal (`statusOk`) o el número de incidencias activas (`statusError`), acompañado en Rodalies por la marca de actualización de Renfe.
- **Cabeceras de sección unificadas:** `PlusJakartaSans`, `12.sp`, `Bold`, mayúsculas con `letterSpacing = 0.8.sp`, texto en `onSurfaceVariant` y pill de contador neutro/semántico (sin `FontFamily.Monospace` ni arcoíris).
- **Tarjetas de aviso unificadas:** `16.dp`, fondo `surface` (`#222222` / `#FFFFFF`) con borde `1.dp` `#333333` (o `statusErrorContainer` + borde `statusError @ 35%` para incidencias urgentes).
- **Limpieza de texto de Renfe:** eliminar prefijos `#AVISO#`, `#LÍNEA CX#` y truncar vistas previas largas (`maxLines = 4`) con indicación de lectura completa en el bottom sheet.
- **Sección Accesibilidad plegable:** icono `Icons.Default.Accessible` + contador + chevron vectorial `Icons.Default.ExpandMore` con rotación animada (sin `▲`/`▼`).
- **Detalle en BottomSheet:** tanto avisos especiales de Metro como avisos de Rodalies y noticias abren un `ModalBottomSheet` consistente.

### 5.6 Componentes validados (no alterar arquitectura ni lógica)
- **BottomSheet `Capes del mapa`:** mantener diseño, logos oficiales y anillos de estado activo/inactivo.
- **Logos de operadores en `Inici`:** mantener sin envoltorios circulares artificiales.
- **Chips de líneas de Bus (`UnifiedLineFilterRow`):** mantener comportamiento actual (todas activas por defecto en color de operador; seleccionadas con borde blanco; excluidas atenuadas).
- **Marcadores y zoom del mapa:** mantener ocultación al alejar zoom y clustering exclusivo de bus.

---

## 6. Layout y navegación

- **Pestañas raíz (5):** `Inici`, `Mapa`, `Bus`, `Metro`, `Rodalies`.
- **Pantallas secundarias sin barra inferior:** `Ajustos`, `Planificador Multimodal`, `Plànols de xarxa`, `Onboarding`.
- **FAB de mapa en directo (Rodalies):** visible **únicamente** en la pestaña `Eixides` (página 0), oculto en `Avisos` (página 1), con `88.dp` de margen inferior en la lista de salidas.
- **Áreas seguras:** `enableEdgeToEdge()`, padding dinámico para `activeTripBottomPadding` en todas las listas desplazables.

---

## 7. Iconografía

- Material Symbols / Material Icons (`Icons.Default` para activos/acciones principales, `Icons.Outlined` para inactivos en navegación, `Icons.AutoMirrored` para flechas direccionales).
- Tamaños estándar: `14–16.dp` en badges/inline, `18–20.dp` en filas y botones, `24.dp` en navegación/cabeceras.
- Alineación vertical centrada (`Alignment.CenterVertically`) con separación `6–12.dp` respecto al texto.

---

## 8. Reglas estrictas (`Do's & Don'ts`)

- ✅ **DO:** Usa siempre `MaterialTheme.colorScheme` y `AppThemeColors` para fondos, textos, bordes y estados.
- ✅ **DO:** Usa `statusOk` (verde) para tarjetas de servicio normal; nunca `primary`.
- ✅ **DO:** Redondea temperaturas horarias a enteros (`17°`) y evita duplicaciones gramaticales de fecha en valenciano (`diumenge, 22 de novembre de 2026`).
- ✅ **DO:** Respeta la paridad de idiomas `VAL` / `ES` en mensajes de estado vacío, errores y botones.
- ❌ **DON'T:** No hardcodees colores azulados (`#4F8CFF`, `#2563EB`, `#131824`, `#1E222B`, `#232630`, `#262C3A`).
- ❌ **DON'T:** No añadas barras de filtros rápidos en las pestañas de Avisos.
- ❌ **DON'T:** No muestres el FAB de mapa en directo sobre la pestaña de Avisos.
- ❌ **DON'T:** No uses iconos rojos de error crítico para estados vacíos de búsqueda.

---

## 9. Prioridades de saneamiento

- **P0 (Base de marca y errores visibles):** Tema Viatgem Red en `Theme.kt`, tokens semánticos `AppThemeColors`, ocultar bottom bar en `Ajustos`, purgar fondos azulados en Ajustes/Diálogos, corregir fecha `"de de"` en valenciano, temperatura entera en tiempo, botón `Connectar` sin truncar, estado vacío del planificador localizado y neutro, y modal de anuncios sin bordes neón.
- **P1 (Avisos e Incidencias):** Rediseño unificado de `MetroAlertsTab` y `CercaniasAlertsTab` (sin filtros rápidos), tarjetas de estado normal en verde `statusOk`, saneamiento de textos crudos de Renfe, `PullToRefresh` en Metro, bottom sheet para avisos especiales de Metro, ocultar FAB de Rodalies en Avisos + margen inferior en Salidas, y logos oficiales en `IncidenciasWidget`.
- **P2 (Unificación de modos de transporte):** Alinear Salidas de Rodalies, EMT, Metrobús y Valenbisi con la referencia de Metrovalencia conservando el color e identidad de cada operador.
- **P3 (Pulido de Onboarding):** Migrar Onboarding a Viatgem Red y carbón neutro, eliminar hueco vertical muerto en Pasos 1–2 y rebajar botones de permisos a tonales.
