package forge.ai;

import com.google.common.collect.Multiset;
import com.google.common.eventbus.Subscribe;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameLogEntry;
import forge.card.mana.ManaAtom;
import forge.game.card.Card;
import forge.game.card.CounterType;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.event.GameEventCardChangeZone;
import forge.game.event.GameEventGameOutcome;
import forge.game.event.GameEventMulligan;
import forge.game.event.GameEventShuffle;
import forge.game.GameLogEntryType;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.zone.ZoneType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * magic-tools : visionneur de parties (tester, 24/10 et 28/10). Enregistre une partie simulée en entier, pour la rejouer
 * pas à pas sur le site : à chaque nouvelle ligne du journal de Forge, l'état complet (vie, poison, bibliothèque, main,
 * champ de bataille avec engagé / marqueurs / force et endurance / attachements, cimetière, exil, pile, combat) et les
 * lignes du journal depuis l'étape précédente. Une étape dont l'état ne change pas fusionne ses lignes avec la précédente.
 * Écrit un fichier JSON par partie à la fin (-Dforge.partie=dossier, branché par SimulateMatch) ; ne change rien au jeu.
 */
public class PartieRecorder implements java.util.Observer {
    private final Game game;
    private final Path dossier;
    private final String nom;
    private final Map<Integer, String> cartes = new LinkedHashMap<>();
    private final List<Etape> etapes = new ArrayList<>();
    private String dernierEtat = null;
    private List<String> lignes = new ArrayList<>();
    private int lus = 0;
    /** Plafonds (29/10, tournoi v9 : une boucle Pod a fait 39 110 étapes, 265 Mo, et une autre a saturé la mémoire). */
    static final int MAX_ETAPES = 5000;
    static final int MAX_LIGNES_ETAPE = 200;
    private int tronque = 0;
    /**
     * Cartes de bibliothèque connues de leur propriétaire (tester, 29/10) : toute carte qui entre dans une bibliothèque
     * (remise sur le dessus depuis la main, le cimetière, le champ ; rangée par un regard ou un scry) est connue, jusqu'au
     * prochain mélange de cette bibliothèque. L'état garde, par joueur, la suite de cartes connues depuis le dessus.
     */
    private final Set<Integer> connues = new HashSet<>();
    /** Mulligans (tester, 29/10) : par joueur, les mains refusées, les cartes mises en dessous, la main gardée. */
    private final Map<Integer, List<String>> refusees = new LinkedHashMap<>();
    private final Map<Integer, String> dessous = new HashMap<>();
    private final Map<Integer, String> gardees = new HashMap<>();
    /** Main → bibliothèque, par joueur : le groupe en cours, et le dernier groupe fermé par une pioche. */
    private final Map<Integer, List<Card>> versBiblio = new HashMap<>();
    private final Map<Integer, List<Card>> groupeFerme = new HashMap<>();

    public PartieRecorder(Game game, Path dossier, String nom) {
        this.game = game;
        this.dossier = dossier;
        this.nom = nom;
        game.getGameLog().addObserver(this);
    }

    @Override
    public void update(java.util.Observable o, Object arg) {
        try {
            List<GameLogEntry> tout = game.getGameLog().getAllEntries();
            boolean mulligan = false;
            for (; lus < tout.size(); lus++) {
                lignes.add(tout.get(lus).message());
                mulligan |= tout.get(lus).type() == GameLogEntryType.MULLIGAN;
            }
            if (mulligan) {
                // « X garde sa main de N cartes » arrive après tous les mulligans : la main est celle de départ
                for (Integer i : refusees.keySet()) {
                    gardees.put(i, ids(game.getRegisteredPlayers().get(i).getCardsIn(ZoneType.Hand)));
                }
            }
            String etat = etat();
            if (!etapes.isEmpty() && (etat.equals(dernierEtat) || etapes.size() >= MAX_ETAPES)) {
                // même état (ou plafond atteint : la suite de la partie n'est plus enregistrée en étapes) :
                // les lignes rejoignent l'étape précédente, sans dépasser MAX_LIGNES_ETAPE
                if (!etat.equals(dernierEtat)) {
                    tronque++;
                }
                List<String> log = etapes.get(etapes.size() - 1).log();
                for (String l : lignes) {
                    if (log.size() < MAX_LIGNES_ETAPE) {
                        log.add(l);
                    }
                }
            } else {
                PhaseHandler ph = game.getPhaseHandler();
                String tete = "{\"t\":" + ph.getTurn() + ",\"ph\":" + q(ph.getPhase() == null ? "" : ph.getPhase().name())
                        + ",\"ap\":" + index(ph.getPlayerTurn());
                etapes.add(new Etape(tete, new ArrayList<>(lignes.size() > MAX_LIGNES_ETAPE
                        ? lignes.subList(0, MAX_LIGNES_ETAPE) : lignes), etat));
                dernierEtat = etat;
            }
            lignes = new ArrayList<>();
        } catch (RuntimeException e) {
            // un enregistrement ne doit jamais casser la partie
        }
    }

    private record Etape(String tete, List<String> log, String etat, List<String> bourdes) {
        Etape(String tete, List<String> log, String etat) {
            this(tete, log, etat, new ArrayList<>());
        }

        String json() {
            StringBuilder sb = new StringBuilder(tete).append(",\"log\":[");
            for (int i = 0; i < log.size(); i++) {
                sb.append(i > 0 ? "," : "").append(q(log.get(i)));
            }
            sb.append("],").append(etat);
            if (!bourdes.isEmpty()) {
                sb.append(",\"bd\":[").append(String.join(",", bourdes)).append(']');
            }
            return sb.append('}').toString();
        }
    }

    /**
     * Bourde vue par le BourdeWatcher (type, joueur fautif, détail) : rangée dans l'étape en cours, pour que le visionneur
     * la montre au bon moment (tester, 28/10 : le retour humain part de ce que l'IA a mal fait).
     */
    public void bourde(String type, Player p, String detail) {
        try {
            update(null, null);
            if (!etapes.isEmpty()) {
                etapes.get(etapes.size() - 1).bourdes().add("{\"ty\":" + q(type) + ",\"pl\":" + index(p) + ",\"d\":" + q(detail) + "}");
            }
        } catch (RuntimeException e) {
            // un enregistrement ne doit jamais casser la partie
        }
    }

    private int index(Player p) {
        return p == null ? -1 : game.getRegisteredPlayers().indexOf(p);
    }

    private String etat() {
        StringBuilder sb = new StringBuilder("\"p\":[");
        boolean premier = true;
        for (Player p : game.getRegisteredPlayers()) {
            if (!premier) {
                sb.append(',');
            }
            premier = false;
            sb.append("{\"life\":").append(p.getLife());
            if (p.getPoisonCounters() > 0) {
                sb.append(",\"poison\":").append(p.getPoisonCounters());
            }
            String mp = reserve(p);
            if (!mp.isEmpty()) {
                sb.append(",\"mp\":").append(q(mp));
            }
            sb.append(",\"lib\":").append(p.getCardsIn(ZoneType.Library).size());
            List<Card> dessus = new ArrayList<>();
            for (Card c : p.getCardsIn(ZoneType.Library)) {
                if (!connues.contains(c.getId()) || dessus.size() >= 20) {
                    break;
                }
                dessus.add(c);
            }
            if (!dessus.isEmpty()) {
                sb.append(",\"lt\":").append(ids(dessus));
            }
            sb.append(",\"hand\":").append(ids(p.getCardsIn(ZoneType.Hand)));
            sb.append(",\"bf\":[");
            boolean pc = true;
            for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
                if (!pc) {
                    sb.append(',');
                }
                pc = false;
                sb.append(permanent(c));
            }
            sb.append("]");
            sb.append(",\"gy\":").append(ids(p.getCardsIn(ZoneType.Graveyard)));
            sb.append(",\"ex\":").append(ids(p.getCardsIn(ZoneType.Exile)));
            List<Card> cmd = new ArrayList<>(p.getCardsIn(ZoneType.Command));
            if (!cmd.isEmpty()) {
                sb.append(",\"cmd\":").append(ids(cmd));
            }
            sb.append('}');
        }
        sb.append("],\"stack\":[");
        boolean ps = true;
        for (SpellAbilityStackInstance si : game.getStack()) {
            if (!ps) {
                sb.append(',');
            }
            ps = false;
            Card src = si.getSourceCard();
            sb.append("{\"c\":").append(src == null ? -1 : carte(src)).append(",\"d\":").append(q(si.getStackDescription())).append('}');
        }
        sb.append("]");
        Combat combat = game.getPhaseHandler().getCombat();
        if (combat != null && !combat.getAttackers().isEmpty()) {
            sb.append(",\"combat\":[");
            boolean pa = true;
            for (Card a : combat.getAttackers()) {
                if (!pa) {
                    sb.append(',');
                }
                pa = false;
                GameEntity def = combat.getDefenderByAttacker(a);
                String cible = def instanceof Player pl ? "\"p" + index(pl) + "\"" : def instanceof Card dc ? String.valueOf(carte(dc)) : "null";
                sb.append("{\"a\":").append(carte(a)).append(",\"d\":").append(cible).append(",\"b\":").append(ids(combat.getBlockers(a)));
                if (def instanceof Player pl) {
                    // bloqueurs legaux (regles Forge : vol, menace mise a part, « can't block », statiques), pour les situations D17
                    sb.append(",\"pb\":").append(ids(bloqueursPossibles(a, pl)));
                }
                sb.append('}');
            }
            sb.append("]");
        }
        return sb.toString();
    }

    /** Réserve de mana (tester, 29/10) : une lettre par mana, WUBRG puis C (« RRG » = deux rouges et un vert) ; vide si rien. */
    private static String reserve(Player p) {
        StringBuilder sb = new StringBuilder();
        if (p.getManaPool().isEmpty()) {
            return "";
        }
        String lettres = "WUBRGC";
        for (int i = 0; i < ManaAtom.MANATYPES.length; i++) {
            int n = p.getManaPool().getAmountOfColor(ManaAtom.MANATYPES[i]);
            for (int j = 0; j < n && sb.length() < 40; j++) {
                sb.append(lettres.charAt(i));
            }
        }
        return sb.toString();
    }

    @Subscribe
    public void zone(GameEventCardChangeZone ev) {
        try {
            if (ev.card() == null || ev.to() == null) {
                return;
            }
            if (ev.to().zoneType() == ZoneType.Library) {
                connues.add(ev.card().getId());
            }
            int i = ev.to().player() == null ? -1 : indexVue(ev.to().player());
            if (i < 0 || ev.from() == null) {
                return;
            }
            Card c = game.findByView(ev.card());
            if (c == null) {
                return;
            }
            if (ev.from().zoneType() == ZoneType.Hand && ev.to().zoneType() == ZoneType.Library) {
                versBiblio.computeIfAbsent(i, k -> new ArrayList<>()).add(c);
            } else if (ev.from().zoneType() == ZoneType.Library && ev.to().zoneType() == ZoneType.Hand
                    && versBiblio.containsKey(i)) {
                // une pioche ferme le groupe (main refusée avant les 7 nouvelles cartes)
                groupeFerme.put(i, versBiblio.remove(i));
            }
        } catch (RuntimeException e) {
            // un enregistrement ne doit jamais casser la partie
        }
    }

    @Subscribe
    public void melange(GameEventShuffle ev) {
        try {
            int i = indexVue(ev.player());
            if (i >= 0) {
                for (Card c : game.getRegisteredPlayers().get(i).getCardsIn(ZoneType.Library)) {
                    connues.remove(c.getId());
                }
            }
        } catch (RuntimeException e) {
            // un enregistrement ne doit jamais casser la partie
        }
    }

    /** Après un mulligan : la main refusée (dernier groupe main → bibliothèque fermé par la pioche), les cartes mises dessous. */
    @Subscribe
    public void mulligan(GameEventMulligan ev) {
        try {
            int i = indexVue(ev.player());
            if (i < 0) {
                return;
            }
            List<Card> refus = groupeFerme.remove(i);
            refusees.computeIfAbsent(i, k -> new ArrayList<>()).add(refus == null ? "[]" : ids(refus));
            List<Card> sous = versBiblio.remove(i);
            dessous.put(i, sous == null ? "[]" : ids(sous));
            gardees.put(i, ids(game.getRegisteredPlayers().get(i).getCardsIn(ZoneType.Hand)));
        } catch (RuntimeException e) {
            // un enregistrement ne doit jamais casser la partie
        }
    }

    private int indexVue(forge.game.player.PlayerView v) {
        for (int i = 0; i < game.getRegisteredPlayers().size(); i++) {
            if (game.getRegisteredPlayers().get(i).getView().equals(v)) {
                return i;
            }
        }
        return -1;
    }

    private static final Keyword[] KW = {Keyword.FLYING, Keyword.REACH, Keyword.MENACE, Keyword.TRAMPLE, Keyword.FIRST_STRIKE,
            Keyword.DOUBLE_STRIKE, Keyword.DEATHTOUCH, Keyword.LIFELINK, Keyword.VIGILANCE, Keyword.INDESTRUCTIBLE};

    private static List<Card> bloqueursPossibles(Card a, Player def) {
        List<Card> out = new ArrayList<>();
        try {
            if (!CombatUtil.canBeBlocked(a, null, def)) {
                return out;
            }
            for (Card b : def.getCreaturesInPlay()) {
                if (CombatUtil.canBlock(b) && CombatUtil.canBlock(a, b)) {
                    out.add(b);
                }
            }
        } catch (RuntimeException e) {
            out.clear();
        }
        return out;
    }

    private String permanent(Card c) {
        StringBuilder sb = new StringBuilder("{\"i\":").append(carte(c));
        if (c.isTapped()) {
            sb.append(",\"tp\":1");
        }
        if (c.isCreature()) {
            sb.append(",\"pt\":").append(q(c.getNetPower() + "/" + c.getNetToughness()));
            if (c.getDamage() > 0) {
                sb.append(",\"dmg\":").append(c.getDamage());
            }
            if (c.isSick()) {
                sb.append(",\"sick\":1");
            }
            StringBuilder kw = new StringBuilder();
            for (Keyword w : KW) {
                if (c.hasKeyword(w)) {
                    kw.append(kw.length() == 0 ? "" : ",").append(q(w.toString()));
                }
            }
            if (kw.length() > 0) {
                sb.append(",\"kw\":[").append(kw).append(']');
            }
        }
        if (c.isFaceDown()) {
            sb.append(",\"fd\":1");
        }
        Multiset<CounterType> k = c.getCounters();
        if (k != null && !k.isEmpty()) {
            sb.append(",\"k\":{");
            boolean pk = true;
            for (Multiset.Entry<CounterType> e : k.entrySet()) {
                if (!pk) {
                    sb.append(',');
                }
                pk = false;
                sb.append(q(e.getElement().getName())).append(':').append(e.getCount());
            }
            sb.append('}');
        }
        GameEntity att = c.getEntityAttachedTo();
        if (att instanceof Card ac) {
            sb.append(",\"at\":").append(carte(ac));
        } else if (att instanceof Player ap) {
            sb.append(",\"at\":\"p").append(index(ap)).append('"');
        }
        if (c.getController() != c.getOwner()) {
            sb.append(",\"own\":").append(index(c.getOwner()));
        }
        return sb.append('}').toString();
    }

    private String ids(Iterable<Card> cs) {
        StringBuilder sb = new StringBuilder("[");
        for (Card c : cs) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append(carte(c));
        }
        return sb.append(']').toString();
    }

    /** Numéro de la carte, et sa fiche (nom, édition, jeton, terrain) la première fois. */
    private int carte(Card c) {
        int id = c.getId();
        if (!cartes.containsKey(id)) {
            String set = "";
            try {
                set = c.getSetCode();
            } catch (RuntimeException e) {
                // pas d'édition connue
            }
            cartes.put(id, "{\"n\":" + q(c.getName()) + (set == null || set.isEmpty() ? "" : ",\"s\":" + q(set))
                    + (c.isToken() ? ",\"tok\":1" : "") + (c.isLand() ? ",\"land\":1" : "") + "}");
        }
        return id;
    }

    @Subscribe
    public void recieve(GameEventGameOutcome ev) {
        try {
            update(null, null);
            StringBuilder sb = new StringBuilder("{\"version\":1,\"players\":[");
            boolean premier = true;
            for (Player p : game.getRegisteredPlayers()) {
                if (!premier) {
                    sb.append(',');
                }
                premier = false;
                sb.append("{\"name\":").append(q(p.getName()));
                int i = index(p);
                if (refusees.containsKey(i)) {
                    // mulligans : mains refusées dans l'ordre, cartes mises sous la bibliothèque, main gardée
                    sb.append(",\"mul\":{\"r\":[").append(String.join(",", refusees.get(i))).append("],\"b\":")
                            .append(dessous.getOrDefault(i, "[]")).append(",\"k\":").append(gardees.getOrDefault(i, "[]")).append('}');
                }
                sb.append('}');
            }
            sb.append("],\"result\":");
            if (game.getOutcome() == null || game.getOutcome().isDraw()) {
                sb.append("{\"draw\":true}");
            } else {
                int w = -1;
                for (Player p : game.getRegisteredPlayers()) {
                    if (p.getOutcome() != null && p.getOutcome().lossState == null) {
                        w = index(p);
                    }
                }
                sb.append("{\"winner\":").append(w).append(",\"turn\":").append(game.getPhaseHandler().getTurn()).append('}');
            }
            sb.append(",\"cards\":{");
            boolean pc = true;
            for (Map.Entry<Integer, String> e : cartes.entrySet()) {
                if (!pc) {
                    sb.append(',');
                }
                pc = false;
                sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
            }
            sb.append('}');
            if (tronque > 0) {
                sb.append(",\"truncated\":").append(tronque);
            }
            sb.append(",\"steps\":[");
            Files.createDirectories(dossier);
            // écrit étape par étape : la partie entière ne passe plus par une seule chaîne en mémoire
            try (java.io.Writer w = Files.newBufferedWriter(dossier.resolve(nom + ".json"), StandardCharsets.UTF_8)) {
                w.write(sb.toString());
                for (int i = 0; i < etapes.size(); i++) {
                    w.write(i > 0 ? ",\n" : "");
                    w.write(etapes.get(i).json());
                }
                w.write("]}\n");
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("PartieRecorder: " + e);
        }
    }

    static String q(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
