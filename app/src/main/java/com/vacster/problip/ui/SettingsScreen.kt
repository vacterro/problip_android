package com.vacster.problip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.vacster.problip.trial.PremiumAccess

/**
 * Compact settings screen. Its only job in this wave is temporary access: what is
 * granted without a purchase, and one button to give all of it back.
 */
@Composable
fun SettingsScreen(
    viewModel: ProblipViewModel,
    onBack: () -> Unit,
) {
    val access by viewModel.access.collectAsState()
    val developerExpiry by viewModel.developerExpiryMillis.collectAsState()
    val now = rememberTrialNow(enabled = access.developerAccess)

    StoreScaffold(title = "SETTINGS", onBack = onBack) {
        SectionLabel("TEMPORARY ACCESS")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Developer Access",
                color = P.TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (access.developerAccess) {
                    "${PremiumAccess.formatDeveloperRemaining(developerExpiry, now)} remaining"
                } else {
                    "OFF"
                },
                color = if (access.developerAccess) P.Gold else P.TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }

        BigButton(
            text = "RESET TEMPORARY ACCESS",
            fontSize = 13.sp,
            onClick = viewModel::resetTemporaryAccess,
        )
        Text(
            text = "Clears Developer Access and all five-minute trials. " +
                "Purchases are never removed.",
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}
