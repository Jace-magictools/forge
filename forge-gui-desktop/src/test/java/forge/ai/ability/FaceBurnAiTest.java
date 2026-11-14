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

// magic-tools: rule 40, a damage spell that can only hit players (Sorcier kept Lava Spike against Nothing Gulch, 27/10)
public class FaceBurnAiTest extends AITest {

    // opponent at 20: stock AI waited for him to fall under 8 life
    @Test
    public void testLavaSpikeToTheFaceInMain2() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", 2, ai);
        addCardToZone("Lava Spike", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(opponent.getLife(), 17, "Lava Spike held\n" + gameStateToString(game));
    }

    // rule 44: an instant waits for the end of the opponent's turn (Lightning Bolt held in our main 2)
    @Test
    public void testLightningBoltNotForced() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", 2, ai);
        Card bolt = addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(opponent.getLife(), 20, "Lightning Bolt thrown at 20 life\n" + gameStateToString(game));
        assertTrue(bolt.isInZone(ZoneType.Hand));
    }

    // rule 44: at the end of the opponent's turn, Lightning Bolt goes to the face, not on a 3/3
    @Test
    public void testLightningBoltFaceFirst() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", 2, ai);
        addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        Card wolf = addCard("Watchwolf", opponent);
        wolf.setSickness(false);
        addCard("Watchwolf", ai).setSickness(false); // rule 283: not the defender
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(opponent.getLife(), 17, "Lightning Bolt not at the face\n" + gameStateToString(game));
        assertTrue(wolf.isInZone(ZoneType.Battlefield), "Watchwolf burnt\n" + gameStateToString(game));
    }

    // rule 283: we are the defender (their Watchwolf, nothing of ours): the Bolt kills the Watchwolf
    @Test
    public void testLightningBoltDefender283() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", 2, ai);
        addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        Card wolf = addCard("Watchwolf", opponent);
        wolf.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(opponent.getLife(), 20, "Lightning Bolt at the face\n" + gameStateToString(game));
        assertTrue(!wolf.isInZone(ZoneType.Battlefield), "Watchwolf alive\n" + gameStateToString(game));
    }

    // rule 44: at 3 life against a 3/3, the face can wait
    @Test
    public void testLightningBoltNotFaceInDanger() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        ai.setLife(3, null);
        addCards("Mountain", 2, ai);
        addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        Card wolf = addCard("Watchwolf", opponent);
        wolf.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(opponent.getLife(), 20, "Lightning Bolt at the face at 3 life\n" + gameStateToString(game));
    }

    // rule 48: Arc Trail (2 to a target, 1 to another) was held all game: the face took the 2, the 1 found nothing
    @Test
    public void testArcTrailCreatureAndFace() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", 2, ai);
        addCardToZone("Arc Trail", ai, ZoneType.Hand);
        Card bears = addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertTrue(bears.isInZone(ZoneType.Graveyard), "Arc Trail held or bears alive\n" + gameStateToString(game));
        assertEquals(opponent.getLife(), 19, "the 1 damage not at the face\n" + gameStateToString(game));
    }
}
