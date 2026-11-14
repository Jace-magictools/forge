package forge.ai;

import java.util.List;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.player.Player;

/**
 * magic-tools: rule 183 (plateau D105, tester 04/11). Uruk deck, AI at 20 life: an Acoucheur d'Uruk 3/2 (black) stayed
 * home facing only Sam Gamegie 1/3 with protection from black. Sam can block it forever, but nothing can kill it:
 * attacking costs nothing (Sam blocks and nothing happens) and deals 3 when he does not. Forge's moderate aggression
 * levels (2-3) only attack when the attacker also kills its blocker. Attack when the creature is not needed at home
 * (already filtered by notNeededAsBlockers), deals damage, and no single blocker nor all the possible blockers together
 * can kill it.
 */
public final class FreeAttackAi {
    private FreeAttackAi() { }

    public static boolean freeAttacker(final Player ai, final Card attacker, final List<Card> defenders, final Combat combat,
            final boolean canBeKilled, final boolean canBeKilledByOne) {
        if (canBeKilled || canBeKilledByOne || attacker.getNetCombatDamage() <= 0) {
            return false;
        }
        final CardCollection blockers = CardLists.filter(defenders, b -> CombatUtil.canBlock(attacker, b));
        for (final Card b : blockers) {
            if (ComputerUtilCombat.canDestroyAttacker(ai, attacker, b, combat, false)) {
                return false;
            }
        }
        return ComputerUtilCombat.totalDamageOfBlockers(attacker, blockers) < ComputerUtilCombat.getDamageToKill(attacker, false);
    }

    /** An untapped opposing creature can block the attacker and survive it, and nothing tramples over: a pump is wasted. */
    public static boolean stoppedForFree(final Player ai, final Card attacker, final Player defender, final Combat combat) {
        if (defender == null || attacker.hasKeyword(Keyword.TRAMPLE)) {
            return false;
        }
        for (final Card b : defender.getCreaturesInPlay()) {
            if (!b.isTapped() && CombatUtil.canBlock(attacker, b)
                    && !ComputerUtilCombat.canDestroyBlocker(ai, b, attacker, combat, false)) {
                return true;
            }
        }
        return false;
    }
}
