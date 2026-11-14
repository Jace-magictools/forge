package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 114 (a tester report 30/10 19:03, Gobelin - Nothing Gulch turn 5): the AI lets Mogg War Marshal's
// echo go (it makes a Goblin when it dies) when paying would keep it from casting Goblin Bombardment
public class EchoPay114Test extends AITest {

    private Game setup(boolean spellInHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        Card marshal = addCard("Mogg War Marshal", ai);
        marshal.setSickness(false);
        marshal.setCameUnderControlSinceLastUpkeep(true);
        addCardToZone(spellInHand ? "Goblin Bombardment" : "Mountain", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UNTAP, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testLetsEchoGoForSpell() {
        Game game = setup(true);
        playUntilPhase(game, PhaseType.DRAW);
        assertEquals(countCardsWithName(game, "Mogg War Marshal", ZoneType.Battlefield), 0, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Goblin Token", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testPaysEchoWithNothingToCast() {
        Game game = setup(false);
        playUntilPhase(game, PhaseType.DRAW);
        assertEquals(countCardsWithName(game, "Mogg War Marshal", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
