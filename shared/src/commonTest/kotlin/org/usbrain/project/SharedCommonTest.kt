package org.usbrain.project

import kotlinx.datetime.LocalDate
import org.usbrain.project.data.ApiJson
import org.usbrain.project.data.BootstrapDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Las reglas de negocio (duplicados por día, línea base fija al cerrarla, permisos) viven
 * en el servidor y se prueban en backend/tests/app_e2e.py. Aquí se prueba lo que sigue
 * siendo lógica de la app: leer el JSON del servidor y calcular las series de las gráficas.
 */
class SharedCommonTest {

    // Recorte real del GET /v1/app/bootstrap (incluye los null que envía Oracle para listas vacías).
    private val bootstrapJson = """
    {
      "today": "2026-10-17",
      "user": {"id": "2", "email": "e.patino@usb.edu.co", "full_name": "José E. Patiño", "role": "THERAPIST"},
      "patients": [{
        "id": "3", "name": "Ana Torres", "email": "ana.torres@usbbog.edu.co", "age": 20,
        "program": "Psicología · 4.º sem", "color": 4286277314, "condition": "Ansiedad social",
        "therapist_name": "José E. Patiño",
        "diagnoses": [{"code": "F40.1", "label": "Fobia social"}],
        "case": {"id": "1", "title": "Caso de ansiedad social", "behaviors": [{
          "id": "10", "case_id": "1", "name": "Episodios de ansiedad en clase",
          "operational_definition": "Episodios observables.", "type": "DESADAPTATIVA",
          "baseline_open": false, "baseline_closed_on": "4/9/2026", "function_id": "fn-escape",
          "replacement_behavior_id": null, "baseline_dimensions": ["FRECUENCIA"],
          "baseline_entries": [
            {"id": "r1", "task_id": "20", "date": "2026-09-01", "value": 5, "note": "", "dimension": "FRECUENCIA", "observation_minutes": 45},
            {"id": "r2", "task_id": "20", "date": "2026-09-02", "value": 6, "note": "", "dimension": "FRECUENCIA", "observation_minutes": 45}
          ],
          "tasks": [
            {"id": "20", "behavior_id": "10", "title": "Línea base", "instructions": "", "task_type": "REGISTRO_AUTONOMO",
             "dimension": "FRECUENCIA", "metric_unit": null, "widget_id": null, "assigned_date": "1/9/2026",
             "is_recurring": false, "reminder_active": false, "phase": "LINEA_BASE", "status": "COMPLETADA",
             "entries": [
               {"date": "2026-09-01", "value": 5, "note": "", "phase": "LINEA_BASE", "observation_minutes": 45},
               {"date": "2026-09-02", "value": 6, "note": "", "phase": "LINEA_BASE", "observation_minutes": 45}
             ], "widget_entries": null},
            {"id": "21", "behavior_id": "10", "title": "Intervención", "instructions": "Registra.", "task_type": "REGISTRO_AUTONOMO",
             "dimension": "FRECUENCIA", "assigned_date": "Hoy", "is_recurring": true, "frequency": "Diaria",
             "reminder_active": true, "phase": "INTERVENCION", "status": "PENDIENTE",
             "entries": [{"date": "2026-09-05", "value": 3, "note": "Tarde", "phase": "INTERVENCION", "observation_minutes": 60}],
             "widget_entries": null},
            {"id": "22", "behavior_id": "10", "title": "Emociones", "task_type": "MEDICION_CONFIGURABLE",
             "dimension": "FRECUENCIA", "widget_id": "w-seleccion", "phase": "INTERVENCION", "status": "PENDIENTE",
             "entries": null,
             "widget_entries": [{"id": "we1", "widget_id": "w-seleccion", "date": "2026-09-07", "time": "18:30",
                                 "selected_options": ["Ansiedad", "Evitación"], "note": null}]}
          ]
        }]}
      }],
      "catalogs": {
        "widgets": [
          {"id": "w-seleccion", "name": "Síntomas", "value_kind": "MULTI_SELECT", "unit": null, "min_value": null,
           "max_value": null, "options": ["Ansiedad", "Evitación"], "description": null, "is_preset": true},
          {"id": "w-texto", "name": "Observación", "value_kind": "TEXT", "options": null, "is_preset": true}
        ],
        "functions": [{"id": "fn-no-aplica", "label": "No aplica"}],
        "cie10": [{"code": "F40.1", "label": "Fobia social"}]
      }
    }
    """.trimIndent()

    private fun patients(): List<Patient> {
        val dto = ApiJson.decodeFromString(BootstrapDto.serializer(), bootstrapJson)
        return dto.toPatients(dto.catalogs.widgets.map { it.toWidgetConfig() })
    }

    @Test
    fun bootstrapBuildsTheCaseTree() {
        val ana = patients().single()
        assertEquals("José E. Patiño", ana.therapistName)
        assertEquals(listOf(Cie10Diagnosis("F40.1", "Fobia social")), ana.diagnoses.toList())
        val behavior = ana.behaviors.single()
        assertFalse(behavior.baselineOpen)
        assertEquals("4/9/2026", behavior.baselineClosedOn)
        assertEquals(3, behavior.tasks.size)
        assertEquals(Phase.INTERVENCION, ana.phase)
    }

    @Test
    fun nullListsFromOracleBecomeEmpty() {
        val ana = patients().single()
        val baselineTask = ana.tasks.first { it.id == "20" }
        assertTrue(baselineTask.widgetEntries.isEmpty())
        assertTrue(ana.tasks.first { it.id == "22" }.entries.isEmpty())
        assertNull(baselineTask.metricUnit)
    }

    @Test
    fun recordsCombineFixedBaselineWithInterventionWithoutDuplicates() {
        val behavior = patients().single().behaviors.single()
        // 2 de línea base (desde baseline_entries, no duplicadas desde la tarea) + 1 de intervención.
        val records = behavior.recordsFor(Dimension.FRECUENCIA)
        assertEquals(3, records.size)
        assertEquals(2, records.count { it.phase == Phase.LINEA_BASE })
        assertEquals(LocalDate(2026, 9, 5), records.single { it.phase == Phase.INTERVENCION }.date)
    }

    @Test
    fun frequencyWithObservedMinutesIsChartedAsRate() {
        val behavior = patients().single().behaviors.single()
        val chart = behavior.chartValuesFor(Dimension.FRECUENCIA).map { it.second }
        assertEquals(listOf(5.0 / 45, 6.0 / 45, 3.0 / 60), chart)
    }

    @Test
    fun configurableMeasurementKeepsItsWidgetAndValues() {
        val task = patients().single().tasks.first { it.id == "22" }
        assertEquals(WidgetValueKind.MULTI_SELECT, task.widgetConfig?.valueKind)
        val value = task.widgetEntries.single().value
        assertEquals(WidgetValue.MultiSelect(listOf("Ansiedad", "Evitación")), value)
        assertTrue(task.feedsWidgetSeries)
    }

    @Test
    fun rateMakesSessionsOfDifferentLengthComparable() {
        val first = BehaviorRecord("a", "t", "b", LocalDate(2026, 9, 1), 3.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA, 30)
        val second = BehaviorRecord("b", "t", "b", LocalDate(2026, 9, 2), 6.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA, 60)
        assertEquals(0.1, first.valueForChart())
        assertEquals(first.valueForChart(), second.valueForChart())
    }

    @Test
    fun passwordAssessmentMatchesBackendPolicy() {
        assertTrue(assessPassword("Seguro-Mixto-123", "persona@usb.edu.co").isValid)
        assertFalse(assessPassword("corta", "persona@usb.edu.co").isValid)
        assertFalse(assessPassword("abcdefghij", "persona@usb.edu.co").isValid)
        assertFalse(assessPassword("Persona-Segura-123", "persona@usb.edu.co").isValid)
        assertFalse(assessPassword("A".repeat(129) + "a1!", "persona@usb.edu.co").isValid)
    }

    @Test
    fun firstLoginPasswordMustBeExactlyTenCharacters() {
        assertTrue(assessPassword("Abcdef1!Xy", "persona@usb.edu.co", exactLength = true).isValid)
        assertFalse(assessPassword("Abcdef1!X", "persona@usb.edu.co", exactLength = true).isValid)
        assertFalse(assessPassword("Abcdef1!Xyz", "persona@usb.edu.co", exactLength = true).isValid)
        assertEquals(10, passwordCharacterCount("Abcdef1!X😀"))
        assertTrue(assessPassword("Abcdef1!X😀", "persona@usb.edu.co", exactLength = true).isValid)
        assertFalse(assessPassword("Abcdef1!😀", "persona@usb.edu.co", exactLength = true).isValid)
        assertFalse(assessPassword("Abcdef1!Xy😀", "persona@usb.edu.co", exactLength = true).isValid)
    }
}
