package forge.ai;

import java.util.HashSet;
import java.util.Set;

import forge.card.MagicColor;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.player.Player;
import forge.game.trigger.Trigger;

/**
 * magic-tools: opening hands of mana-creature decks (ElfBall: 12 lands, 19 one-mana elves). Forge only counted
 * lands, so Forest + Llanowar Elves + Elvish Mystic + ... was a "one-land hand" and ElfBall mulliganed to 5 in
 * most games. General rule: a non-land mana source of mana value 1 that a land of the hand can cast counts as a
 * land for keeping (not for flooding).
 */
public final class MulliganAi {
    private MulliganAi() { }

    /**
     * magic-tools, règle 241 : Forge left every land carrying NeedsToPlay out of the hand's lands, so 2 Orzhov Basilica
     * + Swamp + 4 spells was a one-land hand and a mulligan (tester 6/11 : « pourquoi faire mulligan ? tu avais trois
     * terrains »). NeedsToPlay says what the land waits for on the battlefield; when another land of the hand gives it
     * (a land to return for a karoo, a Mountain for Dormant Volcano, a land to copy for Vesuva), it will be played: it
     * counts. Alone with no such land, it stays out.
     */
    public static CardCollectionView withConditionalLands(final CardCollectionView hand, final Player ai,
            final CardCollectionView lands) {
        CardCollection all = null;
        for (final Card c : hand) {
            if (!c.isLand() || !c.hasSVar("NeedsToPlay") || c.getManaCost().getCMC() > 0) {
                continue;
            }
            final boolean karoo = KarooAi.isKaroo(c);
            final String[] needs = c.getSVar("NeedsToPlay").replace(';', ',').split(",");
            for (final Card other : lands) {
                if (other.isLand() && (karoo || other.isValid(needs, ai, c, null))) {
                    if (all == null) {
                        all = new CardCollection(lands);
                    }
                    all.add(c);
                    break;
                }
            }
        }
        return all == null ? lands : all;
    }

    public static int cheapManaSources(final CardCollectionView hand, final CardCollectionView lands) {
        if (lands.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (final Card c : hand) {
            if (c.isLand() || lands.contains(c) || c.getManaAbilities().isEmpty()) {
                continue;
            }
            if (c.getManaCost().getCMC() != 1) {
                continue;
            }
            if (castableWith(c, lands)) {
                count++;
            }
        }
        return count;
    }

    /**
     * magic-tools: flooding for the deck's curve. Forge only mulliganed 6 lands of 7, so a low-curve deck kept
     * 5 lands and 2 spells (Discard, mean mana value 2.2: reported by tester 28/10). General rule: a first
     * 7-card hand with 5 lands or more is a mulligan when the nonland cards of the deck (hand and library) have
     * a mean mana value of 2.5 or less: such a deck needs 3 or 4 lands, not 5.
     */
    public static boolean floodedForCurve(final CardCollectionView hand, final CardCollectionView library,
            final int realLands) {
        if (hand.size() < 7 || realLands < 5) {
            return false;
        }
        int spells = 0;
        int total = 0;
        for (final CardCollectionView zone : java.util.List.of(hand, library)) {
            for (final Card c : zone) {
                if (c.isLand()) {
                    continue;
                }
                spells++;
                total += c.getManaCost().getCMC();
            }
        }
        return spells > 0 && total <= 2.5 * spells;
    }

    /**
     * magic-tools, règle 91 : the London mulligan tucked a cheap mana elf or a lord of a one-land ElfBall hand and
     * kept Elvish Harbinger (tester 29/10 11:54). General rule: when the kept hand is short of lands, the card
     * to put back is first a creature that enters and searches the library for a card put on top (it only
     * replaces a draw, for 3 mana the hand cannot spend yet), then the nonland card of highest mana value that is
     * not a mana source of mana value 2 or less. Null when every nonland card is a cheap mana source.
     */
    public static Card tuckWhenShort(final CardCollectionView hand, final java.util.Collection<Card> toReturn) {
        Card tutor = null;
        Card dearest = null;
        for (final Card c : hand) {
            if (c.isLand() || toReturn.contains(c)) {
                continue;
            }
            final int cmc = c.getManaCost().getCMC();
            if (!c.getManaAbilities().isEmpty() && cmc <= 2) {
                continue;
            }
            if (tutorsToTop(c) && (tutor == null || cmc > tutor.getManaCost().getCMC())) {
                tutor = c;
            }
            if (dearest == null || cmc > dearest.getManaCost().getCMC()) {
                dearest = c;
            }
        }
        return tutor != null ? tutor : dearest;
    }

    /** Enters and searches the library for a card put on top (Elvish Harbinger, Personal Tutor-like bodies). */
    static boolean tutorsToTop(final Card c) {
        // read the script, not t.ensureAbility(): building the trigger's ability can change later AI choices
        for (final Trigger t : c.getTriggers()) {
            if (!"Battlefield".equals(t.getParam("Destination")) || !"Card.Self".equals(t.getParam("ValidCard"))) {
                continue;
            }
            final String exec = t.getParam("Execute");
            final String sv = exec == null || !c.hasSVar(exec) ? "" : c.getSVar(exec);
            if (sv.contains("ChangeZone") && sv.contains("Origin$ Library") && sv.contains("Destination$ Library")
                    && sv.contains("LibraryPosition$ 0")) {
                return true;
            }
        }
        return false;
    }

    private static boolean castableWith(final Card c, final CardCollectionView lands) {
        final Set<String> colors = new HashSet<>();
        for (final byte col : MagicColor.WUBRG) {
            if ((c.getManaCost().getColorProfile() & col) != 0) {
                colors.add(MagicColor.toLongString(col));
            }
        }
        if (colors.isEmpty()) {
            return true;
        }
        for (final Card land : lands) {
            if (land.canProduceColorMana(colors)) {
                return true;
            }
        }
        return false;
    }
}
