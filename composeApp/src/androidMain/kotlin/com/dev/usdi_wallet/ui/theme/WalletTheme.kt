package com.dev.usdi_wallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

// ── Colors ────────────────────────────────────────────────────────────────────
object WalletColors {
    val Primary       = Color(0xFF185FA5)
    val PrimaryLight  = Color(0xFFE6F1FB)
    val Success       = Color(0xFF639922)
    val SuccessLight  = Color(0xFFE1F5EE)
    val Danger        = Color(0xFFE24B4A)
    val DangerLight   = Color(0xFFFAECE7)
    val Warning       = Color(0xFFF59E0B)
    val WarningLight  = Color(0xFFFEF3C7)
    val TextPrimary   = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF64748B)
    val TextTertiary  = Color(0xFF94A3B8)
    val Border        = Color(0xFFE2E8F0)
    val Surface       = Color(0xFFF8FAFC)
    val White         = Color(0xFFFFFFFF)
}

private val WalletColorScheme = lightColorScheme(
    primary          = WalletColors.Primary,
    onPrimary        = WalletColors.White,
    primaryContainer = WalletColors.PrimaryLight,
    background       = WalletColors.Surface,
    surface          = WalletColors.White,
    onSurface        = WalletColors.TextPrimary,
    onSurfaceVariant = WalletColors.TextSecondary,
    outline          = WalletColors.Border,
    error            = WalletColors.Danger,
    errorContainer   = WalletColors.DangerLight,
)

// ── Typography ────────────────────────────────────────────────────────────────
private val WalletTypography = Typography(
    displayLarge  = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.W500, color = WalletColors.TextPrimary),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.W500, color = WalletColors.TextPrimary),
    headlineMedium= TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W500, color = WalletColors.TextPrimary),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W500, color = WalletColors.TextPrimary),
    titleLarge    = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W500, color = WalletColors.TextPrimary),
    titleMedium   = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W500, color = WalletColors.TextPrimary),
    titleSmall    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W500, color = WalletColors.TextPrimary),
    bodyLarge     = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W400, color = WalletColors.TextPrimary),
    bodyMedium    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W400, color = WalletColors.TextSecondary),
    bodySmall     = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W400, color = WalletColors.TextTertiary),
    labelMedium   = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W500, color = WalletColors.TextTertiary, letterSpacing = 0.05.sp),
)

// ── Shapes ────────────────────────────────────────────────────────────────────
private val WalletShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

// ── Theme ─────────────────────────────────────────────────────────────────────
@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WalletColorScheme,
        typography  = WalletTypography,
        shapes      = WalletShapes,
        content     = content,
    )
}