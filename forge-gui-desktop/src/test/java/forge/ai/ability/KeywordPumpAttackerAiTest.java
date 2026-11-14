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

public class KeywordPumpAttackerAiTest extends AITest {

    // magic-tools rule 57: a sorcery that only grants keywords (Assault Strobe) is cast in main 1 on a creature
    // that attacks anyway when it raises its unblocked damage (Poudlar, v8: Assault Strobe never cast).
    private Game strobe(String creature, String blocker) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");

        addCard("Mountain", ai);
        addCardToZone("Assault Strobe", ai, ZoneType.Hand);
        Card c = addCard(creature, ai);
        c.setSickness(false);
        if (blocker != null) {
            addCard(blocker, opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 5);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    private boolean cast(Game game) {
        return game.getPlayers().get(1).getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Assault Strobe"));
    }

    @Test
    public void testKilnFiendDoubleStrike() {
        Game game = strobe("Kiln Fiend", null);
        assertTrue(cast(game), "Assault Strobe kept in hand\n" + gameStateToString(game));
        // Kiln Fiend 1/2 gets +3/+0 from the sorcery, then hits twice for 4
        assertEquals(game.getPlayers().get(0).getLife(), 12);
    }

    @Test
    public void testPlainAttacker() {
        Game game = strobe("Grizzly Bears", null);
        assertTrue(cast(game), "Assault Strobe kept in hand\n" + gameStateToString(game));
        assertEquals(game.getPlayers().get(0).getLife(), 16);
    }

    // no attack (a wall stops a 1/1): no double strike for nothing
    @Test
    public void testNoAttackNoStrobe() {
        Game game = strobe("Raging Goblin", "Wall of Wood");
        assertTrue(!cast(game), "Assault Strobe cast without an attack\n" + gameStateToString(game));
    }

    // magic-tools rule 180: the bare 1/2 Kiln Fiend stays home behind a wall, but the sorcery itself gives it +3/+0:
    // 4/2 double strike kills the wall or hits for 8, so Assault Strobe is cast and Kiln Fiend attacks
    @Test
    public void testKilnFiendStrobeBeatsWall() {
        Game game = strobe("Kiln Fiend", "Wall of Wood");
        assertTrue(cast(game), "Assault Strobe kept in hand\n" + gameStateToString(game));
        Player opp = game.getPlayers().get(0);
        assertTrue(opp.getLife() == 12 || opp.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Wall of Wood")),
                "life=" + opp.getLife());
    }
}
