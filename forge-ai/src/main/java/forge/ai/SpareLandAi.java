package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * magic-tools: ComputerUtil.isWorseThanDraw keeps every land until six are in play (the highest CMC
 * in hand is floored at 6), so Magmatic Insight never gets cast in a low-curve burn deck.
 * A land is spare for a "discard a land: draw" spell when the lands in play already cast everything
 * else in hand (at least 3), and either 4 are in play or another land stays in hand.
 */
public final class SpareLandAi {
    private SpareLandAi() { }

    public static boolean isSpare(final Player ai, final Card discard, final Card source) {
        if (!discard.isLand()) {
            return false;
        }
        final int inPlay = CardLists.filter(ai.getCardsIn(ZoneType.Battlefield), CardPredicates.LANDS_PRODUCING_MANA).size();
        final CardCollection hand = new CardCollection(ai.getCardsIn(ZoneType.Hand));
        hand.remove(source);
        hand.remove(discard);
        int highest = 3;
        int landsLeft = 0;
        for (final Card c : hand) {
            if (c.isLand()) {
                landsLeft++;
            } else {
                highest = Math.max(highest, c.getCMC());
            }
        }
        return inPlay >= highest && (inPlay >= 4 || landsLeft > 0);
    }
}
