package com.vacster.problip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.theme.ThemeCatalog

/**
 * Secondary screen: buy individual sounds, or start a five-minute trial. Both actions stay visible
 * on an unowned row — a trial must not hide the purchase.
 */
@Composable
fun SoundsScreen(
    viewModel: ProblipViewModel,
    onBack: () -> Unit,
    onPurchaseRequested: (String) -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val products by viewModel.products.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val storeError by viewModel.billingError.collectAsState()
    val access by viewModel.access.collectAsState()
    val trialExpiries by viewModel.trialExpiries.collectAsState()
    val now = rememberTrialNow(enabled = access.activeTrials.isNotEmpty())

    val effectivePool =
        SoundCatalog.playableSelection(settings.selectedSounds, owned, access.grantedIds)
    StoreScaffold(title = "SOUNDS", onBack = onBack) {
        SectionLabel("SELECT YOUR SOUND POOL")
        SoundCatalog.all.forEach { entry ->
            val price = products[entry.id]?.formattedPrice
            val state =
                storeItemState(
                    productId = entry.id,
                    free = entry.free,
                    owned = owned,
                    pending = pending,
                    price = price,
                    connection = connection,
                )
            val granted = access.grants(entry.id, free = entry.free, owned = entry.id in owned)
            Column(modifier = Modifier.background(P.Surface)) {
                SelectionRow(
                    name = entry.displayName,
                    selected = entry.id in effectivePool,
                    multiple = true,
                    label =
                        if (granted)
                            trialLabel(
                                free = entry.free,
                                owned = entry.id in owned,
                                developerAccess = access.developerAccess,
                                expiryMillis = trialExpiries[entry.id],
                                nowMillis = now,
                            ) ?: "INCLUDED"
                        else null,
                    onSelect = { viewModel.toggleSound(entry.id) },
                )
                if (state != StoreItemState.INCLUDED && state != StoreItemState.OWNED) {
                    StoreRow(
                        state = state,
                        price = price,
                        actionName = entry.displayName,
                        trial =
                            if (!granted && state != StoreItemState.PENDING) "TRY 5 MIN" else null,
                        onTrial = { viewModel.trySound(entry.id) },
                        onClick = { onPurchaseRequested(entry.id) },
                    )
                }
            }
        }

        StoreFooter(storeError = storeError, onRetry = viewModel::retryStore)
    }
}

/**
 * Secondary screen: pick any theme, and buy the one pack that unlocks the five non-classic palettes
 * permanently. Every row is selectable — picking a premium palette without the pack starts its own
 * five-minute trial — and the pack row stays the only purchase entry point.
 */
@Composable
fun ThemesScreen(
    viewModel: ProblipViewModel,
    onBack: () -> Unit,
    onPurchaseRequested: (String) -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val products by viewModel.products.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val storeError by viewModel.billingError.collectAsState()
    val access by viewModel.access.collectAsState()
    val trialExpiries by viewModel.trialExpiries.collectAsState()
    val now = rememberTrialNow(enabled = access.activeTrials.isNotEmpty())

    val ownsPack = ProductCatalog.THEME_PACK in owned
    val packPrice = products[ProductCatalog.THEME_PACK]?.formattedPrice
    val packState =
        storeItemState(
            productId = ProductCatalog.THEME_PACK,
            free = false,
            owned = owned,
            pending = pending,
            price = packPrice,
            connection = connection,
        )
    // The selection marker follows the EFFECTIVE theme, so an expired trial shows
    // Classic selected here without any Activity restart.
    val effectiveTheme = ThemeCatalog.effective(settings.themeId, ownsPack, access.grantedIds).id

    StoreScaffold(title = "THEMES", onBack = onBack) {
        SectionLabel("CUSTOMIZATION PACK")
        StoreRow(
            name = "All extra palettes + Manual & Pulse Interval",
            state = packState,
            price = packPrice,
            onClick = { onPurchaseRequested(ProductCatalog.THEME_PACK) },
        )

        SectionLabel("SELECT OR TRY A PALETTE")
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ThemeCatalog.all.forEach { entry ->
                SelectionRow(
                    name = entry.displayName,
                    selected = entry.id == effectiveTheme,
                    label =
                        trialLabel(
                            free = entry.free,
                            owned = ownsPack,
                            developerAccess = access.developerAccess,
                            expiryMillis = trialExpiries[entry.id],
                            nowMillis = now,
                        ) ?: "INCLUDED",
                    onSelect = { viewModel.setTheme(entry.id) },
                )
            }
        }
        StoreFooter(storeError = storeError, onRetry = viewModel::retryStore)
    }
}

@Composable
internal fun StoreScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(P.Bg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "< BACK",
                color = P.Gold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier =
                    Modifier.clickable(role = Role.Button) { onBack() }
                        .sizeIn(minWidth = 72.dp, minHeight = 48.dp)
                        .padding(vertical = 14.dp, horizontal = 8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                color = P.Gold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.sp,
            )
        }
        content()
    }
}

/** Selection never purchases: TRY and BUY have separate, explicit touch targets. */
@Composable
private fun StoreRow(
    state: StoreItemState,
    price: String?,
    onClick: () -> Unit,
    name: String? = null,
    actionName: String = name.orEmpty(),
    trial: String? = null,
    onTrial: () -> Unit = {},
) {
    val buyable = state == StoreItemState.PURCHASABLE
    val label = storeItemLabel(state, price)
    val feedback = clickFeedback()
    Column(
        modifier = Modifier.fillMaxWidth().background(P.Surface).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (name != null) {
            Text(
                name,
                color = P.TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (trial != null) {
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .heightIn(min = 48.dp)
                            .border(1.dp, P.Edge)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Try $actionName for five minutes",
                            ) {
                                feedback()
                                onTrial()
                            }
                            .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        trial,
                        color = P.ActionText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
            if (label != null) {
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .heightIn(min = 48.dp)
                            .border(if (buyable) 2.dp else 1.dp, P.Edge)
                            .clickable(
                                enabled = buyable,
                                role = Role.Button,
                                onClickLabel = "Buy $actionName",
                            ) {
                                onClick()
                            }
                            .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (buyable) "BUY $label" else label,
                        color = if (buyable) P.ActionText else P.TextDim,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

/** One 48 dp choice, with a real checkbox/radio state and a non-purchase access badge. */
@Composable
private fun SelectionRow(
    name: String,
    selected: Boolean,
    label: String?,
    onSelect: () -> Unit,
    multiple: Boolean = false,
) {
    val feedback = clickFeedback()
    val choose = {
        if (multiple || !selected) feedback()
        onSelect()
    }
    val selection =
        if (multiple) {
            Modifier.toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { choose() },
            )
        } else {
            Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = choose)
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(if (selected) P.Compare else P.Surface)
                .border(if (selected) 2.dp else 1.dp, if (selected) P.ActionText else P.Edge)
                .then(selection)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                if (multiple) {
                    if (selected) "[x]" else "[ ]"
                } else {
                    if (selected) "(*)" else "( )"
                },
            modifier = Modifier.clearAndSetSemantics {},
            color = P.ActionText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 16.sp,
        )
        Text(
            name,
            modifier = Modifier.weight(1f),
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
        if (label != null) {
            Text(
                label,
                modifier =
                    Modifier.background(P.Compare)
                        .border(1.dp, P.Edge)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                color = P.ActionText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
            )
        }
    }
}

/** Store error plus retry; purchases are restored automatically on every resume. */
@Composable
private fun StoreFooter(storeError: String?, onRetry: () -> Unit) {
    if (storeError != null) {
        Text(
            text = storeError,
            color = P.Danger,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
    BigButton(text = "RETRY STORE", onClick = onRetry)
    Text(
        text = "Purchases are restored from your Google account automatically.",
        color = P.TextDim,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
    )
}
