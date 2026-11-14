package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 223, one blue land + a filter land did not pay {U}{U}: Narset stayed in hand (Patrilock - Kavaron, t8)
public class DoubleSymbolFilter223Test extends AITest {

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
    public void testFilterGivesTheSecondSymbol() {
        Player ai = play("Narset, Parter of Veils", "Island", "Plains", "Plains", "Guildmages' Forum");
        assertTrue(has(ai, ZoneType.Battlefield, "Narset, Parter of Veils"), "U from the Island, U from the Forum " + ai.getGame().getGameLog().getLogEntries(null));
    }

    @Test
    public void testFilterNotPaidWithTheOnlySourceOfTheColor() {
        Player ai = play("Narset, Parter of Veils", "Plains", "Island", "Plains", "Guildmages' Forum");
        assertTrue(has(ai, ZoneType.Battlefield, "Narset, Parter of Veils"), "the Forum's {1} comes from a Plains " + ai.getGame().getGameLog().getLogEntries(null));
    }

    @Test
    public void testNotEnoughManaKeepsTheFilterUntapped() {
        Player ai = play("Narset, Parter of Veils", "Island", "Plains", "Guildmages' Forum");
        assertTrue(has(ai, ZoneType.Hand, "Narset, Parter of Veils"), "three lands make two mana through the filter");
        assertFalse(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.isTapped()), "nothing tapped for nothing");
    }

    @Test
    public void testThirdSymbolStaysOutOfReach() {
        Player ai = play("Goblin Chainwhirler", "Mountain", "Plains", "Plains", "Guildmages' Forum");
        assertTrue(has(ai, ZoneType.Hand, "Goblin Chainwhirler"), "{R}{R}{R}: one filter gives one R only");
        assertFalse(ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.isTapped()), "nothing tapped for nothing");
    }
}
