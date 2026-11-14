package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: with three Forests, Birthing Pod ({3}{G/P}) is cast by paying 2 life for {G/P}
// (stock AI paid {G/P} with a Forest first, then lacked one generic mana: "CantAfford")
public class PhyrexianPayAiTest extends AITest {

    @Test
    public void testPaysLifeWhenManaIsShort() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Forest", ai);
        }
        addCardToZone("Birthing Pod", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Birthing Pod", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(ai.getLife(), 18);
    }

    // enough mana: keep the life
    @Test
    public void testPaysManaWhenItCan() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCard("Forest", ai);
        }
        addCardToZone("Birthing Pod", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Birthing Pod", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(ai.getLife(), 20);
    }
}
