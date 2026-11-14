package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: Overgrown Tomb is paid untapped when it lets the AI cast Kitchen Finks this turn
// (stock AI asked in Main 1, where creatures wait for Main 2: the land entered tapped, Finks stayed in hand)
public class ShockLandAiTest extends AITest {

    @Test
    public void testPaysLifeToCastCreatureInMain2() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Overgrown Tomb", ai, ZoneType.Hand);
        addCardToZone("Kitchen Finks", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Overgrown Tomb", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Kitchen Finks", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // nothing to cast with the third mana: keep the life
    @Test
    public void testEntersTappedWithoutReason() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Overgrown Tomb", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Overgrown Tomb", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(ai.getLife(), 20);
    }
}
