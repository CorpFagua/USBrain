package org.usbrain.project

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun newBehaviorStartsWithItsOwnBaseline() {
        val state = PrototypeState()
        state.startNewBehavior("ana")
        state.draftBehaviorName = "Participar en clase"
        state.draftDefinition = "Participación observable durante la clase"
        state.draftBehaviorType = BehaviorType.ADAPTATIVA
        state.saveBehavior()

        val behavior = state.activeBehavior()
        assertEquals(BehaviorType.ADAPTATIVA, behavior.type)
        assertTrue(behavior.baselineOpen)
        assertEquals(0, behavior.observationCount)
    }

    @Test
    fun tasksFollowBaselineStateAndClosedDataStaysFixed() {
        val state = PrototypeState()
        state.startNewBehavior("ana")
        state.draftBehaviorName = "Conducta de prueba"
        state.draftDefinition = "Definición operacional de prueba"
        state.draftBehaviorType = BehaviorType.ADAPTATIVA
        state.saveBehavior()
        val behavior = state.activeBehavior()

        state.startNewTask("ana", behavior.id)
        state.draftTaskTitle = "Registrar la conducta"
        state.draftInstructions = "Registra cada participación"
        state.assign()
        assertEquals(Phase.LINEA_BASE, behavior.tasks.last().phase)

        state.activeTaskId = behavior.tasks.last().id
        state.activeBehaviorId = behavior.id
        state.taskInputValue = 2.0
        state.saveTask(LocalDate(2026, 9, 16))
        val countAfterBaseline = behavior.observationCount
        assertEquals(1, countAfterBaseline)

        state.closeBaseline(behavior.id)
        assertFalse(behavior.baselineOpen)
        state.startNewTask("ana", behavior.id)
        state.draftTaskTitle = "Registrar en intervención"
        state.draftInstructions = "Registra la participación durante la intervención"
        state.assign()
        assertEquals(Phase.INTERVENCION, behavior.tasks.last().phase)
        assertEquals(countAfterBaseline, behavior.observationCount)
        assertNotNull(behavior.baselineClosedOn)
    }

    @Test
    fun baselineConfigurationCreatesDimensionTasksAndStoresRealDates() {
        val state = PrototypeState()
        state.startNewBehavior("ana")
        state.draftBehaviorName = "Ansiedad al hablar"
        state.draftDefinition = "Episodios observables de nerviosismo antes de hablar"
        state.draftBehaviorType = BehaviorType.DESADAPTATIVA
        state.saveBehavior()

        state.draftBaselineDimensions.clear()
        state.draftBaselineDimensions.addAll(listOf(Dimension.FRECUENCIA, Dimension.INTENSIDAD))
        state.configureBaseline()

        val behavior = state.activeBehavior()
        assertEquals(listOf(Dimension.FRECUENCIA, Dimension.INTENSIDAD), behavior.baselineDimensions.toList())
        assertTrue(behavior.tasks.any { it.phase == Phase.LINEA_BASE && it.dimension == Dimension.FRECUENCIA })

        val task = behavior.tasks.first { it.phase == Phase.LINEA_BASE && it.dimension == Dimension.FRECUENCIA }
        state.activeTaskId = task.id
        state.activeBehaviorId = behavior.id
        state.taskInputValue = 4.0
        state.saveTask(LocalDate(2026, 9, 16))

        assertEquals(LocalDate(2026, 9, 16), behavior.baselineEntries.last().date)
    }

    @Test
    fun frequencyRateUsesObservationMinutesWhenAvailable() {
        val first = BehaviorRecord(
            "r1", "t1", "b1", LocalDate(2026, 9, 16), 3.0, "", Phase.LINEA_BASE,
            Dimension.FRECUENCIA, observationMinutes = 30,
        )
        val second = BehaviorRecord(
            "r2", "t1", "b1", LocalDate(2026, 9, 17), 6.0, "", Phase.INTERVENCION,
            Dimension.FRECUENCIA, observationMinutes = 60,
        )

        assertEquals(0.1, first.valueForChart())
        assertEquals(first.valueForChart(), second.valueForChart())
    }

    @Test
    fun savingFrequencyObservationStoresMinutesForRate() {
        val state = PrototypeState()
        state.startNewBehavior("ana")
        state.draftBehaviorName = "Conducta frecuente"
        state.draftDefinition = "Conducta observable durante una sesión"
        state.saveBehavior()
        state.configureBaseline()

        val behavior = state.activeBehavior()
        val task = behavior.tasks.single { it.dimension == Dimension.FRECUENCIA }
        state.activeTaskId = task.id
        state.taskInputValue = 3.0
        state.taskObservationMinutes = 30
        state.saveTask(LocalDate(2026, 9, 16))

        assertEquals(30, task.entries.single().observationMinutes)
        assertEquals(30, behavior.baselineEntries.single().observationMinutes)
        assertEquals(0.1, behavior.baselineEntries.single().valueForChart())
    }

    @Test
    fun chartValuesUseDerivedRateAndRejectDuplicateDates() {
        val state = PrototypeState()
        state.startNewBehavior("ana")
        state.draftBehaviorName = "Conducta repetida"
        state.draftDefinition = "Conducta observable durante una sesión"
        state.saveBehavior()
        state.configureBaseline()

        val behavior = state.activeBehavior()
        val task = behavior.tasks.single { it.dimension == Dimension.FRECUENCIA }
        state.activeTaskId = task.id
        state.taskInputValue = 6.0
        state.taskObservationMinutes = 60
        val date = LocalDate(2026, 9, 16)
        state.saveTask(date)
        assertEquals(0.1, behavior.chartValuesFor(Dimension.FRECUENCIA).single().second)

        state.taskInputValue = 4.0
        state.taskObservationMinutes = 60
        state.saveTask(date)

        assertEquals(1, task.entries.size)
        assertNotNull(state.taskError)
    }

    @Test
    fun duplicateDateIsRejectedAcrossTasksWithSameDimension() {
        val state = PrototypeState()
        state.startNewBehavior("ana")
        state.draftBehaviorName = "Conducta con registros paralelos"
        state.draftDefinition = "Conducta observable"
        state.saveBehavior()
        state.configureBaseline()
        val behavior = state.activeBehavior()
        val firstTask = behavior.tasks.single { it.dimension == Dimension.FRECUENCIA }
        state.activeTaskId = firstTask.id
        state.taskInputValue = 1.0
        state.taskObservationMinutes = 30
        val date = LocalDate(2026, 9, 16)
        state.saveTask(date)

        val secondTask = TrackTask(
            "parallel", behavior.id, "Segundo registro", "", TaskType.REGISTRO_AUTONOMO,
            Dimension.FRECUENCIA, null, "Hoy", true, "Diaria", null, false,
            Phase.LINEA_BASE, TaskStatus.PENDIENTE,
        )
        behavior.tasks.add(secondTask)
        state.activeTaskId = secondTask.id
        state.taskInputValue = 2.0
        state.taskObservationMinutes = 30
        state.saveTask(date)

        assertTrue(secondTask.entries.isEmpty())
        assertNotNull(state.taskError)
    }
}