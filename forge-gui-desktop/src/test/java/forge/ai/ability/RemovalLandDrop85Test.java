package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.RemovalReserveAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 85 (a tester reports 29/10 15:15, 15:17, 15:57): in its upkeep, Dauthi spent Cut Down / Victim of
// Night on Tithe Drinker / Nip Gwyllion and Vampire Cast Down on Nightsky Mimic, then the land drop no longer paid for
// Dauthi Horror / Gatekeeper of Malakir: the mana of the land in hand counts for the creature that waits
public class RemovalLandDrop85Test extends AITest {

    private Game setup(String[] lands, String threat, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (String l : lands) {
            addCardToZone(l, ai, ZoneType.Battlefield);
        }
        for (int i = 0; i < 3; i++) {
            addCardToZone("Plains", opponent, ZoneType.Battlefield);
        }
        addCardToZone(threat, opponent, ZoneType.Battlefield);
        addCardToZone("Swamp", ai, ZoneType.Hand);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    private void check(String[] lands, String threat, String removal, String body) {
        for (int i = 0; i < 4; i++) {
            Game game = setup(lands, threat, removal, body);
            assertEquals(countCardsWithName(game, body, ZoneType.Battlefield), 1,
                    gameStateToString(game) + game.getGameLog().getLogEntries(null));
        }
    }

    @Test
    public void testDauthiHorrorBeforeCutDownOnTitheDrinker() {
        check(new String[] {"Swamp"}, "Tithe Drinker", "Cut Down", "Dauthi Horror");
    }

    @Test
    public void testDauthiHorrorBeforeVictimOfNightOnNipGwyllion() {
        check(new String[] {"Swamp", "Swamp"}, "Nip Gwyllion", "Victim of Night", "Dauthi Horror");
    }

    @Test
    public void testGatekeeperBeforeCastDownOnNightskyMimic() {
        check(new String[] {"Swamp", "Cavern of Souls"}, "Nightsky Mimic", "Cast Down", "Gatekeeper of Malakir");
    }

    @Test
    public void testHarmless() {
        Game game = initAndCreateGame();
        Player opponent = game.getPlayers().get(0);
        Card drinker = addCardToZone("Tithe Drinker", opponent, ZoneType.Battlefield);
        Card nip = addCardToZone("Nip Gwyllion", opponent, ZoneType.Battlefield);
        Card mimic = addCardToZone("Nightsky Mimic", opponent, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        assertTrue(RemovalReserveAi.harmless(drinker), "drinker");
        assertTrue(RemovalReserveAi.harmless(nip), "nip");
        assertTrue(RemovalReserveAi.harmless(mimic), "mimic");
    }
}
