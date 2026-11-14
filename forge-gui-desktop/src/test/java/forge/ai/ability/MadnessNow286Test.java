package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 286 (11/11, Schizoph, under-rated by Forge): a permanent cast by an effect (madness) is now or never,
// the AI declined a free Blazing Rootwalla discarded to Faithless Looting in main 1 ("wait for main 2")
public class MadnessNow286Test extends AITest {

    private Game setup(boolean pressure) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        addCard("Mountain", ai);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCardToZone("Faithless Looting", ai, ZoneType.Hand);
        addCardToZone("Blazing Rootwalla", ai, ZoneType.Hand);
        addCardToZone("Bone Shards", ai, ZoneType.Hand);
        addCardToZone("Violent Eruption", ai, ZoneType.Hand);
        if (pressure) {
            ai.setLife(6, null);
            for (String c : new String[] {"Savage Knuckleblade", "Lotus Cobra", "Grizzly Bears"}) {
                Card k = addCard(c, opponent);
                k.setSickness(false);
            }
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    private void check(boolean pressure) {
        Game game = setup(pressure);
        playUntilPhase(game, PhaseType.COMBAT_DECLARE_ATTACKERS);
        Player ai = game.getPlayers().get(1);
        int inExile = countCardsWithName(game, "Blazing Rootwalla", ZoneType.Exile);
        int inGrave = countCardsWithName(game, "Blazing Rootwalla", ZoneType.Graveyard);
        int onField = countCardsWithName(game, "Blazing Rootwalla", ZoneType.Battlefield);
        int inHand = countCardsWithName(game, "Blazing Rootwalla", ZoneType.Hand);
        assertEquals(onField, 1, "Rootwalla not cast " + inExile + "/" + inGrave + "/" + inHand + "\nLOG286 " + log(game));
        // discarded (graveyard) but not cast = the fault
        assertEquals(inGrave, 0, "Rootwalla discarded without being cast " + inExile + "/" + onField + "/" + inHand + "\n" + gameStateToString(game) + "\nLOG286 " + log(game));
    }

    private static String log(Game game) {
        StringBuilder sb = new StringBuilder();
        java.util.List<forge.game.GameLogEntry> l = new java.util.ArrayList<>(game.getGameLog().getAllEntries());
        java.util.Collections.reverse(l);
        for (forge.game.GameLogEntry e : l) {
            sb.append(e.message()).append(" | ");
        }
        return sb.toString();
    }

    @Test
    public void testFreeMadnessCast() {
        check(false);
    }
}
