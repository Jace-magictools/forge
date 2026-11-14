package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 123, optional "dig until land / nonland → hand" draw replacement (Pavel Chekov, Abundance)
public class DrawDig123Test extends AITest {

    private Game run(String host, int lands, boolean landInHand, String around, String buried) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < lands; i++) {
            addCard("Forest", ai);
        }
        addCard(host, ai);
        if (landInHand) {
            addCardToZone("Island", ai, ZoneType.Hand);
        }
        for (int i = 0; i < 10; i++) {
            addCardToZone(around, ai, ZoneType.Library);
        }
        addCardToZone(buried, ai, ZoneType.Library);
        for (int i = 0; i < 10; i++) {
            addCardToZone(around, ai, ZoneType.Library);
        }
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, ai, 3);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN1);
        return game;
    }

    // Two lands, none in hand: Chekov digs for a land instead of drawing a Bear
    @Test
    public void testDigsLandWhenShort() {
        Game game = run("Pavel Chekov, First Posting", 2, false, "Runeclaw Bear", "Plains");
        assertEquals(countCardsWithName(game, "Plains", ZoneType.Hand), 1, gameStateToString(game));
    }

    // Five lands and a land in hand: digs for a spell instead of drawing a Forest
    @Test
    public void testDigsSpellWhenFlooded() {
        Game game = run("Pavel Chekov, First Posting", 5, true, "Mountain", "Runeclaw Bear");
        assertEquals(countCardsWithName(game, "Runeclaw Bear", ZoneType.Hand), 1, gameStateToString(game));
    }

    // Same text on Abundance (marked AI:RemoveDeck:All by Forge)
    @Test
    public void testAbundance() {
        Game game = run("Abundance", 5, true, "Mountain", "Runeclaw Bear");
        assertEquals(countCardsWithName(game, "Runeclaw Bear", ZoneType.Hand), 1, gameStateToString(game));
    }
}
