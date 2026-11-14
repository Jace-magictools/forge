package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Akul the Unrepentant (Nothing Gulch, tester) was marked AI:RemoveDeck:All: the AI would
 * sacrifice any three creatures to put any creature from its hand onto the battlefield.
 * With AILogic$ Akul it only does it in its own main phase, stack empty, when the three creatures it would
 * sacrifice (the worst ones, as the cost payment picks them) are cheap (tokens, mana value 2 or less, SacMe,
 * undying/persist) and the creature put in is one it cannot cast this turn and clearly better than each of them.
 */
public final class AkulAi {
    private static final int SAC_COUNT = 3;
    private static final int MARGIN = 100;

    private AkulAi() { }

    public static boolean isAkul(final SpellAbility sa) {
        return "Akul".equals(sa.getParam("AILogic"));
    }

    public static boolean shouldActivate(final Player ai, final SpellAbility sa) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || !ai.getGame().getStack().isEmpty()) {
            return false;
        }
        final CardCollection fodder = fodder(ai, sa);
        if (fodder == null) {
            return false;
        }
        int worstFodderMax = 0;
        for (final Card c : fodder) {
            if (!isCheap(ai, c)) {
                return false;
            }
            worstFodderMax = Math.max(worstFodderMax, ComputerUtilCard.evaluateCreature(c));
        }
        final Card best = best(ai, sa, candidates(ai, sa));
        return best != null && ComputerUtilCard.evaluateCreature(best) >= worstFodderMax + MARGIN;
    }

    public static Card choose(final Player ai, final SpellAbility sa, final CardCollection fetchList) {
        final Card best = best(ai, sa, fetchList);
        return best != null ? best : ComputerUtilCard.getBestCreatureAI(fetchList);
    }

    /** the creatures the cost payment would sacrifice (getWorstAI three times), or null if fewer than three */
    private static CardCollection fodder(final Player ai, final SpellAbility sa) {
        final Card host = sa.getHostCard();
        final CardCollection pool = CardLists.filter(ai.getCreaturesInPlay(), c -> !c.equals(host));
        if (pool.size() < SAC_COUNT) {
            return null;
        }
        final CardCollection out = new CardCollection();
        for (int i = 0; i < SAC_COUNT; i++) {
            final Card worst = ComputerUtilCard.getWorstCreatureAI(pool);
            out.add(worst);
            pool.remove(worst);
        }
        return out;
    }

    private static boolean isCheap(final Player ai, final Card c) {
        return c.isToken() || c.getCMC() <= 2 || c.hasSVar("SacMe")
                || ComputerUtilCard.hasActiveUndyingOrPersist(c) || ComputerUtilCard.isUselessCreature(ai, c);
    }

    private static CardCollectionView candidates(final Player ai, final SpellAbility sa) {
        final String type = sa.getParamOrDefault("ChangeType", "Creature");
        return CardLists.getValidCards(ai.getCardsIn(ZoneType.Hand), type, ai, sa.getHostCard(), sa);
    }

    /** the best creature we cannot cast this turn */
    private static Card best(final Player ai, final SpellAbility sa, final CardCollectionView list) {
        final int mana = ComputerUtilMana.getAvailableManaEstimate(ai, false);
        Card best = null;
        int bestValue = -1;
        for (final Card c : list) {
            if (!c.isCreature() || c.getCMC() <= mana) {
                continue;
            }
            final int value = ComputerUtilCard.evaluateCreature(c);
            if (value > bestValue) {
                best = c;
                bestValue = value;
            }
        }
        return best;
    }
}
