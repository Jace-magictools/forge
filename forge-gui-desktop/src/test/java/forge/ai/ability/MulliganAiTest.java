package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.ComputerUtil;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: ElfBall (12 lands, 19 one-mana elves) mulliganed to 5 in most games
public class MulliganAiTest extends AITest {

    private Player elfDeck(String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 10; i++) {
            addCardToZone("Forest", ai, ZoneType.Library);
        }
        for (int i = 0; i < 40; i++) {
            addCardToZone("Llanowar Elves", ai, ZoneType.Library);
        }
        for (String name : hand) {
            addCardToZone(name, ai, ZoneType.Hand);
        }
        return ai;
    }

    @Test
    public void testKeepsOneForestWithElves() {
        Player ai = elfDeck("Forest", "Llanowar Elves", "Elvish Mystic", "Fyndhorn Elves",
                "Elvish Archdruid", "Beast Whisperer", "Nettle Sentinel");
        assertFalse(ComputerUtil.wantMulligan(ai, 0));
    }

    // a black mana creature cannot be cast from a Forest: still a one-land hand
    @Test
    public void testOffColorDorksDoNotCount() {
        Player ai = elfDeck("Forest", "Blood Pet", "Elvish Archdruid", "Beast Whisperer",
                "Lead the Stampede", "Gilt-Leaf Archdruid", "Elvish Harbinger");
        assertTrue(ComputerUtil.wantMulligan(ai, 0));
    }

    @Test
    public void testNoLandStillMulligans() {
        Player ai = elfDeck("Llanowar Elves", "Elvish Mystic", "Fyndhorn Elves", "Arbor Elf",
                "Elvish Archdruid", "Beast Whisperer", "Nettle Sentinel");
        assertTrue(ComputerUtil.wantMulligan(ai, 0));
    }

    // Discard (21 Swamps, mean mana value 2.2) kept 5 Swamps of 7 (tester 28/10)
    private Player curveDeck(String spell, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 16; i++) {
            addCardToZone("Swamp", ai, ZoneType.Library);
        }
        for (int i = 0; i < 37; i++) {
            addCardToZone(spell, ai, ZoneType.Library);
        }
        for (String name : hand) {
            addCardToZone(name, ai, ZoneType.Hand);
        }
        return ai;
    }

    @Test
    public void testFiveLandsLowCurveMulligans() {
        Player ai = curveDeck("Hymn to Tourach", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp",
                "Hymn to Tourach", "Sign in Blood");
        assertTrue(ComputerUtil.wantMulligan(ai, 0));
    }

    @Test
    public void testFiveLandsHighCurveKeeps() {
        Player ai = curveDeck("Gravedigger", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp",
                "Gravedigger", "Gravedigger");
        assertFalse(ComputerUtil.wantMulligan(ai, 0));
    }

    @Test
    public void testFourLandsLowCurveKeeps() {
        Player ai = curveDeck("Hymn to Tourach", "Swamp", "Swamp", "Swamp", "Swamp",
                "Hymn to Tourach", "Sign in Blood", "Hymn to Tourach");
        assertFalse(ComputerUtil.wantMulligan(ai, 0));
    }

    // after a first mulligan (London: 7 drawn, 1 to bottom) the AI keeps rather than going to 5
    @Test
    public void testSecondHandKeeps() {
        Player ai = curveDeck("Hymn to Tourach", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp",
                "Hymn to Tourach", "Sign in Blood");
        assertFalse(ComputerUtil.wantMulligan(ai, 1));
    }
}
