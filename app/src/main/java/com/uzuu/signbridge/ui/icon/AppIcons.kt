package com.uzuu.signbridge.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignLanguage
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.IconCode

/** Map `iconCode` của API sang icon hiển thị. */
fun String.toImageVector(): ImageVector = when (this) {
    IconCode.PILL -> Icons.Filled.Medication
    IconCode.MONEY -> Icons.Filled.Payments
    IconCode.QR_PAYMENT -> Icons.Filled.QrCode2
    IconCode.COUNTER -> Icons.Filled.Storefront
    IconCode.TIME -> Icons.Filled.Schedule
    IconCode.DOCUMENT -> Icons.Filled.Description
    IconCode.DRINK -> Icons.Filled.LocalCafe
    IconCode.FOOD -> Icons.Filled.Restaurant
    IconCode.CHECK -> Icons.Filled.CheckCircle
    IconCode.WARNING -> Icons.Filled.Warning
    else -> Icons.Filled.Info
}

fun ContextType.toImageVector(): ImageVector = when (this) {
    ContextType.CAFE -> Icons.Filled.LocalCafe
    ContextType.PHARMACY -> Icons.Filled.LocalPharmacy
    ContextType.PUBLIC -> Icons.Filled.AccountBalance
    ContextType.CUSTOM -> Icons.AutoMirrored.Filled.Chat
}

object AppIcons {
    val Sign: ImageVector = Icons.Filled.SignLanguage
}
