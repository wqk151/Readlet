package com.readlet.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
)

/** 句子正文：西文衬线，保持"阅读感" */
val SentenceType = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 15.sp,
    lineHeight = 24.sp,
)

val SentenceLarge = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 19.sp,
    lineHeight = 32.sp,
)

/** 详情页原句：比列表/复习页略小，避免大段内容页面显得拥挤 */
val SentenceMedium = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 17.sp,
    lineHeight = 28.sp,
)
