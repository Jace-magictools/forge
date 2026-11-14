package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 281 (Forge task 10/11, follows 276b): against a deck with a combo plan, our last removal is not spent
// on a harmless creature (Trinket Mage) that is not a combo piece; without a plan, or with a second removal, it goes.
public class RemovalCombo281Test extends AITest {

    private int mageLeft(boolean plan, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        if (plan) {
            opponent.getRegisteredPlayer().getDeck().setAiHints("Combo$Melira, Sylvok Outcast;Kitchen Finks;Viscera Seer");
        }
        for (int i = 0; i < 4; i++) {
            addCardToZone("Swamp", ai, ZoneType.Battlefield);
        }
        for (int i = 0; i < 3; i++) {
            addCardToZone("Island", opponent, ZoneType.Battlefield);
        }
        addCardToZone("Trinket Mage", opponent, ZoneType.Battlefield);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return countCardsWithName(game, "Trinket Mage", ZoneType.Battlefield);
    }

    @Test(timeOut = 300000)
    public void testLastRemovalWaitsForTheCombo() {
        for (int i = 0; i < 4; i++) {
            assertEquals(mageLeft(true, "Murder"), 1, "the last removal is kept against a combo deck");
        }
    }

    @Test(timeOut = 300000)
    public void testRemovalGoesWithoutPlanOrWithASecondOne() {
        int killed = 0;
        int killedTwo = 0;
        for (int i = 0; i < 4; i++) {
            killed += 1 - mageLeft(false, "Murder");
            killedTwo += 1 - mageLeft(true, "Murder", "Murder");
        }
        assertTrue(killed > 0, "without a plan the removal is cast at least once in 4 games");
        assertTrue(killedTwo > 0, "with a second removal in hand one is cast at least once in 4 games");
    }
}
