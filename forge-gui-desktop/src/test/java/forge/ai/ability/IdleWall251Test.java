package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 251 (a tester report 08/11, D143 point 6, a test game turn 2): no creature on the
// other side, Wall of Omens was cast instead of Acquisitions Expert
public class IdleWall251Test extends AITest {

    private Game setup(String[] lands, String opposingCreature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String l : lands) {
            addCard(l, ai);
        }
        if (opposingCreature != null) {
            addCard(opposingCreature, opponent);
        }
        addCardToZone("Wall of Omens", ai, ZoneType.Hand);
        addCardToZone("Acquisitions Expert", ai, ZoneType.Hand);
        for (int i = 0; i < 6; i++) {
            addCardToZone("Lightning Bolt", opponent, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testExpertBeforeWallWithNoThreat() {
        Game game = setup(new String[] {"Plains", "Swamp"}, null);
        assertEquals(countCardsWithName(game, "Acquisitions Expert", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Wall of Omens", ZoneType.Hand), 1, gameStateToString(game));
    }

    @Test
    public void testBothWithEnoughMana() {
        Game game = setup(new String[] {"Plains", "Swamp", "Plains", "Swamp"}, null);
        assertEquals(countCardsWithName(game, "Acquisitions Expert", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Wall of Omens", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
