package com.agro.interviewer.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.agro.interviewer.domain.model.PerformanceGrade
import com.agro.interviewer.domain.model.QuestionCategory

val QuestionCategory.outlinedIcon: ImageVector
    get() = when (this) {
        QuestionCategory.KOTLIN -> Icons.Outlined.Code
        QuestionCategory.ANDROID_FUNDAMENTALS -> Icons.Outlined.Smartphone
        QuestionCategory.JETPACK_COMPOSE -> Icons.Outlined.Palette
        QuestionCategory.ARCHITECTURE -> Icons.Outlined.Layers
        QuestionCategory.COROUTINES -> Icons.Outlined.Sync
        QuestionCategory.TESTING -> Icons.Outlined.BugReport
        QuestionCategory.PERFORMANCE -> Icons.Outlined.Speed
        QuestionCategory.SYSTEM_DESIGN -> Icons.Outlined.Schema
    }

val PerformanceGrade.outlinedIcon: ImageVector
    get() = when (this) {
        PerformanceGrade.EXCELLENT -> Icons.Outlined.EmojiEvents
        PerformanceGrade.GOOD -> Icons.Outlined.ThumbUp
        PerformanceGrade.AVERAGE -> Icons.AutoMirrored.Outlined.TrendingUp
        PerformanceGrade.NEEDS_WORK -> Icons.Outlined.AutoFixHigh
    }
