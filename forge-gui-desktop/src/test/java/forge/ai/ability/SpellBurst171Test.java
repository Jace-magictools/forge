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

// magic-tools: rule 171, relevé « mana perdu » v209 (Eldratron - Avacyn) : Spell Burst / Spell Blast (« counter target
// spell with mana value X ») se ciblaient avec X = 0 et ne contraient jamais rien
public class SpellBurst171Test extends AITest {

    private Game run(final int islands, final String oppSpell) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < islands; i++) {
            addCard("Island", ai);
        }
        addCardToZone("Spell Burst", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent);
        game.getAction().checkStateEffects(true);

        Card spell = addCardToZone(oppSpell, opponent, ZoneType.Hand);
        SpellAbility sa = spell.getFirstSpellAbility();
        sa.setActivatingPlayer(opponent);
        game.getStackZone().add(spell);
        game.getStack().add(sa);
        playUntilStackClear(game);
        return game;
    }

    // Gray Ogre (3) with four Islands: X = 3, countered
    @Test
    public void testCountersWithXEqualToManaValue() {
        Game game = run(4, "Gray Ogre");
        assertEquals(countCardsWithName(game, "Gray Ogre", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    // Three Islands cannot pay X = 3 plus U: nothing happens, Spell Burst stays in hand
    @Test
    public void testKeepsItWhenXUnpayable() {
        Game game = run(3, "Gray Ogre");
        assertEquals(countCardsWithName(game, "Spell Burst", ZoneType.Hand), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Gray Ogre", ZoneType.Battlefield), 1, gameStateToString(game));
    }
}
