package com.uzuu.signbridge.ui.component.text

import androidx.annotation.StringRes
import com.uzuu.signbridge.R
import com.uzuu.signbridge.domain.model.ContextType

@StringRes
fun ContextType.labelRes(): Int = when (this) {
    ContextType.CAFE -> R.string.context_cafe
    ContextType.PHARMACY -> R.string.context_pharmacy
    ContextType.PUBLIC -> R.string.context_public
    ContextType.CUSTOM -> R.string.context_custom
}
