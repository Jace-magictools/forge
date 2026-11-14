package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 268 (tester 09/11 13:01), Rattlechains held at the opponent's end step with two lands open.
public class FlashEot268Test extends AITest {

    private boolean flashedAtOppEot(String flashCard, String... otherHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Plains", ai);
        addCard("Island", ai);
        addCard("Swamp", opponent);
        addCard("Swamp", opponent);
        addCard("Black Knight", opponent);
        addCardToZone(flashCard, ai, ZoneType.Hand);
        for (String c : otherHand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.UPKEEP);
        for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals(flashCard)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testRattlechainsAlone() {
        assertTrue(flashedAtOppEot("Rattlechains"), "Rattlechains alone in hand, 2 lands open");
    }

    @Test
    public void testRattlechainsWithHand() {
        assertTrue(flashedAtOppEot("Rattlechains", "Drogskol Captain", "Drogskol Captain", "Spectral Procession", "Empyrean Eagle", "Island"),
                "Rattlechains with the hand of the reported game");
    }

    @Test
    public void testPlainFlashCreature() {
        assertTrue(flashedAtOppEot("Spectral Sailor"), "a flash creature without ETB, 2 lands open");
    }
}
