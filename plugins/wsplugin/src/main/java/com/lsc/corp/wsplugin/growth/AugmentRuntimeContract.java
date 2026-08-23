package com.lsc.corp.wsplugin.growth;

import java.util.Map;
import java.util.Set;

/**
 * Closed runtime vocabulary for Season 1 augment effects.
 *
 * <p>The production data intentionally uses a small opcode vocabulary shared by multiple
 * augments. Keeping the vocabulary here prevents a data edit from silently creating an effect
 * that can be drawn by a player but has no runtime owner.</p>
 */
public final class AugmentRuntimeContract {
    public enum Owner {
        COMBAT,
        STATUS,
        ECONOMY,
        FACILITY,
        WORLD
    }

    private static final Map<String, Owner> OWNERS = Map.ofEntries(
            Map.entry("AMMO", Owner.COMBAT),
            Map.entry("AMMO_CONSERVE", Owner.COMBAT),
            Map.entry("AMMO_PARADOX", Owner.COMBAT),
            Map.entry("AP_REGEN", Owner.COMBAT),
            Map.entry("AREA", Owner.COMBAT),
            Map.entry("BLEED", Owner.STATUS),
            Map.entry("BREAK", Owner.COMBAT),
            Map.entry("BURN", Owner.STATUS),
            Map.entry("COMBO", Owner.COMBAT),
            Map.entry("COOLDOWN", Owner.COMBAT),
            Map.entry("CORRUPTION", Owner.WORLD),
            Map.entry("COUNTER", Owner.COMBAT),
            Map.entry("CRAFT_CONSERVE", Owner.ECONOMY),
            Map.entry("DODGE", Owner.COMBAT),
            Map.entry("DODGE_COST", Owner.COMBAT),
            Map.entry("ENCOUNTER", Owner.WORLD),
            Map.entry("EQUIPMENT", Owner.COMBAT),
            Map.entry("GUARD", Owner.COMBAT),
            Map.entry("HEAVY", Owner.COMBAT),
            Map.entry("LOW_HP_BURST", Owner.COMBAT),
            Map.entry("MAGIC", Owner.COMBAT),
            Map.entry("MARK", Owner.COMBAT),
            Map.entry("MOBILITY", Owner.COMBAT),
            Map.entry("NOMAD", Owner.WORLD),
            Map.entry("PARRY", Owner.COMBAT),
            Map.entry("PARTY_AMMO_CRAFT", Owner.ECONOMY),
            Map.entry("PARTY_AP_REGEN", Owner.COMBAT),
            Map.entry("PARTY_RESOURCE", Owner.ECONOMY),
            Map.entry("PARTY_REVIVE", Owner.COMBAT),
            Map.entry("PATTERN", Owner.COMBAT),
            Map.entry("POISON", Owner.STATUS),
            Map.entry("RANGED", Owner.COMBAT),
            Map.entry("RECONSTRUCTION", Owner.FACILITY),
            Map.entry("RELOAD", Owner.COMBAT),
            Map.entry("RESCUE", Owner.COMBAT),
            Map.entry("REVIVE_SPEED", Owner.COMBAT),
            Map.entry("SHIELD", Owner.COMBAT),
            Map.entry("STATUS", Owner.STATUS),
            Map.entry("SURVIVAL", Owner.COMBAT),
            Map.entry("TENACITY", Owner.STATUS),
            Map.entry("TRIDENT_RECALL", Owner.COMBAT),
            Map.entry("UNARMED_COUNTER", Owner.COMBAT),
            Map.entry("UTILITY", Owner.FACILITY)
    );

    private AugmentRuntimeContract() {
    }

    public static boolean supports(String opcode) {
        return OWNERS.containsKey(opcode);
    }

    public static Owner owner(String opcode) {
        Owner owner = OWNERS.get(opcode);
        if (owner == null) {
            throw new IllegalArgumentException("Unknown augment opcode " + opcode);
        }
        return owner;
    }

    public static Set<String> opcodes() {
        return OWNERS.keySet();
    }
}
