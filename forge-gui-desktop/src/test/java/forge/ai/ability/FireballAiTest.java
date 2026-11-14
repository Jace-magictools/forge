package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: Fireball never cast by Elemental against Zombie (zomb11, 20 refusals in 3 games)
public class FireballAiTest extends AITest {

    @Test
    public void testFireballKillsCreatureLateGame() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        addCards("Mountain", 5, ai);
        addCardToZone("Fireball", ai, ZoneType.Hand);
        addCard("Hill Giant", opponent);
        addCards("Swamp", 5, opponent);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 21);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Fireball", ZoneType.Hand), 0,
                "Fireball kept with 5 Mountains against a Hill Giant on turn 21\n" + gameStateToString(game));
    }
}
