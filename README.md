# VLC Transit

Esta aplicación es un proyecto personal, creado en un principio para uso propio y desarrollado con la ayuda de **Google AI Studio**. Su objetivo es unificar en una sola plataforma la información de los distintos operadores de transporte público de la provincia de València.

## Características Principales
* **Consulta de tiempos y horarios:** Información programada de todos los operadores locales.
* **Tiempo real:** Seguimiento en vivo de vehículos, retrasos y estimaciones de llegada.
* **Mapa interactivo:** Visualización de paradas, estaciones y trazados en el área metropolitana.
* **Consulta de trayectos:** Planificador de rutas multimodales (bus, metro, tren, bici o a pie).

---

## 1. Desarrollo y Autoría
Esta aplicación ha sido creada y desarrollada de forma independiente como un asistente integral y multimodal de movilidad urbana para el área metropolitana de València. Es un proyecto personal y de carácter no oficial.

## 2. Desvinculación Oficial y Marcas
**Aviso importante:** Esta aplicación es un proyecto independiente de desarrollo de software. NO existe ningún tipo de vinculación, afiliación, patrocinio, asociación comercial ni respaldo oficial con:
* Renfe Operadora (Cercanías Renfe)
* Ferrocarrils de la Generalitat Valenciana (FGV) (Metrovalencia)
* Empresa Municipal de Transportes de València (EMT València) ni el Ajuntament de València
* Valenbisi / JCDecaux España
* Autoritat de Transport Metropolità de València (ATMV / Título SUMA)

Todos los nombres comerciales, marcas registradas, logotipos y denominaciones de líneas, estaciones, paradas o tarifas pertenecen de forma exclusiva a sus respectivos titulares.

## 3. Fuentes de Datos, APIs y Atribución
Esta aplicación funciona gracias a la integración y reutilización de datos abiertos procedentes de plataformas oficiales, servicios públicos e iniciativas comunitarias y colaborativas:

1. **Renfe Operadora & GTFS-RT:** Datos de horarios, paradas, trayectos y posiciones/retrasos de trenes en tiempo real procedentes de su portal de datos abiertos (https://data.renfe.com/) y su servidor público GTFS-RT (https://gtfsrt.renfe.com/).
2. **Ministerio de Transportes y Movilidad Sostenible:** Información integrada de acuerdo con la licencia y condiciones de reutilización del Punto de Acceso Nacional de Información de Transporte Multimodal (NAP) (https://nap.transportes.gob.es/licencia-datos).
3. **EMT València / Ajuntament de València:** Datos de red, líneas, paradas y estimaciones de paso en tiempo real procedentes de la plataforma municipal de datos abiertos (https://opendata.vlci.valencia.es/es/dataset/emt) y del sistema SAE de EMT València.
4. **Geoportal Ajuntament de València (Valenbisi):** Consulta en tiempo real de disponibilidad de bicicletas y anclajes libres en las estaciones de Valenbisi a través del Geoportal municipal (https://geoportal.valencia.es/).
5. **Ferrocarrils de la Generalitat Valenciana (FGV / Metrovalencia):** Trazados cartográficos, accesos a estaciones y geometría de andenes de Metrovalencia derivados de datos públicos del operador.
6. **Open-Meteo:** Previsión meteorológica y datos de clima/probabilidad de lluvia proporcionados por Open-Meteo bajo licencia Creative Commons Reconocimiento 4.0 (CC BY 4.0) (https://open-meteo.com/).
7. **Transitous & MOTIS:** Motor de enrutamiento y cálculo de itinerarios multimodales proporcionado por la red comunitaria de transporte Transitous y el motor MOTIS (https://transitous.org/ - https://motis-project.de/).
8. **OpenStreetMap & Nominatim:** Búsqueda de destinos, geocodificación de direcciones y datos cartográficos base © Colaboradores de OpenStreetMap, disponibles bajo licencia Open Database License (ODbL) (https://www.openstreetmap.org/copyright - https://nominatim.openstreetmap.org/).
9. **Cartografía CARTO:** Teselas de mapas base (CartoDB Voyager y Dark Matter) facilitadas por CARTO (https://carto.com/basemaps/) con datos de OpenStreetMap.
10. **MetroAPI (Metrovalencia):** API comunitaria para la consulta de previsiones en tiempo real, incidencias en andenes y consulta de títulos/viajes de tarjetas desarrollada por Alex Badi (https://docs.metroapi.alexbadi.es/).
11. **EMTValencia-API:** Wrapper y API comunitaria de apoyo para datos de autobuses desarrollada por ElEd0 (https://github.com/ElEd0/EMTValencia-API).

## 4. Exención de Responsabilidad y Datos Offline
* **Carácter orientativo:** La información sobre horarios programados, itinerarios, cálculo de rutas y estimaciones en tiempo real se ofrece «tal cual» (*as is*) con fines puramente informativos. La precisión de los tiempos de llegada, disponibilidad de bicis y avisos de incidencias depende exclusivamente de la disponibilidad y actualización de las APIs públicas de origen.
* **Horarios teóricos locales:** Los horarios programados offline proceden de las bases de datos integradas en la aplicación y pueden sufrir variaciones imprevistas por festividades, cortes por obras, huelgas o eventos especiales.
* **Conexión a red:** El acceso a estimaciones en vivo, clima, rutas dinámicas y mapas interactivos requiere conexión activa a Internet.
* **Limitación de responsabilidad:** Quien ha desarrollado esta aplicación no se hace responsable de transportes perdidos, enlaces no efectuados, retrasos imprevistos, cambios no anunciados de andén/vía o decisiones de viaje adoptadas a partir de la información mostrada.

## 5. Privacidad y Tratamiento de Datos
* **Sin registro de datos personales:** Esta aplicación no recopila, no almacena ni comparte ningún tipo de dato personal, identificador de dispositivo ni registro de búsqueda con servidores externos ni terceros.
* **Geolocalización en el dispositivo:** El acceso a la ubicación GPS se utiliza de manera estrictamente local, instantánea y anónima para calcular rutas desde tu posición, alertar sobre paradas próximas y mostrar los transportes más cercanos. Las coordenadas nunca se envían a servidores de seguimiento ni se guardan en históricos remotos.
* **Almacenamiento local:** Todos tus datos (estaciones o paradas favoritas, tarjetas de transporte añadidas, eventos de calendario y preferencias visuales) se almacenan única y exclusivamente de forma privada en la memoria interna de tu dispositivo.

## 6. Licencias de Software
Esta aplicación utiliza componentes, fuentes y librerías de código abierto (incluyendo osmdroid, OkHttp, Retrofit, Jetpack Compose, Material 3 y Kotlin Coroutines) bajo sus respectivas licencias de software libre (Apache License 2.0 / MIT License).
