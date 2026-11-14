package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 181, a sweeper hidden behind an energy counter (Wrath of the Skies held by socrate at 5 life)
public class EnergyWrath181Test extends AITest {

    @Test(timeOut = 120000)
    public void testWrathOfTheSkiesCast() {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 7; i++) {
            addCard("Plains", ai);
        }
        addCardToZone("Wrath of the Skies", ai, ZoneType.Hand);
        Card s = addCard("Blood Scrivener", opp);
        s.setSickness(false);
        Card b = addCard("Bonesplitter", opp);
        b.attachToEntity(s, null);
        ai.setLife(5, null);
        fillLibrary(ai, 20);
        fillLibrary(opp, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertTrue(opp.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Blood Scrivener")),
                "hand=" + ai.getCardsIn(ZoneType.Hand) + " log=" + game.getGameLog().getLogEntries(null));
    }

    private Player sweep(Game game, String land, String spell, String mine) {
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 8; i++) {
            addCard(land, ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        addCard(mine, ai).setSickness(true);
        addCard("Blood Scrivener", opp).setSickness(false);
        addCard("Grizzly Bears", opp).setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opp, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        return ai;
    }

    // the energy paid stops below our own big creature
    @Test(timeOut = 120000)
    public void testWrathSparesOwnAngel() {
        Game game = initAndCreateGame();
        Player ai = sweep(game, "Plains", "Wrath of the Skies", "Serra Angel");
        Player opp = game.getPlayers().get(0);
        assertTrue(opp.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Blood Scrivener")), "log=" + game.getGameLog().getLogEntries(null));
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Serra Angel")), "gy=" + ai.getCardsIn(ZoneType.Graveyard));
    }

    // Forced March: X = 2, not all the mana, so our Hill Giant lives
    @Test(timeOut = 120000)
    public void testForcedMarchSmallX() {
        Game game = initAndCreateGame();
        Player ai = sweep(game, "Swamp", "Forced March", "Hill Giant");
        Player opp = game.getPlayers().get(0);
        assertTrue(opp.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Grizzly Bears")), "log=" + game.getGameLog().getLogEntries(null));
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Hill Giant")), "gy=" + ai.getCardsIn(ZoneType.Graveyard));
    }
}
