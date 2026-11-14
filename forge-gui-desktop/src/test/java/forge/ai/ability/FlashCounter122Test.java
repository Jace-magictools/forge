package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 122, Wesley Crusher flashed in response to counter a noncreature spell
public class FlashCounter122Test extends AITest {

    private Game run(String oppSpell, int oppLands, boolean untilStackClear) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCard("Island", ai);
        }
        for (int i = 0; i < oppLands; i++) {
            addCard("Island", opponent);
        }
        addCardToZone("Wesley Crusher, Starfleet Cadet", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
        game.getAction().checkStateEffects(true);

        Card spell = addCardToZone(oppSpell, opponent, ZoneType.Hand);
        SpellAbility sa = spell.getFirstSpellAbility();
        sa.setActivatingPlayer(opponent);
        game.getStackZone().add(spell);
        game.getStack().add(sa);

        if (untilStackClear) {
            playUntilStackClear(game);
        } else {
            playUntilPhase(game, PhaseType.MAIN2);
        }
        return game;
    }

    // Divination, opponent tapped out: Wesley in response, counter mode, no cards drawn
    @Test
    public void testCountersDivination() {
        Game game = run("Divination", 0, false);
        Player opponent = game.getPlayers().get(0);
        assertEquals(countCardsWithName(game, "Wesley Crusher, Starfleet Cadet", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Divination", ZoneType.Graveyard), 1, gameStateToString(game));
        assertEquals(opponent.getCardsIn(ZoneType.Library).size(), 20, gameStateToString(game));
    }

    // Our own main phase, empty stack, opponent holding cards: Wesley waits for their turn
    @Test
    public void testHoldsOnOwnTurn() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCard("Island", ai);
        }
        addCardToZone("Wesley Crusher, Starfleet Cadet", ai, ZoneType.Hand);
        addCardToZone("Divination", opponent, ZoneType.Hand);
        addCard("Grizzly Bears", opponent);
        addCard("Runeclaw Bear", ai);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Wesley Crusher, Starfleet Cadet", ZoneType.Hand), 1, gameStateToString(game));
    }

    // A creature spell is not a target: Wesley is held (Forge cast it in response to anything)
    @Test
    public void testIgnoresCreatureSpell() {
        Game game = run("Grizzly Bears", 0, true);
        assertEquals(countCardsWithName(game, "Wesley Crusher, Starfleet Cadet", ZoneType.Hand), 1, gameStateToString(game));
    }
}
