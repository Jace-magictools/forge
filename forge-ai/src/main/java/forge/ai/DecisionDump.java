package forge.ai;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CounterType;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.zone.ZoneType;

/**
 * Magic Tools, essai « Claude arbitre » (tester 12/11) : enregistreur de décisions de l'IA.
 * FORGE_DECISION_DUMP=<préfixe> écrit dans <préfixe>.<pid>.jsonl une ligne JSON par décision (mulligan, lancer ou passer, cible,
 * attaque, blocage) : état lisible du point de vue du joueur, coups légaux, coup choisi.
 * Ne change aucune décision ; inactif sans la variable.
 */
public final class DecisionDump {
    private static final String FILE = System.getenv("FORGE_DECISION_DUMP");
    private DecisionDump() { }

    public static boolean on() {
        return FILE != null && !FILE.isEmpty();
    }

    // --- points d'appel ---------------------------------------------------------------------------

    public static void mulligan(Player p, int cardsToReturn, boolean keep) {
        if (!on()) return;
        try {
            List<String> opts = List.of("garder", "mulligan");
            write(p, "mulligan", opts, List.of(keep ? "garder" : "mulligan"), "cartes à remettre si on garde : " + cardsToReturn);
        } catch (RuntimeException e) { /* jamais gêner la partie */ }
    }

    /** Après le choix de l'IA à la priorité : chosen = null ou vide = passer. */
    public static void spell(Player p, List<SpellAbility> chosen) {
        if (!on()) return;
        try {
            Game game = p.getGame();
            List<String> opts = new ArrayList<>();
            List<SpellAbility> legal = new ArrayList<>();
            for (Card c : ComputerUtilAbility.getAvailableLandsToPlay(game, p) == null ? new CardCollection()
                    : ComputerUtilAbility.getAvailableLandsToPlay(game, p)) {
                opts.add("jouer le terrain " + c.getName());
            }
            for (SpellAbility sa : ComputerUtilAbility.getSpellAbilities(ComputerUtilAbility.getAvailableCards(game, p), p)) {
                if (sa.isLandAbility() || sa.isManaAbility()) continue;
                try {
                    if (sa.canPlay() && ComputerUtilCost.canPayCost(sa, p, false)) {
                        String s = label(sa);
                        if (!opts.contains(s)) { opts.add(s); legal.add(sa); }
                    }
                } catch (RuntimeException e) { /* option illisible : ignorée */ }
            }
            if (opts.isEmpty()) return;
            opts.add("passer");
            List<String> ch = new ArrayList<>();
            String extra = "";
            if (chosen == null || chosen.isEmpty()) {
                ch.add("passer");
            } else {
                SpellAbility sa = chosen.get(0);
                ch.add(sa.isLandAbility() ? "jouer le terrain " + sa.getHostCard().getName() : label(sa));
                if (sa.usesTargeting() && sa.getTargets() != null && !sa.getTargets().isEmpty()) {
                    extra = "cibles choisies : " + sa.getTargets().toString();
                }
            }
            write(p, "lancer", opts, ch, extra);
            // la cible : une décision à part, si le sort choisi en a plusieurs possibles
            if (chosen != null && !chosen.isEmpty()) {
                SpellAbility sa = chosen.get(0);
                if (sa.usesTargeting() && sa.getTargets() != null && !sa.getTargets().isEmpty()) {
                    List<String> cand = new ArrayList<>();
                    for (GameEntity e : sa.getTargetRestrictions().getAllCandidates(sa, false)) {
                        cand.add(entity(e, p));
                    }
                    if (cand.size() > 1) {
                        List<String> got = new ArrayList<>();
                        for (GameEntity e : sa.getTargets().getTargetEntities()) got.add(entity(e, p));
                        write(p, "cible", cand, got, "sort : " + label(sa));
                    }
                }
            }
        } catch (RuntimeException e) { /* jamais gêner la partie */ }
    }

    public static List<Card> potentialAttackers(Player p) {
        List<Card> l = new ArrayList<>();
        if (!on()) return l;
        try {
            for (Card c : p.getCreaturesInPlay()) if (CombatUtil.canAttack(c)) l.add(c);
        } catch (RuntimeException e) { /* rien */ }
        return l;
    }

    public static void attack(Player p, Combat combat, List<Card> potential) {
        if (!on() || potential.isEmpty()) return;
        try {
            List<String> opts = new ArrayList<>();
            for (Card c : potential) opts.add(card(c));
            List<String> ch = new ArrayList<>();
            for (Card c : combat.getAttackers()) ch.add(card(c) + " → " + entity(combat.getDefenderByAttacker(c), p));
            if (ch.isEmpty()) ch.add("aucune attaque");
            write(p, "attaque", opts, ch, "coups légaux : toute partie des créatures listées (attaquer avec chacune ou non)");
        } catch (RuntimeException e) { /* rien */ }
    }

    public static void block(Player p, Combat combat) {
        if (!on()) return;
        try {
            if (combat == null || combat.getAttackers().isEmpty()) return;
            List<Card> blockers = new ArrayList<>();
            for (Card c : p.getCreaturesInPlay()) if (CombatUtil.canBlock(c, combat) || combat.isBlocking(c)) blockers.add(c);
            if (blockers.isEmpty()) return;
            List<String> opts = new ArrayList<>();
            for (Card c : blockers) opts.add(card(c));
            List<String> ch = new ArrayList<>();
            for (Card b : blockers) {
                List<Card> att = combat.getAttackersBlockedBy(b);
                if (att != null && !att.isEmpty()) {
                    List<String> a = new ArrayList<>();
                    for (Card x : att) a.add(card(x));
                    ch.add(card(b) + " bloque " + String.join(" + ", a));
                }
            }
            if (ch.isEmpty()) ch.add("aucun blocage");
            List<String> att = new ArrayList<>();
            for (Card a : combat.getAttackers()) att.add(card(a) + " → " + entity(combat.getDefenderByAttacker(a), p));
            write(p, "blocage", opts, ch, "attaquants : " + String.join(" ; ", att)
                    + ". Coups légaux : chaque bloqueur listé peut bloquer un attaquant (ou aucun)");
        } catch (RuntimeException e) { /* rien */ }
    }

    // --- état lisible -------------------------------------------------------------------------------

    static String label(SpellAbility sa) {
        Card h = sa.getHostCard();
        String d = sa.isSpell() ? "lancer " + h.getName() : "activer " + h.getName() + " : " + sa.getDescription();
        if (sa.isSpell() && sa.getPayCosts() != null && !sa.getHostCard().isInZone(ZoneType.Hand)) {
            d += " (depuis " + zone(h) + ")";
        }
        if (d.length() > 160) d = d.substring(0, 160) + "…";
        return d;
    }

    static String zone(Card c) {
        return c.getZone() == null ? "?" : c.getZone().getZoneType().name().toLowerCase();
    }

    static String entity(GameEntity e, Player me) {
        if (e instanceof Player) return ((Player) e).equals(me) ? "moi" : "adversaire";
        if (e instanceof Card) {
            Card c = (Card) e;
            return card(c) + (c.getController() != null && c.getController().equals(me) ? " (à moi)" : " (adverse)");
        }
        return String.valueOf(e);
    }

    static String card(Card c) {
        StringBuilder b = new StringBuilder(c.getName());
        List<String> f = new ArrayList<>();
        if (c.isCreature()) f.add(c.getNetPower() + "/" + c.getNetToughness());
        if (c.isPlaneswalker()) f.add("loyauté " + c.getCurrentLoyalty());
        if (c.isTapped()) f.add("engagée");
        if (c.isCreature() && c.isInPlay() && c.isSick()) f.add("mal d'invocation");
        if (c.isToken()) f.add("jeton");
        if (c.isFaceDown()) f.add("face cachée");
        try {
            for (com.google.common.collect.Multiset.Entry<CounterType> e : c.getCounters().entrySet()) {
                if (e.getCount() > 0) f.add(e.getElement().getName() + "×" + e.getCount());
            }
        } catch (RuntimeException e) { /* rien */ }
        if (c.getAttachedTo() != null) f.add("attachée à " + c.getAttachedTo().getName());
        if (!f.isEmpty()) b.append(" (").append(String.join(", ", f)).append(")");
        return b.toString();
    }

    static List<String> cards(Iterable<Card> l) {
        List<String> r = new ArrayList<>();
        for (Card c : l) r.add(card(c));
        return r;
    }

    static String side(Player pl, boolean me) {
        StringBuilder b = new StringBuilder("{");
        b.append("\"vie\":").append(pl.getLife());
        if (pl.getPoisonCounters() > 0) b.append(",\"poison\":").append(pl.getPoisonCounters());
        b.append(",\"bibliotheque\":").append(pl.getCardsIn(ZoneType.Library).size());
        if (me) b.append(",\"main\":").append(arr(cards(pl.getCardsIn(ZoneType.Hand))));
        else b.append(",\"main_nombre\":").append(pl.getCardsIn(ZoneType.Hand).size());
        b.append(",\"champ\":").append(arr(cards(pl.getCardsIn(ZoneType.Battlefield))));
        b.append(",\"cimetiere\":").append(arr(cards(pl.getCardsIn(ZoneType.Graveyard))));
        if (!pl.getCardsIn(ZoneType.Exile).isEmpty()) b.append(",\"exil\":").append(arr(cards(pl.getCardsIn(ZoneType.Exile))));
        if (!pl.getCardsIn(ZoneType.Command).isEmpty()) b.append(",\"commandement\":").append(arr(cards(pl.getCardsIn(ZoneType.Command))));
        return b.append("}").toString();
    }

    static synchronized void write(Player p, String type, List<String> opts, List<String> chosen, String extra) {
        Game game = p.getGame();
        Player opp = p.getWeakestOpponent();
        StringBuilder b = new StringBuilder("{");
        b.append("\"type\":").append(q(type));
        b.append(",\"joueur\":").append(q(p.getName()));
        b.append(",\"adversaire\":").append(q(opp == null ? "" : opp.getName()));
        b.append(",\"partie\":").append(game.getId());
        b.append(",\"tour\":").append(game.getPhaseHandler().getTurn());
        b.append(",\"phase\":").append(q(String.valueOf(game.getPhaseHandler().getPhase())));
        b.append(",\"mon_tour\":").append(game.getPhaseHandler().isPlayerTurn(p));
        b.append(",\"moi\":").append(side(p, true));
        if (opp != null) b.append(",\"adv\":").append(side(opp, false));
        List<String> stack = new ArrayList<>();
        for (SpellAbilityStackInstance si : game.getStack()) {
            stack.add(si.getSourceCard().getName() + " (" + (si.getActivatingPlayer().equals(p) ? "à moi" : "adverse") + ")");
        }
        b.append(",\"pile\":").append(arr(stack));
        b.append(",\"coups\":").append(arr(opts));
        b.append(",\"choix\":").append(arr(chosen));
        if (extra != null && !extra.isEmpty()) b.append(",\"note\":").append(q(extra));
        b.append("}\n");
        try (FileWriter w = new FileWriter(FILE + "." + ProcessHandle.current().pid() + ".jsonl", true)) {
            w.write(b.toString());
        } catch (IOException e) { /* rien */ }
    }

    static String arr(List<String> l) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) b.append(',');
            b.append(q(l.get(i)));
        }
        return b.append(']').toString();
    }

    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
}
