package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 142, a "discard X cards" spell caps X at the cards the AI agrees to discard
// (traced games after tournament v23, Profanation vs Geist of Saint Traft: X = 2 killed Lyev Skyknight and Geist,
// one fatty only, Sickening Dreams refused for five turns)
public class DiscardX142Test extends AITest {

    private Game board(boolean secondFatty) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCard("Plains", ai);
        addCard("Plains", ai);
        addCard("Nyx-Fleece Ram", ai).setSickness(false);
        addCardToZone("Sickening Dreams", ai, ZoneType.Hand);
        addCardToZone("Animate Dead", ai, ZoneType.Hand);
        addCardToZone("Griselbrand", ai, ZoneType.Hand);
        addCardToZone("Condemn", ai, ZoneType.Hand);
        if (secondFatty) {
            addCardToZone("Iona, Shield of Emeria", ai, ZoneType.Hand);
        }
        addCard("Lyev Skyknight", opponent).setSickness(false);
        addCard("Geist of Saint Traft", opponent).setSickness(false);
        addCard("Island", opponent);
        ai.setLife(14, null);
        opponent.setLife(20, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        return game;
    }

    @Test
    public void testOneFattyDiscardsForXOne() {
        Game game = board(false);
        Player ai = game.getPlayers().get(1);
        playUntilNextTurn(game);
        assertTrue(!ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Sickening Dreams")), gameStateToString(game));
        assertTrue(!ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Griselbrand")), gameStateToString(game));
    }

    @Test
    public void testTwoFattiesKillBoth() {
        Game game = board(true);
        Player opponent = game.getPlayers().get(0);
        playUntilNextTurn(game);
        assertTrue(!opponent.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Geist of Saint Traft")), gameStateToString(game));
    }
}
