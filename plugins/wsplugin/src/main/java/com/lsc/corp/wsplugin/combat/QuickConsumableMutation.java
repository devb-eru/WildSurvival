package com.lsc.corp.wsplugin.combat;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.LinkedHashMap;

/** Durable player-state changes that must share the quick-item decrement checkpoint. */
final class QuickConsumableMutation {
    private static final double EPSILON = 1.0e-6;

    private QuickConsumableMutation() { }

    static void commitUseCost(RunSnapshot.PlayerState state, String itemId,
                              String combatScope, double effectiveApCost) {
        if (state == null) throw new IllegalArgumentException("Player state is required");
        if (!Double.isFinite(effectiveApCost) || effectiveApCost < 0.0) {
            throw new IllegalArgumentException("AP cost must be finite and non-negative");
        }
        boolean productionConsumable = itemId.startsWith("WSI-CONS-");
        if (productionConsumable && (combatScope == null || combatScope.isBlank())) {
            throw new IllegalArgumentException("Production consumable use requires a combat scope");
        }
        if (!"ACTIVE".equals(state.lifeState) || state.ap + EPSILON < effectiveApCost) {
            throw new IllegalStateException("Player cannot pay the consumable AP cost");
        }
        state.ap = Math.max(0.0, state.ap - effectiveApCost);
        if (!productionConsumable) return;
        if (state.quickItemUsesByCombat == null) state.quickItemUsesByCombat = new LinkedHashMap<>();
        state.quickItemUsesByCombat.merge(combatScope + ":" + itemId, 1, Integer::sum);
    }

    static void applyDirectDurableEffect(RunSnapshot.PlayerState state, String itemId) {
        switch (itemId) {
            case "WSI-CONS-AP_STIM" -> {
                ApStimPulsePolicy.State started = ApStimPulsePolicy.start(state.ap, state.maxAp);
                state.ap = started.ap();
                state.apStimPulsesRemaining = started.pulsesRemaining();
                state.apStimTicksUntilNextPulse = started.ticksUntilNextPulse();
            }
            case "WSI-CONS-RESCUE_BRACE" -> reserveBrace(state,
                    ConsumableRuntimePolicy.BASE_RESCUE_BRACE_THRESHOLD_BONUS);
            case "WSI-CONS-REINFORCED_RESCUE_BRACE" -> reserveBrace(state,
                    ConsumableRuntimePolicy.REINFORCED_RESCUE_BRACE_THRESHOLD_BONUS);
            default -> { }
        }
    }

    private static void reserveBrace(RunSnapshot.PlayerState state, double bonus) {
        RescueBracePolicy.Pending pending = RescueBracePolicy.reserve(
                state.rescueInterruptThresholdBonus, bonus);
        state.rescueBraceCharges = pending.charges();
        state.rescueInterruptThresholdBonus = pending.bonus();
    }
}
