package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 159 (bench vs human decisions a test game): Kili the Resourceful draws once each turn, so one
// equipment waits for it and the other one comes down now instead of wasting the mana
public class ArrivalPayoff159Test extends AITest {

    private Game setup(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testOneEquipmentWaitsTheOtherComesDown() {
        Game game = setup("Kíli the Resourceful", "Lavaspur Boots", "Lost Jitte");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        int down = countCardsWithName(game, "Lavaspur Boots", ZoneType.Battlefield)
                + countCardsWithName(game, "Lost Jitte", ZoneType.Battlefield);
        assertEquals(down, 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Kíli the Resourceful", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testLoneEquipmentStillWaits() {
        Game game = setup("Kíli the Resourceful", "Lavaspur Boots", "Plains"); // rule 215: the wait needs a land drop
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Lavaspur Boots", ZoneType.Hand), 1, gameStateToString(game));
    }
}
