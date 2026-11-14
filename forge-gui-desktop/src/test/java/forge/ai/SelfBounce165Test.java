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

// magic-tools rule 165 (plateau, deck Uruk de tester, 04/11): Lieutenant Orque ("When this enters, return an Orc you
// control to its owner's hand") was cast with no other Orc and bounced itself, RR lost. Alone it stays in hand;
// control: with Brassclaw Orcs on the battlefield it is cast and the other Orc goes back.
public class SelfBounce165Test extends AITest {

    private static final String[] LIEUTENANT = {
            "Name:Lieutenant Orque",
            "ManaCost:R R",
            "Types:Creature Orc Soldier",
            "PT:3/2",
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ TrigBounce | TriggerDescription$ When CARDNAME enters, return an Orc you control to its owner's hand.",
            "SVar:TrigBounce:DB$ ChangeZone | Origin$ Battlefield | Destination$ Hand | Hidden$ True | ChangeType$ Orc.YouCtrl | ChangeNum$ 1 | Mandatory$ True",
            "S:Mode$ Continuous | Affected$ Orc.YouCtrl | AddKeyword$ Haste | Description$ Orcs you control have haste.",
            "T:Mode$ SpellCast | ValidCard$ Orc | ValidActivatingPlayer$ You | TriggerZones$ Battlefield | Execute$ TrigPump | TriggerDescription$ Whenever you cast an Orc spell, another target Orc gets +1/+0 until end of turn.",
            "SVar:TrigPump:DB$ Pump | ValidTgts$ Orc.Other | NumAtt$ +1",
            "SVar:PlayMain1:TRUE",
            "DeckHints:Type$Orc",
            "DeckNeeds:Type$Orc"
    };

    // the plateau card itself (scripts/forge/cards-perso/cus_jrrt), read from its script: not in the test card base
    private Card perso(Player owner, ZoneType zone, String... script) {
        PaperCard pc = new PaperCard(CardRules.fromScript(Arrays.asList(script)), "M10", CardRarity.Common);
        Card c = Card.fromPaperCard(pc, owner);
        c.setGameTimestamp(owner.getGame().getNextTimestamp());
        owner.getZone(zone).add(c);
        return c;
    }

    private Game game(boolean otherOrc) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Mountain", ai);
        if (otherOrc) {
            addCard("Brassclaw Orcs", ai).setSickness(false);
        }
        perso(ai, ZoneType.Hand, LIEUTENANT);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testAloneStaysInHand() {
        Game game = game(false);
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(ai.getCardsIn(ZoneType.Hand).size(), 1, "Lieutenant Orque kept in hand");
        assertEquals(ai.getLandsInPlay().stream().filter(c -> !c.isTapped()).count(), 2L, "no mana spent");
    }

    @Test(timeOut = 120000)
    public void testWithAnotherOrcIsCast() {
        Game game = game(true);
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(ai.getCreaturesInPlay().stream().anyMatch(c -> c.getName().equals("Lieutenant Orque")), "Lieutenant cast");
        assertTrue(ai.getCardsIn(ZoneType.Hand).stream().anyMatch(c -> c.getName().equals("Brassclaw Orcs")), "the other Orc bounced");
    }

    // tester 04/11: the rule must not hold back Plaguecrafter ("each player sacrifices a creature or planeswalker"):
    // alone on our side it sacrifices itself, but the opponent loses a creature, that is the point of the card.
    @Test(timeOut = 120000)
    public void testPlaguecrafterAloneIsCast() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Swamp", ai);
        }
        addCard("Grizzly Bears", opponent);
        addCardToZone("Plaguecrafter", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue(ai.getCardsIn(ZoneType.Hand).stream().noneMatch(c -> c.getName().equals("Plaguecrafter")), "Plaguecrafter cast");
        assertTrue(opponent.getCreaturesInPlay().isEmpty(), "the opponent sacrificed its creature");
    }

}
