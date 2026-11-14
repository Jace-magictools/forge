package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools rule 284 (D165, tester 11/11): Mal de la Comté ({W}: return target untapped creature you control
// with power 1 or less to hand) + Ancien de Hobbitbourg (draws on arrival) = a card for {W}{W}; the AI kept its
// Plains untapped. Here Riptide Laboratory ({1}{U},{T}: return target Wizard you control) + Sea Gate Oracle:
// at the opponent's end step, with the lands to recast it, the Oracle goes back to hand.
public class RebuyEtb284Test extends AITest {

    private Game game(String wizard, int islands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Riptide Laboratory", ai);
        for (int i = 0; i < islands; i++) {
            addCard("Island", ai);
        }
        addCard(wizard, ai).setSickness(false);
        addCard("Plains", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.UPKEEP);
        return game;
    }

    private static int inHand(Game game, String name) {
        Player ai = game.getPlayers().get(1);
        return (int) ai.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals(name)).count();
    }

    @Test(timeOut = 120000)
    public void testEndStepBouncesArrivalCreature() {
        Game game = game("Sea Gate Oracle", 3);
        assertEquals(inHand(game, "Sea Gate Oracle"), 1, gameStateToString(game));
    }

    // no arrival trigger: nothing to gain
    @Test(timeOut = 120000)
    public void testNoArrivalStays() {
        Game game = game("Prodigal Sorcerer", 3);
        assertEquals(inHand(game, "Prodigal Sorcerer"), 0, gameStateToString(game));
    }

    // two lands only: Sea Gate Oracle (3) could not be cast again next turn
    @Test(timeOut = 120000)
    public void testNotEnoughLandsStays() {
        Game game = game("Sea Gate Oracle", 1);
        assertEquals(inHand(game, "Sea Gate Oracle"), 0, gameStateToString(game));
    }
}
