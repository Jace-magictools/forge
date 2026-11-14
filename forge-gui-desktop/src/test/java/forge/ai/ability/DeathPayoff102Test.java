package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.DeathPayoffAttachAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 102 (a tester reports 30/10 12:20-12:29, Gobelin - Patrilock): Skullclamp goes on a cheap X/1 of ours
// (it dies, we draw two) instead of staying off it or going on the bigger creature
public class DeathPayoff102Test extends AITest {

    private Game setup(String... creatures) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 2; i++) {
            addCard("Mountain", ai);
        }
        addCard("Skullclamp", ai);
        for (String c : creatures) {
            addCard(c, ai);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private Card onField(Game game, String name) {
        for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void testClampKillsTheOneToughness() {
        Game game = setup("Grizzly Bears", "Savannah Lions");
        Player ai = game.getPlayers().get(1);
        Card clamp = onField(game, "Skullclamp");
        assertTrue(DeathPayoffAttachAi.paysOnDeath(clamp));
        assertTrue(DeathPayoffAttachAi.isFodder(ai, clamp, onField(game, "Savannah Lions")));
        assertFalse(DeathPayoffAttachAi.isFodder(ai, clamp, onField(game, "Grizzly Bears")));
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Savannah Lions", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(ai.getCardsIn(ZoneType.Hand).size(), 2, gameStateToString(game));
    }

    @Test
    public void testExpensiveOneToughnessKept() {
        Game game = setup("Grizzly Bears", "Prodigal Sorcerer");
        Player ai = game.getPlayers().get(1);
        Card clamp = onField(game, "Skullclamp");
        assertFalse(DeathPayoffAttachAi.isFodder(ai, clamp, onField(game, "Prodigal Sorcerer")));
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Prodigal Sorcerer", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
