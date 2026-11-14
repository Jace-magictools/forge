package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Lobelia Sackville-Baggins (ketramos, tester ; Sauron, tester) was marked AI:RemoveDeck:All.
 * Her ETB exiles an opposing creature card put into a graveyard from the battlefield this turn for that
 * many Treasures: with flash she waits for a creature to die (ours killed it, or combat), and otherwise
 * comes down as a 2/3 menace at the opponent's end step. Card SVar AIFlashTreasure:True.
 */
public final class TreasureFlashAi {
    private TreasureFlashAi() { }

    public static final String VALID = "Creature.ThisTurnEnteredFrom_Battlefield+OppCtrl";

    public static boolean applies(final Card card) {
        return card.hasSVar("AIFlashTreasure");
    }

    /** Best power among the opposing creature cards that died this turn, 0 if none. */
    public static int bestTarget(final Player ai, final Card host) {
        final CardCollection dead = new CardCollection();
        for (final Player opp : ai.getOpponents()) {
            dead.addAll(opp.getCardsIn(ZoneType.Graveyard));
        }
        int best = 0;
        for (final Card c : CardLists.getValidCards(dead, VALID, ai, host, null)) {
            best = Math.max(best, c.getNetPower());
        }
        return best;
    }

    public static boolean wantToCast(final Player ai, final SpellAbility sa) {
        final Card card = sa.getHostCard();
        if (bestTarget(ai, card) > 0) {
            return true;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ai.getGame().getStack().isEmpty()) {
            return false;
        }
        // nothing to eat: a body before our turn, or before discarding at our own cleanup
        if (ph.is(PhaseType.END_OF_TURN) && ph.getNextTurn().equals(ai)) {
            return true;
        }
        return ph.is(PhaseType.END_OF_TURN, ai) && !ai.isUnlimitedHandSize()
                && ai.getCardsIn(ZoneType.Hand).size() > ai.getMaxHandSize();
    }
}
