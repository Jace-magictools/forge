package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: a tester report 29/10 (ElfBall - Toxico turn 4, engine of 28/10 16:00): after Heritage Druid and
// Nettle Sentinel, ElfBall attacked with Llanowar Elves for 1 instead of tapping the three Elves for Lead the Stampede.
// No longer reproduced with rules 1-69: regression tests only
public class HeritageStampede70Test extends AITest {

    @Test
    public void testDruidManaForStampedeFromHand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCards("Forest", 2, ai);
        addCard("Llanowar Elves", ai);
        addCardToZone("Heritage Druid", ai, ZoneType.Hand);
        addCardToZone("Nettle Sentinel", ai, ZoneType.Hand);
        addCardToZone("Lead the Stampede", ai, ZoneType.Hand);
        play(game, ai, opponent);
        assertEquals(countCardsWithName(game, "Lead the Stampede", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(opponent.getLife(), 20, gameStateToString(game));
    }

    @Test
    public void testDruidManaForStampedeInReportedPosition() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCards("Forest", 2, ai).forEach(c -> c.setTapped(true));
        addCard("Llanowar Elves", ai);
        addCard("Heritage Druid", ai).setSickness(true);
        addCard("Nettle Sentinel", ai).setSickness(true);
        addCardToZone("Lead the Stampede", ai, ZoneType.Hand);
        addCardToZone("Forest", ai, ZoneType.Hand);
        ai.setLandsPlayedThisTurn(1);
        play(game, ai, opponent);
        assertEquals(countCardsWithName(game, "Lead the Stampede", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(opponent.getLife(), 20, gameStateToString(game));
    }

    private void play(Game game, Player ai, Player opponent) {
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        addCard("Plains", opponent);
        addCard("Mirrex", opponent);
        addCard("Prosthetic Injector", opponent);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
    }
}
