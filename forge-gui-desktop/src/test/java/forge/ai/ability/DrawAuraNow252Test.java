package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 252 (D143 point 3, tester 08/11): Curiosity only on a creature that deals damage this turn, one per creature
public class DrawAuraNow252Test extends AITest {

    private Game game;
    private Card first;
    private Card second;

    private int cast(final PhaseType phase, final boolean sick, final boolean worn, final boolean two) {
        game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Island", ai);
        }
        first = addCard("Grizzly Bears", ai);
        first.setSickness(sick);
        second = null;
        if (two) {
            second = addCard("Grizzly Bears", ai);
            second.setSickness(false);
        }
        if (worn) {
            addCard("Curiosity", ai).attachToEntity(first, null);
        }
        addCardToZone("Curiosity", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(phase, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return countCardsWithName(game, "Curiosity", ZoneType.Battlefield);
    }

    @Test
    public void testBeforeAttacksItGoes() {
        assertEquals(cast(PhaseType.MAIN1, false, false, false), 1);
    }

    @Test
    public void testAfterCombatItWaits() {
        assertEquals(cast(PhaseType.MAIN2, false, false, false), 0);
    }

    @Test
    public void testSummoningSickItWaits() {
        assertEquals(cast(PhaseType.MAIN1, true, false, false), 0);
    }

    @Test
    public void testSecondCopyWaits() {
        assertEquals(cast(PhaseType.MAIN1, false, true, false), 1);
    }

    @Test
    public void testSecondCopyGoesElsewhere() {
        assertEquals(cast(PhaseType.MAIN1, false, true, true), 2);
        assertTrue(second.getEnchantedBy().size() == 1 && first.getEnchantedBy().size() == 1);
    }
}
