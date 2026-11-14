package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 202, a tester reports 05/11 (a test game): play what finds cards before attacking.
public class DrawFirst202Test extends AITest {

    private Player setup(Game game) {
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        addCard("Swamp", opponent);
        ai.setLandsPlayedThisTurn(1);
        return ai;
    }

    private void toCombat(Game game, Player ai) {
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
    }

    /** the attack would tap the third mana: Lead the Stampede first */
    @Test
    public void testSorceryBeforeTheElfAttacks() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Fyndhorn Elves", ai).setSickness(false);
        addCardToZone("Lead the Stampede", ai, ZoneType.Hand);
        toCombat(game, ai);
        assertEquals(countCardsWithName(game, "Lead the Stampede", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    /** Gilt-Leaf Archdruid draws for a Druid: Leaf-Crowned Visionary before the attack */
    @Test
    public void testEngineDrawsForTheSpell() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Gilt-Leaf Archdruid", ai).setSickness(false);
        addCardToZone("Leaf-Crowned Visionary", ai, ZoneType.Hand);
        toCombat(game, ai);
        assertEquals(countCardsWithName(game, "Leaf-Crowned Visionary", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** an engine in hand with a creature to feed it */
    @Test
    public void testEngineWithFuelInHand() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        for (int i = 0; i < 4; i++) {
            addCard("Forest", ai);
        }
        addCard("Grizzly Bears", ai).setSickness(false);
        addCardToZone("Beast Whisperer", ai, ZoneType.Hand);
        addCardToZone("Runeclaw Bear", ai, ZoneType.Hand);
        toCombat(game, ai);
        assertEquals(countCardsWithName(game, "Beast Whisperer", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** draws when it enters */
    @Test
    public void testDrawsWhenItEnters() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Grizzly Bears", ai).setSickness(false);
        addCardToZone("Elvish Visionary", ai, ZoneType.Hand);
        toCombat(game, ai);
        assertEquals(countCardsWithName(game, "Elvish Visionary", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** nothing to find: the stock timing stays (the Bears wait for main 2) */
    @Test
    public void testPlainCreatureStillWaits() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Gilt-Leaf Archdruid", ai).setSickness(false);
        addCardToZone("Runeclaw Bear", ai, ZoneType.Hand);
        toCombat(game, ai);
        assertEquals(countCardsWithName(game, "Runeclaw Bear", ZoneType.Battlefield), 0, gameStateToString(game));
    }
}
