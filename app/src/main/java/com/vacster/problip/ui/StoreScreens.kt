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
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.theme.ThemeCatalog

/** Secondary screen: buy individual sounds. Pool selection stays on the main screen. */
@Composable
fun SoundsScreen(
    viewModel: ProblipViewModel,
    onBack: () -> Unit,
    onPurchaseRequested: (String) -> Unit,
) {
    val owned by viewModel.owned.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val products by viewModel.products.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val storeError by viewModel.billingError.collectAsState()

    StoreScaffold(title = "SOUNDS", onBack = onBack) {
        SoundCatalog.all.forEach { entry ->
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
 * Secondary screen: pick a theme, and buy the one pack that unlocks the five
 * non-classic palettes. Locked palettes stay visible but inert; the pack row is
 * the only purchase entry point.
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

    StoreScaffold(title = "THEMES", onBack = onBack) {
        ThemeCatalog.all.forEach { entry ->
            val unlocked = entry.free || ownsPack
            ThemePickRow(
                name = entry.displayName,
                selected = entry.id == settings.themeId,
                unlocked = unlocked,
                onSelect = { viewModel.setTheme(entry.id) },
            )
        }

        SectionLabel("THEMES PACK")
        StoreRow(
            name = "All 5 extra palettes",
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
private fun StoreScaffold(
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

/** One purchasable line. Only PURCHASABLE rows react to a tap. */
@Composable
private fun StoreRow(
    name: String,
    state: StoreItemState,
    price: String?,
    onClick: () -> Unit,
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
        if (label != null) {
            Text(
                text = label,
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

@Composable
private fun ThemePickRow(
    name: String,
    selected: Boolean,
    unlocked: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = unlocked) { onSelect() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected && unlocked) "(*)" else "( )",
            color = if (unlocked) P.Gold else P.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            color = if (unlocked) P.TextMain else P.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        if (!unlocked) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "IN PACK",
                color = P.Muted,
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
