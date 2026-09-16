package org.usbrain.project

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
        state.draftCategoryId = state.categories.first().id
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
        state.draftCategoryId = state.categories.first().id
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
        state.saveTask()
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
}