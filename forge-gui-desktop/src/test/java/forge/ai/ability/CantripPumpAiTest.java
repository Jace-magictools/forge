package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CantripPumpAiTest extends AITest {

    // magic-tools rule 56: a pump that draws a card is cast for the card when its mana would go unused
    // (Poudlar, v8 tournament: Shadow Rift still in hand in 78 % of its losses).
    private Game cantrip(String spell, String land, String ourCreature, String theirCreature, boolean ourTurn) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");

        addCard(land, ai);
        addCardToZone(spell, ai, ZoneType.Hand);
        if (ourCreature != null) {
            Card c = addCard(ourCreature, ai);
            c.setSickness(false);
        }
        if (theirCreature != null) {
            addCard(theirCreature, opponent);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);

        game.getPhaseHandler().devModeSet(ourTurn ? PhaseType.MAIN2 : PhaseType.END_OF_TURN, ourTurn ? ai : opponent, 5);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        return game;
    }

    private void assertCast(Game game, String spell) {
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals(spell)),
                spell + " kept in hand\n" + gameStateToString(game));
        assertEquals(ai.getCardsIn(ZoneType.Library).size(), 19, "the card was not drawn");
    }

    @Test
    public void testShadowRiftOnOurCreatureAtOpponentEndStep() {
        assertCast(cantrip("Shadow Rift", "Island", "Kiln Fiend", "Grizzly Bears", false), "Shadow Rift");
    }

    @Test
    public void testShadowRiftWithoutCreatureOfOurs() {
        assertCast(cantrip("Shadow Rift", "Island", null, "Grizzly Bears", false), "Shadow Rift");
    }

    @Test
    public void testImpoliteEntranceInMain2() {
        assertCast(cantrip("Impolite Entrance", "Mountain", "Kiln Fiend", null, true), "Impolite Entrance");
    }

    // a sorcery in our main 2 never targets an opponent's creature (haste and trample would still matter)
    @Test
    public void testImpoliteEntranceNotOnOpponentCreature() {
        Game game = cantrip("Impolite Entrance", "Mountain", null, "Grizzly Bears", true);
        Player ai = game.getPlayers().get(1);
        assertTrue(ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.getName().equals("Impolite Entrance")),
                "Impolite Entrance cast on an opponent's creature\n" + gameStateToString(game));
    }
}
