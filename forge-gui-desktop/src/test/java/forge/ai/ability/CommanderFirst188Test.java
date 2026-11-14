package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 188, the first cast of the commander goes before a bigger spell
public class CommanderFirst188Test extends AITest {

    private static boolean inPlay(Player p, String name) {
        return p.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals(name));
    }

    private Game play(int previousCasts) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 6; i++) {
            addCard("Mountain", ai);
        }
        Card commander = addCardToZone("The Scarlet Witch", ai, ZoneType.Command);
        ai.addCommander(commander);
        for (int i = 0; i < previousCasts; i++) {
            ai.incCommanderCast(commander);
        }
        addCardToZone("Shivan Dragon", ai, ZoneType.Hand);
        addCard("Mountain", opp);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return game;
    }

    @Test
    public void testFirstCastBeforeBiggerSpell() {
        Game game = play(0);
        Player ai = game.getPlayers().get(1);
        assertTrue(inPlay(ai, "The Scarlet Witch"), "the commander should come down first");
        assertFalse(inPlay(ai, "Shivan Dragon"), "not enough mana left for the dragon");
    }

    @Test
    public void testLaterCastKeepsCostOrder() {
        // already cast once: the usual order, biggest spell first
        Game game = play(1);
        Player ai = game.getPlayers().get(1);
        assertTrue(inPlay(ai, "Shivan Dragon"), "the dragon goes first");
        assertFalse(inPlay(ai, "The Scarlet Witch"), "no bonus on later casts");
    }
}
