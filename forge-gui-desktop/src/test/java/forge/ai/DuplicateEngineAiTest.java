package forge.ai;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 31, Pod against Rohan cast a second Birthing Pod instead of activating the first
public class DuplicateEngineAiTest extends AITest {

    private Game game(boolean podInPlay) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        if (podInPlay) {
            addCard("Birthing Pod", ai);
        }
        for (int i = 0; i < 4; i++) {
            addCard("Forest", ai);
        }
        addCard("Kitchen Finks", ai);
        addCardToZone("Birthing Pod", ai, ZoneType.Hand);
        addCardToZone("Murderous Redcap", ai, ZoneType.Library);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testActivatesPodBeforeCastingAnother() {
        Game game = game(true);
        assertEquals(countCardsWithName(game, "Murderous Redcap", ZoneType.Battlefield), 1,
                "Pod not activated\n" + gameStateToString(game));
    }

    @Test
    public void testFirstPodStillCast() {
        Game game = game(false);
        assertTrue(countCardsWithName(game, "Birthing Pod", ZoneType.Battlefield) == 1, gameStateToString(game));
    }

    @Test
    public void testRedundantOnlyWithCopyInPlay() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        forge.game.card.Card pod = addCardToZone("Birthing Pod", ai, ZoneType.Hand);
        forge.game.spellability.SpellAbility spell = pod.getFirstSpellAbility();
        assertTrue(!DuplicateEngineAi.redundant(ai, spell), "redundant without a copy");
        addCard("Birthing Pod", ai);
        assertTrue(DuplicateEngineAi.redundant(ai, spell), "not redundant with a copy");
    }
}
