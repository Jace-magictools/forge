package forge.ai;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

// magic-tools: releve des bourdes, tournoi v15 (31/10) : Galvanic Blast sans metal (2 degats) compte « lancable » 15 tours
// face a un 0/5 et un adversaire a 22 ; un sort de degats ne compte que s'il tue une cible ou si l'adversaire est a portee
public class BourdeDamageUtileTest extends AITest {

    private Card setup(String blocker, int oppLife) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard(blocker, opp);
        opp.setLife(oppLife, null);
        return addCardToZone("Galvanic Blast", ai, ZoneType.Hand);
    }

    @Test
    public void testNothingToKillNotCounted() {
        Card c = setup("Wall of Glare", 20);
        assertNull(BourdeWatcher.removalSa(c.getController(), c, false));
    }

    @Test
    public void testKillableCreatureCounted() {
        Card c = setup("Grizzly Bears", 20);
        assertNotNull(BourdeWatcher.removalSa(c.getController(), c, false));
    }

    @Test
    public void testOpponentInReachCounted() {
        Card c = setup("Wall of Glare", 6);
        assertNotNull(BourdeWatcher.removalSa(c.getController(), c, false));
    }

    // Experimental Frenzy : rien ne se lance depuis la main, le Bolt n'est pas « gardé »
    @Test
    public void testFrenzyNotCounted() {
        Card c = setup("Grizzly Bears", 3);
        addCard("Experimental Frenzy", c.getController());
        assertNull(BourdeWatcher.removalSa(c.getController(), c, false));
    }
}
