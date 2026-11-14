package forge.ai;

import java.util.Map;

import forge.game.GameType;
import forge.game.card.Card;
import forge.game.player.Player;

/**
 * magic-tools: rule 162 (table à 3-4 du site, 04/11). On defense the AI already counts commander damage
 * (ComputerUtilCombat.getLifeThreateningCommanders), but on offense it never did: the choice of the player to attack
 * only looked at life (« TODO commander damage ») and the lethal check of the attack ignored the 21 commander damage,
 * so an AI whose commander could finish an opponent attacked someone else or held back.
 */
public final class CommanderDamageAttackAi {
    private CommanderDamageAttackAi() { }

    /** The 21 commander damage rule only applies in the Commander variant (Player.checkLoseCondition). */
    static boolean counts(final Player opp) {
        return opp.getGame().getRules().hasAppliedVariant(GameType.Commander);
    }

    /** Commander damage still needed to kill {@code opp} with one of our commanders on the battlefield (MAX_VALUE if none). */
    public static int remaining(final Player ai, final Player opp) {
        if (!counts(opp)) {
            return Integer.MAX_VALUE;
        }
        int best = Integer.MAX_VALUE;
        for (final Card c : ai.getCreaturesInPlay()) {
            if (c.isCommander()) {
                best = Math.min(best, Math.max(0, 21 - opp.getCommanderDamage(c)));
            }
        }
        return best;
    }

    /** True when an unblocked (or trampling) commander finishes {@code opp} with commander damage. */
    public static boolean lethal(final Player opp, final Iterable<Card> unblocked, final Map<Card, Integer> trampleDmg) {
        if (!counts(opp) || opp.cantLose()) {
            return false;
        }
        for (final Card c : unblocked) {
            if (c.isCommander() && c.getNetCombatDamage() > 0
                    && ComputerUtilCombat.damageIfUnblocked(c, opp, null, false) + opp.getCommanderDamage(c) >= 21) {
                return true;
            }
        }
        for (final Map.Entry<Card, Integer> e : trampleDmg.entrySet()) {
            if (e.getKey().isCommander() && e.getValue() + opp.getCommanderDamage(e.getKey()) >= 21) {
                return true;
            }
        }
        return false;
    }
}
