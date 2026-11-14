package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Magus of the Bazaar (Oasis) was marked AI:RemoveDeck:All. "Draw two, discard three" is a
 * card lost each time, unless the discards are worth something: a creature to embalm or eternalize, a
 * flashback / unearth card, a spare land, and a discard payoff of ours (Drake Haven, Scrounging Skyray).
 * With AILogic$ Bazaar the AI activates it at the end of the opponent's turn, stack empty, when the hand
 * holds two such discards (one with a payoff in play), and discards those first.
 */
public final class BazaarAi {
    private BazaarAi() { }

    public static boolean isBazaar(final SpellAbility sa) {
        return sa != null && "Bazaar".equals(sa.getRootAbility().getParam("AILogic"));
    }

    public static boolean shouldActivate(final Player ai, final SpellAbility sa) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.is(PhaseType.END_OF_TURN) || !ph.getNextTurn().equals(ai) || !ai.getGame().getStack().isEmpty()) {
            return false;
        }
        if (ai.getCardsIn(ZoneType.Library).size() < 5) {
            return false;
        }
        int good = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (goodDiscard(ai, c, sa.getHostCard())) {
                good++;
            }
        }
        return good >= (hasDiscardPayoff(ai) ? 1 : 2);
    }

    /** discards for the Bazaar: good ones first, the rest chosen by Forge's usual logic */
    public static CardCollection chooseDiscards(final AiController aic, final Player ai, final SpellAbility sa,
            final int min, final int max, final CardCollection validCards) {
        final CardCollection result = new CardCollection();
        final CardCollection rest = new CardCollection(validCards);
        for (final Card c : validCards) {
            if (result.size() >= min) {
                break;
            }
            if (goodDiscard(ai, c, sa.getHostCard())) {
                result.add(c);
                rest.remove(c);
            }
        }
        if (result.size() < min) {
            result.addAll(aic.getCardsToDiscard(min - result.size(), max - result.size(), rest, null));
        }
        return result;
    }

    static boolean goodDiscard(final Player ai, final Card c, final Card source) {
        return graveyardValue(c) || c.hasSVar("DiscardMe") || SpareLandAi.isSpare(ai, c, source);
    }

    /** the card still works from the graveyard: also Forge's generic rule for discard costs (ComputerUtil) */
    public static boolean graveyardValue(final Card c) {
        return c.hasKeyword(Keyword.EMBALM) || c.hasKeyword(Keyword.ETERNALIZE) || c.hasKeyword(Keyword.FLASHBACK)
                || c.hasKeyword(Keyword.UNEARTH) || c.hasKeyword(Keyword.JUMP_START) || c.hasKeyword(Keyword.ESCAPE)
                || c.hasKeyword(Keyword.DISTURB) || c.hasKeyword(Keyword.ENCORE) || c.hasKeyword(Keyword.SCAVENGE)
                || c.hasSVar("IsReanimatorCard");
    }

    /** a permanent of ours triggers when we discard (Drake Haven, Scrounging Skyray, Bone Miser...) */
    static boolean hasDiscardPayoff(final Player ai) {
        for (final Card p : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : p.getTriggers()) {
                if (t.getMode() == TriggerType.Discarded || t.getMode() == TriggerType.DiscardedAll) {
                    return true;
                }
            }
        }
        return false;
    }
}
