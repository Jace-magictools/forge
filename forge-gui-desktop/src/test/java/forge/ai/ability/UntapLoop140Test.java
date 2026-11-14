package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: deck Myr, two Myr Galvanizer + Palladium Myr = unbounded mana; the AI never ran the loop
// (energy puzzle MT09: it attacked with three Myr and never touched Myr Matrix). Rule 140: the loop as a shortcut.
public class UntapLoop140Test extends AITest {

    private Game setup(int galvanizers) {
        return setup(galvanizers, false);
    }

    private Game setup(int galvanizers, boolean tapped) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < galvanizers; i++) {
            forge.game.card.Card g = addCard("Myr Galvanizer", ai);
            g.setSickness(false);
            g.setTapped(tapped && i > 0);
        }
        forge.game.card.Card pal = addCard("Palladium Myr", ai);
        pal.setSickness(false);
        pal.setTapped(tapped);
        addCard("Myr Matrix", ai);
        addCard("Plains", ai);
        addCard("Plains", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private int tokens(Game game) {
        Player ai = game.getPlayers().get(1);
        return (int) ai.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isToken()).count();
    }

    @Test
    public void testLoopMakesManyTokens() {
        Game game = setup(2);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(tokens(game) >= 5, gameStateToString(game));
    }

    @Test
    public void testOneGalvanizerIsNoLoop() {
        Game game = setup(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(tokens(game) <= 1, gameStateToString(game));
    }

    // the producer and the second engine already tapped (Palladium paid a spell): the Plains pays the first {1}
    @Test
    public void testLoopFromTappedPieces() {
        Game game = setup(2, true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(tokens(game) >= 5, gameStateToString(game));
    }
}
