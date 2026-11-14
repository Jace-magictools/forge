package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 86, the Urza lands ("Tron": Urza's Mine, Urza's Power-Plant, Urza's Tower). Together they make
 * seven mana from three lands, so a Tron deck holding two different pieces bets on the trio: the pieces go down first,
 * before the basics. Stock AI scored the lands on colors and played Island turn 1 with Urza's Tower and Urza's Mine in
 * hand (a tester report 29/10 15:25, Eldratron - Gwyll: "le bleu ne sert à rien").
 */
public final class TronAi {
    private static final String[] PIECES = {"Mine", "Power-Plant", "Tower"};

    private TronAi() { }

    private static String piece(final Card c) {
        if (c == null || !c.isLand() || !c.getType().hasSubtype("Urza's")) {
            return null;
        }
        for (final String p : PIECES) {
            if (c.getType().hasSubtype(p)) {
                return p;
            }
        }
        return null;
    }

    private static boolean anyPiece(final CardCollectionView cards, final String p) {
        return cards.anyMatch(c -> p.equals(piece(c)));
    }

    /**
     * The Tron pieces of landList not yet in play, when the trio is on its way (two different pieces between
     * battlefield and hand, the missing ones still in hand or library); otherwise an empty list.
     */
    public static CardCollection piecesFirst(final Player ai, final CardCollection landList) {
        final CardCollectionView inPlay = ai.getCardsIn(ZoneType.Battlefield);
        final CardCollectionView hand = ai.getCardsIn(ZoneType.Hand);
        final CardCollectionView library = ai.getCardsIn(ZoneType.Library);
        int known = 0;
        for (final String p : PIECES) {
            if (anyPiece(inPlay, p) || anyPiece(hand, p)) {
                known++;
            } else if (!anyPiece(library, p)) {
                return new CardCollection();
            }
        }
        if (known < 2) {
            return new CardCollection();
        }
        return CardLists.filter(landList, c -> {
            final String p = piece(c);
            return p != null && !anyPiece(inPlay, p);
        });
    }
}
