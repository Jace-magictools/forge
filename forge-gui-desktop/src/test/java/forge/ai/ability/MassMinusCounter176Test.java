package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 176, "a -1/-1 counter on each creature" weighed on both sides with our -1/-1 payoffs
// (Hapatra: Soul Snuffers held 10 turns next to two Nest of Scarabs and Obelisk Spider)
public class MassMinusCounter176Test extends AITest {

    private Player setup(Game game, boolean payoffs) {
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 4; i++) {
            addCard("Swamp", ai);
        }
        addCard("Forest", ai);
        addCardToZone("Soul Snuffers", ai, ZoneType.Hand);
        addCard("Fume Spitter", ai);
        if (payoffs) {
            addCard("Nest of Scarabs", ai);
            addCard("Nest of Scarabs", ai);
            addCard("Obelisk Spider", ai);
        }
        addCard("Torbran, Thane of Red Fell", opp);
        addCard("Mountain", opp);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return ai;
    }

    @Test
    public void testSnuffersCastWithPayoffs() {
        Game game = initAndCreateGame();
        Player ai = setup(game, true);
        assertTrue(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Soul Snuffers")),
                "bf=" + ai.getCardsIn(ZoneType.Battlefield));
    }

    @Test
    public void testSnuffersKeptWithoutPayoffs() {
        Game game = initAndCreateGame();
        Player ai = setup(game, false);
        assertFalse(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Soul Snuffers")),
                "bf=" + ai.getCardsIn(ZoneType.Battlefield));
    }
}
