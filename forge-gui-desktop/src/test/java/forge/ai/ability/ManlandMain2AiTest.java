package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class ManlandMain2AiTest extends AITest {

    private Game playTurn(final boolean withCreature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Lavaclaw Reaches", ai).setSickness(false);
        addCard("Mountain", ai);
        addCards("Swamp", 2, ai);
        if (withCreature) {
            addCardToZone("Foundry Inspector", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    // Mishra (mis14, 27/10): 4 lands and Foundry Inspector in hand, the land does not take the mana to attack
    @Test
    public void testCreatureCastInsteadOfManland() {
        Game game = playTurn(true);
        assertEquals(countCardsWithName(game, "Foundry Inspector", ZoneType.Battlefield), 1);
        assertEquals(game.getPlayers().get(0).getLife(), 20);
    }

    // nothing to cast: stock behaviour, the land attacks
    @Test
    public void testManlandAttacksWithEmptyHand() {
        Game game = playTurn(false);
        assertEquals(game.getPlayers().get(0).getLife(), 18);
    }

    // rule 245: Skinrender with no target but itself will not be cast in main 2, the land does not wait for it
    @Test
    public void testNoManaKeptForRefusedEtb() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Lavaclaw Reaches", ai).setSickness(false);
        addCard("Mountain", ai);
        addCards("Swamp", 2, ai);
        addCardToZone("Skinrender", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(opponent.getLife(), 18);
    }
}
