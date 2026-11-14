package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 221, Bleeding Edge stayed in hand with 9 free lands (Zombie - Dino, t13-21)
public class CurseRider221Test extends AITest {

    private static boolean has(Player p, ZoneType zone, String name) {
        return p.getCardsIn(zone).anyMatch(c -> c.getName().equals(name));
    }

    private Player[] play(String oppCreature) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        for (int i = 0; i < 3; i++) {
            addCardToZone("Swamp", ai, ZoneType.Battlefield);
        }
        addCardToZone("Bleeding Edge", ai, ZoneType.Hand);
        if (oppCreature != null) {
            addCardToZone(oppCreature, opp, ZoneType.Battlefield).setSickness(false);
        }
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return new Player[] {ai, opp};
    }

    private static boolean army(Player p) {
        return p.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getType().hasCreatureType("Army"));
    }

    @Test
    public void testNothingToKillStillAmasses() {
        Player[] p = play("Serra Angel");
        assertTrue(has(p[0], ZoneType.Graveyard, "Bleeding Edge") && army(p[0]),
                "amass alone is worth it " + p[0].getGame().getGameLog().getLogEntries(null));
        assertTrue(has(p[1], ZoneType.Battlefield, "Serra Angel"), "the angel survives");
    }

    @Test
    public void testNoCreatureAtAll() {
        Player[] p = play(null);
        assertTrue(has(p[0], ZoneType.Graveyard, "Bleeding Edge") && army(p[0]), "an empty board is no reason to wait");
    }

    @Test
    public void testKillStillPreferred() {
        Player[] p = play("Grizzly Bears");
        assertTrue(has(p[1], ZoneType.Graveyard, "Grizzly Bears"), "the curse still takes what it kills");
    }
}
