package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 301 (tester 12/11, D170): the AI tapped a land, then cast Wild Growth on it, losing the bonus
// mana of the turn. The aura goes on an untapped land, and that land does not pay the aura.
public class ManaAura301Test extends AITest {

    private Game play(final int tapped) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < tapped; i++) {
            addCard("Forest", ai).setTapped(true);
        }
        addCards("Forest", 2, ai);
        addCardToZone("Wild Growth", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    private static Card enchanted(Game game) {
        return game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Forest") && c.isEnchanted()).findFirst().orElse(null);
    }

    @Test
    public void testUntappedLandEnchantedAndNotTapped() {
        for (int tapped : new int[] {0, 1, 2}) {
            Game game = play(tapped);
            Card land = enchanted(game);
            assertTrue(land != null && !land.isTapped(), tapped + " " + gameStateToString(game));
        }
    }
}
