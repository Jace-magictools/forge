package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 264 (tester 09/11, linear decks): Wild Growth cast before Arbor Elf with both in hand ; the elf
// has summoning sickness, the aura does not, the elf goes first.
public class SickFirst264Test extends AITest {

    private Game setup(int forests, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < forests; i++) {
            addCard("Forest", ai);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    /** one Forest: Arbor Elf, not Wild Growth */
    @Test
    public void testArborElfBeforeWildGrowth() {
        Game game = setup(1, "Wild Growth", "Arbor Elf", "Leatherback Baloth");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Arbor Elf", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** one Forest: Llanowar Elves, not Utopia Sprawl */
    @Test
    public void testElvesBeforeSprawl() {
        Game game = setup(1, "Utopia Sprawl", "Llanowar Elves", "Leatherback Baloth");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Llanowar Elves", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** two Forests: both get cast */
    @Test
    public void testBothWithTwoForests() {
        Game game = setup(2, "Wild Growth", "Arbor Elf", "Leatherback Baloth");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Arbor Elf", ZoneType.Battlefield)
                + countCardsWithName(game, "Wild Growth", ZoneType.Battlefield), 2, gameStateToString(game));
    }
}
