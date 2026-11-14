package com.uzuu.signbridge.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Cỡ chữ lớn hơn mặc định để đọc từ khoảng cách bàn quầy.
val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
)

/** Câu người khiếm thính nói, hiển thị cho nhân viên (nửa trên). */
val SpokenSentenceStyle = TextStyle(
    fontSize = 30.sp,
    lineHeight = 38.sp,
    fontWeight = FontWeight.SemiBold,
)

/** Tóm tắt lời nhân viên, hiển thị cho người khiếm thính (nửa dưới). */
val SummaryStyle = TextStyle(
    fontSize = 28.sp,
    lineHeight = 38.sp,
    fontWeight = FontWeight.Normal,
)
