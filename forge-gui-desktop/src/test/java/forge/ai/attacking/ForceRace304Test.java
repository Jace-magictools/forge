package forge.ai.attacking;

import java.util.Arrays;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import static org.testng.Assert.assertTrue;

// magic-tools rule 304 (D172, tester 12/11, a test game turn 12): 6/4, 5/4, 5/4, 3/2 and a 1/1 mana elf
// ready to attack against 3/4, 3/5, 1/3, 1/3 and an opponent at 16: the AI stayed home. With the power edge and the
// life race won it attacks. Control: same board against a 7/7 and a 6/6 (13 power, no edge) it stays home.
public class ForceRace304Test extends AITest {

    // Thorïn Écu-de-Chêne (scripts/forge/cards-perso/cus_jrrt), read from its script: not in the test card base
    private static final String[] THORIN = {
            "Name:Thorïn Écu-de-Chêne",
            "ManaCost:1 R R",
            "Types:Legendary Creature Dwarf Warrior",
            "PT:3/4",
            "K:Battle cry",
            "A:AB$ Pump | Cost$ Sac<1/Artifact> | Defined$ Self | KW$ Indestructible | ActivationLimit$ 1 | SubAbility$ DBPutCounter | SpellDescription$ CARDNAME gains indestructible until end of turn. Put a +1/+1 counter on him. Activate only once each turn.",
            "SVar:DBPutCounter:DB$ PutCounter | Defined$ Self | CounterType$ P1P1 | CounterNum$ 1"
    };

    private Card perso(Player owner, String... script) {
        PaperCard pc = new PaperCard(CardRules.fromScript(Arrays.asList(script)), "M10", CardRarity.Common);
        Card c = Card.fromPaperCard(pc, owner);
        c.setGameTimestamp(owner.getGame().getNextTimestamp());
        owner.getZone(ZoneType.Battlefield).add(c);
        return c;
    }

    private Card body(String name, Player p, int pow, int tou) {
        Card c = addCard(name, p);
        c.setBasePower(pow);
        c.setBaseToughness(tou);
        c.setSickness(false);
        return c;
    }

    private Game play(boolean edge) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        opponent.setLife(16, null);
        ai.setLife(17, null);
        for (int i = 0; i < 4; i++) {
            addCard("Forest", ai).setTapped(true);
            addCard("Mountain", opponent).setTapped(true);
        }
        Card beetle = addCard("Nessian Hornbeetle", ai);
        beetle.setSickness(false);
        beetle.addCounterInternal(CounterEnumType.P1P1, 1, ai, false, null, null);
        Card bronto = addCard("Thrashing Brontodon", ai);
        bronto.setSickness(false);
        addCard("Rancor", ai).attachToEntity(beetle, null);
        addCard("Rancor", ai).attachToEntity(bronto, null);
        addCard("Kraul Harpooner", ai).setSickness(false);
        addCard("Arbor Elf", ai).setSickness(false);
        addCard("Surrak, the Hunt Caller", ai).setSickness(false);
        if (edge) {
            Card thorin = perso(opponent, THORIN);
            thorin.setSickness(false);
            addCard("Sol Ring", opponent);
            body("Hill Giant", opponent, 3, 5);
            body("Hill Giant", opponent, 1, 3);
            body("Hill Giant", opponent, 1, 3);
        } else {
            body("Hill Giant", opponent, 7, 7);
            body("Hill Giant", opponent, 6, 6);
            body("Hill Giant", opponent, 6, 6);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.COMBAT_DECLARE_BLOCKERS);
        return game;
    }

    @Test(timeOut = 120000)
    public void testPowerEdgeAttacks() {
        Game game = play(true);
        assertTrue(game.getCombat() != null && game.getCombat().getAttackers().size() >= 2,
                "attacked with the power edge\n" + gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testNoEdgeStaysHome() {
        Game game = play(false);
        assertTrue(game.getCombat() == null || game.getCombat().getAttackers().isEmpty(),
                "stayed home without the edge\n" + gameStateToString(game));
    }
}
