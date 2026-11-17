package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools rule 322 (a tester, 17/11): Thran Vigil + Safehold Elite + Altar of Dementia
// (and Nantuko Husk, Kitchen Finks) on the AI's battlefield, opponent at 14 with no creature. The AI attacked, then
// looped Kitchen Finks into Nantuko Husk after combat for life. The mill loop was seen (PersistLoopAi.millLoopCard)
// but the sacrifice was refused: only a SacMe card (what Melira gives) paid the outlet.
public class LoopPiecePaysOutlet322Test extends AITest {

    private Game board(Player[] out, boolean finks) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Thran Vigil", ai);
        Card elite = addCard("Safehold Elite", ai);
        elite.setSickness(false);
        elite.addCounterInternal(CounterEnumType.P1P1, 2, ai, false, null, null);
        addCard("Nantuko Husk", ai).setSickness(false);
        if (finks) {
            addCard("Kitchen Finks", ai);
        }
        addCard("Altar of Dementia", ai);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 49);
        opponent.setLife(14, null);
        out[0] = ai;
        out[1] = opponent;
        return game;
    }

    @Test(timeOut = 120000)
    public void testMillsOutInMain1() {
        Player[] p = new Player[2];
        Game game = board(p, false);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p[0]);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(game.isGameOver() || p[1].getCardsIn(ZoneType.Library).isEmpty(),
                "library " + p[1].getCardsIn(ZoneType.Library).size());
    }

    @Test(timeOut = 120000)
    public void testMillsOutRatherThanHuskLifeLoop() {
        Player[] p = new Player[2];
        Game game = board(p, true);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p[0]);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(game.isGameOver() || p[1].getCardsIn(ZoneType.Library).isEmpty(),
                "library " + p[1].getCardsIn(ZoneType.Library).size() + ", AI life " + p[0].getLife());
    }
}
