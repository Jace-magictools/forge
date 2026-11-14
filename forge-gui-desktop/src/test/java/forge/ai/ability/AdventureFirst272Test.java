package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 272 (tester 09/11 12:19): one Island, Merfolk Secretkeeper in hand, nothing threatens us:
// the AI casts Venture Deeper (mill 4) and keeps the creature in exile for later.
public class AdventureFirst272Test extends AITest {

    @Test
    public void testAdventureBeforeCreature() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCardToZone("Merfolk Secretkeeper", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai, 5);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(opponent.getCardsIn(ZoneType.Library).size(), 26, gameStateToString(game));
        assertEquals(ai.getCardsIn(ZoneType.Exile).anyMatch(c -> c.getName().equals("Merfolk Secretkeeper")), true,
                gameStateToString(game));
    }

    @Test
    public void testCreatureWhenInDanger() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        ai.setLife(3, null);
        addCard("Island", ai);
        addCard("Grizzly Bears", opponent);
        addCard("Grizzly Bears", opponent);
        addCardToZone("Merfolk Secretkeeper", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai, 5);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Merfolk Secretkeeper")), true,
                gameStateToString(game));
    }
}
