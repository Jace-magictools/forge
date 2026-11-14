package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 180, a sorcery-speed pump that makes a creature attack, counting its own cast triggers
// (Poudlar held Assault Strobe whole games with Kiln Fiend facing a Kor Blademaster)
public class SorceryPump180Test extends AITest {

    private Player play(Game game, String creature) {
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        Card c = addCard(creature, ai);
        c.setSickness(false);
        addCardToZone("Assault Strobe", ai, ZoneType.Hand);
        addCard("Kor Blademaster", opp).setSickness(false);
        opp.setLife(17, null);
        fillLibrary(ai, 20);
        fillLibrary(opp, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        return ai;
    }

    private static boolean inGraveyard(Player p, String name) {
        return p.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals(name));
    }

    @Test(timeOut = 120000)
    public void testKilnFiendStrobeAttacks() {
        Game game = initAndCreateGame();
        Player ai = play(game, "Kiln Fiend");
        Player opp = game.getPlayers().get(0);
        assertTrue(inGraveyard(ai, "Assault Strobe"), "hand=" + ai.getCardsIn(ZoneType.Hand) + " log=" + game.getGameLog().getLogEntries(null));
        assertTrue(opp.getLife() <= 9 || inGraveyard(opp, "Kor Blademaster"), "life=" + opp.getLife());
    }

    @Test(timeOut = 120000)
    public void testSmallCreatureStrobeKept() {
        Game game = initAndCreateGame();
        Player ai = play(game, "Raging Goblin");
        assertFalse(inGraveyard(ai, "Assault Strobe"), "gy=" + ai.getCardsIn(ZoneType.Graveyard));
    }
}
