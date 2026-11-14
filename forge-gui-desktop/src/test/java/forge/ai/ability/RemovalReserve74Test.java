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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 74 (a tester reports 29/10 12:49 and 12:57): Vampire cast Cast Down on Trinket Mage in its upkeep
// instead of Cordial Vampire; Avacyn cast Oblivion Ring on Nightsky Mimic instead of Righteous Valkyrie
public class RemovalReserve74Test extends AITest {

    private Game setup(PhaseType start, String land, int lands, String threat, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < lands; i++) {
            addCardToZone(land, ai, ZoneType.Battlefield);
        }
        for (int i = 0; i < 3; i++) {
            addCardToZone("Island", opponent, ZoneType.Battlefield);
        }
        addCardToZone(threat, opponent, ZoneType.Battlefield);
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(start, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testCordialVampireBeforeCastDownOnTrinketMage() {
        for (int i = 0; i < 4; i++) {
            Game game = setup(PhaseType.UPKEEP, "Swamp", 3, "Trinket Mage", "Cast Down", "Cordial Vampire");
            assertEquals(countCardsWithName(game, "Trinket Mage", ZoneType.Battlefield), 1,
                    gameStateToString(game) + game.getGameLog().getLogEntries(null));
            assertEquals(countCardsWithName(game, "Cordial Vampire", ZoneType.Battlefield), 1, gameStateToString(game));
        }
    }

    @Test
    public void testValkyrieBeforeOblivionRingOnNightskyMimic() {
        for (int i = 0; i < 4; i++) {
            Game game = setup(PhaseType.MAIN1, "Plains", 4, "Nightsky Mimic", "Oblivion Ring", "Righteous Valkyrie");
            assertEquals(countCardsWithName(game, "Nightsky Mimic", ZoneType.Battlefield), 1,
                    gameStateToString(game) + game.getGameLog().getLogEntries(null));
            assertEquals(countCardsWithName(game, "Righteous Valkyrie", ZoneType.Battlefield), 1, gameStateToString(game));
        }
    }

    @Test
    public void testRealThreatsKeepTheirRemoval() {
        Game game = initAndCreateGame();
        Player opponent = game.getPlayers().get(0);
        Card mage = addCardToZone("Trinket Mage", opponent, ZoneType.Battlefield);
        Card seer = addCardToZone("Viscera Seer", opponent, ZoneType.Battlefield);
        Card elf = addCardToZone("Llanowar Elves", opponent, ZoneType.Battlefield);
        Card angel = addCardToZone("Serra Angel", opponent, ZoneType.Battlefield);
        Card lord = addCardToZone("Lord of Atlantis", opponent, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        assertTrue(RemovalReserveAi.harmless(mage));
        assertFalse(RemovalReserveAi.harmless(seer));
        assertFalse(RemovalReserveAi.harmless(elf));
        assertFalse(RemovalReserveAi.harmless(angel));
        assertFalse(RemovalReserveAi.harmless(lord));
    }
}
