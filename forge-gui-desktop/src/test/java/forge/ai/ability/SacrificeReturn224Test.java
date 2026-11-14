package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 224, Victimize stayed in hand for 15 end steps with a creature in play and 3 to 5 in the graveyard (nazgul).
public class SacrificeReturn224Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Player play(String[] inPlay, String[] graveyard) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        for (int i = 0; i < 3; i++) {
            addCardToZone("Swamp", ai, ZoneType.Battlefield);
        }
        for (String c : inPlay) {
            addCardToZone(c, ai, ZoneType.Battlefield).setSickness(false);
        }
        for (String c : graveyard) {
            addCardToZone(c, ai, ZoneType.Graveyard);
        }
        addCardToZone("Victimize", ai, ZoneType.Hand);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return ai;
    }

    @Test
    public void testOneForTwo() {
        Player ai = play(new String[] {"Gray Ogre"}, new String[] {"Serra Angel", "Shivan Dragon"});
        assertTrue(has(ai, ZoneType.Graveyard, "Gray Ogre"), "the ogre goes " + ai.getCardsIn(ZoneType.Graveyard));
        assertTrue(has(ai, ZoneType.Battlefield, "Serra Angel"), "the angel comes back " + ai.getCardsIn(ZoneType.Battlefield));
        assertTrue(has(ai, ZoneType.Battlefield, "Shivan Dragon"), "the dragon comes back " + ai.getCardsIn(ZoneType.Battlefield));
    }

    @Test
    public void testOneTargetIsNotEnough() {
        Player ai = play(new String[] {"Gray Ogre"}, new String[] {"Serra Angel"});
        assertTrue(has(ai, ZoneType.Hand, "Victimize"), "two targets are required");
        assertTrue(has(ai, ZoneType.Battlefield, "Gray Ogre"), "the ogre stays");
    }

    @Test
    public void testNothingToSacrifice() {
        Player ai = play(new String[] {}, new String[] {"Serra Angel", "Shivan Dragon"});
        assertTrue(has(ai, ZoneType.Hand, "Victimize"), "no creature, no return");
    }
}
