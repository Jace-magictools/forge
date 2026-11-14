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

// magic-tools rule 166 (plateau, deck Uruk de tester, 04/11): Port des Havres Gris ("{T}: Add {W}, {G} or {R}.
// Exile the bottom three cards of your library") is no mana ability under CR 605.1a (it moves cards from a library), so
// it never paid a spell, and DigAi refused the exile at instant speed with more than one card in hand. Two Ports and
// two Grizzly Bears in hand: both Ports are activated first, then a Bear is cast.
// Control: with 4 cards left in the library the Ports are not activated (the library guard stays).
public class ManaDig166Test extends AITest {

    private static final String[] PORT = {
            "Name:Port des Havres Gris",
            "ManaCost:no cost",
            "Types:Land City",
            "A:AB$ Mana | Cost$ T | Produced$ Combo W G R | SubAbility$ DBExile | SpellDescription$ Add {W}, {G}, or {R}. Exile the bottom three cards of your library.",
            "SVar:DBExile:DB$ Dig | DigNum$ 3 | FromBottom$ True | ChangeNum$ All | DestinationZone$ Exile",
            "A:AB$ Mana | Cost$ T | Produced$ C | IsPresent$ Card.YouOwn | PresentZone$ Exile | PresentCompare$ GE9 | SpellDescription$ Add {1}. Activate only if you have at least nine cards in exile."
    };

    // the plateau card itself (scripts/forge/cards-perso/cus_jrrt), read from its script: not in the test card base
    private Card perso(Player owner, ZoneType zone, String... script) {
        PaperCard pc = new PaperCard(CardRules.fromScript(Arrays.asList(script)), "M10", CardRarity.Common);
        Card c = Card.fromPaperCard(pc, owner);
        c.setGameTimestamp(owner.getGame().getNextTimestamp());
        owner.getZone(zone).add(c);
        return c;
    }

    private Game game(int library) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        perso(ai, ZoneType.Battlefield, PORT);
        perso(ai, ZoneType.Battlefield, PORT);
        addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        fillLibrary(ai, library);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testPortPaysForABear() {
        Game game = game(20);
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(ai.getCreaturesInPlay().size(), 1, "a Grizzly Bears paid with the Ports");
        assertEquals(ai.getCardsIn(ZoneType.Exile).size(), 6, "two Ports tapped, six cards exiled");
    }

    @Test(timeOut = 120000)
    public void testNearlyEmptyLibraryKeepsTheGuard() {
        Game game = game(4);
        Player ai = game.getPlayers().get(1);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(ai.getCardsIn(ZoneType.Library).size(), 4, "library left alone");
    }
}
