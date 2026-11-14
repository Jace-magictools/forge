package forge.ai.ability;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

public class TrailFace262Test extends AITest {

    // magic-tools rule 262: BlastBurn kept Arc Trail from turn 5 to its death on turn 20 (visionneur 9/11):
    // no opposing creature died to 2 damage (Triskaidekaphile 1/3, Enduring Curiosity 4/3), so Forge never cast it.
    private Game play(String blocker, int life) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", 3, ai);
        addCardToZone("Arc Trail", ai, ZoneType.Hand);
        if (blocker != null) {
            addCard(blocker, opponent);
        }
        opponent.setLife(life, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 11);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testArcTrailFaceAgainstToughCreature() {
        Game game = play("Wall of Omens", 13);
        Player opponent = game.getPlayers().get(0);
        Card wall = findCardWithName(game, "Wall of Omens");
        assertEquals(opponent.getLife(), 11, "Arc Trail kept in hand\n" + gameStateToString(game));
        assertEquals(wall.getDamage(), 1, "the other target should be the opponent's creature\n" + gameStateToString(game));
    }

    @Test
    public void testArcTrailStillKillsAX2() {
        Game game = play("Grizzly Bears", 20);
        assertTrue(game.getPlayers().get(0).getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Grizzly Bears")),
                "Forge's logic kills the 2/2 first\n" + gameStateToString(game));
    }

    @Test
    public void testArcTrailWaitsWithoutOtherTarget() {
        Game game = play(null, 13);
        assertEquals(game.getPlayers().get(0).getLife(), 13, "no opposing permanent: the 1 damage would hit us\n" + gameStateToString(game));
    }

    // Searing Blaze (player, then his creature; 3 + 3 with landfall) is not Arc Trail: no land, no 1 + 1 (cmp295, BlastBurn -5)
    @Test
    public void testSearingBlazeWaitsForLandfall() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", 3, ai);
        addCardToZone("Searing Blaze", ai, ZoneType.Hand);
        addCard("Wall of Omens", opponent);
        opponent.setLife(13, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 11);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(opponent.getLife(), 13, "Searing Blaze cast for 1 without landfall\n" + gameStateToString(game));
    }
}
