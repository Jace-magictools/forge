package forge.ai;

import org.apache.commons.lang3.StringUtils;

import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Birthing Pod (and the other SacAndUpgrade cards) fetching the missing piece of the persist loop
 * (Melira, Sylvok Outcast + Murderous Redcap + free sacrifice outlet, see {@link PersistLoopAi}).
 * The stock logic only activates Pod in main phase 2, after the mana went into creature spells, upgrades the creature
 * with the highest mana value (Melira into a 3-drop) and fetches the "best" creature (Kitchen Finks over the Redcap).
 */
public final class PodComboAi {
    private PodComboAi() { }

    /** Creatures you control can't have -1/-1 counters put on them (Melira). */
    static boolean shieldsPersist(final Card c) {
        for (final StaticAbility st : c.getStaticAbilities()) {
            if ("CantPutCounter".equals(st.getParam("Mode")) && "M1M1".equals(st.getParam("CounterType"))
                    && StringUtils.contains(st.getParam("ValidCard"), "Creature")) {
                return true;
            }
        }
        return false;
    }

    static boolean isPayoff(final Card c) {
        return c.isCreature() && c.hasKeyword(Keyword.PERSIST) && PersistLoopAi.dealsDamageOnEnter(c);
    }

    static boolean isOutlet(final Card c) {
        for (final SpellAbility sa : c.getSpellAbilities()) {
            if (PersistLoopAi.freeSacCost(sa) != null) {
                return true;
            }
        }
        return false;
    }

    /** Shield, payoff and an outlet that is not the payoff itself. */
    static boolean hasLoop(final Iterable<Card> cards) {
        boolean shield = false;
        Card payoff = null;
        for (final Card c : cards) {
            shield |= shieldsPersist(c);
            if (payoff == null && isPayoff(c)) {
                payoff = c;
            }
        }
        if (!shield || payoff == null) {
            return false;
        }
        for (final Card c : cards) {
            if (c != payoff && isOutlet(c)) {
                return true;
            }
        }
        return false;
    }

    /** Sacrificing {@code sac} (may be null) and getting {@code add} onto the battlefield assembles the loop. */
    static boolean completesLoop(final Player ai, final Card sac, final Card add) {
        final CardCollection board = new CardCollection(ai.getCardsIn(ZoneType.Battlefield));
        if (hasLoop(board)) {
            return false;
        }
        if (sac != null) {
            board.remove(sac);
        }
        board.add(add);
        return hasLoop(board);
    }

    /** Card to fetch from our library that assembles the loop with what is on the battlefield, or null. */
    public static Card chooseFetch(final Player ai, final CardCollectionView fetchList) {
        for (final Card c : fetchList) {
            if (c.getOwner() == ai && c.isInZone(ZoneType.Library) && completesLoop(ai, null, c)) {
                return c;
            }
        }
        return null;
    }

    /** {sacrifice, creature to fetch} for a SacAndUpgrade ability that assembles the loop now, or null. */
    public static Card[] plan(final Player ai, final SpellAbility sa) {
        if (sa == null || sa.getApi() == null
                || !(StringUtils.startsWith(sa.getParam("AILogic"), "SacAndUpgrade") || SacUpgradeAi.prepare(sa))
                || !"Library".equals(sa.getParam("Origin")) || !"Battlefield".equals(sa.getParam("Destination"))) {
            return null;
        }
        final Card source = sa.getHostCard();
        final String pref = source.getSVar("AIPreference");
        final String goal = sa.getParam("ChangeType");
        if (!pref.contains("$") || goal == null || !goal.contains(".cmc")) {
            return null;
        }
        CardCollection sacs = CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield), StringUtils.split(pref, "$")[1], ai, source, sa);
        sacs = CardLists.filter(sacs, CardPredicates.canBeSacrificedBy(sa, false));
        sacs.sort(CardLists.CmcComparator);
        for (final Card sac : sacs) {
            final int cmc = sac.getCMC();
            final int goalCMC = source.hasSVar("X")
                    ? AbilityUtils.calculateAmount(source, source.getSVar("X").replace("Sacrificed$CardManaCost", "Number$" + cmc), sa)
                    : cmc + 1;
            CardCollection targets = CardLists.getValidCards(ai.getCardsIn(ZoneType.Library), goal.replace("X", String.valueOf(goalCMC)), ai, source, sa);
            targets = CardLists.filter(targets, c -> !c.getType().isLegendary() || !ai.isCardInPlay(c.getName()));
            for (final Card t : targets) {
                if (completesLoop(ai, sac, t)) {
                    return new Card[] {sac, t};
                }
            }
        }
        return null;
    }

    /** Rule 273: Melira (static) or Vizier of Remedies (replacement "N minus one" -1/-1 counters) on its own card. */
    static boolean shieldsPersistLoose(final Card c) {
        if (shieldsPersist(c)) {
            return true;
        }
        for (final forge.game.replacement.ReplacementEffect re : c.getReplacementEffects()) {
            if (re.getMode() != forge.game.replacement.ReplacementType.AddCounter
                    || !"M1M1".equals(re.getParam("ValidCounterType"))) {
                continue;
            }
            final SpellAbility rsa = re.ensureAbility();
            if (rsa == null || rsa.getApi() != forge.game.ability.ApiType.ReplaceCounter) {
                continue;
            }
            final String amount = rsa.getParamOrDefault("Amount", "");
            final String x = c.getSVar(amount);
            if ("0".equals(amount) || (x != null && x.contains("CounterNum/Minus."))) {
                return true;
            }
        }
        return false;
    }

    /** Bit mask of the roles the card can fill: 1 = shield, 2 = persist creature, 4 = free outlet. */
    static int roles(final Card c) {
        int r = 0;
        if (shieldsPersistLoose(c)) {
            r |= 1;
        }
        if (c.isCreature() && c.hasKeyword(Keyword.PERSIST)) {
            r |= 2;
        }
        if (isOutlet(c)) {
            r |= 4;
        }
        return r;
    }

    /** Roles filled by distinct cards of the pool (a card fills one role). */
    static int filled(final Iterable<Card> pool) {
        // try every assignment greedily on the rarest role first: shield, then persist, then outlet
        final java.util.List<Card> left = new java.util.ArrayList<>();
        for (final Card c : pool) {
            if (roles(c) != 0) {
                left.add(c);
            }
        }
        int best = 0;
        for (final int[] order : new int[][] {{1, 2, 4}, {1, 4, 2}, {2, 1, 4}, {2, 4, 1}, {4, 1, 2}, {4, 2, 1}}) {
            final java.util.List<Card> rest = new java.util.ArrayList<>(left);
            int got = 0;
            for (final int role : order) {
                for (final Card c : rest) {
                    if ((roles(c) & role) != 0) {
                        got |= role;
                        rest.remove(c);
                        break;
                    }
                }
            }
            if (Integer.bitCount(got) > Integer.bitCount(best)) {
                best = got;
            }
        }
        return best;
    }

    /**
     * Rule 273 (tester 09/11 13:24, Pod: Worldly Tutor took a second Kitchen Finks with Kitchen Finks and Viscera
     * Seer in hand, "Melira is the missing piece of the combo"). A tutor to hand or to the top of the library looks at
     * our hand and battlefield: when two of the three pieces of the persist loop are there (shield, persist creature,
     * free sacrifice outlet), it fetches the third. Read on the scripts (static / replacement, keyword, cost).
     */
    public static Card chooseMissingPiece(final Player ai, final CardCollectionView fetchList) {
        if (!NewAi.on(ai, 273)) {
            return null;
        }
        final CardCollection pool = new CardCollection(ai.getCardsIn(ZoneType.Hand));
        pool.addAll(ai.getCardsIn(ZoneType.Battlefield));
        final int have = filled(pool);
        if (Integer.bitCount(have) != 2) {
            return null;
        }
        for (final Card c : fetchList) {
            if (c.getOwner() != ai || !c.isInZone(ZoneType.Library)) {
                continue;
            }
            if (c.getType().isLegendary() && ai.isCardInPlay(c.getName())) {
                continue;
            }
            pool.add(c);
            final boolean done = filled(pool) == 7;
            pool.remove(c);
            if (done) {
                return c;
            }
        }
        return null;
    }
}
