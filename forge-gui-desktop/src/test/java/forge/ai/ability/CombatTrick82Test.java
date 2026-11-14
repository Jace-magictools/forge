package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;

public class CombatTrick82Test extends AITest {

    // magic-tools rule 82 (tester 29/10 15:08, Chevalier - Gwyll): Boulder Rush was cast on Venerable Knight at the
    // beginning of combat, then the knight stayed home. A pure combat trick is not precast before attackers.
    @Test(invocationCount = 10)
    public void testNoPrecastWithoutAttack() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");

        addCard("Mountain", ai);
        addCard("Plains", ai);
        addCardToZone("Rimrock Knight", ai, ZoneType.Hand);
        Card knight = addCard("Venerable Knight", ai);
        knight.setSickness(false);
        Card bears = addCard("Grizzly Bears", opponent);
        bears.setSickness(false);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 5);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);

        String log = game.getGameLog().getLogEntries(null).stream().map(e -> e.message())
                .collect(java.util.stream.Collectors.joining(" / "));
        boolean rushCast = log.contains("cast Boulder Rush");
        boolean nothingHappened = opponent.getLife() == 20 && bears.isInPlay() && knight.isInPlay();
        assertFalse(rushCast && nothingHappened, "Boulder Rush cast without a fight\n" + log);
    }
}
