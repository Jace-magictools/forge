package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertTrue;

// magic-tools rule 151: Kiora's Follower ({T}: untap another target permanent) wears Hawkeye's Bow (whenever equipped
// creature becomes tapped, 1 damage to each opponent) and Freed from the Real ({U}: untap enchanted creature). Tap an Island
// for U, the Follower untaps it (the Bow pings), the U untaps the Follower: endless damage (combo relue « vraie » D'Vana Tendi,
// puzzle MT27). Forge's runaway brake cut it at 9 pings. Four 0/8 walls stop the attack.
public class UntapPingLoop151Test extends AITest {

    private Player followerGame(boolean aura) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Island", ai);
        Card xeno = addCard("Kiora's Follower", ai);
        xeno.setSickness(false);
        Card bow = addCard("Hawkeye's Bow", ai);
        bow.attachToEntity(xeno, null);
        if (aura) {
            Card pemmin = addCard("Freed from the Real", ai);
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
    public void testFollowerLoopKills() {
        Player opponent = followerGame(true);
        assertTrue(opponent.getLife() <= 0, "life " + opponent.getLife());
    }

    // without Pemmin's Aura, one tap (the attack or the mana): one damage at most... a few at most
    @Test(timeOut = 120000)
    public void testWithoutAuraNoLoop() {
        Player opponent = followerGame(false);
        assertTrue(opponent.getLife() >= 17, "life " + opponent.getLife());
    }
}
