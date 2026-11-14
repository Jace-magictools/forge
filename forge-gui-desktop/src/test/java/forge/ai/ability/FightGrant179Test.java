package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 179, a fight spell that grants our fighter indestructible or deathtouch
// (Drow held Blizzard Brawl 4 turns with a deathtouch archer and 4 snow lands against a 4/5)
public class FightGrant179Test extends AITest {

    private Player setup(Game game, String spell, String land, int lands, String opp1) {
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < lands; i++) {
            addCard(land, ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        addCard("Grizzly Bears", ai);
        addCard(opp1, opp);
        addCard("Mountain", opp);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return ai;
    }

    private static boolean inGraveyard(Player p, String name) {
        return p.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals(name));
    }

    @Test
    public void testBrawlWithSnowIndestructible() {
        Game game = initAndCreateGame();
        Player ai = setup(game, "Blizzard Brawl", "Snow-Covered Forest", 3, "Hill Giant");
        assertTrue(inGraveyard(game.getPlayers().get(0), "Hill Giant"), "gy=" + game.getPlayers().get(0).getCardsIn(ZoneType.Graveyard) + " hand=" + ai.getCardsIn(ZoneType.Hand) + " stack=" + game.getStack() + " log=" + game.getGameLog().getLogEntries(null));
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Grizzly Bears")));
    }

    @Test
    public void testBrawlWithoutSnowKept() {
        Game game = initAndCreateGame();
        Player ai = setup(game, "Blizzard Brawl", "Forest", 3, "Hill Giant");
        assertFalse(inGraveyard(ai, "Blizzard Brawl"), "gy=" + ai.getCardsIn(ZoneType.Graveyard));
    }

    @Test
    public void testUprisingDeathtouchKillsWall() {
        Game game = initAndCreateGame();
        Player ai = setup(game, "Undercity Uprising", "Bayou", 4, "Wall of Wood");
        assertTrue(inGraveyard(game.getPlayers().get(0), "Wall of Wood"), "gy=" + game.getPlayers().get(0).getCardsIn(ZoneType.Graveyard) + " hand=" + ai.getCardsIn(ZoneType.Hand) + " stack=" + game.getStack() + " log=" + game.getGameLog().getLogEntries(null));
    }
}
