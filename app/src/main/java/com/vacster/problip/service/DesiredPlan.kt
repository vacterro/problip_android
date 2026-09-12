package com.vacster.problip.service

import com.vacster.problip.trial.PremiumAccess

/**
 * A desired session plan plus the snapshot it was resolved from (W2-002).
 *
 * [ColdStart.Plan] carries the resolved pool/interval, but the accepted-plan
 * transition must also refresh the notification from the same revision — so the
 * ownership and temporary-access snapshots ride along. Everything about one
 * accepted revision stays coherent: active audio pool, preparedPool, effective
 * scheduler interval and notification representation all describe this exact
 * plan, never a mix of revisions.
 */
internal data class DesiredPlan(
    val resolved: ColdStart.Plan,
    val owned: Set<String>,
    val access: PremiumAccess,
)
