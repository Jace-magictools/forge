package forge.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.annotations.Test;

import forge.card.mana.ManaAtom;
import forge.game.Game;
import forge.game.GameLogEntryType;
import forge.game.mana.Mana;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class PartieRecorderTest extends AITest {

    // magic-tools, 29/10 : une boucle (Pod contre Veiga, tournoi v9) a écrit 39 110 étapes et 265 Mo, une autre a saturé
    // la mémoire. Au-delà de MAX_ETAPES, la partie n'est plus découpée en étapes et le fichier le dit (« truncated »).
    @Test
    public void testCapsStepsOfALoop() throws Exception {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Path dossier = Files.createTempDirectory("partie");
        PartieRecorder recorder = new PartieRecorder(game, dossier, "boucle");

        int tours = PartieRecorder.MAX_ETAPES + 500;
        for (int i = 0; i < tours; i++) {
            ai.setLife(1000 + i, null);
            game.getGameLog().add(GameLogEntryType.INFORMATION, "gain " + i);
        }
        recorder.recieve(null);

        String json = Files.readString(dossier.resolve("boucle.json"), StandardCharsets.UTF_8);
        int etapes = json.split("\\{\"t\":", -1).length - 1;
        assertEquals(etapes, PartieRecorder.MAX_ETAPES);
        assertTrue(json.contains("\"truncated\":"), "le fichier doit dire qu'il est tronqué");
        // la dernière étape garde au plus MAX_LIGNES_ETAPE lignes
        String derniere = json.substring(json.lastIndexOf("{\"t\":"));
        int lignes = derniere.split("\"gain ", -1).length - 1;
        assertTrue(lignes <= PartieRecorder.MAX_LIGNES_ETAPE, "lignes : " + lignes);
    }

    // magic-tools, 29/10 (tester) : la réserve de mana de chaque joueur, une lettre par mana, absente quand elle est vide.
    @Test
    public void testRecordsManaPool() throws Exception {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Path dossier = Files.createTempDirectory("partie");
        PartieRecorder recorder = new PartieRecorder(game, dossier, "mana");

        forge.game.card.Card src = addCard("Mountain", ai);
        game.getGameLog().add(GameLogEntryType.INFORMATION, "vide");
        ai.getManaPool().addMana(new Mana((byte) ManaAtom.RED, src, null, ai), new Mana((byte) ManaAtom.RED, src, null, ai),
                new Mana((byte) ManaAtom.GREEN, src, null, ai), new Mana((byte) ManaAtom.COLORLESS, src, null, ai));
        game.getGameLog().add(GameLogEntryType.INFORMATION, "rempli");
        recorder.recieve(null);

        String json = Files.readString(dossier.resolve("mana.json"), StandardCharsets.UTF_8);
        String premiere = json.substring(json.indexOf("{\"t\":"), json.indexOf("\"rempli\""));
        assertTrue(!premiere.contains("\"mp\""), "réserve vide : pas de champ");
        assertTrue(json.contains("\"mp\":\"RRGC\""), json);
    }

    // magic-tools, 29/10 (tester) : le mulligan se voit (main refusée, cartes mises dessous, main gardée), et une carte
    // remise sur le dessus de la bibliothèque est connue de son propriétaire jusqu'au prochain mélange.
    @Test
    public void testRecordsMulliganAndKnownTop() throws Exception {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Path dossier = Files.createTempDirectory("partie");
        PartieRecorder recorder = new PartieRecorder(game, dossier, "mulligan");
        game.subscribeToEvents(recorder);

        for (int i = 0; i < 20; i++) {
            addCardToZone(i % 2 == 0 ? "Island" : "Opt", ai, forge.game.zone.ZoneType.Library);
        }
        ai.drawCards(7);
        String refusee = ids(ai.getCardsIn(forge.game.zone.ZoneType.Hand));
        forge.game.mulligan.LondonMulligan m = new forge.game.mulligan.LondonMulligan(ai, false);
        m.mulligan();
        m.mulligan();
        m.keep();
        m.afterMulligan();
        assertEquals(ai.getCardsIn(forge.game.zone.ZoneType.Hand).size(), 5);
        String gardee = ids(ai.getCardsIn(forge.game.zone.ZoneType.Hand));

        // une carte de la main remise sur le dessus (comme Brainstorm) : connue ; un mélange l'oublie
        forge.game.card.Card dessus = ai.getCardsIn(forge.game.zone.ZoneType.Hand).get(0);
        game.getAction().moveToLibrary(dessus, null);
        game.getGameLog().add(GameLogEntryType.INFORMATION, "remise");
        ai.shuffle(null);
        game.getGameLog().add(GameLogEntryType.INFORMATION, "melange");
        recorder.recieve(null);

        String json = Files.readString(dossier.resolve("mulligan.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"mul\":{\"r\":[" + refusee + ",["), json);
        assertTrue(json.matches("(?s).*\"b\":\\[\\d+,\\d+\\],\"k\":" + java.util.regex.Pattern.quote(gardee) + ".*"), json);
        String remise = json.substring(json.indexOf("\"remise\""), json.indexOf("\"melange\""));
        assertTrue(remise.contains("\"lt\":[" + dessus.getId() + "]"), remise);
        String apres = json.substring(json.indexOf("\"melange\""));
        assertTrue(!apres.contains("\"lt\""), apres);
    }

    private static String ids(Iterable<forge.game.card.Card> cs) {
        StringBuilder sb = new StringBuilder("[");
        for (forge.game.card.Card c : cs) {
            sb.append(sb.length() > 1 ? "," : "").append(c.getId());
        }
        return sb.append(']').toString();
    }
}
