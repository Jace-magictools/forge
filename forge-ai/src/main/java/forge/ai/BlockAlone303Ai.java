package forge.ai;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;

/**
 * magic-tools: rule 303 (tester 12/11, plateau, D171 point 1): the AI kept two 5/4 at home because the human's only
 * creature, Troll des Cavernes (5/6, "can't block unless at least two other creatures block"), was counted as a
 * blocker. Forge applies that restriction (and "can't block alone") only when blocks are declared: the attack AI
 * listed the troll as a possible blocker, and the block simulation it runs had it block alone. General rule: a creature
 * whose block needs N other blockers is not a possible blocker when fewer than N other creatures can block, in the
 * attack AI's blocker list and in the block AI's candidates (hence in every simulated block).
 */
public final class BlockAlone303Ai {
    private BlockAlone303Ai() { }

    /** number of other blockers this creature needs to block (0: none) */
    static int othersNeeded(final Card c) {
        if (c.hasKeyword("CARDNAME can't block unless at least two other creatures block.")) {
            return 2;
        }
        if (c.hasKeyword("CARDNAME can't attack or block alone.") || c.hasKeyword("CARDNAME can't block alone.")) {
            return 1;
        }
        return 0;
    }

    /** blockers already able to block (attack AI list): drop the restricted ones until the rest is consistent */
    public static List<Card> filter(final List<Card> blockers, final Player gate) {
        if (gate == null || !NewAi.on(gate, 303)) {
            return blockers;
        }
        List<Card> out = blockers;
        while (true) {
            final int n = out.size();
            final List<Card> next = new ArrayList<>(n);
            for (final Card c : out) {
                if (othersNeeded(c) <= n - 1) {
                    next.add(c);
                }
            }
            if (next.size() == n) {
                return out;
            }
            out = next;
        }
    }

    /** all creatures of the blocking player (block AI): same, counting those that can block at all */
    public static List<Card> filterCandidates(final List<Card> creatures, final Player gate) {
        if (gate == null || !NewAi.on(gate, 303)) {
            return creatures;
        }
        boolean any = false;
        for (final Card c : creatures) {
            if (othersNeeded(c) > 0) {
                any = true;
                break;
            }
        }
        if (!any) {
            return creatures;
        }
        final List<Card> able = new ArrayList<>();
        final List<Card> unable = new ArrayList<>();
        for (final Card c : creatures) {
            (CombatUtil.canBlock(c) ? able : unable).add(c);
        }
        final List<Card> kept = filter(able, gate);
        if (kept.size() == able.size()) {
            return creatures;
        }
        final List<Card> out = new ArrayList<>(kept);
        out.addAll(unable);
        return out;
    }
}
