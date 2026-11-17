package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools rule 320 (a tester, 17/11): Melira + Kitchen Finks + Altar of Dementia stayed
// on the battlefield through the opponent's upkeep, draw step and main phase 1 without looping, until a Cleansing Nova.
// MillAi only activated a mill ability from main phase 2 on. A winning mill loop now runs at any priority.
public class MillLoopAnyTime320Test extends AITest {

    private Game board(Player[] out, boolean melira) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        if (melira) {
            addCard("Melira, Sylvok Outcast", ai).setSickness(false);
        }
        addCard("Kitchen Finks", ai).setSickness(false);
        addCard("Altar of Dementia", ai);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 48);
        out[0] = ai;
        out[1] = opponent;
        return game;
    }

    // the opponent's upkeep: milled out before the draw step, the opponent loses when it draws
    @Test(timeOut = 120000)
    public void testLoopsInOpponentUpkeep() {
        Player[] p = new Player[2];
        Game game = board(p, true);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, p[1]);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN1);
        assertTrue(game.isGameOver() || p[1].getCardsIn(ZoneType.Library).isEmpty(),
                "library " + p[1].getCardsIn(ZoneType.Library).size());
    }

    // without Melira the loop is not winning: no mill before main phase 2
    @Test(timeOut = 120000)
    public void testNoLoopNoEarlyMill() {
        Player[] p = new Player[2];
        Game game = board(p, false);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, p[1]);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN1);
        assertTrue(p[1].getCardsIn(ZoneType.Library).size() >= 47, "library " + p[1].getCardsIn(ZoneType.Library).size());
    }
}
