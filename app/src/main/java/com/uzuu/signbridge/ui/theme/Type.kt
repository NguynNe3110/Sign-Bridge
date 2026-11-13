package com.uzuu.signbridge.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Cỡ chữ lớn hơn mặc định để đọc từ khoảng cách bàn quầy.
val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

/** Câu người khiếm thính nói, hiển thị cho nhân viên (nửa trên). */
val SpokenSentenceStyle = TextStyle(
    fontSize = 34.sp,
    lineHeight = 42.sp,
    fontWeight = FontWeight.Bold,
)

/** Tóm tắt lời nhân viên, hiển thị cho người khiếm thính (nửa dưới). */
val SummaryStyle = TextStyle(
    fontSize = 30.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight.SemiBold,
)
