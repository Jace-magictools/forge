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

// magic-tools: rule 97, Rohan died at 5 life to Jace's Phantasm with Lightning Helix in hand all game (tournament v12, 30/10)
public class DangerFace97Test extends AITest {

    // 4 life against a 4/4 flier Helix cannot kill: the face and three life, in our own turn
    @Test
    public void testHelixInDanger() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        ai.setLife(4, null);
        addCard("Mountain", ai);
        addCard("Plains", ai);
        addCardToZone("Lightning Helix", ai, ZoneType.Hand);
        Card air = addCard("Air Elemental", opponent);
        air.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(ai.getCardsIn(ZoneType.Graveyard).size(), 1, "Lightning Helix held at 4 life\n" + gameStateToString(game));
        assertEquals(ai.getLife(), 7, "no life gained\n" + gameStateToString(game));
        assertEquals(opponent.getLife(), 17, "Helix not at the face\n" + gameStateToString(game));
    }

    // witness: at 20 life Helix still waits for the end of the opponent's turn (rule 44)
    @Test
    public void testHelixNotInDanger() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Mountain", ai);
        addCard("Plains", ai);
        Card helix = addCardToZone("Lightning Helix", ai, ZoneType.Hand);
        Card air = addCard("Air Elemental", opponent);
        air.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(helix.isInZone(ZoneType.Hand), "Lightning Helix cast at 20 life in our turn\n" + gameStateToString(game));
    }

    // rule 98: divided damage, Forked Bolt at 4 life against a 4/4 flier goes whole to the face in our main 2
    @Test
    public void testForkedBoltInDanger() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        ai.setLife(4, null);
        addCard("Mountain", ai);
        addCardToZone("Forked Bolt", ai, ZoneType.Hand);
        Card air = addCard("Air Elemental", opponent);
        air.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(ai.getCardsIn(ZoneType.Graveyard).size(), 1, "Forked Bolt held at 4 life\n" + gameStateToString(game));
        assertEquals(opponent.getLife(), 18, "Forked Bolt not at the face\n" + gameStateToString(game));
    }

    // rule 98: Electrolyze at the opponent's end step, nothing to kill: both points to the face (50 deaths with it in hand, v12)
    @Test
    public void testElectrolyzeInDanger() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        ai.setLife(4, null);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        addCard("Island", ai);
        addCardToZone("Electrolyze", ai, ZoneType.Hand);
        Card air = addCard("Air Elemental", opponent);
        air.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, opponent);
        game.getAction().checkStateEffects(true);
        playUntilNextTurn(game);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Electrolyze")),
                "Electrolyze held at 4 life\n" + gameStateToString(game));
        assertEquals(opponent.getLife(), 18, "Electrolyze not at the face\n" + gameStateToString(game));
    }

    // rule 99: Fireball (X, divided evenly) at 4 life with four Mountains, the 4/4 flier out of reach: X=3 to the face
    @Test
    public void testFireballInDanger() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        ai.setLife(4, null);
        for (int i = 0; i < 4; i++) {
            addCard("Mountain", ai);
        }
        addCardToZone("Fireball", ai, ZoneType.Hand);
        Card air = addCard("Air Elemental", opponent);
        air.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Fireball")),
                "Fireball held at 4 life\n" + gameStateToString(game));
        assertEquals(opponent.getLife(), 17, "Fireball not at the face with X=3\n" + gameStateToString(game));
    }
}
