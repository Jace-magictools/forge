package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

// magic-tools: rule 234, tournament v24 traces: Dog Umbra 0 casts for 2320 refusals in 60 games, its script has no AttachAILogic.
public class UnscriptedAura234Test extends AITest {

    private Card castOn(final String aura, final String mine, final String theirs) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 5; i++) {
            addCard("Plains", ai);
            addCard("Mountain", ai);
            addCard("Swamp", ai);
        }
        if (mine != null) {
            addCard(mine, ai).setSickness(false);
        }
        if (theirs != null) {
            addCard(theirs, opponent).setSickness(false);
        }
        addCardToZone(aura, ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals(aura)) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void testRestrictionGoesOnTheOpponent() {
        Card c = castOn("Dog Umbra", "Grizzly Bears", "Serra Angel");
        assertNotNull(c, "Dog Umbra stays in hand");
        assertEquals(c.getAttachedTo().getName(), "Serra Angel");
    }

    @Test
    public void testPetrifyGoesOnTheOpponent() {
        Card c = castOn("Petrify", null, "Serra Angel");
        assertNotNull(c, "Petrify stays in hand");
        assertEquals(c.getAttachedTo().getName(), "Serra Angel");
    }

    @Test
    public void testBonusGoesOnOurCreature() {
        Card c = castOn("Deviant Glee", "Grizzly Bears", "Serra Angel");
        assertNotNull(c, "Deviant Glee stays in hand");
        assertEquals(c.getAttachedTo().getName(), "Grizzly Bears");
    }

    @Test
    public void testNoTargetNoCast() {
        assertEquals(castOn("Dog Umbra", "Grizzly Bears", null), null);
    }
}
