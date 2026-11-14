package com.uzuu.signbridge.feature.conversation

import androidx.annotation.StringRes
import com.uzuu.signbridge.R

/** Presentation choices only. All layouts dispatch the same conversation events. */
enum class ConversationLayout(@param:StringRes val labelRes: Int, @param:StringRes val descriptionRes: Int) {
    SHARED(R.string.layout_shared, R.string.layout_shared_description),
    FOCUSED(R.string.layout_focused, R.string.layout_focused_description),
    TABLE(R.string.layout_table, R.string.layout_table_description),
}
