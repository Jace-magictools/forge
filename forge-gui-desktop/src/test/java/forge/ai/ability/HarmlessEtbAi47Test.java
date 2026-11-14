package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

/** Rule 47: a pump of ourselves (player) or exiling a card from a graveyard doesn't keep the permanent in hand. */
public class HarmlessEtbAi47Test extends AITest {

    private boolean castInMain2(String name, String... lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String l : lands) {
            addCard(l, ai);
        }
        addCardToZone(name, ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals(name));
    }

    // "you gain protection from everything until your next turn" (it was BadEtbEffects)
    @Test
    public void testTheOneRingCast() {
        assertTrue(castInMain2("The One Ring", "Plains", "Plains", "Plains", "Plains"), "The One Ring kept in hand");
    }

    // "exile a card from a graveyard" with every graveyard empty
    @Test
    public void testCemeteryIlluminatorCast() {
        assertTrue(castInMain2("Cemetery Illuminator", "Island", "Island", "Island"), "Cemetery Illuminator kept in hand");
    }

    @Test
    public void testRestInPeaceCast() {
        assertTrue(castInMain2("Rest in Peace", "Plains", "Plains"), "Rest in Peace kept in hand");
    }
}
