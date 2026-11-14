package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 130, tournament v19, Profanation: Sickening Dreams kept in hand with the opponent at 2 life
public class SickeningDreams130Test extends AITest {

    private Game board(boolean withFatty) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        addCard("Plains", ai);
        addCard("Plains", ai);
        addCard("Swamp", ai);
        addCard("Iona, Shield of Emeria", ai).setSickness(false);
        addCardToZone("Sickening Dreams", ai, ZoneType.Hand);
        addCardToZone("Mausoleum Secrets", ai, ZoneType.Hand);
        addCardToZone("Necromancy", ai, ZoneType.Hand);
        addCardToZone("Mortify", ai, ZoneType.Hand);
        if (withFatty) {
            addCardToZone("Vilis, Broker of Blood", ai, ZoneType.Hand);
        }
        addCard("Plains", opponent);
        ai.setLife(8, null);
        opponent.setLife(2, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        return game;
    }

    @Test
    public void testLethalDreams() {
        Game game = board(true);
        Player opponent = game.getPlayers().get(0);
        playUntilNextTurn(game);
        assertTrue(game.isGameOver() || opponent.getLife() <= 0, gameStateToString(game));
    }

    @Test
    public void testLethalDreamsNoFatty() {
        Game game = board(false);
        Player opponent = game.getPlayers().get(0);
        playUntilNextTurn(game);
        assertTrue(game.isGameOver() || opponent.getLife() <= 0, gameStateToString(game));
    }

    // already right before rule 130 (rule 19b), kept as a guard; tournament v19, Bataillon vs Profanation g2 turn 8: Warden in hand, Exhume in hand, nothing in the graveyard
    @Test
    public void testDreamsForExhume() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Concealed Courtyard", ai);
        addCard("Iona, Shield of Emeria", ai).setSickness(false);
        addCardToZone("Dark Ritual", ai, ZoneType.Hand);
        addCardToZone("Sickening Dreams", ai, ZoneType.Hand);
        addCardToZone("Exhume", ai, ZoneType.Hand);
        addCardToZone("Final Parting", ai, ZoneType.Hand);
        addCardToZone("Sanctuary Warden", ai, ZoneType.Hand);
        addCard("Honored Crop-Captain", opponent);
        addCard("Plains", opponent);
        ai.setLife(17, null);
        opponent.setLife(13, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        playUntilNextTurn(game);
        assertTrue(!ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Sanctuary Warden")), gameStateToString(game));
    }
}
