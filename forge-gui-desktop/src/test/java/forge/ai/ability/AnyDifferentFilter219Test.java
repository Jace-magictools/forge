package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 219, Interplanar Beacon was the only blue source and Teferi stayed in hand (Dovin - Penitence, t13)
public class AnyDifferentFilter219Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Player play(String spell, String... lands) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        for (String land : lands) {
            addCardToZone(land, ai, ZoneType.Battlefield);
        }
        addCardToZone(spell, ai, ZoneType.Hand);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return ai;
    }

    @Test
    public void testBeaconGivesTheMissingColorOfAPlaneswalker() {
        Player ai = play("Teferi, Time Raveler", "Plains", "Swamp", "Interplanar Beacon");
        assertTrue(has(ai, ZoneType.Battlefield, "Teferi, Time Raveler"), "{1}: W and U from the Beacon, Plains for the rest " + ai.getGame().getGameLog().getLogEntries(null));
    }

    @Test
    public void testBeaconWithASingleColoredSymbol() {
        Player ai = play("Kasmina, Enigmatic Mentor", "Plains", "Plains", "Plains", "Interplanar Beacon");
        assertTrue(has(ai, ZoneType.Battlefield, "Kasmina, Enigmatic Mentor"), "one U, the second color pays the generic " + ai.getGame().getGameLog().getLogEntries(null));
    }

    @Test
    public void testBeaconDoesNotTapForACreature() {
        Player ai = play("Reflector Mage", "Plains", "Swamp", "Interplanar Beacon");
        assertTrue(has(ai, ZoneType.Hand, "Reflector Mage"), "mana for planeswalker spells only");
        assertTrue(ai.getManaPool().totalMana() == 0 && !ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.isTapped()), "nothing tapped for nothing");
    }
}
