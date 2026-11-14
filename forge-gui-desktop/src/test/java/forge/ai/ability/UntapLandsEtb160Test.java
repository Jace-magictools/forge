package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class UntapLandsEtb160Test extends AITest {

    // Peregrine Drake: "when this enters, untap up to five lands" is a gain, the 4U flyer gets cast
    @Test
    public void testCastPeregrineDrake() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        for (int i = 0; i < 5; i++) {
            addCard("Island", ai);
        }
        addCardToZone("Peregrine Drake", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCreaturesInPlay().size(), 1, "Peregrine Drake kept in hand");
    }

    // and the trigger does untap the five lands tapped to cast it
    @Test
    public void testDrakeUntapsLands() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        for (int i = 0; i < 5; i++) {
            addCard("Island", ai);
        }
        addCardToZone("Peregrine Drake", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);

        assertEquals(ai.getCreaturesInPlay().size(), 1, "Peregrine Drake kept in hand");
        long untapped = ai.getLandsInPlay().stream().filter(c -> !c.isTapped()).count();
        assertEquals(untapped, 5L, "lands not untapped by the trigger");
    }
}
