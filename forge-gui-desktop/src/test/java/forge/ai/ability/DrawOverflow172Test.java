package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 172, relevé « mana perdu » v210 (Dragoonz - Vampire t4) : avec deux Swamps en jeu et quatre en
// main, Vampire n'a jamais lancé Sign in Blood (main à 6 + 2 = 8 > 7, DrawAi refuse « pour ne pas défausser »), alors
// que la défausse de fin de tour n'aurait coûté qu'un terrain en trop.
// Contre-épreuve : la main pleine de sorts (aucun terrain en trop), Sign in Blood reste en main.
public class DrawOverflow172Test extends AITest {

    private Game game(final String filler) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        addCard("Swamp", ai);
        addCard("Swamp", ai);
        addCardToZone("Sign in Blood", ai, ZoneType.Hand);
        for (int i = 0; i < 4; i++) {
            addCardToZone(filler, ai, ZoneType.Hand);
        }
        addCardToZone("Doom Blade", ai, ZoneType.Hand);
        addCardToZone("Hill Giant", ai, ZoneType.Hand);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        ai.addLandPlayedThisTurn();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    @Test(timeOut = 120000)
    public void testDrawsWithSpareLands() {
        Game game = game("Swamp");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Sign in Blood", ZoneType.Graveyard), 1, gameStateToString(game));
    }

    @Test(timeOut = 120000)
    public void testKeepsItWithAHandOfSpells() {
        Game game = game("Gray Ogre");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Sign in Blood", ZoneType.Hand), 1, gameStateToString(game));
    }
}
