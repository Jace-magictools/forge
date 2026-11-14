package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: Counterspell answers a 1 mana aura (Ethereal Armor on Invisible Stalker)
// (stock AI: ALWAYS_COUNTER_AURAS was only read below MIN_SPELL_CMC_TO_COUNTER, 0 in Default.ai,
// so a 1 mana spell was countered 30 % of the time)
public class CounterAuraAiTest extends AITest {

    private int armorsCountered(int games) {
        int countered = 0;
        for (int i = 0; i < games; i++) {
            Game game = initAndCreateGame();
            Player ai = game.getPlayers().get(1);
            Player opponent = game.getPlayers().get(0);
            addCard("Invisible Stalker", opponent);
            addCard("Plains", opponent);
            addCardToZone("Ethereal Armor", opponent, ZoneType.Hand);
            addCard("Island", ai);
            addCard("Island", ai);
            addCardToZone("Counterspell", ai, ZoneType.Hand);
            fillLibrary(ai, 20);
            fillLibrary(opponent, 20);
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
            game.getAction().checkStateEffects(true);

            playUntilPhase(game, PhaseType.END_OF_TURN);
            assertEquals(countCardsWithName(game, "Ethereal Armor", ZoneType.Hand), 0, "armor not cast\n" + gameStateToString(game));
            if (countCardsWithName(game, "Ethereal Armor", ZoneType.Graveyard) == 1) {
                countered++;
            }
        }
        return countered;
    }

    @Test
    public void testCountersOneManaAura() {
        assertEquals(armorsCountered(12), 12);
    }
}
