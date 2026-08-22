package com.neuroflow.app.presentation.common.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NeuroFlowTypography = Typography(
    headlineLarge  = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge     = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontSize = 16.sp),
    bodyMedium     = TextStyle(fontSize = 14.sp),
    labelSmall     = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
)
