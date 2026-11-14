package forge.ai;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

// magic-tools: releve des bourdes, tournoi v14 (30/10) : Goblin Grenade, Shrapnel Blast, Reckless Abandon comptes
// « lancables » (65 morts avec en main) sans rien a sacrifier en jeu
public class BourdeSacCostTest extends AITest {

    private Card setup(String spell, String fodder) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCards("Mountain", 2, ai);
        if (fodder != null) {
            addCard(fodder, ai);
        }
        addCard("Grizzly Bears", opponent);
        return addCardToZone(spell, ai, ZoneType.Hand);
    }

    @Test
    public void testGrenadeWithoutGoblinNotCastable() {
        Card c = setup("Goblin Grenade", null);
        assertNull(BourdeWatcher.removalSa(c.getController(), c, false));
    }

    @Test
    public void testGrenadeWithGoblinCastable() {
        Card c = setup("Goblin Grenade", "Goblin Arsonist");
        assertNotNull(BourdeWatcher.removalSa(c.getController(), c, false));
    }

    @Test
    public void testShrapnelBlastWithoutArtifactNotCastable() {
        Card c = setup("Shrapnel Blast", "Grizzly Bears");
        assertNull(BourdeWatcher.removalSa(c.getController(), c, false));
    }
}
