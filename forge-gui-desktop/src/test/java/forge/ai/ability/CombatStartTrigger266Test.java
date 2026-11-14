package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 266 (tester 09/11): Surrak, the Hunt Caller kept for main 2 ; cast before combat, formidable
// gives it haste.
public class CombatStartTrigger266Test extends AITest {

    @Test
    public void testSurrakBeforeCombat() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCard("Forest", ai);
        }
        for (int i = 0; i < 2; i++) {
            Card w = addCard("Craw Wurm", ai);
            w.setSickness(false);
        }
        addCardToZone("Surrak, the Hunt Caller", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 7);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        // 6 + 6 + 5 (Surrak with haste)
        assertEquals(opponent.getLife(), 3, gameStateToString(game));
    }
}
