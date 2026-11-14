package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.ComputerUtil;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools, règle 241 : 2 Orzhov Basilica + Swamp was a one-land hand (tester 6/11, a test game)
public class ConditionalLandMulligan241Test extends AITest {

    private Player deck(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 17; i++) {
            addCardToZone("Swamp", ai, ZoneType.Library);
        }
        for (int i = 0; i < 26; i++) {
            addCardToZone("Plaguecrafter", ai, ZoneType.Library);
        }
        for (String name : hand) {
            addCardToZone(name, ai, ZoneType.Hand);
        }
        return ai;
    }

    @Test
    public void testKeepsTwoBounceLandsAndABasic() {
        Player ai = deck("Orzhov Basilica", "Orzhov Basilica", "Swamp", "Plaguecrafter", "Charming Prince",
                "Selfless Savior", "Nightmare Shepherd");
        assertFalse(ComputerUtil.wantMulligan(ai, 0));
    }

    @Test
    public void testKeepsBounceLandAndABasic() {
        Player ai = deck("Orzhov Basilica", "Swamp", "Plaguecrafter", "Charming Prince",
                "Selfless Savior", "Nightmare Shepherd", "Woe Strider");
        assertFalse(ComputerUtil.wantMulligan(ai, 0));
    }

    // no land to return: neither can be played as a real land
    @Test
    public void testBounceLandsAloneStillMulligan() {
        Player ai = deck("Orzhov Basilica", "Orzhov Basilica", "Plaguecrafter", "Charming Prince",
                "Selfless Savior", "Nightmare Shepherd", "Woe Strider");
        assertTrue(ComputerUtil.wantMulligan(ai, 0));
    }

    // what it waits for is not in the hand: a Plains does not give Dormant Volcano its Mountain
    @Test
    public void testWrongBasicDoesNotCount() {
        Player ai = deck("Dormant Volcano", "Plains", "Plaguecrafter", "Charming Prince",
                "Selfless Savior", "Nightmare Shepherd", "Woe Strider");
        assertTrue(ComputerUtil.wantMulligan(ai, 0));
    }

    // flooding counts them too: 5 basics + 2 bounce lands is seven lands
    @Test
    public void testSevenLandsWithBounceLandsIsFlood() {
        Player ai = deck("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Orzhov Basilica", "Orzhov Basilica");
        assertTrue(ComputerUtil.wantMulligan(ai, 0));
    }

    @Test
    public void testRightBasicCounts() {
        Player ai = deck("Dormant Volcano", "Mountain", "Plaguecrafter", "Charming Prince",
                "Selfless Savior", "Nightmare Shepherd", "Woe Strider");
        assertFalse(ComputerUtil.wantMulligan(ai, 0));
    }
}
