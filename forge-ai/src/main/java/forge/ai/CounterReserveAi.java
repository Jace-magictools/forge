package forge.ai;

import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 22, keep the mana of a counterspell for the opponent's turn. Stock AI reserved mana only for
 * its own main 2 and for blockers: Transfuge (14 counterspells) cast its cheap creatures on its turn and had nothing
 * left to counter with. On our turn, a spell that would leave less than the cheapest counterspell in hand is not
 * cast, unless it is one of the big plays of the deck (tester, 25/10: "it keeps them in hand, unless Control
 * Magic, Agent of Treachery or Thassa": mana value 4 or more, or gaining control), it makes mana, it imprints a card
 * from hand (Isochron Scepter takes the counterspell with it), or the hand is full.
 * Rule 117: same for an instant-speed creature removal (Doom Blade, Swords to Plowshares) when no opponent has a
 * creature to aim it at yet but one already lost a creature this game: the next creature comes on their turn, and a
 * tapped-out AI let it attack before it could answer. A removal that can hit players (Lightning Bolt) is not kept.
 * Rule 248 (D142): an X counterspell needs the mana of X = 1 at least before it holds anything back.
 * Rule 297 (merfolk, v335 report: 27 turns "keeps Hindering Light / Rebuff the Wicked" in 48 games): a
 * counterspell that only counters spells aimed at us or our permanents protects what is on the battlefield; it never
 * holds back a permanent (the permanent is what it would protect), nor anything while we control no nonland permanent.
 */
public final class CounterReserveAi {
    private CounterReserveAi() { }

    /** Mana value from which a spell is worth tapping out for. */
    static final int TAP_OUT_CMC = 4;

    public static boolean holdsForCounter(final Player ai, final SpellAbility sa) {
        return counterKept(ai, sa) != null;
    }

    /** The counterspell whose mana {@code sa} would spend, or null if the spell can be cast. */
    public static Card counterKept(final Player ai, final SpellAbility sa) {
        if (sa == null || !sa.isSpell() || sa.getApi() == ApiType.Counter) {
            return null;
        }
        if (!ai.getGame().getPhaseHandler().isPlayerTurn(ai)) {
            return null;
        }
        final Card host = sa.getHostCard();
        if (host == null || host.isLand() || host.getCMC() >= TAP_OUT_CMC || !host.getManaAbilities().isEmpty()
                || gainsControl(host) || imprintsFromHand(host)) {
            return null;
        }
        if (ai.getCardsIn(ZoneType.Hand).size() >= ai.getMaxHandSize()) {
            return null;
        }
        final int available = ComputerUtilMana.getAvailableManaEstimate(ai, true);
        int reserve = Integer.MAX_VALUE;
        Card kept = null;
        final boolean waitingRemoval = removalWaitsForTarget(ai);
        final boolean deploysFirst = ProactiveDeckAi.deploysFirst(ai, host);
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.equals(host)) {
                continue;
            }
            final ApiType kind = isCounterspell(c) ? ApiType.Counter
                    : waitingRemoval && isCreatureRemoval(c) ? reactiveApi(c) : null;
            if (kind == null) {
                continue;
            }
            // rule 173: Force of Will, Daze... cast without mana once the spell is gone
            if (kind == ApiType.Counter && FreeCounterAi.castsForFree(ai, c, host)) {
                continue;
            }
            // rule 174: an aggressive deck (Infection) puts a creature on the board before it holds counters
            if (kind == ApiType.Counter && deploysFirst) {
                continue;
            }
            // rule 297: a protective counter (Hindering Light) does not hold back the board it would protect
            if (kind == ApiType.Counter && NewAi.on(ai, 297) && protectsOnly(c)
                    && (host.isPermanent() || !controlsNonlandPermanent(ai))) {
                continue;
            }
            // rule 248 (D142, tester 08/11): an X counterspell (Spell Burst, X U) kept with X = 0 counters nothing
            // (no spell of mana value 0 to fear): the reserve pays X = 1 at least, else the mana goes to the Top
            final int need = manaNeeded(c.getManaCost()) + c.getManaCost().countX();
            if (need > 0 && need <= available && need < reserve && payableNow(ai, c, kind)) {
                reserve = need;
                kept = c;
            }
        }
        if (kept == null) {
            return null;
        }
        final int cost = sa.getPayCosts() != null && sa.getPayCosts().getTotalMana() != null
                ? sa.getPayCosts().getTotalMana().getCMC() : host.getCMC();
        // a spell it cannot pay anyway is not "held" (the trace said Wall of Frost was kept back with 2 lands)
        return cost <= available && available - cost < reserve ? kept : null;
    }

    /** Rule 297: every counter mode of the card only counters spells that target us or our permanents. */
    static boolean protectsOnly(final Card c) {
        boolean any = false;
        for (final SpellAbility s : c.getSpells()) {
            if (s.getApi() != ApiType.Counter) {
                continue;
            }
            if (!s.hasParam("TargetValidTargeting")) {
                return false;
            }
            any = true;
        }
        return any;
    }

    static boolean controlsNonlandPermanent(final Player ai) {
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (!c.isLand()) {
                return true;
            }
        }
        return false;
    }

    static boolean isCounterspell(final Card c) {
        if (c.isLand()) {
            return false;
        }
        for (final SpellAbility s : c.getSpells()) {
            if (s.getApi() == ApiType.Counter) {
                return true;
            }
        }
        return false;
    }

    /**
     * The counterspell can be paid with the lands the AI has now, colours included (grenouille kept two mana for
     * Long River's Pull, U U, with one blue source, for six turns, and never countered anything).
     */
    static boolean payableNow(final Player ai, final Card c) {
        return payableNow(ai, c, ApiType.Counter);
    }

    static boolean payableNow(final Player ai, final Card c, final ApiType api) {
        for (final SpellAbility s : c.getSpells()) {
            if (s.getApi() != api) {
                continue;
            }
            s.setActivatingPlayer(ai);
            if (ComputerUtilMana.canPayManaCost(s, ai, 0, false)) {
                return true;
            }
        }
        return false;
    }

    /** Rule 117: no opponent creature to aim at now, but an opponent already lost one (they do play creatures). */
    static boolean removalWaitsForTarget(final Player ai) {
        boolean lostOne = false;
        for (final Player o : ai.getOpponents()) {
            if (!o.getCreaturesInPlay().isEmpty()) {
                return false;
            }
            for (final Card g : o.getCardsIn(ZoneType.Graveyard)) {
                if (g.isCreature()) {
                    lostOne = true;
                    break;
                }
            }
        }
        return lostOne;
    }

    /** Rule 117: an instant (or flash) spell that destroys, exiles, bounces or burns only creatures it does not own. */
    static boolean isCreatureRemoval(final Card c) {
        return reactiveApi(c) != null;
    }

    static ApiType reactiveApi(final Card c) {
        if (c.isLand() || c.isCreature() || !(c.isInstant() || c.hasKeyword(Keyword.FLASH))) {
            return null;
        }
        for (final SpellAbility s : c.getSpells()) {
            final ApiType api = s.getApi();
            if (api != ApiType.Destroy && api != ApiType.DealDamage && api != ApiType.ChangeZone) {
                continue;
            }
            final String tgts = s.getParamOrDefault("ValidTgts", "");
            if (!tgts.contains("Creature") || tgts.contains("You") || tgts.contains("Player") || tgts.contains("Any")) {
                continue;
            }
            if (api == ApiType.ChangeZone && (!"Battlefield".equals(s.getParam("Origin"))
                    || "Battlefield".equals(s.getParam("Destination")))) {
                continue;
            }
            return api;
        }
        return null;
    }

    /** Mana to keep open: phyrexian symbols can be paid with life (Mental Misstep keeps nothing). */
    static int manaNeeded(final ManaCost cost) {
        int need = cost.getCMC();
        for (final ManaCostShard shard : cost) {
            if (shard.isPhyrexian()) {
                need--;
            }
        }
        return need;
    }

    /** Control Magic (static GainControl) or Agent of Treachery (enters: gain control). */
    static boolean gainsControl(final Card c) {
        for (final StaticAbility st : c.getStaticAbilities()) {
            if (st.hasParam("GainControl")) {
                return true;
            }
        }
        for (final Trigger t : c.getTriggers()) {
            final SpellAbility ex = t.ensureAbility();
            if (ex != null && ex.getApi() == ApiType.GainControl) {
                return true;
            }
        }
        for (final SpellAbility s : c.getSpells()) {
            if (s.getApi() == ApiType.GainControl) {
                return true;
            }
        }
        return false;
    }

    /** Isochron Scepter: enters and exiles (imprints) a card from hand, often the counterspell itself. */
    static boolean imprintsFromHand(final Card c) {
        // read the script, not t.ensureAbility(): building the trigger's ability here changed later AI choices
        for (final Trigger t : c.getTriggers()) {
            final String exec = t.getParam("Execute");
            final String sv = exec == null || !c.hasSVar(exec) ? "" : c.getSVar(exec);
            if (sv.contains("ChangeZone") && sv.contains("Imprint$ True") && sv.contains("Origin$ Hand")) {
                return true;
            }
        }
        return false;
    }
}
