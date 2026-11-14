package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools rule 148: Xenobotanist ({T}: one mana of any color) wears Hawkeye's Bow (whenever equipped creature becomes
// tapped, 1 damage to each opponent) and Pemmin's Aura ({U}: untap enchanted creature). Tap for U, the Bow pings, the U
// untaps it: endless damage (combo relue « vraie », puzzle MT24). Four 0/8 walls stop the attack.
public class TapPingLoop148Test extends AITest {

    private Player bowGame(boolean aura) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Island", ai);
        Card xeno = addCard("Xenobotanist", ai);
        xeno.setSickness(false);
        Card bow = addCard("Hawkeye's Bow", ai);
        bow.attachToEntity(xeno, null);
        if (aura) {
            Card pemmin = addCard("Pemmin's Aura", ai);
            pemmin.attachToEntity(xeno, null);
        }
        for (int i = 0; i < 4; i++) {
            addCard("Wall of Stone", opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        return opponent;
    }

    @Test(timeOut = 120000)
    public void testBowAuraLoopKills() {
        Player opponent = bowGame(true);
        assertTrue(opponent.getLife() <= 0, "life " + opponent.getLife());
    }

    // without Pemmin's Aura, one tap (the attack or the mana): one damage at most... a few at most
    @Test(timeOut = 120000)
    public void testWithoutAuraNoLoop() {
        Player opponent = bowGame(false);
        assertTrue(opponent.getLife() >= 17, "life " + opponent.getLife());
    }
}
