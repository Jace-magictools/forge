package forge.ai;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: bourde watcher, v13 (30/10): 546 "removal" lines counted every burn spell left in hand at death,
// even drawn the turn before or never castable with the mana left open; only spells castable at some end step count now
public class BourdeRemovalTest extends AITest {

    @Test
    public void testOpenManaCounts() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Grizzly Bears", opp);
        Card bolt = addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        BourdeWatcher w = new BourdeWatcher(game);
        w.removalOuverts(ai, true);
        w.removalOuverts(ai, false);
        assertEquals(w.ouverts(bolt), 2);
        assertEquals(BourdeWatcher.removalGardes(ai).size(), 1);
    }

    @Test
    public void testTappedOutDoesNotCount() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        Card m = addCard("Mountain", ai);
        m.setTapped(true);
        addCard("Grizzly Bears", opp);
        Card bolt = addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        BourdeWatcher w = new BourdeWatcher(game);
        w.removalOuverts(ai, true);
        assertEquals(w.ouverts(bolt), 0);
    }

    @Test
    public void testSorceryOnlyOnOwnTurn() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Grizzly Bears", opp);
        Card slash = addCardToZone("Flame Slash", ai, ZoneType.Hand);
        BourdeWatcher w = new BourdeWatcher(game);
        w.removalOuverts(ai, false);
        assertEquals(w.ouverts(slash), 0);
        w.removalOuverts(ai, true);
        assertEquals(w.ouverts(slash), 1);
    }

    @Test
    public void testXSpellNeedsXAtLeastTwo() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Grizzly Bears", opp);
        Card fireball = addCardToZone("Fireball", ai, ZoneType.Hand);
        BourdeWatcher w = new BourdeWatcher(game);
        w.removalOuverts(ai, true);
        assertEquals(w.ouverts(fireball), 0);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        w.removalOuverts(ai, true);
        assertEquals(w.ouverts(fireball), 1);
    }
}
