package forge.ai;

import java.util.Arrays;

import org.testng.annotations.Test;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools rule 315 (D181, plateau 14/11, tester, a test game): Pisteur Uruk-Hai
// "{X}{R}{B}: X target other creatures you control gain landwalk" was activated at X=0, without target, mana lost.
public class XTargets315Test extends AITest {

    private static final String[] PISTEUR = {
            "Name:Pisteur Uruk-Hai",
            "ManaCost:1 B R",
            "Types:Creature Orc Uruk Scout",
            "PT:2/3",
            "K:Landwalk:Plains",
            "K:Landwalk:Mountain",
            "K:Landwalk:Swamp",
            "A:AB$ Pump | Cost$ X R B | ValidTgts$ Creature.Other+YouCtrl | TargetMin$ X | TargetMax$ X | KW$ Landwalk:Plains & Landwalk:Mountain & Landwalk:Swamp | SpellDescription$ X target other creatures you control gain plainswalk, mountainwalk, and swampwalk until end of turn.",
            "SVar:X:Count$xPaid"
    };

    // the plateau card itself (scripts/forge/cards-perso/cus_jrrt), read from its script: not in the test card base
    private Card perso(Player owner, ZoneType zone, String... script) {
        PaperCard pc = new PaperCard(CardRules.fromScript(Arrays.asList(script)), "M10", CardRarity.Common);
        Card c = Card.fromPaperCard(pc, owner);
        c.setGameTimestamp(owner.getGame().getNextTimestamp());
        owner.getZone(zone).add(c);
        return c;
    }

    private Game game(boolean otherCreature) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", ai);
        }
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        perso(ai, ZoneType.Battlefield, PISTEUR).setSickness(false);
        if (otherCreature) {
            addCard("Hill Giant", ai).setSickness(false);
        }
        addCard("Swamp", opponent);
        addCard("Wall of Stone", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private static long tapped(Player p) {
        return p.getLandsInPlay().stream().filter(Card::isTapped).count();
    }

    @Test(timeOut = 120000)
    public void testNoOtherCreatureNoActivation() {
        Game game = game(false);
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(tapped(ai), 0L, "Pisteur activated without any other creature to target (X=0)");
    }

    @Test(timeOut = 120000)
    public void testOtherCreatureNeverAtZero() {
        Game game = game(true);
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        long t = tapped(ai);
        assertTrue(t != 2 && t != 4, "Pisteur activated at X=0 (" + t + " lands tapped)");
        // PumpAi keeps a random chance not to pump: activating is not guaranteed, but when it is, X=1 on Hill Giant
        assertTrue(t == 0 || (t == 3 && opponent.getLife() <= 17), t + " lands tapped, opp life " + opponent.getLife());
    }
}
