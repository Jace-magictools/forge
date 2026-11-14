package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

/**
 * Rule 49: an ETB that only helps us (a pump of itself without Defined, our own tokens, +1/+1 counters on itself,
 * amass, a reflexive trigger or an opponent's choice between such effects) or that may only target what an
 * opponent controls doesn't keep the permanent in hand (r12 blunder run: 43 BadEtbEffects in 160 games).
 */
public class HarmlessEtbAi49Test extends AITest {

    private boolean castInMain2(String name, String... lands) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String l : lands) {
            addCard(l, ai);
        }
        addCardToZone(name, ai, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals(name));
    }

    private static final String[] LANDS = {"Mountain", "Mountain", "Mountain", "Plains", "Plains", "Swamp", "Forest"};

    // "it gets +2/+2 until end of turn" (Pump without Defined)
    @Test
    public void testViashinoFirstbladeCast() {
        assertTrue(castInMain2("Viashino Firstblade", LANDS), "Viashino Firstblade kept in hand");
    }

    // "create a number of 1/1 Gnome tokens equal to its power"
    @Test
    public void testThreefoldThunderhulkCast() {
        assertTrue(castInMain2("Threefold Thunderhulk", LANDS), "Threefold Thunderhulk kept in hand");
    }

    // "target opponent may have you create two Lander tokens; if they don't, two +1/+1 counters on it"
    @Test
    public void testTerrapactIntimidatorCast() {
        assertTrue(castInMain2("Terrapact Intimidator", LANDS), "Terrapact Intimidator kept in hand");
    }

    // "amass Orcs 2; when you do, gain control of target nonlegendary creature an opponent controls"
    @Test
    public void testGrishnakhCast() {
        assertTrue(castInMain2("Grishnákh, Brash Instigator", LANDS), "Grishnákh kept in hand");
    }

    // rule 50: "destroy up to one target nonbasic land; its controller may search for a basic land"
    @Test
    public void testWhiteOrchidPhantomCast() {
        assertTrue(castInMain2("White Orchid Phantom", LANDS), "White Orchid Phantom kept in hand");
    }

    // "exile target artifact or creature an opponent controls with mana value 3 or less"
    @Test
    public void testAnnexSentryCast() {
        assertTrue(castInMain2("Annex Sentry", LANDS), "Annex Sentry kept in hand");
    }

    // rule 51: "target opponent reveals ... you choose one of those cards; that player discards it"
    @Test
    public void testAcquisitionsExpertCast() {
        assertTrue(castInMain2("Acquisitions Expert", LANDS), "Acquisitions Expert kept in hand");
    }

    // rule 51: "put a +1/+1 counter on target creature you control"
    @Test
    public void testMerfolkSkydiverCast() {
        assertTrue(castInMain2("Merfolk Skydiver", "Island", "Island", "Forest", "Forest"), "Merfolk Skydiver kept in hand");
    }
}
