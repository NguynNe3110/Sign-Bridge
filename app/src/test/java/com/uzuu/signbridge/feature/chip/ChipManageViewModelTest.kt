package com.uzuu.signbridge.feature.chip

import com.uzuu.signbridge.MainDispatcherRule
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.QuickChip
import com.uzuu.signbridge.feature.conversation.StubChipRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChipManageViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = StubChipRepository(
        listOf(QuickChip("1", ContextType.CAFE, "Mang đi", isPersonal = false)),
    )

    private fun createViewModel() = ChipManageViewModel(ContextType.CAFE, repository)

    @Test
    fun `splits personal and preset chips`() {
        val vm = createViewModel()
        assertEquals(listOf("Mang đi"), vm.uiState.value.presetChips.map { it.label })
        assertTrue(vm.uiState.value.personalChips.isEmpty())
    }

    @Test
    fun `adds trimmed personal chip and clears input`() {
        val vm = createViewModel()
        vm.onEvent(ChipManageUiEvent.NewLabelChanged("  Không đường  "))
        vm.onEvent(ChipManageUiEvent.AddClicked)

        val state = vm.uiState.value
        assertEquals(listOf("Không đường"), state.personalChips.map { it.label })
        assertEquals("", state.newLabel)
        assertNull(state.inputError)
    }

    @Test
    fun `rejects empty, too long and duplicate labels`() {
        val vm = createViewModel()

        vm.onEvent(ChipManageUiEvent.NewLabelChanged("   "))
        vm.onEvent(ChipManageUiEvent.AddClicked)
        assertEquals(ChipInputError.EMPTY, vm.uiState.value.inputError)

        vm.onEvent(ChipManageUiEvent.NewLabelChanged("a".repeat(ChipManageViewModel.MAX_LABEL_LENGTH + 1)))
        vm.onEvent(ChipManageUiEvent.AddClicked)
        assertEquals(ChipInputError.TOO_LONG, vm.uiState.value.inputError)

        vm.onEvent(ChipManageUiEvent.NewLabelChanged("mang đi"))
        vm.onEvent(ChipManageUiEvent.AddClicked)
        assertEquals(ChipInputError.DUPLICATE, vm.uiState.value.inputError)

        assertTrue(vm.uiState.value.personalChips.isEmpty())
    }

    @Test
    fun `deletes chip`() {
        val vm = createViewModel()
        vm.onEvent(ChipManageUiEvent.NewLabelChanged("Thẻ cá nhân"))
        vm.onEvent(ChipManageUiEvent.AddClicked)
        val personal = vm.uiState.value.personalChips.single()
        vm.onEvent(ChipManageUiEvent.DeleteClicked(personal.id))
        assertTrue(vm.uiState.value.personalChips.isEmpty())
        assertEquals(1, vm.uiState.value.presetChips.size)
    }
}
