package forge.ai;

import java.util.Comparator;
import java.util.List;

import forge.game.card.Card;

/**
 * magic-tools: each-player edicts (Barter in Blood, Innocent Blood). Solitaire plays one creature at a time,
 * so a mutual sacrifice favours it, above all right after its creature died (tester, 24/10).
 * Each player sacrifices its own worst creatures: cast the spell only if the opponent loses clearly more
 * than we do (at least a small creature's worth, the card spent).
 */
public final class EdictTradeAi {
    private EdictTradeAi() { }

    /** Roughly a 1/1 token: below that, the trade does not pay for the card. */
    static final int MIN_GAIN = 110;

    public static boolean worthCasting(final List<Card> ours, final List<Card> theirs, final int amount) {
        if (theirs.isEmpty()) {
            return false;
        }
        return loss(theirs, amount) - loss(ours, amount) >= MIN_GAIN;
    }

    /** Value of the {@code amount} creatures a player would choose to sacrifice (its worst ones). */
    static int loss(final List<Card> creatures, final int amount) {
        final List<Integer> values = creatures.stream()
                .map(EdictTradeAi::value).sorted(Comparator.naturalOrder()).toList();
        int sum = 0;
        for (int i = 0; i < Math.min(amount, values.size()); i++) {
            sum += values.get(i);
        }
        return sum;
    }

    private static int value(final Card c) {
        return c.hasSVar("SacMe") ? 0 : ComputerUtilCard.evaluateCreature(c);
    }
}
