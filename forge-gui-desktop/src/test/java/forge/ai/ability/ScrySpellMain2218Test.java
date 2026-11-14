package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 218, Elven Farsight kept four turns with 13 mana free (relevé mana perdu v251, merfolk - elfe)
public class ScrySpellMain2218Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Game main2(String land, String spell) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard(land, ai);
        addCardToZone(spell, ai, ZoneType.Hand);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testSorceryScryCastWithTheManaLeftInMain2() {
        Game game = main2("Forest", "Elven Farsight");
        assertTrue(has(game.getPlayers().get(1), ZoneType.Graveyard, "Elven Farsight"), "cast, not left to the dice");
    }

    @Test
    public void testInstantScryStillWaitsForTheOpponentsTurn() {
        Game game = main2("Island", "Opt");
        assertTrue(has(game.getPlayers().get(1), ZoneType.Hand, "Opt"), "an instant keeps its timing");
    }
}
