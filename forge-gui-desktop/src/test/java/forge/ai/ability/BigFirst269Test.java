package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 269 (tester 09/11 12:26, a test game turn 7): with Wolfir Silverheart and Gigantosaurus
// in hand, five green and no creature in play, the AI cast Wolfir alone. Gigantosaurus first: next turn Wolfir pairs with
// it and a 14/14 attacks.
public class BigFirst269Test extends AITest {

    private String castFirst(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 5; i++) {
            addCard("Forest", ai);
        }
        addCard("Swamp", opponent);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c.isCreature()) {
                return c.getName();
            }
        }
        return "";
    }

    @Test
    public void testGigantosaurusBeforeWolfir() {
        String cast = castFirst("Wolfir Silverheart", "Gigantosaurus", "Gigantosaurus", "Rancor", "Barkhide Troll");
        assertTrue(cast.equals("Gigantosaurus"), "cast " + cast);
    }

    @Test
    public void testTwoCards() {
        String cast = castFirst("Wolfir Silverheart", "Gigantosaurus");
        assertTrue(cast.equals("Gigantosaurus"), "two cards: cast " + cast);
    }

    @Test
    public void testGigantosaurusAlone() {
        String cast = castFirst("Gigantosaurus");
        assertTrue(cast.equals("Gigantosaurus"), "alone: cast " + cast);
    }
}
