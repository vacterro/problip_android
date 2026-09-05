package com.vacster.problip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vacster.problip.R

/**
 * The `?` overlay: one Wintage modal, scrollable inside, no WebView and no
 * markdown engine. MAIN itself never scrolls — only this content does.
 *
 * Deliberately absent: the hidden Developer Access gesture is not documented
 * anywhere in here.
 */
@Composable
internal fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .background(P.Surface)
                .border(1.dp, P.Edge)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.help_title),
                color = P.Gold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.sp,
            )

            HelpHeading(stringResource(R.string.help_quick_start))
            HelpBody(stringResource(R.string.help_qs_volume))
            HelpBody(stringResource(R.string.help_qs_interval))
            HelpBody(stringResource(R.string.help_qs_sounds))
            HelpBody(stringResource(R.string.help_qs_start))
            HelpBody(stringResource(R.string.help_qs_minimize))
            HelpBody(stringResource(R.string.help_qs_exit))

            HelpHeading(stringResource(R.string.help_intervals))
            HelpBody(stringResource(R.string.help_intervals_body))

            HelpHeading(stringResource(R.string.help_premium))
            HelpBody(stringResource(R.string.help_premium_body))

            HelpHeading(stringResource(R.string.help_background))
            HelpBody(stringResource(R.string.help_background_body))

            HelpHeading(stringResource(R.string.help_tips))
            HelpBody(stringResource(R.string.help_tips_body))

            HelpHeading(stringResource(R.string.help_faq))
            Faq(stringResource(R.string.help_faq_notif_q), stringResource(R.string.help_faq_notif_a))
            Faq(stringResource(R.string.help_faq_minimize_q), stringResource(R.string.help_faq_minimize_a))
            Faq(stringResource(R.string.help_faq_exit_q), stringResource(R.string.help_faq_exit_a))
            Faq(stringResource(R.string.help_faq_renew_q), stringResource(R.string.help_faq_renew_a))
            Faq(stringResource(R.string.help_faq_expire_q), stringResource(R.string.help_faq_expire_a))
            Faq(stringResource(R.string.help_faq_theme_q), stringResource(R.string.help_faq_theme_a))

            BigButton(text = stringResource(R.string.ok), onClick = onDismiss)
        }
    }
}

@Composable
private fun HelpHeading(text: String) {
    Text(
        text = text,
        color = P.ActionText,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun HelpBody(text: String) {
    Text(
        text = text,
        color = P.TextDim,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Faq(question: String, answer: String) {
    Column {
        Text(
            text = question,
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Text(
            text = answer,
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}
