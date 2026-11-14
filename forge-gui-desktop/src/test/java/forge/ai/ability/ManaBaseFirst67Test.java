package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 67 (a tester reports 29/10, ElfBall - Aragorn turn 4, ElfBall - Héliode turn 2):
// mana elves before Elvish Harbinger, Llanowar Elves before Birchlore Rangers
public class ManaBaseFirst67Test extends AITest {

    private Game setup(String[] lands, String[] hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        for (String l : lands) {
            Card c = addCard(l, ai);
            c.setTapped(false);
        }
        for (String c : hand) {
            addCardToZone(c, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        return game;
    }

    @Test
    public void testElvesBeforeHarbinger() {
        Game game = setup(new String[] {"Forest", "Forest", "Forest"},
                new String[] {"Elvish Harbinger", "Llanowar Elves", "Fyndhorn Elves", "Birchlore Rangers"});
        assertEquals(countCardsWithName(game, "Elvish Harbinger", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Llanowar Elves", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testLlanowarBeforeBirchlore() {
        Game game = setup(new String[] {"Forest"},
                new String[] {"Birchlore Rangers", "Llanowar Elves", "Nettle Sentinel"});
        assertEquals(countCardsWithName(game, "Llanowar Elves", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    @Test
    public void testLoneThreatStaysFirst() {
        // 3 mana, Llanowar Elves and a 3-drop: the 2 mana left after the elf would be lost
        Game game = setup(new String[] {"Forest", "Forest", "Forest"},
                new String[] {"Llanowar Elves", "Centaur Courser"});
        assertEquals(countCardsWithName(game, "Centaur Courser", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
