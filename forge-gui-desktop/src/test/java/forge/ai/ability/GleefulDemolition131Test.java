package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 131, tournament v19, Artob: Gleeful Demolition 103 times in hand, 27 cast (own artifact never targeted)
public class GleefulDemolition131Test extends AITest {

    private Game board(boolean oppArtifact) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCard("Ichor Wellspring", ai);
        addCard("Ensnaring Bridge", ai);
        addCardToZone("Gleeful Demolition", ai, ZoneType.Hand);
        addCard("Plains", opponent);
        addCard("Grizzly Bears", opponent);
        if (oppArtifact) {
            addCard("Myr Enforcer", opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        return game;
    }

    @Test
    public void testOwnWellspringForGoblins() {
        Game game = board(false);
        Player ai = game.getPlayers().get(1);
        playUntilNextTurn(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Ichor Wellspring")), gameStateToString(game));
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Ensnaring Bridge")), gameStateToString(game));
        assertTrue(ai.getCreaturesInPlay().size() >= 3, gameStateToString(game));
    }

    @Test
    public void testOpponentArtifactFirst() {
        Game game = board(true);
        Player opponent = game.getPlayers().get(0);
        playUntilNextTurn(game);
        assertTrue(!opponent.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Myr Enforcer")), gameStateToString(game));
    }
}
