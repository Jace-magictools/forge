package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.AiAttackController;
import forge.game.Game;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 162 (table à 3-4 du site, 04/11): the AI counted commander damage on defense only. Its Wind Drake
// commander has dealt 19 commander damage to an opponent at 40 life with three Grizzly Bears, the AI is at 5 life:
// the Drake attacks and wins. Control: the same Drake that is not a commander stays home to block.
public class CommanderDamage162Test extends AITest {

    private Object[] game(boolean commander) {
        Game game = initAndCreateGame();
        game.getRules().addAppliedVariant(GameType.Commander);
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(5, null);
        opponent.setLife(40, null);
        for (int i = 0; i < 3; i++) {
            addCard("Grizzly Bears", opponent);
        }
        Card drake = addCard("Wind Drake", ai);
        drake.setSickness(false);
        if (commander) {
            drake.setCommander(true);
            opponent.addCommanderDamage(drake, 19);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return new Object[] {game, opponent, drake};
    }

    @Test(timeOut = 120000)
    public void testCommanderGoesForCommanderDamageKill() {
        Object[] p = game(true);
        Player opp = (Player) p[1];
        assertTrue(opp.getCommanderDamage((Card) p[2]) >= 21 || opp.hasLost(),
                "the commander attacked for the 21st point, commander damage " + opp.getCommanderDamage((Card) p[2]));
    }

    @Test(timeOut = 120000)
    public void testNonCommanderStaysHome() {
        Object[] p = game(false);
        Player opp = (Player) p[1];
        assertEquals(opp.getLife(), 40, "the Drake stayed home to block");
    }

    @Test(timeOut = 120000)
    public void testPrefersOpponentCloseToCommanderDeath() {
        Game game = initAndCreateThreePlayerGame();
        game.getRules().addAppliedVariant(GameType.Commander);
        Player first = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        Player third = game.getPlayers().get(2);
        first.setLife(40, null);
        third.setLife(30, null);
        Card drake = addCard("Wind Drake", ai);
        drake.setCommander(true);
        first.addCommanderDamage(drake, 19);
        for (int i = 0; i < 30; i++) {
            assertEquals(AiAttackController.choosePreferredDefenderPlayer(ai, true), first,
                    "the opponent 2 commander damage from death is the target");
        }
    }
}
