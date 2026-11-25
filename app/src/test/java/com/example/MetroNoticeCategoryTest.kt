package com.example

import com.example.ui.metro.MetroNoticeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetroNoticeCategoryTest {

    @Test
    fun `fromRaw recognizes circulation, accented, and Valencian category variants`() {
        assertEquals(MetroNoticeCategory.AVISO, MetroNoticeCategory.fromRaw("circulacion"))
        assertEquals(MetroNoticeCategory.AVISO, MetroNoticeCategory.fromRaw("Circulación"))
        assertEquals(MetroNoticeCategory.AVISO, MetroNoticeCategory.fromRaw("aviso_circulacion"))
        assertEquals(MetroNoticeCategory.AVISO, MetroNoticeCategory.fromRaw("avís"))
        assertEquals(MetroNoticeCategory.AVISO, MetroNoticeCategory.fromRaw("prioritario"))
        assertEquals(MetroNoticeCategory.INCIDENCIA, MetroNoticeCategory.fromRaw("incidència"))
        assertEquals(MetroNoticeCategory.INCIDENCIA, MetroNoticeCategory.fromRaw("avería"))
        assertEquals(MetroNoticeCategory.ALERTA_METEOROLOGICA, MetroNoticeCategory.fromRaw("aviso_meteorológico"))
        assertEquals(MetroNoticeCategory.OBRAS, MetroNoticeCategory.fromRaw("obras_obras"))
        assertEquals(MetroNoticeCategory.ACCESIBILIDAD, MetroNoticeCategory.fromRaw("accesibilidad"))
    }

    @Test
    fun `priority feed circulation notices tagged as otro are promoted to network incidents`() {
        val cat1 = MetroNoticeCategory.resolveEffectiveCategory(
            category = "otro",
            categoryName = "Información general",
            title = "Circulación interrumpida entre Empalme y Bétera",
            description = "Por avería técnica el servicio permanece interrumpido.",
            isFromPriorityFeed = true,
            hasAffectedLines = true
        )
        assertEquals(MetroNoticeCategory.INCIDENCIA, cat1)
        assertTrue(cat1.isCirculationImpact)

        val cat2 = MetroNoticeCategory.resolveEffectiveCategory(
            category = "otro",
            categoryName = "Otro",
            title = "Retrasos en Línea 3",
            description = "Los trenes circulan con demoras de 10 minutos.",
            isFromPriorityFeed = true,
            hasAffectedLines = false
        )
        assertEquals(MetroNoticeCategory.AVISO, cat2)
        assertTrue(cat2.isCirculationImpact)

        val cat3 = MetroNoticeCategory.resolveEffectiveCategory(
            category = "otro",
            categoryName = "Aviso de circulación",
            title = "Afección en L4",
            description = "Los tranvías finalizan recorrido en Pont de Fusta.",
            isFromPriorityFeed = true,
            hasAffectedLines = true
        )
        assertEquals(MetroNoticeCategory.AVISO, cat3)
        assertTrue(cat3.isCirculationImpact)
    }

    @Test
    fun `pure weather advisories, promotions, and customer service info stay out of active incidents`() {
        assertFalse(
            MetroNoticeCategory.isRealCirculationIncident(
                category = "otro",
                title = "Aviso naranja por calor",
                description = "Altas temperaturas en Valencia, recuerde hidratarse.",
                isFromPriorityFeed = true,
                hasAffectedLines = false
            )
        )
        assertEquals(
            MetroNoticeCategory.ALERTA_METEOROLOGICA,
            MetroNoticeCategory.resolveEffectiveCategory(
                category = "otro",
                title = "Ola de calor",
                description = "Temperaturas elevadas hoy.",
                isFromPriorityFeed = true
            )
        )
        assertFalse(
            MetroNoticeCategory.isRealCirculationIncident(
                category = "otro",
                title = "Horario de atención al cliente",
                description = "El Punt del Client abrirá de 9 a 14h.",
                isFromPriorityFeed = true,
                hasAffectedLines = false
            )
        )
        assertFalse(
            MetroNoticeCategory.isRealCirculationIncident(
                category = "promocion",
                title = "Descuento en abonos",
                description = "Descuento del 50% en títulos SUMA.",
                isFromPriorityFeed = true,
                hasAffectedLines = false
            )
        )
        assertFalse(
            MetroNoticeCategory.isRealCirculationIncident(
                category = "otro",
                title = "Ascensor fuera de servicio en Colón",
                description = "El ascensor del vestíbulo norte no funciona.",
                isFromPriorityFeed = true,
                hasAffectedLines = true
            )
        )
    }
}
