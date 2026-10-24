package org.usbrain.project

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.usbrain.project.data.BehaviorDto
import org.usbrain.project.data.BootstrapDto
import org.usbrain.project.data.PatientDto
import org.usbrain.project.data.TaskDto
import org.usbrain.project.data.WidgetDto
import org.usbrain.project.data.WidgetEntryDto

/**
 * Convierte el JSON del bootstrap en el modelo que ya usan las pantallas
 * (Patient → SingleCase → Behavior → TrackTask). Mantenerlo puro y sin red
 * permite probarlo sin servidor (ver SharedCommonTest).
 */
fun WidgetDto.toWidgetConfig() = WidgetConfig(
    id = id,
    name = name,
    valueKind = enumOr(valueKind, WidgetValueKind.NUMERIC),
    unit = unit,
    minValue = minValue,
    maxValue = maxValue,
    options = options,
    description = description,
    isPreset = isPreset,
)

fun BootstrapDto.toPatients(widgets: List<WidgetConfig>): List<Patient> =
    patients.map { it.toPatient(widgets) }

private fun PatientDto.toPatient(widgets: List<WidgetConfig>): Patient {
    val caseId = case?.id ?: "case-$id"
    return Patient(
        id = id,
        name = name,
        age = age,
        program = program,
        colorArgb = color,
        condition = condition,
        case = SingleCase(caseId, id, case?.title ?: "Caso", case?.behaviors.orEmpty().map { it.toBehavior(widgets) }),
        diagnoses = diagnoses.map { Cie10Diagnosis(it.code, it.label) },
        therapistName = therapistName.orEmpty(),
    )
}

private fun BehaviorDto.toBehavior(widgets: List<WidgetConfig>): Behavior {
    val baseline = baselineEntries.map {
        BehaviorRecord(
            id = it.id, taskId = it.taskId, behaviorId = id, date = LocalDate.parse(it.date),
            value = it.value, note = it.note, phase = Phase.LINEA_BASE,
            dimension = enumOr(it.dimension, Dimension.FRECUENCIA), observationMinutes = it.observationMinutes,
        )
    }
    return Behavior(
        id = id,
        caseId = caseId,
        name = name,
        operationalDefinition = operationalDefinition,
        type = enumOr(type, BehaviorType.DESADAPTATIVA),
        baselineOpenInitially = baselineOpen,
        behavioralFunctionId = functionId,
        replacementBehaviorId = replacementBehaviorId,
        baselineEntries = baseline,
        tasks = tasks.map { it.toTask(widgets) },
        baselineDimensions = baselineDimensions.map { enumOr(it, Dimension.FRECUENCIA) }
            .ifEmpty { listOf(Dimension.FRECUENCIA) },
    ).also { it.baselineClosedOn = baselineClosedOn }
}

private fun TaskDto.toTask(widgets: List<WidgetConfig>): TrackTask {
    val widget = widgetId?.let { wid -> widgets.firstOrNull { it.id == wid } }
    return TrackTask(
        id = id,
        behaviorId = behaviorId,
        title = title,
        instructions = instructions,
        taskType = enumOr(taskType, TaskType.REGISTRO_AUTONOMO),
        dimension = enumOr(dimension, Dimension.FRECUENCIA),
        metricUnit = metricUnit?.let { enumOr<MetricUnit>(it, MetricUnit.SOLO_COMPLETADO) },
        assignedDate = assignedDate,
        isRecurring = isRecurring,
        frequency = frequency,
        dueDate = dueDate,
        reminderActive = reminderActive,
        phase = enumOr(phase, Phase.LINEA_BASE),
        status = enumOr(status, TaskStatus.PENDIENTE),
        entries = entries.map {
            TaskEntry(LocalDate.parse(it.date), it.value, it.note, enumOr(it.phase, Phase.INTERVENCION), it.observationMinutes)
        },
        widgetConfig = widget,
        therapeuticGoal = therapeuticGoal,
        widgetEntries = if (widget == null) emptyList() else widgetEntries.map { it.toWidgetEntry(id, widget) },
    )
}

private fun WidgetEntryDto.toWidgetEntry(taskId: String, widget: WidgetConfig) = WidgetEntry(
    id = id,
    activityId = taskId,
    widgetId = widgetId,
    date = LocalDate.parse(date),
    time = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime(9, 0)),
    value = when (widget.valueKind) {
        WidgetValueKind.NUMERIC -> WidgetValue.Numeric(numericValue ?: 0.0, observationMinutes)
        WidgetValueKind.YES_NO -> WidgetValue.YesNo(boolValue ?: false)
        WidgetValueKind.TEXT -> WidgetValue.Text(textValue.orEmpty())
        WidgetValueKind.MULTI_SELECT -> WidgetValue.MultiSelect(selectedOptions)
    },
    note = note,
)

/** Un valor desconocido (p. ej. un enum nuevo en el servidor) no rompe la app: usa el valor por defecto. */
private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
