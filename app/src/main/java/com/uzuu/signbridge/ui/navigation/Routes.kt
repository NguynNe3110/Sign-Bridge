package com.uzuu.signbridge.ui.navigation

import com.uzuu.signbridge.domain.model.ContextType

object Routes {
    const val ARG_CONTEXT = "context"

    const val CONTEXT_SELECT = "context_select"
    const val CONVERSATION = "conversation/{$ARG_CONTEXT}"
    const val CHIP_MANAGE = "chip_manage/{$ARG_CONTEXT}"

    fun conversation(context: ContextType) = "conversation/${context.name}"
    fun chipManage(context: ContextType) = "chip_manage/${context.name}"
}
