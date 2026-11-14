package forge.ai;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CopyEnchantmentAiTest extends AITest {

    // Copy Enchantment (Pacifisme) was AI:RemoveDeck:All and never cast: with Journey to Nowhere in play,
    // it should come in as a second Journey and exile the opponent's creature
    @Test
    public void testCopiesJourneyToNowhere() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        for (int i = 0; i < 3; i++) {
            addCard("Island", ai);
        }
        addCard("Journey to Nowhere", ai);
        addCardToZone("Copy Enchantment", ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertTrue(ai.getCardsIn(ZoneType.Hand).isEmpty(), "Copy Enchantment kept in hand");
        assertEquals(ai.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Journey to Nowhere")).count(),
                2L, "Copy Enchantment did not copy Journey to Nowhere");
        assertTrue(opponent.getCreaturesInPlay().isEmpty(), "Grizzly Bears not exiled");
    }

    // copying an aura: the copy must go on one of its own creatures, not on the opponent's
    @Test
    public void testCopiedArmorOnOwnCreature() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        for (int i = 0; i < 3; i++) {
            addCard("Island", ai);
        }
        Card stalker = addCard("Invisible Stalker", ai);
        Card armor = addCard("Ethereal Armor", ai);
        armor.attachToEntity(stalker, null);
        addCardToZone("Copy Enchantment", ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Ethereal Armor")) {
                assertEquals(c.getEnchantingCard(), stalker, "Ethereal Armor copy put on the wrong creature");
            }
        }
        // 1/1 + 2 Armors x 2 enchantments; uncopied it would be 3/3
        assertEquals(stalker.getNetPower(), 5, "Copy Enchantment did not copy Ethereal Armor");
    }
}
