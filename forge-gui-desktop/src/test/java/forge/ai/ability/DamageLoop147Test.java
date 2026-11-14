package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 147: Massacre Girl, Most Wanted (+1/+1 counter when an opponent is dealt noncombat damage)
// + Shalai and Hallar (counters -> that much damage to target opponent) = endless loop, any death starts it.
// Puzzle MT22: the AI attacked into walls instead of feeding Grizzly Bears to Viscera Seer.
public class DamageLoop147Test extends AITest {

    private Game setup(boolean shalai) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        if (shalai) {
            addCard("Shalai and Hallar", ai).setSickness(false);
        }
        for (String n : new String[] {"Massacre Girl, Most Wanted", "Viscera Seer", "Grizzly Bears"}) {
            addCard(n, ai).setSickness(false);
        }
        for (int i = 0; i < 4; i++) {
            addCard("Wall of Stone", opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testLoopStartedBySacrifice() {
        Game game = setup(true);
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        assertTrue(game.getPlayers().get(0).getLife() <= 0 || game.isGameOver(), gameStateToString(game));
    }

    // without Shalai one or two pings do not kill: Grizzly Bears is not thrown away for a scry
    @Test(timeOut = 120000)
    public void testNoLoopKeepsCreatures() {
        Game game = setup(false);
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        long bears = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Grizzly Bears")).count();
        assertEquals(bears, 1L, gameStateToString(game));
    }
}
