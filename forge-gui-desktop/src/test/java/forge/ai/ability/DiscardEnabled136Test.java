package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 136 (tournament v21, Schizoph): looting with Fiery Temper and Violent Eruption in hand, the AI
// kept discarding Asmoranomardicadaistinaculdacar, the card the discard makes castable for {B/R}
public class DiscardEnabled136Test extends AITest {

    @Test
    public void testKeepsAsmoranomardicadaistinaculdacar() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        // turn 10 of the tournament game: enough lands, no card is "unplayable by its cost"
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", ai);
            addCard("Swamp", ai);
        }
        Card temper = addCardToZone("Fiery Temper", ai, ZoneType.Hand);
        Card asmor = addCardToZone("Asmoranomardicadaistinaculdacar", ai, ZoneType.Hand);
        Card eruption = addCardToZone("Violent Eruption", ai, ZoneType.Hand);
        CardCollection valid = new CardCollection(java.util.List.of(temper, asmor, eruption));
        CardCollection out = ((PlayerControllerAi) ai.getController()).getAi().getCardsToDiscard(2, 2, valid, null);
        assertEquals(out.size(), 2);
        assertFalse(out.contains(asmor), out.toString());
    }

    @Test
    public void testDiscardsItWhenNothingElse() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card temper = addCardToZone("Fiery Temper", ai, ZoneType.Hand);
        Card asmor = addCardToZone("Asmoranomardicadaistinaculdacar", ai, ZoneType.Hand);
        Card eruption = addCardToZone("Violent Eruption", ai, ZoneType.Hand);
        CardCollection valid = new CardCollection(java.util.List.of(temper, asmor, eruption));
        CardCollection out = ((PlayerControllerAi) ai.getController()).getAi().getCardsToDiscard(3, 3, valid, null);
        assertTrue(out.contains(asmor));
    }

    @Test
    public void testHollowOneKept() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Mountain", ai);
        Card hollow = addCardToZone("Hollow One", ai, ZoneType.Hand);
        Card bolt = addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        CardCollection valid = new CardCollection(java.util.List.of(hollow, bolt));
        CardCollection out = ((PlayerControllerAi) ai.getController()).getAi().getCardsToDiscard(1, 1, valid, null);
        assertEquals(out.size(), 1);
        assertEquals(out.get(0), bolt);
    }
}
