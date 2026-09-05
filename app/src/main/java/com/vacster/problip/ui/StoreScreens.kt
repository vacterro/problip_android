package com.vacster.problip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vacster.problip.audio.SoundEntry
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.theme.ThemeCatalog

/**
 * Secondary screen: buy individual sounds, or start a five-minute trial. Both
 * actions stay visible on an unowned row — a trial must not hide the purchase.
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

    val effectivePool = SoundCatalog.playableSelection(settings.selectedSounds, owned, access.grantedIds)
    StoreScaffold(title = "SOUNDS", onBack = onBack) {
        SoundCatalog.all.forEach { entry ->
            PoolRow(
                entry = entry,
                checked = entry.id in effectivePool,
                label = trialLabel(
                    free = entry.free,
                    owned = entry.id in owned,
                    developerAccess = access.developerAccess,
                    expiryMillis = trialExpiries[entry.id],
                    nowMillis = now,
                ),
                onToggle = { viewModel.toggleSound(entry.id) },
            )
            val price = products[entry.id]?.formattedPrice
            val state = storeItemState(
                productId = entry.id,
                free = entry.free,
                owned = owned,
                pending = pending,
                price = price,
                connection = connection,
            )
            StoreRow(
                name = entry.displayName,
                state = state,
                price = price,
                // Owned and free rows have nothing to try; a pending purchase is
                // already on its way, so pushing a trial there would be noise.
                // Developer Access shows DEV instead of TRY, because a granted
                // item cannot start a trial (TrialAccess.startTrial refuses).
                trial = when (state) {
                    StoreItemState.PURCHASABLE, StoreItemState.LOADING, StoreItemState.UNAVAILABLE ->
                        trialLabel(
                            free = false,
                            owned = false,
                            developerAccess = access.developerAccess,
                            expiryMillis = trialExpiries[entry.id],
                            nowMillis = now,
                        )
                    else -> null
                },
                onTrial = { viewModel.trySound(entry.id) },
                onClick = { onPurchaseRequested(entry.id) },
            )
        }

        StoreFooter(
            storeError = storeError,
            onRetry = viewModel::retryStore,
        )
    }
}

/**
 * Secondary screen: pick any theme, and buy the one pack that unlocks the five
 * non-classic palettes permanently. Every row is selectable — picking a premium
 * palette without the pack starts its own five-minute trial — and the pack row
 * stays the only purchase entry point.
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
    val packState = storeItemState(
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
        ThemeCatalog.all.forEach { entry ->
            ThemePickRow(
                name = entry.displayName,
                selected = entry.id == effectiveTheme,
                label = trialLabel(
                    free = entry.free,
                    owned = ownsPack,
                    developerAccess = access.developerAccess,
                    expiryMillis = trialExpiries[entry.id],
                    nowMillis = now,
                ),
                onSelect = { viewModel.setTheme(entry.id) },
            )
        }

        SectionLabel("CUSTOMIZATION PACK")
        StoreRow(
            name = "All extra palettes + Manual & Pulse Interval",
            state = packState,
            price = packPrice,
            onClick = { onPurchaseRequested(ProductCatalog.THEME_PACK) },
        )

        StoreFooter(
            storeError = storeError,
            onRetry = viewModel::retryStore,
        )
    }
}

@Composable
internal fun StoreScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(P.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "< BACK",
                color = P.Gold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
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

/**
 * One purchasable line. Only PURCHASABLE rows react to a tap on the row itself;
 * [trial] is a separate tap target so BUY and TRY never compete for it.
 */
@Composable
private fun StoreRow(
    name: String,
    state: StoreItemState,
    price: String?,
    onClick: () -> Unit,
    trial: String? = null,
    onTrial: () -> Unit = {},
) {
    val buyable = state == StoreItemState.PURCHASABLE
    val label = storeItemLabel(state, price)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(P.Surface)
            .border(1.dp, P.Bevel)
            .clickable(enabled = buyable) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = when (state) {
                StoreItemState.INCLUDED, StoreItemState.OWNED -> P.TextMain
                else -> P.TextDim
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (trial != null) {
            Text(
                text = trial,
                color = P.Gold,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clickable { onTrial() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        if (label != null) {
            Text(
                text = if (buyable) "BUY $label" else label,
                color = when (state) {
                    StoreItemState.PURCHASABLE -> P.Gold
                    StoreItemState.OWNED -> P.Success
                    StoreItemState.PENDING -> P.Compare
                    StoreItemState.UNAVAILABLE -> P.Danger
                    else -> P.TextDim
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }
    }
}

/**
 * One theme choice. Always selectable: an unowned premium palette starts its
 * trial on tap, so there is no inert row any more. [label] shows OWNED,
 * "TRIAL mm:ss" or "TRY 5 MIN".
 */
@Composable
private fun ThemePickRow(
    name: String,
    selected: Boolean,
    label: String?,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "(*)" else "( )",
            color = P.Gold,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            color = if (selected) P.TextMain else P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        if (label != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = label,
                color = if (label == "OWNED") P.Success else P.Gold,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
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

/**
 * Pool membership toggle for one catalog sound. [label] carries the access state
 * (OWNED / TRIAL mm:ss / TRY 5 MIN); tapping a locked row starts its trial.
 */
@Composable
private fun PoolRow(
    entry: SoundEntry,
    checked: Boolean,
    label: String?,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "[x]" else "[ ]",
            color = P.Gold,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = entry.displayName,
            color = if (checked) P.TextMain else P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        if (label != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = label,
                color = if (label == "OWNED") P.Success else P.Gold,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
        }
    }
}
