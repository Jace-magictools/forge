package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools, regle 100 : brulure a cout de sacrifice (Shrapnel Blast, Reckless Abandon, Goblin Grenade)
// lancee au visage quand elle tue l'adversaire, meme sans permanent « prefere » a sacrifier
public class SacBurnLethal100Test extends AITest {

    private Player opp;

    private Game setup(String spell, String fodder, int oppLife) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        opp = opponent;
        addCard(fodder, ai);
        addCards("Mountain", 2, ai);
        addCardToZone(spell, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        opponent.setLife(oppLife, null);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        return game;
    }

    // Shrapnel Blast, adversaire a 5 : l'IA sacrifie l'artefact et gagne
    @Test
    public void testShrapnelBlastLethal() {
        Game game = setup("Shrapnel Blast", "Ornithopter", 5);
        assertTrue(opp.getLife() <= 0, "life " + opp.getLife());
        assertTrue(game.isGameOver());
    }

    // Reckless Abandon, adversaire a 4, seul un ours (pas dans AIPreference) : sacrifie et gagne
    @Test
    public void testRecklessAbandonLethal() {
        Game game = setup("Reckless Abandon", "Grizzly Bears", 4);
        assertTrue(opp.getLife() <= 0, "life " + opp.getLife());
        assertTrue(game.isGameOver());
    }

    // pas letal : l'artefact reste
    @Test
    public void testShrapnelBlastNotLethal() {
        Game game = setup("Shrapnel Blast", "Ornithopter", 6);
        assertEquals(opp.getLife(), 6);
        assertEquals(countCardsWithName(game, "Ornithopter"), 1);
    }
}
