package forge.ai;

import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools, rule 263 (09/11, consigne A de tester) : the deck plan given to the AI.
 * The deck header carries what our Brain knows of the deck (scripts/forge/deck-plans.py) :
 * KeyCards=A;B;C (combo pieces, keystone, pillars) and AiHints=Combo$A;B | Combo$C;D;E.
 * Forge already reads KeyCards for tutors (ChangeZoneAi) ; this class adds, for every deck, from the plan alone :
 * - a key card is not discarded while another card can go (unless the card wants the graveyard) ;
 * - a key permanent is not sacrificed to an edict while another permanent can go ;
 * - the spell that completes a combo of the plan (all its other pieces on our battlefield) is played first ;
 * - otherwise a key permanent spell is tried before the other permanent spells ;
 * - a trade block is not made with a key creature unless life is in danger, a chump block uses another creature first.
 * FORGE_DECK_PLAN=0 turns the rule off (paired measurement, same jar) ; FORGE_DECK_PLAN_DECK=Ai(1)- keeps it for seat 1 only.
 */
public final class DeckPlanAi {
    private DeckPlanAi() { }

    static boolean enabled() {
        return !"0".equals(System.getenv("FORGE_DECK_PLAN"));
    }

    private static final String ONLY = System.getenv("FORGE_DECK_PLAN_DECK");

    private static Deck deck(Player p) {
        if (p == null) {
            return null;
        }
        // FORGE_DECK_PLAN_DECK=texte : le plan ne sert qu'aux joueurs dont le nom le contient (mesure à un seul siège)
        if (ONLY != null && !ONLY.isEmpty() && (p.getName() == null || !p.getName().contains(ONLY))) {
            return null;
        }
        RegisteredPlayer rp = p.getRegisteredPlayer();
        return rp == null ? null : rp.getDeck();
    }

    public static List<String> keyCards(Player p) {
        Deck d = enabled() ? deck(p) : null;
        return d == null ? new ArrayList<>() : d.getKeyCards();
    }

    /** magic-tools rule 282: the plan's win route (Route$pressure | combo | reach), lower case, or null. */
    public static String route(Player p) {
        Deck d = enabled() ? deck(p) : null;
        if (d == null) {
            return null;
        }
        for (String h : d.getAiHints()) {
            if (h.regionMatches(true, 0, "Route$", 0, 6)) {
                return h.substring(6).trim().toLowerCase();
            }
        }
        return null;
    }

    /** magic-tools rule 282: a deck that wins by combo or by reach (mill, drain, burn) is not an aggro deck. */
    public static boolean nonAggroRoute(Player p) {
        String r = route(p);
        return "combo".equals(r) || "reach".equals(r);
    }

    public static List<List<String>> combos(Player p) {
        Deck d = enabled() ? deck(p) : null;
        List<List<String>> out = new ArrayList<>();
        if (d == null) {
            return out;
        }
        for (String h : d.getAiHints()) {
            if (h.regionMatches(true, 0, "Combo$", 0, 6)) {
                List<String> names = new ArrayList<>();
                for (String n : h.substring(6).split(";")) {
                    if (!n.trim().isEmpty()) {
                        names.add(n.trim());
                    }
                }
                if (names.size() >= 2) {
                    out.add(names);
                }
            }
        }
        return out;
    }

    static boolean isKey(Card c, List<String> keys) {
        if (keys.isEmpty()) {
            return false;
        }
        String n = c.getName();
        for (String k : keys) {
            if (k.equals(n) || k.startsWith(n + " // ")) {
                return true;
            }
        }
        return false;
    }

    /** A card that is meant to reach the graveyard (reanimator target, madness, flashback…) is not protected. */
    private static boolean wantsGraveyard(Card c) {
        return c.hasSVar("DiscardMe") || c.hasSVar("IsReanimatorCard") || c.hasKeyword(Keyword.MADNESS)
                || c.hasKeyword(Keyword.FLASHBACK) || c.hasSVar("SacMe");
    }

    /** The cards that can be discarded without touching the plan, or null when the plan changes nothing. */
    public static CardCollection keepForDiscard(Player ai, CardCollectionView valid, int min) {
        List<String> keys = keyCards(ai);
        if (keys.isEmpty()) {
            return null;
        }
        CardCollection rest = CardLists.filter(valid, c -> !isKey(c, keys) || wantsGraveyard(c));
        if (rest.size() == valid.size() || rest.size() < Math.max(min, 1)) {
            return null;
        }
        return rest;
    }

    /** The permanents an edict can take without touching the plan, or null when the plan changes nothing. */
    public static CardCollection keepForSacrifice(Player ai, CardCollectionView remaining, int amount) {
        List<String> keys = keyCards(ai);
        if (keys.isEmpty()) {
            return null;
        }
        CardCollection rest = CardLists.filter(remaining, c -> !isKey(c, keys) || wantsGraveyard(c));
        if (rest.size() == remaining.size() || rest.size() < amount) {
            return null;
        }
        return rest;
    }

    /** The plan's combo that this card completes : every other piece already on our battlefield. */
    public static List<String> completes(Player ai, Card c) {
        for (List<String> combo : combos(ai)) {
            if (!combo.contains(c.getName())) {
                continue;
            }
            boolean all = true;
            for (String n : combo) {
                if (n.equals(c.getName())) {
                    continue;
                }
                if (!ai.getCardsIn(ZoneType.Battlefield).anyMatch(x -> x.getName().equals(n))) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return combo;
            }
        }
        return null;
    }

    /** Blocks : a trade or a chump block uses another creature than a key card when there is one. */
    public static List<Card> spareKeys(Player ai, List<Card> blockers) {
        List<String> keys = keyCards(ai);
        if (keys.isEmpty() || blockers.isEmpty()) {
            return blockers;
        }
        List<Card> rest = new ArrayList<>();
        for (Card c : blockers) {
            if (!isKey(c, keys)) {
                rest.add(c);
            }
        }
        return rest.isEmpty() ? blockers : rest;
    }

    public static boolean allKeys(Player ai, List<Card> cards) {
        List<String> keys = keyCards(ai);
        if (keys.isEmpty() || cards.isEmpty()) {
            return false;
        }
        for (Card c : cards) {
            if (!isKey(c, keys)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPermanentSpell(SpellAbility sa) {
        Card host = sa.getHostCard();
        return sa.isSpell() && host != null && host.isInZone(ZoneType.Hand) && host.isPermanent() && !host.isLand();
    }

    /** The spell that completes a combo of the plan goes first ; else a key permanent goes before the other permanents. */
    public static void prioritize(Player ai, List<SpellAbility> all) {
        List<String> keys = keyCards(ai);
        if (keys.isEmpty()) {
            return;
        }
        if (combos(ai).isEmpty()) {
            keyBeforeOthers(keys, all);
            return;
        }
        for (int i = 1; i < all.size(); i++) {
            SpellAbility sa = all.get(i);
            Card host = sa.getHostCard();
            if (sa.isSpell() && host != null && host.isInZone(ZoneType.Hand) && host.isPermanent()
                    && completes(ai, host) != null) {
                all.add(0, all.remove(i));
                return;
            }
        }
        keyBeforeOthers(keys, all);
    }

    private static void keyBeforeOthers(List<String> keys, List<SpellAbility> all) {
        int firstOther = -1;
        for (int i = 0; i < all.size(); i++) {
            SpellAbility sa = all.get(i);
            if (!isPermanentSpell(sa)) {
                continue;
            }
            if (!isKey(sa.getHostCard(), keys)) {
                if (firstOther < 0) {
                    firstOther = i;
                }
            } else if (firstOther >= 0) {
                all.add(firstOther, all.remove(i));
                return;
            }
        }
    }

    public static boolean isKeyCard(Player ai, Card c) {
        return isKey(c, keyCards(ai));
    }

    /** magic-tools, rule 276 (narrowed 10/11) : the card is a piece of one of the combos (Combo$) of its player's plan. */
    public static boolean isComboPiece(Player p, Card c) {
        for (List<String> combo : combos(p)) {
            if (isKey(c, combo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * magic-tools, rule 274 (tester 10/11 08:26, « le plan à chaque tour » : mulligan) : the cards the London
     * mulligan may put back without touching the plan. The key cards of the hand are left out, unless only key
     * cards remain. Null when the plan changes nothing.
     */
    public static CardCollection spareForTuck(Player ai, CardCollectionView cards) {
        if (!NewAi.on(ai, 274)) {
            return null;
        }
        List<String> keys = keyCards(ai);
        if (keys.isEmpty()) {
            return null;
        }
        CardCollection rest = CardLists.filter(cards, c -> !isKey(c, keys));
        return rest.isEmpty() || rest.size() == cards.size() ? null : rest;
    }

    /**
     * magic-tools, rule 275 (tester 10/11 08:26, « le plan à chaque tour » : protéger une pièce ; WITHDRAWN 10/11 :
     * duel z -2,1 on 4 seeds, no longer called from CounterAi, kept for a later try) : true when the
     * opponent's spell or ability targets one of our key cards (a combo piece or a pillar of the plan), in its own
     * targets or those of its sub-abilities. The counterspell then goes even when the mana value chances said no.
     */
    public static boolean threatensKey(Player ai, SpellAbility tgtSA) {
        if (tgtSA == null || !NewAi.on(ai, 275) || tgtSA.getActivatingPlayer() == null
                || !tgtSA.getActivatingPlayer().isOpponentOf(ai)) {
            return false;
        }
        List<String> keys = keyCards(ai);
        if (keys.isEmpty()) {
            return false;
        }
        for (SpellAbility s = tgtSA; s != null; s = s.getSubAbility()) {
            if (s.getTargets() == null) {
                continue;
            }
            for (Card c : s.getTargets().getTargetCards()) {
                if (c != null && ai.equals(c.getController()) && isKey(c, keys)) {
                    return true;
                }
            }
        }
        return false;
    }
}
