package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 26, Elemental kept Fireball with 13 lands against Zombie at 11 life (zomb18)
// and split a Fireball between grenouille and Pond Prophet (gren09)
public class LethalBurnAiTest extends AITest {

    private Game setUp(boolean blocker) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        // Overgrowth Elemental is cast in main 1 next to another creature (PlayMain1), like in Elemental
        addCards("Mountain", 3, ai);
        addCards("Forest", 2, ai);
        addCards("Island", 3, ai);
        addCard("Wall of Wood", ai);
        addCardToZone("Fireball", ai, ZoneType.Hand);
        addCardToZone("Cloudkin Seer", ai, ZoneType.Hand);
        addCardToZone("Overgrowth Elemental", ai, ZoneType.Hand);
        addCardToZone("Overgrowth Elemental", ai, ZoneType.Hand);
        if (blocker) {
            addCard("Grizzly Bears", opponent);
        }
        opponent.setLife(7, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 21);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testLethalFireballBeforeCreatures() {
        Game game = setUp(false);
        Player opponent = game.getPlayers().get(0);
        assertTrue(opponent.getLife() <= 0 || game.isGameOver(),
                "Fireball not cast for lethal before the creatures\n" + gameStateToString(game));
    }

    @Test
    public void testLethalFireballNotSplit() {
        Game game = setUp(true);
        Player opponent = game.getPlayers().get(0);
        assertTrue(opponent.getLife() <= 0 || game.isGameOver(),
                "Fireball split or kept with lethal on the table\n" + gameStateToString(game));
    }
}
