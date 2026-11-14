package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 66 (a tester report 29/10, Avacyn - Wid turn 5): Wid played Mosswort Bridge tapped
// while a Forest made Yorvo (GGG) castable this turn
public class TapLandYorvo66Test extends AITest {

    @Test
    public void testForestForYorvo() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String l : new String[] {"Castle Garenbrig", "Forest"}) {
            Card c = addCard(l, ai);
            c.setTapped(false);
        }
        for (String c : new String[] {"Mosswort Bridge", "Forest", "Forest", "Kogla, the Titan Ape", "Yorvo, Lord of Garenbrig"}) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Yorvo, Lord of Garenbrig", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
