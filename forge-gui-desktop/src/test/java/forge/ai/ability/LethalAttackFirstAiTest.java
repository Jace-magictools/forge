package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

public class LethalAttackFirstAiTest extends AITest {

    // magic-tools rule 52: three elves pumped by Elvish Archdruid deal 8 to an opponent at 6 with no blocker;
    // the AI used to tap them in main 1 to cast Elvish Harbinger (one Forest only) and skip the attack
    @Test
    public void testAttackBeforeTappingElvesForMana() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Forest", ai);
        addCard("Elvish Archdruid", ai).setSickness(false);
        for (int i = 0; i < 3; i++) {
            addCard("Llanowar Elves", ai).setSickness(false);
        }
        addCardToZone("Elvish Harbinger", ai, ZoneType.Hand);
        addCardToZone("Elvish Visionary", ai, ZoneType.Hand);
        opponent.setLife(6, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 11);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);

        assertTrue(opponent.getLife() <= 0 || game.isGameOver(),
                "lethal elf attack skipped for main 1 spells\n" + gameStateToString(game));
    }
}
