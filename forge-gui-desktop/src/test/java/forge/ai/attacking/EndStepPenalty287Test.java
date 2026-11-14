package forge.ai.attacking;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools rule 287 (plateau D168, Orque Berserk): a creature that hurts its controller at end step when it stays
// home attacks each combat. Erg Raiders 2/3 ("if it didn't attack this turn, it deals 2 damage to you") against a
// Hill Giant 3/3 that kills it: the stock AI stays home and takes 2. Control: Grizzly Bears, no trigger, stay home.
public class EndStepPenalty287Test extends AITest {

    private Player[] game(String aiCreature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Hill Giant", opponent).setSickness(false);
        Card c = addCard(aiCreature, ai);
        c.setSickness(false);
        // Forge scripts Erg Raiders with SVar:MustAttack:True by hand; Orque Berserk had nothing: drop it to read the trigger
        c.removeSVar("MustAttack");
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.MAIN2);
        return new Player[] {ai, opponent};
    }

    private static boolean attacked(Player ai, Player opp, String name) {
        return opp.getLife() < 20
                || ai.getCardsIn(ZoneType.Graveyard).anyMatch(x -> x.getName().equals(name))
                || ai.getCreaturesInPlay().anyMatch(x -> x.getName().equals(name) && x.isTapped());
    }

    @Test(timeOut = 120000)
    public void testErgRaidersAttacks() {
        Player[] p = game("Erg Raiders");
        assertTrue(attacked(p[0], p[1], "Erg Raiders"), "Erg Raiders stayed home, opp life " + p[1].getLife());
    }

    @Test(timeOut = 120000)
    public void testPlainCreatureStaysHome() {
        Player[] p = game("Grizzly Bears");
        assertTrue(!attacked(p[0], p[1], "Grizzly Bears"), "Bears attacked into the Giant");
    }
}
