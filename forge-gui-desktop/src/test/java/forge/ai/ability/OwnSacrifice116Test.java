package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 116 (follow-up of rule 114): a creature our own effect is about to sacrifice (unpaid echo,
// end-step sacrifice) feeds our free sacrifice outlet first (Goblin Bombardment) instead of being lost for nothing
public class OwnSacrifice116Test extends AITest {

    private Game setup(String creature, boolean lands, PhaseType phase) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        if (lands) {
            addCard("Mountain", ai);
            addCard("Mountain", ai);
        }
        addCard("Goblin Bombardment", ai);
        Card c = addCard(creature, ai);
        c.setSickness(false);
        c.setCameUnderControlSinceLastUpkeep(true);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(phase, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testUnpaidEchoFeedsOutlet() {
        // no land: the echo of Mogg War Marshal cannot be paid, it goes to Goblin Bombardment first
        Game game = setup("Mogg War Marshal", false, PhaseType.UNTAP);
        playUntilPhase(game, PhaseType.DRAW);
        Player opponent = game.getPlayers().get(0);
        assertEquals(countCardsWithName(game, "Mogg War Marshal", ZoneType.Battlefield), 0, gameStateToString(game));
        assertEquals(opponent.getLife(), 19, gameStateToString(game));
    }

    @Test
    public void testPaidEchoKeepsCreature() {
        // the echo is paid (nothing else to cast): no threat, the Marshal stays and nothing is sacrificed
        Game game = setup("Mogg War Marshal", true, PhaseType.UNTAP);
        playUntilPhase(game, PhaseType.DRAW);
        Player opponent = game.getPlayers().get(0);
        assertEquals(countCardsWithName(game, "Mogg War Marshal", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(opponent.getLife(), 20, gameStateToString(game));
    }

    @Test
    public void testEndStepSacrificeFeedsOutlet() {
        // Ball Lightning is sacrificed at the end step: it goes to Goblin Bombardment first
        Game game = setup("Ball Lightning", false, PhaseType.MAIN2);
        playUntilPhase(game, PhaseType.CLEANUP);
        Player opponent = game.getPlayers().get(0);
        assertEquals(countCardsWithName(game, "Ball Lightning", ZoneType.Battlefield), 0, gameStateToString(game));
        assertEquals(opponent.getLife(), 19, gameStateToString(game));
    }
}
