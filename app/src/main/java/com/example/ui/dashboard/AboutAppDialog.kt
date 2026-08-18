package com.example.ui.dashboard

import com.example.ui.components.LinkifiedText
import com.example.util.LocationUtils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.model.MetroStation
import com.example.data.model.ValenciaMetroData
import com.example.ui.metro.MetroViewModel
import com.example.ui.metro.MetroStationSelectionDialog
import com.example.ui.metro.computeMetroSearchScore
import com.example.ui.theme.ScreenHeader
import com.example.ui.theme.UnifiedAppCard
import kotlinx.coroutines.launch
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppDialog(
    isDarkMode: Boolean,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit
) {
    val texts = remember(appLanguage) { AppTexts.get(appLanguage) }
    val textColor = if (isDarkMode) Color(0xFFF2F4F8) else Color(0xFF1C1B1F)
    val subtextColor = if (isDarkMode) Color(0xFF8791A6) else Color(0xFF49454F)
    val accentColor = if (isDarkMode) Color(0xFF4F8CFF) else MaterialTheme.colorScheme.primary
    val borderColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE2E8F0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp)),
            color = if (isDarkMode) Color(0xFF131824) else MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header with Info Icon and Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = texts.aboutTitle,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = texts.aboutSubtitle,
                                fontSize = 12.sp,
                                color = subtextColor
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (appLanguage == AppLanguage.ES) "Cerrar" else "Tancar",
                            tint = subtextColor
                        )
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section 1: Desarrollo y Autoría
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = texts.aboutDevTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = texts.aboutDevBody,
                            fontSize = 13.sp,
                            color = textColor,
                            lineHeight = 18.sp
                        )
                    }

                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f))

                    // Section 2: Desvinculación Oficial y Marcas
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = texts.aboutDisclaimerTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = texts.aboutDisclaimerBody,
                            fontSize = 13.sp,
                            color = textColor,
                            lineHeight = 18.sp
                        )
                    }

                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f))

                    // Section 3: Fuentes de Datos, APIs y Atribución
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = texts.aboutSourcesTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = texts.aboutSourcesBody,
                            fontSize = 13.sp,
                            color = textColor,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val attributions = if (appLanguage == AppLanguage.ES) {
                            listOf(
                                "Renfe Operadora & GTFS-RT" to "Datos de horarios, paradas, trayectos y posiciones/retrasos de trenes en tiempo real desde data.renfe.com y gtfsrt.renfe.com.",
                                "Ministerio de Transportes y Movilidad Sostenible" to "Información integrada conforme al Punto de Acceso Nacional de Información de Transporte (NAP) (https://nap.transportes.gob.es/licencia-datos).",
                                "EMT València / Ajuntament de València" to "Datos de red, líneas, paradas y estimaciones en tiempo real de la plataforma municipal de datos abiertos (https://opendata.vlci.valencia.es/es/dataset/emt).",
                                "Geoportal Ajuntament de València (Valenbisi)" to "Disponibilidad en tiempo real de estaciones y anclajes de bicicletas públicas Valenbisi (https://geoportal.valencia.es/).",
                                "Ferrocarrils de la Generalitat Valenciana (FGV)" to "Trazado cartográfico, accesos y geometría de estaciones de Metrovalencia derivados de datos públicos del operador.",
                                "Open-Meteo" to "Previsión meteorológica y datos de clima bajo licencia Creative Commons BY 4.0 (https://open-meteo.com/).",
                                "Transitous & MOTIS" to "Motor de enrutamiento multimodal proporcionado por la red comunitaria Transitous y MOTIS (https://transitous.org/ - https://motis-project.de/).",
                                "OpenStreetMap & Nominatim" to "Búsqueda de destinos y geocodificación © Colaboradores de OpenStreetMap, bajo licencia ODbL (https://www.openstreetmap.org/copyright).",
                                "Cartografía CARTO" to "Teselas de mapas base CartoDB Voyager y Dark Matter facilitadas por CARTO (https://carto.com/basemaps/).",
                                "MetroAPI (Metrovalencia)" to "API comunitaria para estimaciones en tiempo real, incidencias y tarjetas desarrollada por Alex Badi (https://docs.metroapi.alexbadi.es/).",
                                "EMTValencia-API" to "Wrapper y API comunitaria de apoyo para datos de EMT desarrollado por ElEd0 (https://github.com/ElEd0/EMTValencia-API)."
                            )
                        } else {
                            listOf(
                                "Renfe Operadora & GTFS-RT" to "Dades d'horaris, parades, trajectes i posicions/retards de trens en temps real des de data.renfe.com i gtfsrt.renfe.com.",
                                "Ministeri de Transports i Mobilitat Sostenible" to "Informació integrada conforme al Punt d'Accés Nacional d'Informació de Transport (NAP) (https://nap.transportes.gob.es/licencia-datos).",
                                "EMT València / Ajuntament de València" to "Dades de xarxa, línies, parades i estimacions en temps real de la plataforma municipal de dades obertes (https://opendata.vlci.valencia.es/es/dataset/emt).",
                                "Geoportal Ajuntament de València (Valenbisi)" to "Disponibilitat en temps real d'estacions i ancoratges de bicicletes públiques Valenbisi (https://geoportal.valencia.es/).",
                                "Ferrocarrils de la Generalitat Valenciana (FGV)" to "Tratçat cartogràfic, accessos i geometria d'estacions de Metrovalencia derivats de dades públiques de l'operador.",
                                "Open-Meteo" to "Previsió meteorològica i dades de clima sota llicència Creative Commons BY 4.0 (https://open-meteo.com/).",
                                "Transitous & MOTIS" to "Motor d'enrutament multimodal proporcionat per la xarxa comunitària Transitous i MOTIS (https://transitous.org/ - https://motis-project.de/).",
                                "OpenStreetMap & Nominatim" to "Cercador de destinacions i geocodificació © Col·laboradors d'OpenStreetMap, sota llicència ODbL (https://www.openstreetmap.org/copyright).",
                                "Cartografia CARTO" to "Tessel·les de mapes base CartoDB Voyager i Dark Matter facilitades per CARTO (https://carto.com/basemaps/).",
                                "MetroAPI (Metrovalencia)" to "API comunitària per a previsions en temps real, incidències i targetes desenvolupada per Alex Badi (https://docs.metroapi.alexbadi.es/).",
                                "EMTValencia-API" to "Wrapper i API comunitària de suport per a dades d'EMT desenvolupat per ElEd0 (https://github.com/ElEd0/EMTValencia-API)."
                            )
                        }

                        attributions.forEachIndexed { index, pair ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "${index + 1}. ${pair.first}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                LinkifiedText(
                                    text = pair.second,
                                    textColor = subtextColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f))

                    // Section 4: Exención de Responsabilidad y Datos Offline
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = texts.aboutExemptionTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val exemptionItems = texts.aboutExemptionItems

                        exemptionItems.forEach { pair ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "• ${pair.first}:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Text(
                                    text = pair.second,
                                    fontSize = 12.sp,
                                    color = subtextColor,
                                    modifier = Modifier.padding(start = 12.dp),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f))

                    // Section 5: Privacidad y Tratamiento de Datos
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = texts.aboutPrivacyTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val privacyItems = texts.aboutPrivacyItems

                        privacyItems.forEach { pair ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "• ${pair.first}:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Text(
                                    text = pair.second,
                                    fontSize = 12.sp,
                                    color = subtextColor,
                                    modifier = Modifier.padding(start = 12.dp),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f))

                    // Section 6: Licencias
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = texts.aboutLicensesTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = texts.aboutLicensesBody,
                            fontSize = 13.sp,
                            color = textColor,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                    ) {
                        Text(texts.aboutUnderstood, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
