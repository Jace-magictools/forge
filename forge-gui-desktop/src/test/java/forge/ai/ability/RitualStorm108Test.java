package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: Gobelin - Patrilock (forge-v129, turn 17): three Mountains, Pyretic Ritual, Empty the Warrens and
// Mass Hysteria in hand; the ritual's mana went to Mass Hysteria and three mana were lost instead of four goblins
// (rule 108; the third test: a Storm spell no longer waits for a spell of the hand nobody casts)
public class RitualStorm108Test extends AITest {

    private Game setup(boolean withHysteria) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 3; i++) {
            addCardToZone("Mountain", ai, ZoneType.Battlefield);
        }
        addCardToZone("Pyretic Ritual", ai, ZoneType.Hand);
        addCardToZone("Empty the Warrens", ai, ZoneType.Hand);
        if (withHysteria) {
            addCardToZone("Mass Hysteria", ai, ZoneType.Hand);
        }
        addCardToZone("Plains", opponent, ZoneType.Battlefield);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test
    public void testRitualIntoWarrens() {
        Game game = setup(false);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Goblin Token", ZoneType.Battlefield), 4, gameStateToString(game));
    }

    @Test
    public void testRitualIntoWarrensBeforeHysteria() {
        Game game = setup(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Goblin Token", ZoneType.Battlefield), 4, gameStateToString(game));
    }

    @Test
    public void testWarrensNotKeptBehindAnUncastableSpell() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        Player opponent = game.getPlayers().get(0);
        for (int i = 0; i < 4; i++) {
            addCardToZone("Mountain", ai, ZoneType.Battlefield);
        }
        addCardToZone("Empty the Warrens", ai, ZoneType.Hand);
        addCardToZone("Inferno Titan", ai, ZoneType.Hand);
        addCardToZone("Plains", opponent, ZoneType.Battlefield);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Goblin Token", ZoneType.Battlefield), 2, gameStateToString(game));
    }
}
