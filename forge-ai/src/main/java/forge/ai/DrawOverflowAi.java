package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 172 (relevé « mana perdu » v210, Dragoonz - Vampire t4). DrawAi refuses a draw spell on our turn
 * whenever the hand would end above its maximum size, "to avoid discarding at end of turn". With two Swamps in play and
 * four more in hand, Vampire kept Sign in Blood for good: the cleanup discard would only have cost a spare land.
 * The overflow is harmless when the hand (without the draw spell) keeps more lands than the overflow, so one land is
 * still left for the next land drop after discarding the extra ones.
 */
public final class DrawOverflowAi {
    private DrawOverflowAi() { }

    public static boolean overflowHarmless(final Player ai, final Card source, final int handSize, final int numCards) {
        final int overflow = handSize + numCards - ai.getMaxHandSize();
        if (overflow <= 0) {
            return true;
        }
        int lands = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c != source && c.isLand()) {
                lands++;
            }
        }
        return lands > overflow;
    }
}
