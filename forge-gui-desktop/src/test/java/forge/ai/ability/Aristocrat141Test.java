package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: deck Sacrfice Mutuel, Mayhem Devil + Woe Strider (free "sacrifice another creature: scry 1").
// Puzzle MT10: the AI attacked into walls with the lethal on the board. Rule 141: the outlet goes when the pings kill.
public class Aristocrat141Test extends AITest {

    private Game setup(int oppLife) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        opponent.setLife(oppLife, null);
        for (String n : new String[] {"Mayhem Devil", "Woe Strider", "Grim Initiate", "Grim Initiate", "Grim Initiate", "Grim Initiate"}) {
            addCard(n, ai).setSickness(false);
        }
        for (int i = 0; i < 6; i++) {
            addCard("Wall of Stone", opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private int initiates(Game game) {
        return (int) game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Grim Initiate")).count();
    }

    // 4 Initiates + the Army they amass = 5 pings, the opponent is at 5
    @Test
    public void testLethalSacrifices() {
        Game game = setup(5);
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        assertTrue(game.getPlayers().get(0).getLife() <= 0 || game.isGameOver(), gameStateToString(game));
    }

    // at 9 the pings fall short: nothing is thrown away for a scry
    @Test
    public void testNotLethalKeepsCreatures() {
        Game game = setup(9);
        playUntilPhase(game, PhaseType.COMBAT_BEGIN);
        assertEquals(initiates(game), 4, gameStateToString(game));
    }
}
