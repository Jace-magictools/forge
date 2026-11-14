package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Goblin Recruiter (Moria) was marked AI:RemoveDeck:All because the AI stacked every goblin of
 * its library on top, in no order, and then drew nothing else (no land, no removal) for many turns.
 * With AILogic$ StackGoblins it stacks at most {@link #MAX_STACKED} goblins, the best one it can cast soon
 * first (mana value up to lands + 1), then the cheapest.
 */
public final class RecruiterAi {
    static final int MAX_STACKED = 3;

    private RecruiterAi() { }

    public static boolean isRecruiter(final SpellAbility sa) {
        return "StackGoblins".equals(sa.getParam("AILogic"));
    }

    public static Card choose(final Player ai, final SpellAbility sa, final CardCollection fetchList) {
        final String type = sa.getParamOrDefault("ChangeType", "Card");
        final int inLibrary = CardLists.getValidCards(ai.getCardsIn(ZoneType.Library), type, ai, sa.getHostCard(), sa).size();
        final int alreadyChosen = Math.max(0, inLibrary - fetchList.size());
        if (alreadyChosen >= MAX_STACKED) {
            return null;
        }
        // the first card is drawn first: next turn we will have one more land at best
        final int lands = CardLists.count(ai.getCardsIn(ZoneType.Battlefield), CardPredicates.LANDS_PRODUCING_MANA);
        final int castable = lands + 1 + alreadyChosen;
        Card best = null;
        for (final Card c : fetchList) {
            if (best == null || better(c, best, castable)) {
                best = c;
            }
        }
        return best;
    }

    private static boolean better(final Card c, final Card best, final int castable) {
        final boolean cOk = c.getCMC() <= castable;
        final boolean bOk = best.getCMC() <= castable;
        if (cOk != bOk) {
            return cOk;
        }
        if (!cOk) {
            return c.getCMC() < best.getCMC();
        }
        return value(c) > value(best);
    }

    private static int value(final Card c) {
        return c.isCreature() ? ComputerUtilCard.evaluateCreature(c) : 100 + 20 * c.getCMC();
    }
}
