package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Rule 62 (v9 tournament, BadEtbEffects refusals): Saga chapters and "as this enters, choose" replacements get the
 * harmless check of ETB triggers (Michiko's Reign of Truth 91, Hollowmurk Siege 119), and a creature whose mandatory
 * trigger has no legal target anywhere is cast for its body (Viridian Corrupter 91, Deputy of Detention 89).
 */
public class HarmlessEtbAi62Test extends AITest {

    private boolean castInMain2(String name, String aiCreature, String oppCreature, String... lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String l : lands) {
            addCard(l, ai);
        }
        if (aiCreature != null) {
            addCard(aiCreature, ai);
        }
        if (oppCreature != null) {
            addCard(oppCreature, opponent);
        }
        addCard("Forest", opponent);
        addCardToZone(name, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals(name));
    }

    @Test
    public void testViridianCorrupterWithoutArtifact() {
        assertTrue(castInMain2("Viridian Corrupter", null, "Grizzly Bears", "Forest", "Forest", "Forest"),
                "Viridian Corrupter kept in hand");
    }

    @Test
    public void testDeputyOfDetentionAgainstLandsOnly() {
        assertTrue(castInMain2("Deputy of Detention", null, null, "Plains", "Island", "Island"),
                "Deputy of Detention kept in hand");
    }

    @Test
    public void testHollowmurkSiege() {
        assertTrue(castInMain2("Hollowmurk Siege", null, "Grizzly Bears", "Swamp", "Forest"),
                "Hollowmurk Siege kept in hand");
    }

    @Test
    public void testMichikosReignOfTruth() {
        assertTrue(castInMain2("Michiko's Reign of Truth", "Grizzly Bears", "Grizzly Bears", "Plains", "Plains"),
                "Michiko's Reign of Truth kept in hand");
    }

    // alone on the battlefield, Skinrender must put its three -1/-1 counters on itself
    @Test
    public void testSkinrenderAloneStaysInHand() {
        assertFalse(castInMain2("Skinrender", null, null, "Swamp", "Swamp", "Swamp", "Swamp"),
                "Skinrender cast onto itself");
    }
}
