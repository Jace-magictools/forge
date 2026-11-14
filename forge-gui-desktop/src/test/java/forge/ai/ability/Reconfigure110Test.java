package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

// magic-tools: rule 110 (a tester report 30/10 12:04, Kavaron - Profanation): Rabbit Battery is reconfigured onto the
// creature cast this turn (haste, +1/+1) so that it attacks, instead of staying a lone 1/1
public class Reconfigure110Test extends AITest {

    private Card onField(Game game, String name) {
        for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void testBatteryOnSickCreature() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Great Furnace", ai);
        addCard("Rabbit Battery", ai);
        for (String c : new String[] {"Kavaron Harrier", "Rust Harvester", "Prized Statue", "Pyrite Spellbomb", "Ichor Wellspring"}) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        Card battery = onField(game, "Rabbit Battery");
        assertEquals(battery.getAttachedTo(), onField(game, "Kavaron Harrier"), gameStateToString(game));
        assertEquals(opponent.getLife(), 17, gameStateToString(game));
    }

    @Test
    public void testNoHasteIntoABlocker() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        Card battery = addCard("Rabbit Battery", ai);
        Card harrier = addCard("Kavaron Harrier", ai);
        harrier.setSickness(true);
        addCard("Hill Giant", opponent);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        for (forge.game.spellability.SpellAbility sa : battery.getSpellAbilities()) {
            assertFalse(forge.ai.HasteAttachAi.wantsHasteNow(ai, sa));
        }
    }
}
