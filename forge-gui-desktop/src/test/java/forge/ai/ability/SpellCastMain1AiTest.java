package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

public class SpellCastMain1AiTest extends AITest {

    private Game setUp(final boolean withSpell) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        addCard("Island", ai);
        addCards("Mountain", 2, ai);
        addCard("Grizzly Bears", ai).setSickness(false);
        addCardToZone("Balmor, Battlemage Captain", ai, ZoneType.Hand);
        if (withSpell) {
            addCardToZone("Lightning Bolt", ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 30);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        gameLoopUntilNextPhase(game);
        return game;
    }

    // Balmor with Lightning Bolt in hand: cast in main 1, before the spell that pumps the team
    @Test
    public void testSpellTriggerCreatureInMain1() {
        Game game = setUp(true);
        assertEquals(countCardsWithName(game, "Balmor, Battlemage Captain", ZoneType.Battlefield), 1);
    }

    // no instant or sorcery in hand: stock behaviour, the creature waits for main 2
    @Test
    public void testNoSpellWaitsForMain2() {
        Game game = setUp(false);
        assertEquals(countCardsWithName(game, "Balmor, Battlemage Captain", ZoneType.Battlefield), 0);
    }
}
