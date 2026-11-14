package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 20, one-sided land wipe (Armageddon with indestructible Bridges was refused against creatures)
public class LandWipeAiTest extends AITest {

    private Game wipeGame(String ourLand, String creature, int creatures, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        for (int i = 0; i < 4; i++) {
            Card land = addCard(ourLand, ai);
            land.setTapped(false);
        }
        addCards("Forest", 4, opponent);
        addCards(creature, creatures, opponent);
        addCardToZone("Armageddon", ai, ZoneType.Hand);
        for (String h : hand) {
            addCardToZone(h, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testArmageddonWithIndestructibleLands() {
        Game game = wipeGame("Rustvale Bridge", "Grizzly Bears", 2);
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getLandsInPlay().size(), 0, "Armageddon not cast\n" + gameStateToString(game));
    }

    @Test
    public void testArmageddonWithSweeperAgainstBigBoard() {
        Game game = wipeGame("Rustvale Bridge", "Craw Wurm", 3, "Pyroclasm");
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getLandsInPlay().size(), 0, "Armageddon not cast\n" + gameStateToString(game));
    }

    @Test
    public void testNoArmageddonAgainstBigBoardWithoutSweeper() {
        Game game = wipeGame("Rustvale Bridge", "Craw Wurm", 3);
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getLandsInPlay().size(), 4, "Armageddon cast into a lethal board\n" + gameStateToString(game));
    }

    // rule 64: the backup sweeper must be payable with the lands we keep (Jokulhaups costs 6, 4 Bridges left)
    @Test
    public void testNoArmageddonWithUnpayableSweeper() {
        Game game = wipeGame("Rustvale Bridge", "Craw Wurm", 3, "Jokulhaups");
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getLandsInPlay().size(), 4, "Armageddon cast on an unpayable sweeper\n" + gameStateToString(game));
    }

    @Test
    public void testNoSymmetricArmageddonAgainstCreatures() {
        Game game = wipeGame("Plains", "Grizzly Bears", 2);
        Player opponent = game.getPlayers().get(0);
        assertEquals(opponent.getLandsInPlay().size(), 4, "symmetric Armageddon cast\n" + gameStateToString(game));
    }
}
