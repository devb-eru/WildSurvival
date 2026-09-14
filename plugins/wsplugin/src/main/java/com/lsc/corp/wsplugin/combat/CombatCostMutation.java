package com.lsc.corp.wsplugin.combat;

import com.lsc.corp.wsplugin.run.RunSnapshot;

/** Pure validation and mutation for one server-authoritative weapon cost checkpoint. */
public final class CombatCostMutation {
    public static final String VANILLA_ARROW_CHECKPOINT = "MATERIAL:ARROW";
    private static final int CROSSBOW_MAGAZINE_CAPACITY = 6;

    private CombatCostMutation() {
    }

    public static AmmoSource selectArrowSource(RunSnapshot.PlayerState state, int vanillaArrowCount,
                                                int amount, boolean includeMagazine) {
        if (state == null || amount <= 0) return AmmoSource.NONE;
        if (includeMagazine && state.crossbowLoadedAmmo >= amount) return AmmoSource.MAGAZINE;
        if (AmmoLedgerPolicy.balance(state.ammoLedger, AmmoLedgerPolicy.GENERAL_ARROW) >= amount) {
            return AmmoSource.LEDGER;
        }
        return vanillaArrowCount >= amount ? AmmoSource.VANILLA_ARROW : AmmoSource.NONE;
    }

    public static Result apply(RunSnapshot.PlayerState state, Request request) {
        validate(state, request);

        state.ap = Math.max(0.0, state.ap - request.effectiveApCost());
        Integer physicalArrowExpected = null;
        if (!request.conserveAmmo() && request.ammoCost() > 0) {
            switch (request.ammoSource()) {
                case MAGAZINE -> state.crossbowLoadedAmmo -= request.ammoCost();
                case LEDGER -> {
                    if (!AmmoLedgerPolicy.consume(state.ammoLedger, AmmoLedgerPolicy.GENERAL_ARROW,
                            request.ammoCost())) {
                        throw new IllegalStateException("Validated ammo ledger changed during mutation");
                    }
                }
                case VANILLA_ARROW -> {
                    physicalArrowExpected = request.vanillaArrowCountBefore() - request.ammoCost();
                    state.pendingPhysicalItemCounts.put(VANILLA_ARROW_CHECKPOINT, physicalArrowExpected);
                }
                case NONE -> throw new IllegalStateException("Missing ammo source");
            }
        }
        state.crossbowLoadedAmmo += request.magazineGain();
        if (request.markSkillTutorial()) state.tutorialSignals.add("USED_SKILL");
        return new Result(request.ammoSource(), !request.conserveAmmo() && request.ammoCost() > 0,
                physicalArrowExpected, state.crossbowLoadedAmmo);
    }

    private static void validate(RunSnapshot.PlayerState state, Request request) {
        if (state == null || !"ACTIVE".equals(state.lifeState)) {
            throw new IllegalStateException("Active player state is required");
        }
        if (!Double.isFinite(request.effectiveApCost()) || request.effectiveApCost() < 0.0) {
            throw new IllegalArgumentException("AP cost must be finite and non-negative");
        }
        if (state.ap + 1.0e-6 < request.effectiveApCost()) {
            throw new IllegalStateException("Insufficient AP");
        }
        if (request.ammoCost() < 0 || request.magazineGain() < 0) {
            throw new IllegalArgumentException("Ammo costs and magazine gain cannot be negative");
        }
        if (request.ammoCost() == 0 && request.ammoSource() != AmmoSource.NONE) {
            throw new IllegalArgumentException("Ammo source must be NONE when no ammo is required");
        }
        if (request.ammoCost() > 0 && request.ammoSource() == AmmoSource.NONE) {
            throw new IllegalStateException("Required ammunition is unavailable");
        }
        if (state.crossbowLoadedAmmo < 0 || state.crossbowLoadedAmmo > CROSSBOW_MAGAZINE_CAPACITY
                || state.crossbowLoadedAmmo + request.magazineGain() > CROSSBOW_MAGAZINE_CAPACITY) {
            throw new IllegalStateException("Crossbow magazine has insufficient room");
        }
        if (request.magazineGain() > 0 && request.ammoSource() == AmmoSource.MAGAZINE) {
            throw new IllegalArgumentException("A magazine cannot reload itself");
        }
        if (request.ammoCost() == 0 || request.conserveAmmo()) return;
        switch (request.ammoSource()) {
            case MAGAZINE -> {
                if (state.crossbowLoadedAmmo < request.ammoCost()) {
                    throw new IllegalStateException("Insufficient loaded ammunition");
                }
            }
            case LEDGER -> {
                if (AmmoLedgerPolicy.balance(state.ammoLedger, AmmoLedgerPolicy.GENERAL_ARROW)
                        < request.ammoCost()) {
                    throw new IllegalStateException("Insufficient ledger ammunition");
                }
            }
            case VANILLA_ARROW -> {
                if (request.vanillaArrowCountBefore() < request.ammoCost()) {
                    throw new IllegalStateException("Insufficient vanilla arrows");
                }
            }
            case NONE -> throw new IllegalStateException("Missing ammo source");
        }
    }

    public enum AmmoSource {
        NONE,
        MAGAZINE,
        LEDGER,
        VANILLA_ARROW
    }

    public record Request(double effectiveApCost, AmmoSource ammoSource, int ammoCost,
                          boolean conserveAmmo, int vanillaArrowCountBefore, int magazineGain,
                          boolean markSkillTutorial) {
        public Request {
            if (ammoSource == null) throw new IllegalArgumentException("ammoSource is required");
        }
    }

    public record Result(AmmoSource ammoSource, boolean ammoConsumed,
                         Integer physicalArrowExpected, int loadedAmmoAfter) {
    }
}
