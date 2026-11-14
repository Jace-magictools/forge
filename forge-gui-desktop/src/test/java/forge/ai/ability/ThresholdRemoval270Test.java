package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 270 (tester 09/11 12:35, a test game turn 7): the opponent has Righteous Valkyrie and
// Soul Warden at 26 life (the anthem turns on at 27). With three lands, the AI cast Drogskol Captain instead of Oblivion
// Ring on the Valkyrie; next turn the Valkyrie was +2/+2 and the opponent at 39.
public class ThresholdRemoval270Test extends AITest {

    private boolean valkyrieExiled(int oppLife) {
        return valkyrieExiled(oppLife, false);
    }

    private boolean valkyrieExiled(int oppLife, boolean ourCreature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Island", ai);
        addCard("Plains", ai);
        addCard("Plains", ai);
        if (ourCreature) {
            addCard("Hanged Executioner", ai);
        }
        addCard("Plains", opponent);
        addCard("Plains", opponent);
        addCard("Plains", opponent);
        addCard("Soul Warden", opponent);
        addCard("Righteous Valkyrie", opponent);
        opponent.setLife(oppLife, null);
        addCardToZone("Drogskol Captain", ai, ZoneType.Hand);
        addCardToZone("Oblivion Ring", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        for (Card c : opponent.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Righteous Valkyrie")) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testRingBeforeThreshold() {
        assertTrue(valkyrieExiled(26), "Valkyrie still in play at 26 life");
    }

    @Test
    public void testRingBeforeThresholdFar() {
        assertTrue(valkyrieExiled(24), "Valkyrie still in play at 24 life");
    }

    @Test
    public void testRingWithOurCreature() {
        assertTrue(valkyrieExiled(26, true), "Valkyrie still in play with our creature");
    }
}
