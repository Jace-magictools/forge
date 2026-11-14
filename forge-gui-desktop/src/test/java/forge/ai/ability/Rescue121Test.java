package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 121, Bones McCoy flashed in to save a creature about to die (mode 2)
public class Rescue121Test extends AITest {

    private Game run(boolean boltBears) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Forest", ai);
        }
        Card bears = addCard("Grizzly Bears", ai);
        addCardToZone("Dr. Leonard \"Bones\" McCoy", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
        game.getAction().checkStateEffects(true);

        Card bolt = addCardToZone("Lightning Bolt", opponent, ZoneType.Hand);
        SpellAbility sa = bolt.getFirstSpellAbility();
        sa.setActivatingPlayer(opponent);
        if (boltBears) {
            sa.getTargets().add(bears);
        } else {
            sa.getTargets().add(ai);
        }
        game.getStackZone().add(bolt);
        game.getStack().add(sa);

        playUntilPhase(game, PhaseType.CLEANUP);
        return game;
    }

    private static int bearsCounters(Game game) {
        Player ai = game.getPlayers().get(1);
        return ai.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Grizzly Bears"))
                .mapToInt(c -> c.getCounters(CounterEnumType.P1P1)).sum();
    }

    // Bolt on the Bears: McCoy in response, mode 2 on the Bears, they come back at the end step with a counter
    @Test
    public void testSavesBoltedCreature() {
        Game game = run(true);
        assertEquals(countCardsWithName(game, "Dr. Leonard \"Bones\" McCoy", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(bearsCounters(game), 1, gameStateToString(game));
    }

    // Bolt to the face: nothing to save, the Bears keep no counter
    @Test
    public void testNoRescueWithoutThreat() {
        Game game = run(false);
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(bearsCounters(game), 0, gameStateToString(game));
    }
}
