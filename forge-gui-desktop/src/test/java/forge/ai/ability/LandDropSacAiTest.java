package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 12, City of Traitors was sacrificed by the next land drop (Casse Terrain)
public class LandDropSacAiTest extends AITest {

    private Game game(String landInHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Mountain", ai);
        addCard("City of Traitors", ai);
        addCardToZone(landInHand, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    private static boolean hasCity(Player p) {
        return p.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("City of Traitors"));
    }

    @Test
    public void testCityKeptOverMountain() {
        Game game = game("Mountain");
        Player ai = game.getPlayers().get(1);
        assertTrue(hasCity(ai), "City of Traitors sacrificed for a Mountain\n" + gameStateToString(game));
    }

    // a land that makes more mana than the City (Gaea's Cradle aside, none in our decks): a plain land drop
    // without the City still happens
    @Test
    public void testLandStillPlayedWithoutCity() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Mountain", ai);
        addCardToZone("Mountain", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertFalse(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.isLand()), "land not played\n" + gameStateToString(game));
    }
}
