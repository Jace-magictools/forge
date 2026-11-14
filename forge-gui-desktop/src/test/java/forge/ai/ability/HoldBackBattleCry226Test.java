package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 226, relevé « mana perdu » v259 (Gobelin - merfolk, tours 9 à 13) : Signal Pest (0/1, cri de guerre)
// gardé en main par la règle 93 avec trois Montagnes dégagées et trois attaquants ; il vaut +3 par attaque, pas un corps de réserve
public class HoldBackBattleCry226Test extends AITest {

    private Game setup(final String inPlay, final String inHand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCard("Mountain", ai);
        }
        addCard("Goblin Piker", ai);
        addCard("Goblin Piker", ai);
        addCard(inPlay, ai);
        addCardToZone(inHand, ai, ZoneType.Hand);
        for (int i = 0; i < 4; i++) {
            addCard("Plains", opponent);
        }
        addCardToZone("Wrath of God", opponent, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testBattleCryComesDown() {
        Game game = setup("Goblin Piker", "Signal Pest");
        assertEquals(countCardsWithName(game, "Signal Pest", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testBattleCryInPlayMakesTheDeckWide() {
        Game game = setup("Signal Pest", "Goblin Piker");
        assertEquals(countCardsWithName(game, "Goblin Piker", ZoneType.Battlefield), 3, gameStateToString(game));
    }

    @Test
    public void testPlainBodyStillHeld() {
        Game game = setup("Goblin Piker", "Goblin Piker");
        assertEquals(countCardsWithName(game, "Goblin Piker", ZoneType.Hand), 1, gameStateToString(game));
    }
}
