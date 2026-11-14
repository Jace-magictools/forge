package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 259 (D147 point 5, tester 08/11, a test game step 125): turn 10, the AI played its
 * land in main 1 and cast Ruin Crab in main 2, losing the landfall trigger. Rule 103 picks another land for the drop
 * when the payoff in hand would count this one, but plays it anyway when every land counts. A permanent of the hand
 * whose trigger counts a land we can still play this turn is offered before the land drop (as PlayBeforeLandDrop
 * cards are); the usual checks decide whether it is cast, and the land comes after it.
 */
public final class LandfallFirst259 {
    private LandfallFirst259() { }

    public static CardCollection beforeLand(final Player ai) {
        final CardCollection out = new CardCollection();
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || !game.getStack().isEmpty()) {
            return out;
        }
        final CardCollection lands = ComputerUtilAbility.getAvailableLandsToPlay(game, ai);
        if (lands == null || lands.isEmpty()) {
            return out;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (!c.isPermanent() || c.isLand() || c.getFirstSpellAbility() == null) {
                continue;
            }
            for (final Card l : lands) {
                if (ArrivalPayoffAi.counts(ai, c, l)) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }
}
