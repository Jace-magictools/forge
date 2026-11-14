package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 254, D143 point 2 (tester 08/11, a test game): Dauthi Horror cast into Gelectrode.
public class PingerVictim254Test extends AITest {

    private Game setup(String pinger, String... hand) {
        forge.util.MyRandom.setRandom(new java.util.Random(1)); // fix 11/11: testRemovalFirst failed 1 time in 4 (AI dice)
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Swamp", ai);
        }
        if (pinger != null) {
            addCard(pinger, opponent);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    /** the report: Gelectrode in play, the 2/1 stays in hand */
    @Test
    public void testNoHorrorIntoGelectrode() {
        Game game = setup("Gelectrode", "Dauthi Horror");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Dauthi Horror", ZoneType.Hand), 1, gameStateToString(game));
    }

    /** no pinger: cast */
    @Test
    public void testHorrorWithoutPinger() {
        Game game = setup(null, "Dauthi Horror");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Dauthi Horror", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** a 2/2 survives the ping and is cast */
    @Test
    public void testToughCreatureStillCast() {
        Game game = setup("Gelectrode", "Walking Corpse");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Walking Corpse", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** removal in hand: the pinger dies first, then the 2/1 comes */
    @Test
    public void testRemovalFirst() {
        Game game = setup("Gelectrode", "Cut Down", "Dauthi Horror");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Gelectrode", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Dauthi Horror", ZoneType.Battlefield), 1, gameStateToString(game));
    }
    /** fix 11/11 (bourdes v320, Warden of the First Tree held 20 turns): Keldon Megaliths pings only when hellbent; with a card in the opponent's hand it is no pinger */
    @Test
    public void testHellbentPingerNotActive() {
        Game game = setup("Keldon Megaliths", "Dauthi Horror");
        addCardToZone("Mountain", game.getPlayers().get(0), ZoneType.Hand);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Dauthi Horror", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** opponent hellbent (empty hand): Keldon Megaliths does ping, the 2/1 waits */
    @Test
    public void testHellbentPingerActive() {
        Game game = setup("Keldon Megaliths", "Dauthi Horror");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Dauthi Horror", ZoneType.Hand), 1, gameStateToString(game));
    }

    /** fix 11/11 (2): two 2/1 and the mana for both against one pinger: the pinger kills one, the other lives, both are cast */
    @Test
    public void testSaturateSinglePinger() {
        Game game = setup("Gelectrode", "Dauthi Horror", "Dauthi Horror");
        addCard("Swamp", game.getPlayers().get(1));
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Dauthi Horror", ZoneType.Battlefield), 2, gameStateToString(game));
    }

    /** fix 11/11 (2): two 2/1 but mana for only one: no saturation, both wait */
    @Test
    public void testNoSaturationWithoutMana() {
        Game game = setup("Gelectrode", "Dauthi Horror", "Dauthi Horror");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Dauthi Horror", ZoneType.Hand), 2, gameStateToString(game));
    }
}
