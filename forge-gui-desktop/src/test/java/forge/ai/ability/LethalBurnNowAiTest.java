package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

public class LethalBurnNowAiTest extends AITest {

    // magic-tools rule 54: a burn spell in hand that kills the opponent must be cast at his face.
    // In the v7 tournament Magma Jet (Scry subability) and Arc Trail (two targets) stayed in hand
    // for several turns against an opponent at 2 life while the AI cast other spells.
    private void lethalBurn(String burn, int life, String other) {
        lethalBurn(burn, life, other, true);
    }

    private void lethalBurn(String burn, int life, String other, boolean ourTurn) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");

        addCards("Mountain", other != null && other.startsWith("Chandra") ? 4 : burn.equals("Magic Missile") ? 3 : 2, ai);
        addCardToZone(burn, ai, ZoneType.Hand);
        if (other != null) {
            addCardToZone(other, ai, ZoneType.Hand);
        }
        addCard("Wall of Omens", opponent);
        opponent.setLife(life, null);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(ourTurn ? PhaseType.MAIN1 : PhaseType.MAIN2, ourTurn ? ai : opponent, 11);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);

        assertTrue(opponent.getLife() <= 0 || game.isGameOver(),
                burn + " kept in hand against an opponent at " + life + "\n" + gameStateToString(game));
    }

    @Test
    public void testMagmaJetLethal() {
        lethalBurn("Magma Jet", 2, null);
    }

    @Test
    public void testArcTrailLethal() {
        lethalBurn("Arc Trail", 2, null);
    }

    @Test
    public void testArcTrailLethalWithCreatureInHand() {
        lethalBurn("Arc Trail", 2, "Grizzly Bears");
    }

    // Red Star: a planeswalker in hand took the mana first
    @Test
    public void testMagmaJetBeforePlaneswalker() {
        lethalBurn("Magma Jet", 2, "Chandra, Torch of Defiance");
    }

    @Test
    public void testLightningBoltLethal() {
        lethalBurn("Lightning Bolt", 3, null);
    }

    @Test
    public void testMagmaJetLethalOnOpponentTurn() {
        lethalBurn("Magma Jet", 2, null, false);
    }

    // rule 55: Maglubiet kept Magic Missile two turns against an opponent at 1 (v8 tournament)
    @Test
    public void testMagicMissileLethal() {
        lethalBurn("Magic Missile", 1, null);
    }

    @Test
    public void testMagicMissileLethalWithCreatureInHand() {
        lethalBurn("Magic Missile", 3, "Grizzly Bears");
    }

    @Test
    public void testLightningBoltLethalOnOpponentTurn() {
        lethalBurn("Lightning Bolt", 3, null, false);
    }
}
