package forge.ai.ability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static org.testng.Assert.assertEquals;

// magic-tools: rule 196, a tester reports 03/11 (ElfBall, a test game, a test game): Birchlore Rangers taps
// two summoning-sick Elves for mana, the AI did not plan for it.
public class TapElvesMana196Test extends AITest {

    private Player setup(Game game) {
        Player ai = game.getPlayers().get(1);
        ((forge.ai.LobbyPlayerAi) ai.getLobbyPlayer()).setAiProfile("Default");
        return ai;
    }

    private void play(Game game, Player ai) {
        Player opponent = game.getPlayers().get(0);
        fillLibrary(ai, 20);
        fillLibrary(opponent, 20);
        addCard("Swamp", opponent);
        ai.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
    }

    /** a test game: Pendelhaven + Llanowar: Mystic + Birchlore + a third Elf, not Mystic + Heritage Druid */
    @Test
    public void testBirchloreBeforeTheLastMana() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Pendelhaven", ai);
        addCard("Llanowar Elves", ai).setSickness(false);
        addCardToZone("Elvish Mystic", ai, ZoneType.Hand);
        addCardToZone("Heritage Druid", ai, ZoneType.Hand);
        addCardToZone("Birchlore Rangers", ai, ZoneType.Hand);
        addCardToZone("Arbor Elf", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(countCardsWithName(game, "Birchlore Rangers", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(ai.getCardsIn(ZoneType.Hand).size(), 1, gameStateToString(game));
    }

    /** a test game: Forest + Fyndhorn: Nettle + Birchlore, then both tapped for Wirewood Symbiote */
    @Test
    public void testNettleBirchloreSymbiote() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Forest", ai);
        addCard("Fyndhorn Elves", ai).setSickness(false);
        addCardToZone("Birchlore Rangers", ai, ZoneType.Hand);
        addCardToZone("Nettle Sentinel", ai, ZoneType.Hand);
        addCardToZone("Wirewood Symbiote", ai, ZoneType.Hand);
        addCardToZone("Gilt-Leaf Archdruid", ai, ZoneType.Hand);
        addCardToZone("Elvish Archdruid", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(countCardsWithName(game, "Birchlore Rangers", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Nettle Sentinel", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(countCardsWithName(game, "Wirewood Symbiote", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    private int tappedOthers(Game game) {
        int n = 0;
        for (final String name : new String[] {"Heritage Druid", "Elvish Lyrist", "Elvish Visionary"}) {
            for (final forge.game.card.Card c : game.getCardsIn(ZoneType.Battlefield)) {
                if (c.getName().equals(name) && c.isTapped()) {
                    n++;
                }
            }
        }
        return n;
    }

    private void elves(Player ai) {
        addCard("Forest", ai);
        addCard("Heritage Druid", ai).setSickness(true);
        addCard("Elvish Lyrist", ai).setSickness(true);
        addCard("Elvish Visionary", ai).setSickness(true);
        addCard("Nettle Sentinel", ai).setSickness(true);
    }

    /** rule 203, a test game: Nettle Sentinel pays first, the green spell untaps it, one more Elf stays free */
    @Test
    public void testNettleSentinelPaysFirst() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        elves(ai);
        addCardToZone("Beast Whisperer", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(countCardsWithName(game, "Beast Whisperer", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(tappedOthers(game), 2, gameStateToString(game));
        assertEquals(game.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Nettle Sentinel") && !c.isTapped()).count(), 1L, gameStateToString(game));
    }

    /** rule 203: no green spell, it would stay tapped: the others pay */
    @Test
    public void testNettleSentinelStaysForAnArtifact() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        elves(ai);
        addCardToZone("Hedron Archive", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(countCardsWithName(game, "Hedron Archive", ZoneType.Battlefield), 1, gameStateToString(game));
        assertEquals(tappedOthers(game), 3, gameStateToString(game));
    }

    /** rule 201: the dork cannot make the colour, Birchlore can: tapping it is no waste (Savannah Lions off a Llanowar) */
    @Test
    public void testBirchloreMakesTheMissingColour() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Birchlore Rangers", ai).setSickness(false);
        addCard("Llanowar Elves", ai).setSickness(false);
        addCardToZone("Savannah Lions", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(countCardsWithName(game, "Savannah Lions", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** rule 201: {G}{W} with two dorks and Birchlore: W by Birchlore and one dork, G by the other dork */
    @Test
    public void testBirchloreColourThenDork() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Birchlore Rangers", ai).setSickness(false);
        addCard("Llanowar Elves", ai).setSickness(false);
        addCard("Fyndhorn Elves", ai).setSickness(false);
        addCardToZone("Qasali Pridemage", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(countCardsWithName(game, "Qasali Pridemage", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** D121 (05/11): the real board: Forests, Fyndhorn, Arbor Elf, Birchlore: Arbor + Birchlore make the 4th mana */
    @Test
    public void testArborAndBirchloreMakeTheFourthMana() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Forest", ai);
        addCard("Forest", ai);
        addCard("Fyndhorn Elves", ai).setSickness(false);
        addCard("Arbor Elf", ai).setSickness(false);
        addCard("Birchlore Rangers", ai).setSickness(false);
        addCard("Wirewood Symbiote", ai).setSickness(false);
        addCardToZone("Beast Whisperer", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(countCardsWithName(game, "Beast Whisperer", ZoneType.Battlefield), 1, gameStateToString(game));
    }

    /** D121 case 2: the Symbiote is an Insect, Birchlore cannot tap it: only one mana, the Bears stay in hand */
    @Test
    public void testSymbioteIsNoElf() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Forest", ai).setTapped(true);
        addCard("Llanowar Elves", ai).setSickness(false);
        addCard("Birchlore Rangers", ai).setSickness(false);
        addCard("Wirewood Symbiote", ai).setSickness(false);
        addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(countCardsWithName(game, "Grizzly Bears", ZoneType.Battlefield), 0, gameStateToString(game));
    }

    /** Birchlore in play, two sick Elves, a 1-drop in hand: paid by the two sick Elves and not by the dork */
    @Test
    public void testSickElvesPayBeforeTheDork() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Birchlore Rangers", ai).setSickness(true);
        addCard("Elvish Mystic", ai).setSickness(true);
        addCard("Llanowar Elves", ai).setSickness(false);
        addCardToZone("Arbor Elf", ai, ZoneType.Hand);
        addCardToZone("Quirion Ranger", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(ai.getCardsIn(ZoneType.Hand).size(), 0, gameStateToString(game));
    }

    /** No engine: Grizzly Bears and friends are left to the stock order, and no mana is invented */
    @Test
    public void testHeritageNeedsThreeElves() {
        Game game = initAndCreateGame();
        Player ai = setup(game);
        addCard("Heritage Druid", ai).setSickness(true);
        addCard("Elvish Mystic", ai).setSickness(true);
        addCardToZone("Arbor Elf", ai, ZoneType.Hand);
        play(game, ai);
        assertEquals(countCardsWithName(game, "Arbor Elf", ZoneType.Hand), 1, gameStateToString(game));
    }
}
