package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.CullingScalesAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 77 (a tester reports 29/10 12:47 and 12:51, Eldratron): with our Culling Scales in play, Expedition
// Map is not cast (it would be the lowest mana value and destroyed at our upkeep); Culling Scales goes before Trinket
// Mage when its first target would be an opponent's creature
public class CullingScales77Test extends AITest {

    private Game setup(String oppPerm, boolean scalesInPlay, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Island", ai);
        }
        if (scalesInPlay) {
            addCard("Culling Scales", ai);
        }
        addCard(oppPerm, opponent);
        for (String h : hand) {
            addCardToZone(h, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private Card inHand(Player ai, String name) {
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    // rule 167: Expedition Map (instant-speed ability) is no longer held; a permanent with no use before the
    // upkeep (Memnite, no ability) still is
    @Test
    public void testMapNotCastUnderOwnScales() {
        Game game = setup("Grizzly Bears", true, "Expedition Map", "Memnite");
        Player ai = game.getPlayers().get(1);
        assertFalse(CullingScalesAi.wouldBeCulled(ai, inHand(ai, "Expedition Map")));
        assertTrue(CullingScalesAi.wouldBeCulled(ai, inHand(ai, "Memnite")));
    }

    @Test
    public void testMapFineWhenOpponentHasLower() {
        Game game = setup("Ornithopter", true, "Expedition Map");
        Player ai = game.getPlayers().get(1);
        assertFalse(CullingScalesAi.wouldBeCulled(ai, inHand(ai, "Expedition Map")));
    }

    @Test
    public void testScalesBeforeTrinketMage() {
        Game game = setup("Grizzly Bears", false, "Trinket Mage", "Culling Scales");
        Player ai = game.getPlayers().get(1);
        assertTrue(CullingScalesAi.isCuller(inHand(ai, "Culling Scales")));
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Culling Scales", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // rule 101: Trinket Mage (enter trigger: search an artifact) is cast even when our Scales would destroy it
    @Test
    public void testEnterValueCastUnderOwnScales() {
        Game game = setup("Hill Giant", true, "Trinket Mage");
        Player ai = game.getPlayers().get(1);
        assertFalse(CullingScalesAi.wouldBeCulled(ai, inHand(ai, "Trinket Mage")));
        assertFalse(CullingScalesAi.wouldBeCulled(ai, addCardToZone("Sensei's Divining Top", ai, ZoneType.Hand)));
    }
}
