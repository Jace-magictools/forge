package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 71 (a tester reports 29/10 07:11 and 07:14, Avacyn - Wid turns 6 and 8): with Sol Ring in hand,
// Avacyn cast Giada then Serra Avenger and kept the Ring; a mana rock that pays for a bigger spell goes first
public class ManaRock71Test extends AITest {

    private Game setup(String[] board, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String c : board) {
            addCard(c, ai);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testSolRingBeforeAvenger() {
        Game game = setup(new String[] {"Plains", "New Benalia", "Plains", "Giada, Font of Hope", "Scroll of Avacyn", "Soul Warden"},
                "Righteous Valkyrie", "Serra Avenger", "Lyra Dawnbringer", "Sol Ring", "Ajani's Pridemate");
        assertEquals(countCardsWithName(game, "Sol Ring", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testSolRingTwoLands() {
        Game game = setup(new String[] {"Plains", "New Benalia", "Soul Warden"},
                "Righteous Valkyrie", "Giada, Font of Hope", "Sol Ring", "Ajani's Pridemate");
        assertEquals(countCardsWithName(game, "Sol Ring", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
