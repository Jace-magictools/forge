package forge.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;

/**
 * magic-tools: rule 304 (D172, tester 12/11, a test game turn 12). At 17 life, with Nessian Hornbeetle
 * 6/4, Thrashing Brontodon 5/4, Surrak 5/4 and Kraul Harpooner 3/2 ready against Thorïn 3/4 (sacrifice an artifact:
 * indestructible and a +1/+1 counter, a Gold token in play), Thror 3/5 and two 1/3 (8 power) and an opponent at 16,
 * the AI declared no attack: Forge judges each attacker alone, and one blocker that can kill any of them and survive
 * stops them all. A blocker stops one attacker, though. When our attackers that can kill at least one blocker (or
 * cannot be blocked) clearly outpower the opponent's creatures (1.5 times, 2 times for a non-aggro deck), win the
 * life race (fewer turns to kill them than they need to kill us), outnumber the blockers able to kill one of them
 * alone, and the opponent's swing back cannot kill us, they all attack.
 */
public final class ForceRace304Ai {
    private ForceRace304Ai() { }

    public static Set<Card> press(final Player ai, final Player opp, final List<Card> attackers, final List<Card> blockers,
            final Combat combat, final boolean nonAggro) {
        final Set<Card> none = new HashSet<>();
        if (ai == null || opp == null || !NewAi.on(ai, 304) || !opp.canLoseLife() || opp.cantLoseForZeroOrLessLife()) {
            return none;
        }
        final List<Card> chosen = new ArrayList<>();
        int mine = 0;
        for (final Card a : attackers) {
            final int p = a.getNetCombatDamage();
            if (p <= 0) {
                continue;
            }
            boolean useful = true;
            for (final Card b : blockers) {
                if (CombatUtil.canBlock(a, b)) {
                    useful = false;
                    break;
                }
            }
            if (!useful) {
                for (final Card b : blockers) {
                    if (CombatUtil.canBlock(a, b) && ComputerUtilCombat.canDestroyBlocker(ai, b, a, combat, false)) {
                        useful = true;
                        break;
                    }
                }
            }
            if (useful) {
                chosen.add(a);
                mine += p;
            }
        }
        if (chosen.size() < 2) {
            return none;
        }
        int theirs = 0;
        for (final Card c : opp.getCreaturesInPlay()) {
            if (ComputerUtilCombat.canAttackNextTurn(c)) {
                theirs += Math.max(0, c.getNetCombatDamage());
            }
        }
        if (mine < (nonAggro ? 2.0 : 1.5) * theirs || theirs >= ai.getLife()) {
            return none;
        }
        final int oppLife = Math.max(1, opp.getLife() - ComputerUtil.possibleNonCombatDamage(ai, opp));
        final int ourTurns = (oppLife + mine - 1) / mine;
        final int theirTurns = theirs == 0 ? Integer.MAX_VALUE : (ai.getLife() + theirs - 1) / theirs;
        if (ourTurns >= theirTurns) {
            return none;
        }
        int killers = 0;
        for (final Card b : blockers) {
            for (final Card a : chosen) {
                if (CombatUtil.canBlock(a, b) && ComputerUtilCombat.canDestroyAttacker(ai, a, b, combat, false)) {
                    killers++;
                    break;
                }
            }
        }
        if (chosen.size() <= killers) {
            return none;
        }
        return new HashSet<>(chosen);
    }
}
