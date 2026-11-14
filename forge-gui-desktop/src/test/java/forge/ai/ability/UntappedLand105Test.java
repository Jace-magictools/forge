package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 105 (a tester report 30/10 10:31, Ork - Profanation turn 6): two Boros Guildgates and Land Tax in
// play, Ork played Temple of Triumph tapped instead of a Mountain and could not cast Orcish Cannoneers
public class UntappedLand105Test extends AITest {

    private Game setup(boolean landTax) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCardToZone("Boros Guildgate", ai, ZoneType.Battlefield);
        addCardToZone("Boros Guildgate", ai, ZoneType.Battlefield);
        if (landTax) {
            addCardToZone("Land Tax", ai, ZoneType.Battlefield);
        }
        for (String c : new String[] {"Mountain", "Orcish Cannoneers", "Pyroclasm", "Mountain", "Mountain",
                "Temple of Triumph", "Crater Hellion"}) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        for (int i = 0; i < 3; i++) {
            addCardToZone("Plains", opponent, ZoneType.Battlefield);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testMountainForCannoneers() {
        Game game = setup(false);
        assertEquals(countCardsWithName(game, "Temple of Triumph", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Orcish Cannoneers", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testMountainForCannoneersWithLandTax() {
        Game game = setup(true);
        assertEquals(countCardsWithName(game, "Temple of Triumph", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Orcish Cannoneers", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
