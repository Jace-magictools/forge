package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

// magic-tools: rule 260 (D147 point 2, tester 08/11, a test game), two Phelia, Exuberant Shepherd held in
// hand from turn 5 to turn 8 while Control Magic held our creatures. The engine already flashes her in at the opponent's
// end step (first two tests, guards); rule 260 makes her attack to take the stolen creature back.
public class PheliaReturn260Test extends AITest {

    @Test
    public void testFlashedInAtTheOpponentsEndStep() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCard("Plains", ai);
        }
        addCard("Glory Seeker", ai);
        addCardToZone("Phelia, Exuberant Shepherd", ai, ZoneType.Hand);
        addCard("Island", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        boolean found = false;
        for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            found |= c.getName().startsWith("Phelia");
        }
        assertTrue(found, "Phelia comes in at the opponent's end step");
    }

    /** the board of a test game at the opponent's end step, turn 7: 2 life against 30, two of our creatures stolen */
    @Test
    public void testFlashedInOnTheLostBoard() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String l : new String[] {"Mountain", "Mountain", "Mountain", "Plains", "Plains", "Plains"}) {
            addCard(l, ai);
        }
        for (String c : new String[] {"Blade Historian", "Pack Leader", "Pack Leader", "Igneous Cur", "Akoum Hellhound"}) {
            addCard(c, ai);
        }
        addCardToZone("Phelia, Exuberant Shepherd", ai, ZoneType.Hand);
        addCardToZone("Phelia, Exuberant Shepherd", ai, ZoneType.Hand);
        Card watchdog = addCard("Alpine Watchdog", ai);
        Card cur = addCard("Igneous Cur", ai);
        long ts = game.getNextTimestamp();
        watchdog.addTempController(opponent, ts);
        cur.addTempController(opponent, ts);
        game.getAction().controllerChangeZoneCorrection(watchdog);
        game.getAction().controllerChangeZoneCorrection(cur);
        for (String l : new String[] {"Island", "Island", "Island", "Plains", "Plains", "Hallowed Fountain", "Azorius Guildgate", "Tranquil Cove"}) {
            addCard(l, opponent);
        }
        for (String c : new String[] {"Archon of Sun's Grace", "Invisible Stalker", "Arctic Aven"}) {
            addCard(c, opponent);
        }
        addCardToZone("Island", opponent, ZoneType.Hand);
        ai.setLife(2, null);
        opponent.setLife(30, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, opponent);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        int found = 0;
        for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            found += c.getName().startsWith("Phelia") ? 1 : 0;
        }
        assertTrue(found > 0, "Phelia comes in at the opponent's end step");
    }

    /**
     * Phelia already in play (2/2, no flying) against Grizzly Bears: she attacks and takes back the Watchdog the opponent
     * stole (it returns to its owner at the end step). Red without rule 260: a blocker can kill her, she stays home.
     */
    @Test
    public void testAttacksToTakeBackTheStolenCreature() {
        assertTrue(attackRun(true, "Phelia, Exuberant Shepherd"));
    }

    /** control: nothing of ours stolen, Phelia (2/2, no flying) does not attack into Grizzly Bears */
    @Test
    public void testStaysHomeWithNothingToTakeBack() {
        assertTrue(attackRun(false, "Phelia, Exuberant Shepherd"));
    }

    private boolean attackRun(boolean steal, String attacker) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCard("Plains", ai);
        }
        Card phelia = addCard(attacker, ai);
        phelia.setSickness(false);
        addCard("Glory Seeker", ai);
        Card watchdog = addCard("Alpine Watchdog", ai);
        if (steal) {
            watchdog.addTempController(opponent, game.getNextTimestamp());
            game.getAction().controllerChangeZoneCorrection(watchdog);
        }
        for (int i = 0; i < 4; i++) {
            addCard("Island", opponent);
        }
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        boolean back = false;
        for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            back |= c.getName().equals("Alpine Watchdog");
        }
        int attacks = phelia.getDamageHistory().getCreatureAttacksThisTurn();
        return steal ? back && attacks > 0 : attacks == 0;
    }
}
