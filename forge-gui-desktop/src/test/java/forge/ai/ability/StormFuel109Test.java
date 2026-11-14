package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: tester 30/10 12:31, Gobelin - Patrilock: Gut Shot fired at the face for 1 at the end of the opponent's
// turn while Empty the Warrens waited in hand (rule 109: a free spell is Storm fuel)
public class StormFuel109Test extends AITest {

    private Game setup(boolean withWarrens, int opponentLife) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        opponent.setLife(opponentLife, null);
        addCardToZone("Mountain", ai, ZoneType.Battlefield);
        addCardToZone("Gut Shot", ai, ZoneType.Hand);
        if (withWarrens) {
            addCardToZone("Empty the Warrens", ai, ZoneType.Hand);
        }
        addCardToZone("Plains", opponent, ZoneType.Battlefield);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        return game;
    }

    @Test
    public void testGutShotKeptForWarrens() {
        Game game = setup(true, 9);
        assertEquals(countCardsWithName(game, "Gut Shot", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testGutShotLethalStillFired() {
        Game game = setup(true, 1);
        assertEquals(countCardsWithName(game, "Gut Shot", ZoneType.Hand), 0, gameStateToString(game));
    }
}
