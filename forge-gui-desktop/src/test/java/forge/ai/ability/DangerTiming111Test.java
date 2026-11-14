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

// magic-tools: rule 111, Lightning Bolt held to death in the v14 tournament (27 deaths): in danger, rule 97 waited for the
// opponent's end step and the attack killed us first
public class DangerTiming111Test extends AITest {

    // 3 life against a 6/6 Bolt cannot kill: the face during the opponent's turn, before his attack
    @Test
    public void testBoltBeforeLethalAttack() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        ai.setLife(3, null);
        addCard("Mountain", ai);
        Card bolt = addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        Card wurm = addCard("Craw Wurm", opponent);
        wurm.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.COMBAT_DAMAGE);
        assertTrue(bolt.isInZone(ZoneType.Graveyard) || ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Lightning Bolt")),
                "Lightning Bolt held before a lethal attack\n" + gameStateToString(game));
        assertEquals(opponent.getLife(), 17, "Bolt not at the face\n" + gameStateToString(game));
    }

    // witness: at 20 life Bolt still waits for the end of the opponent's turn (rule 44)
    @Test
    public void testBoltNotInDanger() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCard("Mountain", ai);
        Card bolt = addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        Card wurm = addCard("Craw Wurm", opponent);
        wurm.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.COMBAT_END);
        assertTrue(bolt.isInZone(ZoneType.Hand), "Lightning Bolt cast at 20 life before the end step\n" + gameStateToString(game));
    }
}
