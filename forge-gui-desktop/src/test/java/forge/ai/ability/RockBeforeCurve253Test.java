package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 253 (D144, tester 08/11, a test game turn 7): Talisman of Progress cast with
// four lands and Elite Guardmage in hand, the Guardmage could no longer be paid
public class RockBeforeCurve253Test extends AITest {

    private Game play(final String spell, final String... lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (final String l : lands) {
            addCard(l, ai);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        addCardToZone("Talisman of Progress", ai, ZoneType.Hand);
        addCardToZone("Azorius Chancery", ai, ZoneType.Hand);
        addCardToZone("Azorius Chancery", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testFourDropBeforeTalisman() {
        Game game = play("Elite Guardmage", "Caves of Koilos", "Swamp", "Plains", "Island");
        assertEquals(countCardsWithName(game, "Elite Guardmage", ZoneType.Battlefield), 1);
    }

    @Test
    public void testTalismanWhenTheSpellStillFits() {
        Game game = play("Elite Guardmage", "Caves of Koilos", "Swamp", "Plains", "Island", "Plains", "Island");
        assertEquals(countCardsWithName(game, "Elite Guardmage", ZoneType.Battlefield), 1);
        assertEquals(countCardsWithName(game, "Talisman of Progress", ZoneType.Battlefield), 1);
    }
}
