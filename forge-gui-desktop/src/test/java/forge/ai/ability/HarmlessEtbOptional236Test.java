package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Rule 236: an enters trigger that can only offer us something does not keep the creature in hand. Batch of 120 v24
 * decks, 480 games: Torrential Gearhulk 9 BadEtbEffects with the mana, 0 cast (d032 T13 and T15, 6 free lands);
 * Dreamdew Entrancer 3, 0 cast. An optional free cast from our graveyard (31 creatures) can be declined; a draw that
 * only happens if we targeted our own creature draws nothing otherwise.
 */
public class HarmlessEtbOptional236Test extends AITest {

    private boolean castInMain2(String name, String graveyard, String... lands) {
        return castInMain2(name, graveyard, "Grizzly Bears", lands);
    }

    private boolean castInMain2(String name, String graveyard, String opposing, String[] lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String l : lands) {
            addCard(l, ai);
        }
        addCardToZone(name, ai, ZoneType.Hand);
        if (graveyard != null) {
            addCardToZone(graveyard, ai, ZoneType.Graveyard);
        }
        if (opposing != null) {
            addCard(opposing, opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals(name));
    }

    @Test
    public void testDarkDwellersWithSpellInGraveyard() {
        assertTrue(castInMain2("Goblin Dark-Dwellers", "Lightning Bolt", "Mountain", "Mountain", "Mountain",
                "Mountain", "Mountain"), "Goblin Dark-Dwellers kept in hand");
    }

    @Test
    public void testDarkDwellersEmptyGraveyard() {
        assertTrue(castInMain2("Goblin Dark-Dwellers", null, "Mountain", "Mountain", "Mountain", "Mountain",
                "Mountain"), "Goblin Dark-Dwellers kept in hand with an empty graveyard");
    }

    // no creature to stun: "up to one", and the draw only follows our own target
    @Test
    public void testDreamdewEntrancerNothingToTap() {
        assertTrue(castInMain2("Dreamdew Entrancer", null, (String) null,
                new String[] { "Forest", "Forest", "Island", "Island" }), "Dreamdew Entrancer kept in hand");
    }

    // still refused: a mandatory mass destruction that also hits us is not "only an offer"
    @Test
    public void testMandatorySweepStillJudged() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Swamp", ai);
            addCard("Mountain", ai);
            addCard("Grizzly Bears", ai);
        }
        addCardToZone("Rakdos, the Showstopper", ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        assertFalse(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Rakdos, the Showstopper")),
                "Rakdos cast over our own creatures");
    }
}
