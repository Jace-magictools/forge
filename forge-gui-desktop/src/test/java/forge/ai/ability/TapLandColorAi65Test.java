package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 65, a protection spell with nothing to protect does not justify the untapped land
// (a tester report 29/10: Bunny Burrow played Forest "for Heroic Intervention", Selesnya Sanctuary waited)
public class TapLandColorAi65Test extends AITest {

    private Game turnTwo(String creature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card forest = addCard("Forest", ai);
        forest.setTapped(false);
        if (creature != null) {
            addCard(creature, ai);
        }
        for (String c : new String[] {"Forest", "Selesnya Sanctuary", "Heroic Intervention", "Burrowguard Mentor", "Swords to Plowshares"}) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testBounceLandWhenNothingToProtect() {
        Game game = turnTwo(null);
        assertEquals(countCardsWithName(game, "Selesnya Sanctuary", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // with a creature to protect, the untapped Forest keeps Heroic Intervention up (stock and rule 21)
    @Test
    public void testUntappedLandWithSomethingToProtect() {
        Game game = turnTwo("Llanowar Elves");
        assertEquals(countCardsWithName(game, "Selesnya Sanctuary", ZoneType.Battlefield), 0, gameStateToString(game));
    }
}
