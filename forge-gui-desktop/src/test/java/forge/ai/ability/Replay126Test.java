package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 126, Snapcaster Mage waits for the land drop so the flashback it gives can be paid
// (tournament v18, Sorcier: 40 triggers, 13 replays; cast during the upkeep with no mana left)
public class Replay126Test extends AITest {

    @Test
    public void testWaitsForLandDropThenReplaysBolt() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Snapcaster Mage", ai, ZoneType.Hand);
        addCardToZone("Mountain", ai, ZoneType.Hand);
        addCardToZone("Lightning Bolt", ai, ZoneType.Graveyard);
        addCard("Island", ai);
        addCard("Mountain", ai);
        addCard("Plains", opponent);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(countCardsWithName(game, "Lightning Bolt", ZoneType.Exile), 1, gameStateToString(game) + game.getGameLog().getLogEntries(null));
    }
}
