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
    fun `edits preset in place and excludes itself from duplicate validation`() {
        val vm = createViewModel()
        vm.onEvent(ChipManageUiEvent.EditClicked("1"))
        assertEquals("Mang đi", vm.uiState.value.newLabel)
        vm.onEvent(ChipManageUiEvent.SaveEditClicked)
        assertNull(vm.uiState.value.inputError)
        vm.onEvent(ChipManageUiEvent.EditClicked("1"))
        vm.onEvent(ChipManageUiEvent.NewLabelChanged("  Dùng tại quán  "))
        vm.onEvent(ChipManageUiEvent.SaveEditClicked)
        val chip = vm.uiState.value.presetChips.single()
        assertEquals("1", chip.id)
        assertEquals("Dùng tại quán", chip.label)
        assertNull(vm.uiState.value.editingChipId)
        assertEquals("", vm.uiState.value.newLabel)
    }

    @Test
    fun `rejects duplicate edit then allows cancel without changing chip`() {
        val vm = createViewModel()
        vm.onEvent(ChipManageUiEvent.NewLabelChanged("Không đá"))
        vm.onEvent(ChipManageUiEvent.AddClicked)
        val id = vm.uiState.value.personalChips.single().id
        vm.onEvent(ChipManageUiEvent.EditClicked(id))
        vm.onEvent(ChipManageUiEvent.NewLabelChanged("MANG ĐI"))
        vm.onEvent(ChipManageUiEvent.SaveEditClicked)
        assertEquals(ChipInputError.DUPLICATE, vm.uiState.value.inputError)
        assertEquals("Không đá", vm.uiState.value.personalChips.single().label)
        vm.onEvent(ChipManageUiEvent.CancelEditClicked)
        assertNull(vm.uiState.value.editingChipId)
        assertNull(vm.uiState.value.inputError)
        assertEquals("", vm.uiState.value.newLabel)
        assertEquals("Không đá", vm.uiState.value.personalChips.single().label)
    }

    @Test
    fun `failed edit keeps draft and original so save can be retried`() {
        val vm = createViewModel()
        vm.onEvent(ChipManageUiEvent.EditClicked("1"))
        vm.onEvent(ChipManageUiEvent.NewLabelChanged("Tại quán"))
        repository.failStorage = true
        vm.onEvent(ChipManageUiEvent.SaveEditClicked)
        assertEquals(ChipInputError.SAVE_FAILED, vm.uiState.value.inputError)
        assertEquals("Tại quán", vm.uiState.value.newLabel)
        assertEquals("1", vm.uiState.value.editingChipId)
        assertTrue(!vm.uiState.value.isSaving)
        assertEquals("Mang đi", vm.uiState.value.presetChips.single().label)
        repository.failStorage = false
        vm.onEvent(ChipManageUiEvent.SaveEditClicked)
        assertEquals("Tại quán", vm.uiState.value.presetChips.single().label)
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
