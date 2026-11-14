package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

// magic-tools: rule 220, Ichor Rats stayed in hand with BBB free (Infection - NRJ, t6-10)
public class SharedPoison220Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Player play(int poison) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        for (int i = 0; i < 3; i++) {
            addCardToZone("Swamp", ai, ZoneType.Battlefield);
        }
        addCardToZone("Ichor Rats", ai, ZoneType.Hand);
        if (poison > 0) {
            ai.setCounters(forge.game.card.CounterEnumType.POISON, poison);
        }
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return ai;
    }

    @Test
    public void testNoPoisonYet() {
        Player ai = play(0);
        assertTrue(has(ai, ZoneType.Battlefield, "Ichor Rats"), "a shared counter at 0-0 is no reason to wait " + ai.getGame().getGameLog().getLogEntries(null));
    }

    @Test
    public void testNotOntoTheTenth() {
        Player ai = play(9);
        assertTrue(ai.getPoisonCounters() == 9, "the setup holds");
        assertFalse(has(ai, ZoneType.Battlefield, "Ichor Rats"), "the tenth counter would kill the AI");
    }
}
