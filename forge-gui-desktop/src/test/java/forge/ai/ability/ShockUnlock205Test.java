package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools, règle 205 : un symbole de couleur = une source ; la shockland qui ouvre le plus gros sort paie ses PV
public class ShockUnlock205Test extends AITest {

    private Game landGame(String[] inPlay, String... hand) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        for (String name : inPlay) {
            addCard(name, ai).setSickness(false);
        }
        for (String name : hand) {
            addCardToZone(name, ai, ZoneType.Hand);
        }
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    // la partie de tester : un seul noir, le 2e vient de la Tombe, Redcap passe avant les deux sacrificateurs
    @Test
    public void testTombePourRedcap() {
        Game game = landGame(new String[] {"Swamp", "Forest", "Llanowar Elves"},
                "Forest", "Overgrown Tomb", "Murderous Redcap", "Bloodthrone Vampire", "Altar of Dementia");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Overgrown Tomb", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Murderous Redcap", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Bloodthrone Vampire", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    // la couleur seule : que du vert en jeu, la Tombe ouvre la Seer et la Forêt rien
    @Test
    public void testTombePourLaCouleur() {
        Game game = landGame(new String[] {"Forest", "Forest"}, "Forest", "Overgrown Tomb", "Viscera Seer");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(countCardsWithName(game, "Viscera Seer", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    // la Tombe n'ouvre rien de plus que la Forêt : on garde ses points de vie
    @Test
    public void testPasDePvSansGain() {
        Game game = landGame(new String[] {"Swamp", "Forest", "Llanowar Elves"},
                "Forest", "Overgrown Tomb", "Bloodthrone Vampire", "Altar of Dementia");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertEquals(game.getPlayers().get(1).getLife(), 20, gameStateToString(game));
    }
}
