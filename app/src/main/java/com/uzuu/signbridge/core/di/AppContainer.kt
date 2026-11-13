package com.uzuu.signbridge.core.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uzuu.signbridge.data.repository.FakeConversationRepository
import com.uzuu.signbridge.data.repository.FakeSpeakerRepository
import com.uzuu.signbridge.data.repository.InMemoryChipRepository
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.repository.ChipRepository
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.domain.repository.SpeakerRepository
import com.uzuu.signbridge.feature.chip.ChipManageViewModel
import com.uzuu.signbridge.feature.conversation.ConversationViewModel

/**
 * Manual DI. Hiện nối bản fake; TV2 đổi sang implementation thật
 * (camera, ghi âm, API, SQLite, TTS) tại đây, UI không cần sửa.
 */
class AppContainer {

    val conversationRepository: ConversationRepository by lazy { FakeConversationRepository() }
    val speakerRepository: SpeakerRepository by lazy { FakeSpeakerRepository() }
    val chipRepository: ChipRepository by lazy { InMemoryChipRepository() }

    fun conversationViewModelFactory(context: ContextType): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ConversationViewModel(
                    context = context,
                    conversationRepository = conversationRepository,
                    speakerRepository = speakerRepository,
                    chipRepository = chipRepository,
                )
            }
        }

    fun chipManageViewModelFactory(context: ContextType): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { ChipManageViewModel(context, chipRepository) }
        }
}
