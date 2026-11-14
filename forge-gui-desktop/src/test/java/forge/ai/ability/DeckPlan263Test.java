package forge.ai.ability;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.ComputerUtil;
import forge.ai.DeckPlanAi;
import forge.ai.PlayerControllerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

// magic-tools rule 263 (tester 09/11, consigne A : le plan du deck passé à l'IA). The deck header carries the Brain's
// plan (KeyCards, Combo$). Viscera Seer is the weakest card, so without the plan it is the one discarded or sacrificed ;
// with the plan (Seer is a combo piece) Grizzly Bears goes instead. Kitchen Finks completes Melira + Seer : played first.
public class DeckPlan263Test extends AITest {

    private Game setUp(boolean plan) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        if (plan) {
            Deck d = ai.getRegisteredPlayer().getDeck();
            d.addKeyCard("Viscera Seer");
            d.addKeyCard("Kitchen Finks");
            d.addKeyCard("Melira, Sylvok Outcast");
            d.setAiHints("Combo$Melira, Sylvok Outcast;Kitchen Finks;Viscera Seer | Route$reach");
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        return game;
    }

    private static CardCollection named(Player p, ZoneType z, String... names) {
        CardCollection out = new CardCollection();
        for (String n : names) {
            for (Card c : p.getCardsIn(z)) {
                if (c.getName().equals(n) && !out.contains(c)) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }

    private String discarded(String key) {
        Game game = setUp(false);
        Player ai = game.getPlayers().get(1);
        if (key != null) {
            ai.getRegisteredPlayer().getDeck().addKeyCard(key);
        }
        addCardToZone("Viscera Seer", ai, ZoneType.Hand);
        addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        CardCollection hand = new CardCollection(ai.getCardsIn(ZoneType.Hand));
        CardCollection d = ((PlayerControllerAi) ai.getController()).getAi().getCardsToDiscard(1, 1, hand, null);
        return d.getFirst().getName();
    }

    @Test(timeOut = 120000)
    public void testDiscardKeepsPiece() {
        String control = discarded(null);
        String other = control.equals("Viscera Seer") ? "Grizzly Bears" : "Viscera Seer";
        assertEquals(discarded(control), other, "the plan keeps " + control + ", discarded without it");
    }

    private String sacrificed(boolean plan) {
        Game game = setUp(plan);
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Viscera Seer", ai);
        addCard("Grizzly Bears", ai);
        Card edict = addCardToZone("Diabolic Edict", opponent, ZoneType.Hand);
        SpellAbility sa = edict.getFirstSpellAbility();
        sa.setActivatingPlayer(opponent);
        CardCollectionView sac = ComputerUtil.choosePermanentsToSacrifice(ai, ai.getCreaturesInPlay(), 1, sa, false, false);
        return sac.getFirst().getName();
    }

    @Test(timeOut = 120000)
    public void testEdictSparesPiece() {
        assertEquals(sacrificed(false), "Viscera Seer", "control: the weakest creature goes");
        assertEquals(sacrificed(true), "Grizzly Bears", "the plan keeps Viscera Seer");
    }

    @Test(timeOut = 120000)
    public void testCompletingPieceFirst() {
        for (boolean plan : new boolean[] {false, true}) {
            Game game = setUp(plan);
            Player ai = game.getPlayers().get(1);
            addCard("Melira, Sylvok Outcast", ai);
            addCard("Viscera Seer", ai);
            Card bears = addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
            Card finks = addCardToZone("Kitchen Finks", ai, ZoneType.Hand);
            List<SpellAbility> all = new ArrayList<>();
            for (Card c : named(ai, ZoneType.Hand, "Grizzly Bears", "Kitchen Finks")) {
                SpellAbility sa = c.getFirstSpellAbility();
                sa.setActivatingPlayer(ai);
                all.add(sa);
            }
            DeckPlanAi.prioritize(ai, all);
            if (plan) {
                assertNotNull(DeckPlanAi.completes(ai, finks), "Finks completes the combo");
                assertEquals(all.get(0).getHostCard(), finks, "the completing piece goes first");
            } else {
                assertNull(DeckPlanAi.completes(ai, finks), "no plan, no combo");
                assertEquals(all.get(0).getHostCard(), bears, "control: order unchanged");
            }
        }
    }

    @Test(timeOut = 120000)
    public void testKeyPermanentFirstAndBlocks() {
        Game game = setUp(false);
        Player ai = game.getPlayers().get(1);
        ai.getRegisteredPlayer().getDeck().addKeyCard("Elvish Archdruid");
        addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        Card druid = addCardToZone("Elvish Archdruid", ai, ZoneType.Hand);
        List<SpellAbility> all = new ArrayList<>();
        for (Card c : named(ai, ZoneType.Hand, "Grizzly Bears", "Elvish Archdruid")) {
            SpellAbility sa = c.getFirstSpellAbility();
            sa.setActivatingPlayer(ai);
            all.add(sa);
        }
        DeckPlanAi.prioritize(ai, all);
        assertEquals(all.get(0).getHostCard(), druid, "the key permanent is tried first");

        Card bears = addCard("Grizzly Bears", ai);
        Card archdruid = addCard("Elvish Archdruid", ai);
        List<Card> blockers = new ArrayList<>(List.of(archdruid, bears));
        assertEquals(DeckPlanAi.spareKeys(ai, blockers), List.of(bears), "Bears blocks instead of the key card");
        assertEquals(DeckPlanAi.spareKeys(ai, List.of(archdruid)), List.of(archdruid), "only the key card left: unchanged");
        assertEquals(DeckPlanAi.allKeys(ai, List.of(archdruid)), true, "only key cards");
        assertEquals(DeckPlanAi.allKeys(ai, blockers), false, "not only key cards");
    }
}
