package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

// magic-tools: rule 187, "target creature you control fights ..." scripted as a Pump without AILogic
// (Wayta never activated in 4 Commander games out of 4; Prey Upon, Pounce, Titanic Brawl same script)
public class ImplicitFight187Test extends AITest {

    private static boolean inGraveyard(Player p, String name) {
        return p.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals(name));
    }

    private Game play(String aiCreature, String handSpell, String oppCreature, String land, int lands) {
        Game game = initAndCreateGame();
        Player opp = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < lands; i++) {
            addCard(land, ai);
        }
        addCard(aiCreature, ai);
        if (handSpell != null) {
            addCardToZone(handSpell, ai, ZoneType.Hand);
        }
        addCard(oppCreature, opp);
        addCard("Mountain", opp);
        fillLibrary(opp, 20);
        fillLibrary(ai, 20);
        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);
        return game;
    }

    @Test
    public void testPreyUponKills() {
        Game game = play("Hill Giant", "Prey Upon", "Grizzly Bears", "Forest", 1);
        assertTrue(inGraveyard(game.getPlayers().get(0), "Grizzly Bears"), "bears should die");
        assertTrue(game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Hill Giant")));
    }

    @Test
    public void testPreyUponKeptOnBadFight() {
        Game game = play("Grizzly Bears", "Prey Upon", "Hill Giant", "Forest", 1);
        assertFalse(inGraveyard(game.getPlayers().get(1), "Prey Upon"), "a losing fight stays in hand");
    }

    @Test
    public void testActivatedFightWayta() {
        // Wayta 1/5 fights a 1/1: kills it and survives
        Game game = play("Wayta, Trainer Prodigy", null, "Elite Vanguard", "Forest", 3);
        assertFalse(game.getPlayers().get(0).getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals("Elite Vanguard") && c.getDamage() == 0),
                "Wayta should fight");
    }
}
