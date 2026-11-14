package forge.ai.ability;

import java.util.List;

import forge.ai.ComputerUtilCard;
import forge.ai.NewAi;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 289 (11/11, Profanation): "each player puts a creature card from their graveyard onto the
 * battlefield" (Exhume) was cast with Sinister Starfish as the only creature in the AI's graveyard while the opponent
 * got back Accursed Marauder. A symmetric reanimation is cast only if the AI's best creature is worth more than the
 * best one any opponent gets back.
 */
public final class SymmetricReanimateAi {
    private SymmetricReanimateAi() { }

    public static boolean badTrade(final Player ai, final SpellAbility sa, final List<ZoneType> origin,
            final String destination, final Iterable<Player> players) {
        if (sa.usesTargeting() || !"Battlefield".equals(destination) || origin == null || origin.size() != 1
                || origin.get(0) != ZoneType.Graveyard || !NewAi.on(ai, 289)) {
            return false;
        }
        final String type = sa.getParamOrDefault("ChangeType", "Card");
        if (!type.contains("Creature")) {
            return false;
        }
        int mine = -1;
        int theirs = -1;
        boolean opponentIn = false;
        for (Player p : players) {
            int best = best(p, type, sa);
            if (p == ai) {
                mine = best;
            } else if (p.isOpponentOf(ai)) {
                opponentIn = true;
                theirs = Math.max(theirs, best);
            }
        }
        return opponentIn && theirs >= mine;
    }

    private static int best(final Player p, final String type, final SpellAbility sa) {
        int best = -1;
        for (Card c : CardLists.getValidCards(p.getCardsIn(ZoneType.Graveyard), type, sa.getActivatingPlayer(),
                sa.getHostCard(), sa)) {
            if (c.isCreature()) {
                best = Math.max(best, ComputerUtilCard.evaluateCreature(c));
            }
        }
        return best;
    }
}
