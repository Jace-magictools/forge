package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CrackBackAiTest extends AITest {

    // magic-tools rule 55: at 4 life, the Bears are the only blocker against a 5/5 tapped from its own attack;
    // swinging for 2 into an opponent at 17 loses to the crack-back (Gorgoroth against Chevalier, tournament v7)
    @Test
    public void testKeepsLastBlockerWhenCrackBackIsLethal() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        ai.setLife(4, null);
        opponent.setLife(17, null);
        Card bears = addCard("Grizzly Bears", ai);
        bears.setSickness(false);
        Card wurm = addCard("Craw Wurm", opponent);
        wurm.setSickness(false);
        wurm.setTapped(true);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 11);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);

        assertFalse(bears.isTapped(), "Bears attacked into a lethal crack-back\n" + gameStateToString(game));
    }

    // Same board at 20 life: the 6/4 does not kill, the Bears attack
    @Test
    public void testAttacksWhenCrackBackIsNotLethal() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        opponent.setLife(17, null);
        Card bears = addCard("Grizzly Bears", ai);
        bears.setSickness(false);
        Card wurm = addCard("Craw Wurm", opponent);
        wurm.setSickness(false);
        wurm.setTapped(true);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 11);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);

        assertTrue(opponent.getLife() < 17, "Bears stayed home with nothing to fear\n" + gameStateToString(game));
    }
    // Trample: at 5 life, chumping the 6/6 Dreadmaw with the Bears leaves 1 life; attacking with them loses
    @Test
    public void testKeepsChumpBlockerAgainstTrampler() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        ai.setLife(5, null);
        opponent.setLife(17, null);
        Card bears = addCard("Grizzly Bears", ai);
        bears.setSickness(false);
        Card maw = addCard("Colossal Dreadmaw", opponent);
        maw.setSickness(false);
        maw.setTapped(true);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 11);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);

        assertFalse(bears.isTapped(), "Bears attacked, the trampler is lethal next turn\n" + gameStateToString(game));
    }
}
