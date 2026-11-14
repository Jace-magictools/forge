package forge.ai.ability;

import java.util.List;
import org.testng.annotations.Test;
import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;

// magic-tools: rule 80 (a tester report 29/10 12:24, ElfBall): with an empty hand and three lands, Elvish Harbinger
// went down the "AI needs a land" path although only elves could be fetched, and took a random elf (Arbor Elf,
// Elvish Mystic...) instead of a draw engine (Beast Whisperer for tester; the stock evaluation takes Gilt-Leaf Archdruid, which
// also draws: most mana elves are Druids)
public class TopTutor80Test extends AITest {
    @Test
    public void testHarbingerFetchesTheBestElf() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opp = game.getPlayers().get(0);
        for (String n : new String[] {"Forest", "Forest", "Forest", "Llanowar Elves", "Llanowar Elves", "Fyndhorn Elves",
                "Heritage Druid", "Nettle Sentinel", "Nettle Sentinel", "Wirewood Symbiote"}) addCard(n, ai);
        Card harb = addCard("Elvish Harbinger", ai);
        for (String n : new String[] {"Vault Skirge", "Dauthi Horror", "Dauthi Horror", "Bonesplitter"}) addCard(n, opp);
        String[] lib = {"Gilt-Leaf Archdruid", "Gilt-Leaf Archdruid", "Gilt-Leaf Archdruid", "Beast Whisperer", "Beast Whisperer",
                "Beast Whisperer", "Elvish Archdruid", "Elvish Archdruid", "Elvish Harbinger", "Leaf-Crowned Visionary",
                "Quirion Ranger", "Quirion Ranger", "Birchlore Rangers", "Birchlore Rangers", "Elvish Mystic", "Elvish Mystic",
                "Arbor Elf", "Arbor Elf", "Heritage Druid", "Nettle Sentinel", "Wirewood Symbiote", "Forest", "Forest"};
        for (String n : lib) addCardToZone(n, ai, ZoneType.Library);
        ai.setLife(16, null);
        fillLibrary(opp, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        SpellAbility sa = null;
        for (Trigger t : harb.getTriggers()) sa = t.ensureAbility();
        sa.setActivatingPlayer(ai);
        for (int i = 0; i < 12; i++) {
            CardCollection elves = new CardCollection();
            for (Card c : ai.getCardsIn(ZoneType.Library)) if (c.getType().hasCreatureType("Elf")) elves.add(c);
            Card choice = ChangeZoneAi.chooseCardToHiddenOriginChangeZone(ZoneType.Library, List.of(ZoneType.Library), sa,
                    elves, ai, ai);
            // a five-plus drop that draws (Gilt-Leaf Archdruid: 16 Druids left), not a random one-drop;
            // since rule 256 (lords count what they give) Elvish Archdruid, +1/+1 and a mana to 10 elves, is fine too
            org.testng.Assert.assertTrue(choice.getCMC() >= 5 || choice.getName().equals("Elvish Archdruid"), choice.getName());
        }
    }
}

