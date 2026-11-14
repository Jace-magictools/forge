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

public class UnblockedPumpAiTest extends AITest {

    // magic-tools rule 58: an instant pump on an unblocked attacker is cast after blockers when, with it,
    // the same attack kills next turn (Poudlar, v8: Titan's Strength in hand in 39 % of its losses).
    private Game attack(int oppLife) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");

        addCard("Mountain", ai);
        addCardToZone("Titan's Strength", ai, ZoneType.Hand);
        addCardToZone("Mountain", ai, ZoneType.Hand);
        Card c = addCard("Grizzly Bears", ai);
        c.setSickness(false);
        opponent.setLife(oppLife, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 5);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    private boolean cast(Game game) {
        return game.getPlayers().get(1).getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Titan's Strength"));
    }

    // 8 life: bears + 3 = 5, the opponent goes to 3, dead to the same attack next turn
    @Test(invocationCount = 8)
    public void testClockPump() {
        Game game = attack(8);
        assertTrue(cast(game), "Titan's Strength kept in hand\n" + gameStateToString(game));
        assertEquals(game.getPlayers().get(0).getLife(), 3);
    }

    // 20 life: no clock, the trick stays in hand for a real fight
    @Test
    public void testNoClockHold() {
        Game game = attack(20);
        assertTrue(!cast(game) || game.getPlayers().get(0).getLife() == 15, gameStateToString(game));
    }
}
