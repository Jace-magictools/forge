package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 225, Necromancy on Akroma, Angel of Wrath (protection from black) fell off at once and the angel was sacrificed (Profanation).
public class ReanimateProtected225Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Player play(String... graveyard) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        for (int i = 0; i < 3; i++) {
            addCardToZone("Swamp", ai, ZoneType.Battlefield);
        }
        for (String c : graveyard) {
            addCardToZone(c, ai, ZoneType.Graveyard);
        }
        addCardToZone("Necromancy", ai, ZoneType.Hand);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return ai;
    }

    @Test
    public void testProtectedAloneStaysInGraveyard() {
        Player ai = play("Akroma, Angel of Wrath");
        assertTrue(has(ai, ZoneType.Hand, "Necromancy"), "Necromancy is kept " + ai.getCardsIn(ZoneType.Graveyard));
        assertTrue(has(ai, ZoneType.Graveyard, "Akroma, Angel of Wrath"), "Akroma stays " + ai.getCardsIn(ZoneType.Battlefield));
    }

    @Test
    public void testOtherCreatureChosen() {
        Player ai = play("Akroma, Angel of Wrath", "Gray Ogre");
        assertTrue(has(ai, ZoneType.Battlefield, "Gray Ogre"), "the ogre comes back " + ai.getCardsIn(ZoneType.Battlefield));
        assertTrue(has(ai, ZoneType.Battlefield, "Necromancy"), "Necromancy stays on it " + ai.getCardsIn(ZoneType.Graveyard));
        assertFalse(has(ai, ZoneType.Battlefield, "Akroma, Angel of Wrath"), "Akroma is not chosen");
    }

    @Test
    public void testUnprotectedStillReturns() {
        Player ai = play("Serra Angel");
        assertTrue(has(ai, ZoneType.Battlefield, "Serra Angel"), "the angel comes back " + ai.getCardsIn(ZoneType.Battlefield));
        assertTrue(has(ai, ZoneType.Battlefield, "Necromancy"), "Necromancy stays on it " + ai.getCardsIn(ZoneType.Graveyard));
    }
}
