package com.dev.usdi_wallet.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dev.usdi_wallet.ui.theme.WalletColors

// ── Screen header ─────────────────────────────────────────────────────────────
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    leadingAction: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        leadingAction?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.headlineLarge)
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        trailingAction?.invoke()
    }
}

// ── Card ─────────────────────────────────────────────────────────────────────
@Composable
fun WalletCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .background(WalletColors.White)
        .border(0.5.dp, WalletColors.Border, MaterialTheme.shapes.large)

    Box(
        modifier = modifier.then(
            if (onClick != null) base.clickable(onClick = onClick) else base
        ).padding(16.dp)
    ) {
        content()
    }
}

// ── Icon container ────────────────────────────────────────────────────────────
@Composable
fun IconContainer(
    icon: ImageVector,
    tint: Color = WalletColors.Primary,
    background: Color = WalletColors.PrimaryLight,
    size: Int = 44,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5).dp),
        )
    }
}

// ── List item ─────────────────────────────────────────────────────────────────
@Composable
fun WalletListItem(
    icon: ImageVector,
    iconTint: Color = WalletColors.Primary,
    iconBackground: Color = WalletColors.PrimaryLight,
    title: String,
    subtitle: String? = null,
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(WalletColors.White)
            .border(0.5.dp, WalletColors.Border, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconContainer(icon = icon, tint = iconTint, background = iconBackground)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            badge?.let {
                Spacer(modifier = Modifier.height(4.dp))
                it()
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = WalletColors.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Status badge ─────────────────────────────────────────────────────────────
@Composable
fun StatusBadge(
    text: String,
    color: Color = WalletColors.Danger,
    background: Color = WalletColors.DangerLight,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

// ── Protocol badge ────────────────────────────────────────────────────────────
@Composable
fun ProtocolBadge(protocol: String) {
    val (bg, fg) = when {
        protocol.contains("OPENID", ignoreCase = true) ->
            WalletColors.PrimaryLight to WalletColors.Primary
        protocol.contains("DIDCOMM", ignoreCase = true) ->
            WalletColors.SuccessLight to WalletColors.Success
        else ->
            WalletColors.Border to WalletColors.TextSecondary
    }
    StatusBadge(text = protocol, color = fg, background = bg)
}

// ── Buttons ───────────────────────────────────────────────────────────────────
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = WalletColors.Primary,
            contentColor = WalletColors.White,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonColors(
            containerColor = Color.Transparent,
            contentColor = WalletColors.TextPrimary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        ),
        border = BorderStroke(0.5.dp, WalletColors.Border),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

// ── Section label ─────────────────────────────────────────────────────────────
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

// ── Settings row ─────────────────────────────────────────────────────────────
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    titleColor: Color = WalletColors.TextPrimary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (titleColor == WalletColors.Danger) WalletColors.Danger else WalletColors.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Divider ───────────────────────────────────────────────────────────────────
@Composable
fun WalletDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(WalletColors.Border),
    )
}
