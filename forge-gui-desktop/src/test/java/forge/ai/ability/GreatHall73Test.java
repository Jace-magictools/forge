package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 73 (a tester reports 29/10 11:36 and 11:38, Auriok - Aragorn): with Island + Great Hall of the
// Citadel, Aragorn activated the Hall ({1},{T}: two mana for legendary spells) and cast nothing; Sphere of the Suns
// (Island + the Hall's {C}) or Pippin (the Hall's two colored mana) was castable
public class GreatHall73Test extends AITest {

    private Game setup(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Great Hall of the Citadel", ai, ZoneType.Battlefield);
        addCardToZone("Island", ai, ZoneType.Battlefield);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testSphereOrPippin() {
        Game game = setup("No More Lies", "Faramir, Steward of Gondor", "Flame of Anor", "Sphere of the Suns",
                "Pippin, Guard of the Citadel");
        int cast = countCardsWithName(game, "Sphere of the Suns", ZoneType.Battlefield)
                + countCardsWithName(game, "Pippin, Guard of the Citadel", ZoneType.Battlefield);
        assertTrue(cast >= 1, gameStateToString(game));
    }

    @Test
    public void testSphereAlone() {
        Game game = setup("No More Lies", "Faramir, Steward of Gondor", "Sphere of the Suns");
        assertTrue(countCardsWithName(game, "Sphere of the Suns", ZoneType.Battlefield) == 1, gameStateToString(game) + game.getGameLog().getLogEntries(null));
    }

    @Test
    public void testPippinWithTheHallColors() {
        Game game = setup("Pippin, Guard of the Citadel");
        assertTrue(countCardsWithName(game, "Pippin, Guard of the Citadel", ZoneType.Battlefield) == 1,
                gameStateToString(game) + game.getGameLog().getLogEntries(null));
    }
}
