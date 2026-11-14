package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 24, Force of Rage pitched in the opponent's upkeep with nothing to block (Precipitron)
public class ShortLivedTokenAiTest extends AITest {

    @Test
    public void testForceOfRageKeptOnQuietOpponentTurn() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Mountain", ai);
        addCardToZone("Force of Rage", ai, ZoneType.Hand);
        addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        addCards("Plains", 2, opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(countCardsWithName(game, "Force of Rage", ZoneType.Hand), 1,
                "Force of Rage wasted on the opponent's turn\n" + gameStateToString(game));
    }

    @Test
    public void testForceOfRageCastOnOwnTurn() {
        // magic-tools: fixed seed, the result depended on the random state left by the rest of the suite
        forge.util.MyRandom.setRandom(new java.util.Random(1));
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", 3, ai);
        addCardToZone("Force of Rage", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(opponent.getLife() <= 14, "Force of Rage not cast and attacking on our turn\n" + gameStateToString(game));
    }
}
