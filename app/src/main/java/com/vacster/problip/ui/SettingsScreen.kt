package com.vacster.problip.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.os.LocaleListCompat
import com.vacster.problip.R
import com.vacster.problip.trial.PremiumAccess

/**
 * Compact settings screen: temporary access and the in-app language. Language
 * rides the AppCompat per-app locale mechanism — the platform is the only
 * authority, no second DataStore key exists for it.
 */
@Composable
fun SettingsScreen(
    viewModel: ProblipViewModel,
    onBack: () -> Unit,
) {
    val access by viewModel.access.collectAsState()
    val developerExpiry by viewModel.developerExpiryMillis.collectAsState()
    val now = rememberTrialNow(enabled = access.developerAccess)
    var languageDialogOpen by remember { mutableStateOf(false) }

    StoreScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        SectionLabel(stringResource(R.string.temporary_access))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.developer_access),
                color = P.TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (access.developerAccess) {
                    stringResource(
                        R.string.remaining_format,
                        PremiumAccess.formatDeveloperRemaining(developerExpiry, now),
                    )
                } else {
                    stringResource(R.string.status_off)
                },
                color = if (access.developerAccess) P.Gold else P.TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }

        BigButton(
            text = stringResource(R.string.reset_temporary_access),
            fontSize = 13.sp,
            onClick = viewModel::resetTemporaryAccess,
        )
        Text(
            text = stringResource(R.string.reset_temporary_access_hint),
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )

        LanguageRow(onOpen = { languageDialogOpen = true })
    }

    if (languageDialogOpen) {
        LanguageDialog(onDismiss = { languageDialogOpen = false })
    }
}

/** The supported languages. The tag is the AppCompat locale; the name is its endonym. */
private val LanguageOptions = listOf(
    null to "English",
    "ru" to "Русский",
    "et" to "Eesti",
    "ja" to "日本語",
)

/** One compact row: LANGUAGE and the current selection, SYSTEM when unset. */
@Composable
private fun LanguageRow(onOpen: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales()
    val currentName =
        if (current.isEmpty) {
            stringResource(R.string.language_value_system)
        } else {
            LanguageOptions
                .firstOrNull { it.first != null && it.first == current[0]!!.language }
                ?.second
                ?: current[0]!!.displayLanguage
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(48.dp)
                .background(P.Surface)
                .border(1.dp, P.Edge)
                .clickable(role = Role.Button) { onOpen() }
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.language_label),
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = currentName,
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Text(
            text = ">",
            color = P.ActionText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * One small palette dialog: System Default plus the four supported languages.
 * Selecting applies immediately through AppCompatDelegate; the empty list IS
 * "System Default" and the platform persists the choice itself.
 */
@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales()
    val selectedTag: String? =
        if (current.isEmpty) null else current[0]?.language?.takeIf { tag ->
            LanguageOptions.any { it.first == tag }
        }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.background(P.Surface).border(1.dp, P.Edge).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.language_label),
                color = P.Gold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
            )
            LanguageOptionRow(
                name = stringResource(R.string.language_option_system),
                selected = selectedTag == null,
            ) {
                onDismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
            LanguageOptions.forEach { (tag, name) ->
                LanguageOptionRow(name = name, selected = selectedTag == tag) {
                    onDismiss()
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(tag!!),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(name: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(40.dp)
                .clickable(role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (selected) "(*)" else "( )",
            color = P.ActionText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Text(
            text = name,
            color = if (selected) P.ActionText else P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}
