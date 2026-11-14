package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: God-Eternal Bontu (Sacrifice Mutuel, tester). "Sacrifice any number of other permanents, then draw
 * that many cards": Forge sacrificed every permanent it had. Sacrifice only what is worth less than a card:
 * SacMe and undying/persist permanents, non-creature tokens, small creature tokens, and lands beyond what the
 * hand needs. Never draw the library down to fewer than 3 cards.
 */
public final class BontuAi {
    private BontuAi() { }

    /** Below this (about a 2/2 token), a creature token is worth less than a card. */
    static final int TOKEN_THRESHOLD = 160;
    /** Lands kept whatever the hand holds. */
    static final int MIN_LANDS = 4;

    public static CardCollection choose(final Player ai, final CardCollectionView candidates, final SpellAbility sa) {
        final Card host = sa.getHostCard();
        final CardCollection fodder = new CardCollection();
        final CardCollection lands = new CardCollection();
        for (final Card c : candidates) {
            if (c.equals(host) || !c.getController().equals(ai)) {
                continue;
            }
            if (c.isLand()) {
                if (!c.isToken()) {
                    lands.add(c);
                }
                continue;
            }
            if (c.hasSVar("SacMe") || ComputerUtilCard.hasActiveUndyingOrPersist(c)) {
                fodder.add(c);
            } else if (c.isToken() && (!c.isCreature() || ComputerUtilCard.evaluateCreature(c) < TOKEN_THRESHOLD)) {
                fodder.add(c);
            }
        }

        int keep = MIN_LANDS;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (!c.isLand()) {
                keep = Math.max(keep, c.getCMC());
            }
        }
        // lands with abilities besides mana last, tapped ones first
        final CardCollection plain = CardLists.filter(lands, c -> c.getNonManaAbilities().isEmpty());
        final CardCollection ordered = new CardCollection(CardLists.filter(plain, Card::isTapped));
        ordered.addAll(CardLists.filter(plain, c -> !c.isTapped()));
        ordered.addAll(CardLists.filter(lands, c -> !plain.contains(c)));
        for (int i = 0; i < lands.size() - keep; i++) {
            fodder.add(ordered.get(i));
        }

        final int maxDraw = Math.max(0, ai.getCardsIn(ZoneType.Library).size() - 3);
        final CardCollection chosen = new CardCollection();
        for (final Card c : fodder) {
            if (chosen.size() >= maxDraw) {
                break;
            }
            chosen.add(c);
        }
        return chosen;
    }
}
