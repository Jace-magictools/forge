package forge.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 257 (D147 point 3, tester 08/11, a test game decisions 46-47). At 2 life against
 * Archon of Sun's Grace, two Pegasus tokens, Arctic Aven and Invisible Stalker (10 damage none of our ground creatures
 * can block), the AI declared no attack in main 1 and cast Winota in main 2: holding back blockers defends nothing.
 * When the opponents' creatures that none of ours can block already deal at least our life next turn (after the best
 * blocks our flash creatures in hand could make, no fog remembered), the AI is doomed on defence: it attacks all out,
 * and keeps no creature home as a blocker. AttackTriggerMain1Ai then sees the attackers and casts Winota first.
 */
public final class DoomedAttack257 {
    private DoomedAttack257() { }

    public static boolean doomed(final Player ai) {
        if (ai.cantLoseForZeroOrLessLife() || !ai.canLoseLife()) {
            return false;
        }
        if (ai.getGame().getPhaseHandler().getNextTurn().equals(ai)) {
            return false;
        }
        if (!AiCardMemory.isMemorySetEmpty(ai, AiCardMemory.MemorySet.CHOSEN_FOG_EFFECT)) {
            return false;
        }
        final List<Card> mine = ai.getCreaturesInPlay();
        final List<Integer> unblockable = new ArrayList<>();
        for (final Card att : ai.getOpponents().getCreaturesInPlay()) {
            if (att.hasSVar("EndOfTurnLeavePlay") || !ComputerUtilCombat.canAttackNextTurn(att)) {
                continue;
            }
            boolean blockable = false;
            for (final Card b : mine) {
                if (CombatUtil.canBlock(att, b, true)) {
                    blockable = true;
                    break;
                }
            }
            if (!blockable) {
                final int dmg = ComputerUtilCombat.damageIfUnblocked(att, ai, null, false);
                if (dmg > 0) {
                    unblockable.add(dmg);
                }
            }
        }
        if (unblockable.isEmpty()) {
            return false;
        }
        // flash creatures in hand may still block the biggest ones
        int flash = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isCreature() && c.hasKeyword(Keyword.FLASH)) {
                flash++;
            }
        }
        unblockable.sort(Collections.reverseOrder());
        int total = 0;
        for (int i = flash; i < unblockable.size(); i++) {
            total += unblockable.get(i);
        }
        return total >= ai.getLife();
    }
}
