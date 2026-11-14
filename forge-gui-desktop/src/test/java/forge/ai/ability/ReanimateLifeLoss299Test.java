package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 299, Profanation at 9 life reanimated Vilis, Broker of Blood (8) with Reanimate, went to 1 and
// died to Goblin Bombardment.
public class ReanimateLifeLoss299Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Player play(int life, String... graveyard) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        ai.setLife(life, null);
        addCardToZone("Swamp", ai, ZoneType.Battlefield);
        for (String c : graveyard) {
            addCardToZone(c, ai, ZoneType.Graveyard);
        }
        addCardToZone("Reanimate", ai, ZoneType.Hand);
        addCardToZone("Goblin Bombardment", opp, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", opp, ZoneType.Battlefield);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return ai;
    }

    @Test
    public void testDeadlyLossKept() {
        Player ai = play(9, "Griselbrand");
        assertTrue(has(ai, ZoneType.Hand, "Reanimate"), "Reanimate is kept, life " + ai.getLife());
        assertTrue(has(ai, ZoneType.Graveyard, "Griselbrand"), "Griselbrand stays " + ai.getCardsIn(ZoneType.Battlefield));
    }

    @Test
    public void testCheaperTargetChosen() {
        Player ai = play(9, "Griselbrand", "Gray Ogre");
        assertTrue(has(ai, ZoneType.Battlefield, "Gray Ogre"), "the ogre comes back " + ai.getCardsIn(ZoneType.Battlefield));
        assertFalse(has(ai, ZoneType.Battlefield, "Griselbrand"), "Griselbrand is not chosen");
    }

    @Test
    public void testHighLifeStillReturns() {
        Player ai = play(20, "Griselbrand");
        assertTrue(has(ai, ZoneType.Battlefield, "Griselbrand"), "Griselbrand comes back " + ai.getCardsIn(ZoneType.Battlefield));
    }
}
