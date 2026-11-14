package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: with one mana and Kitchen Finks waiting in hand, the AI casts Birds of Paradise
// before Viscera Seer (stock AI ranked the 1/1 Seer above the mana creature and lost a turn of ramp)
public class ManaRampAiTest extends AITest {

    @Test
    public void testCastsManaCreatureFirst() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Overgrown Tomb", ai);
        addCardToZone("Viscera Seer", ai, ZoneType.Hand);
        addCardToZone("Birds of Paradise", ai, ZoneType.Hand);
        addCardToZone("Kitchen Finks", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Birds of Paradise", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Viscera Seer", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    // nothing to ramp into: keep the stock order (Seer first)
    @Test
    public void testNoRampWithoutBiggerSpell() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Overgrown Tomb", ai);
        addCardToZone("Viscera Seer", ai, ZoneType.Hand);
        addCardToZone("Birds of Paradise", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Viscera Seer", ZoneType.Battlefield)
                + countCardsWithName(game, "Birds of Paradise", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // enough mana for the bigger spell: cast it, not the mana creature
    @Test
    public void testBiggerSpellFirstWhenAffordable() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Overgrown Tomb", ai);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCardToZone("Birds of Paradise", ai, ZoneType.Hand);
        addCardToZone("Kitchen Finks", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Kitchen Finks", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
