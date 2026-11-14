package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 305 (D173, tester 12/11): Overwhelming Stampede is kept while the attack it makes is weak (two
// small creatures against two walls), cast when it is lethal or brings six more damage with three attackers or more.
public class MassPump305Test extends AITest {

    private Game setUp(final String[] mine, final String[] theirs, final int oppLife) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Forest", 5, ai);
        for (final String c : mine) {
            addCard(c, ai).setSickness(false);
        }
        for (final String c : theirs) {
            addCard(c, opponent);
        }
        opponent.setLife(oppLife, null);
        addCardToZone("Overwhelming Stampede", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);
        return game;
    }

    private int cast(final Game game) {
        return countCardsWithName(game, "Overwhelming Stampede", ZoneType.Graveyard);
    }

    @Test
    public void testKeptWithTwoSmallAttackers() {
        Game game = setUp(new String[] {"Llanowar Elves", "Jackal Pup"}, new String[] {"Horned Turtle", "Horned Turtle"}, 16);
        assertEquals(cast(game), 0, gameStateToString(game));
    }

    @Test
    public void testCastWhenLethal() {
        Game game = setUp(new String[] {"Grizzly Bears", "Grizzly Bears", "Grizzly Bears"}, new String[] {}, 10);
        assertEquals(cast(game), 1, gameStateToString(game));
    }

    @Test
    public void testCastForBigGain() {
        Game game = setUp(new String[] {"Grizzly Bears", "Grizzly Bears", "Grizzly Bears", "Grizzly Bears"},
                new String[] {"Horned Turtle"}, 30);
        assertEquals(cast(game), 1, gameStateToString(game));
    }
}
