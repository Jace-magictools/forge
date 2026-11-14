package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 289 (11/11, Profanation): Exhume was cast with a weak creature in the AI's graveyard while the
// opponent got back a better one
public class SymmetricReanimate289Test extends AITest {

    private Game setup(String mine, String theirs) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCardToZone("Exhume", ai, ZoneType.Hand);
        addCardToZone(mine, ai, ZoneType.Graveyard);
        addCardToZone(theirs, opponent, ZoneType.Graveyard);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        return game;
    }

    @Test(timeOut = 120000)
    public void testNoExhumeForTheOpponent() {
        Game game = setup("Grizzly Bears", "Griselbrand");
        assertEquals(countCardsWithName(game, "Exhume", ZoneType.Hand), 1);
    }

    @Test(timeOut = 120000)
    public void testExhumeWhenMineIsBetter() {
        Game game = setup("Griselbrand", "Grizzly Bears");
        assertEquals(countCardsWithName(game, "Griselbrand", ZoneType.Battlefield), 1);
    }
}
