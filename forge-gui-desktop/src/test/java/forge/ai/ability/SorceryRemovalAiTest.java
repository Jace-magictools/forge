package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 39, sorcery-speed removal (Pox kept Befoul against Imperium, 27/10)
public class SorceryRemovalAiTest extends AITest {

    // the opponent on two lands with a harmless creature: Forge weighed only the creature and kept Befoul
    @Test
    public void testBefoulTakesLandWhenCreatureHeld() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Swamp", 4, ai);
        addCardToZone("Befoul", ai, ZoneType.Hand);
        addCards("Plains", 2, opponent);
        Card memnite = addCard("Memnite", opponent);
        memnite.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(opponent.getLandsInPlay().size(), 1, "Befoul held\n" + gameStateToString(game));
        assertTrue(memnite.isInZone(ZoneType.Battlefield), "Memnite destroyed\n" + gameStateToString(game));
    }

    // at 3 life against a 3/3 in our main 2: the threat was divided by 10 as if an instant could wait
    @Test
    public void testBefoulKillsAttackerInMain2() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        ai.setLife(3, null);
        addCards("Swamp", 4, ai);
        addCardToZone("Befoul", ai, ZoneType.Hand);
        Card wolf = addCard("Watchwolf", opponent);
        wolf.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(wolf.isInZone(ZoneType.Graveyard), "Befoul held\n" + gameStateToString(game));
    }

    // rule 43: Befoul destroys a land first, even with a real threat in front (Serra Angel, ai at 12), in our main 2
    @Test
    public void testBefoulLandFirst() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        ai.setLife(12, null);
        addCards("Swamp", 4, ai);
        addCardToZone("Befoul", ai, ZoneType.Hand);
        addCards("Plains", 2, opponent);
        Card angel = addCard("Serra Angel", opponent);
        angel.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(opponent.getLandsInPlay().size(), 1, "no land destroyed\n" + gameStateToString(game));
        assertTrue(angel.isInZone(ZoneType.Battlefield), "Serra Angel destroyed\n" + gameStateToString(game));
    }
}
