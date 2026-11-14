package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 106 (a tester report 30/10 10:29, Ork - Profanation turn 5): Ork had Land Tax, Profanation played a
// fourth land with nothing new to cast and fed Ork three basics each upkeep
public class LandTax106Test extends AITest {

    private Game setup(boolean landTax, String extra) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String c : new String[] {"Swamp", "Plains", "Plains"}) {
            addCardToZone(c, ai, ZoneType.Battlefield);
        }
        for (String c : new String[] {"Plains", "Iona, Shield of Emeria", extra}) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        addCardToZone("Mountain", opponent, ZoneType.Battlefield);
        addCardToZone("Mountain", opponent, ZoneType.Battlefield);
        if (landTax) {
            addCardToZone("Land Tax", opponent, ZoneType.Battlefield);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testLandWaitsAgainstLandTax() {
        Game game = setup(true, "Swamp");
        assertEquals(game.getPlayers().get(1).getLandsInPlay().size(), 3, gameStateToString(game));
    }

    @Test
    public void testLandPlayedWithoutLandTax() {
        Game game = setup(false, "Swamp");
        assertEquals(game.getPlayers().get(1).getLandsInPlay().size(), 4, gameStateToString(game));
    }

    @Test
    public void testLandPlayedForNewSpell() {
        Game game = setup(true, "Restoration Angel");
        assertEquals(countCardsWithName(game, "Restoration Angel", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
