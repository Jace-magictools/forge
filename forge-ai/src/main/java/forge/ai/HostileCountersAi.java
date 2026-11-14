package forge.ai;

import forge.game.ability.ApiType;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools, rule 182 (general rule): putting -1/-1 counters on a target creature is an attack even when the
 * script forgets IsCurse$ True. Without the flag the AI took the counters for a boon and aimed them at its own
 * creatures: Lame de Morgul (three -1/-1 counters, then a Specter token if it dies) killed its own 3/x while the
 * opponent had no creature. Treated as a curse, the spell targets an opponent's creature or is not cast.
 * Left alone: scripts with an AILogic, targets restricted to our own creatures, and the regeneration trick
 * (Gore Vassal) where the counter is meant for our side.
 */
public final class HostileCountersAi {
    private HostileCountersAi() { }

    public static boolean isCurse(final SpellAbility sa) {
        if (sa.isCurse()) {
            return true;
        }
        if (!sa.usesTargeting() || !"M1M1".equals(sa.getParam("CounterType")) || sa.hasParam("AILogic")) {
            return false;
        }
        final String valid = sa.getParamOrDefault("ValidTgts", "");
        if (valid.contains("YouCtrl") || valid.contains("YouOwn")) {
            return false;
        }
        return sa.findSubAbilityByType(ApiType.Regenerate) == null;
    }
}
