package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertTrue;

/**
 * Rule 60: three more harmless ETB shapes (v9 tournament, blunder run: Guile, Sonic Soldier / Immard never cast
 * in 42 Patrilock games, Éowyn, Fearless Knight kept 170 times, both as BadEtbEffects): a charge counter put on or
 * removed from itself, a modal effect we choose with one harmless mode, a mass effect on our own creatures only.
 */
public class HarmlessEtbAi60Test extends AITest {

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

    // "whenever it enters or attacks, put a charge counter on him or remove one; when you remove one, choose one:
    // 4 damage to any target, or lifelink and indestructible"
    @Test
    public void testGuileSonicSoldierCast() {
        assertTrue(castInMain2("Guile, Sonic Soldier", "Island", "Mountain", "Plains", "Plains"),
                "Guile, Sonic Soldier kept in hand");
    }

    // "exile target creature an opponent controls with greater power; legendary creatures you control gain
    // protection" (Grizzly Bears has less power: no target)
    @Test
    public void testEowynFearlessKnightCast() {
        assertTrue(castInMain2("Éowyn, Fearless Knight", "Mountain", "Mountain", "Plains", "Plains"),
                "Éowyn, Fearless Knight kept in hand");
    }
}
