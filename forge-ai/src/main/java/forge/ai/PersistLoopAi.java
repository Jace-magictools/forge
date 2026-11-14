package forge.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.card.CounterEnumType;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostSacrifice;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: free sacrifice outlet (Viscera Seer, Nantuko Husk, Bloodthrone Vampire) + persist creature that cannot
 * get -1/-1 counters (Melira, Sylvok Outcast) + damage when it enters (Murderous Redcap): endless damage. The AI keeps
 * sacrificing that creature, whatever the outlet does, as long as the opponent can lose life.
 * The loop stops when a full turn of it does not lower the opponent's life (Soul Warden, Auriok Champion gain it back):
 * otherwise the game runs until the time limit.
 */
public final class PersistLoopAi {
    private PersistLoopAi() { }

    /** Per game and AI player id: turn, game timestamp of the loop card, opponent's life when that copy entered the loop. */
    private static final Map<Game, Map<Integer, long[]>> LAST = new WeakHashMap<>();

    /** The outlet's cost is exactly "sacrifice one creature" (not itself), with no target to choose. */
    static CostSacrifice freeSacCost(final SpellAbility sa) {
        if (sa == null || !sa.isActivatedAbility() || sa.usesTargeting()) {
            return null;
        }
        final Cost cost = sa.getPayCosts();
        if (cost == null) {
            return null;
        }
        CostSacrifice sac = null;
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostSacrifice cs && !cs.payCostFromSource() && "1".equals(cs.getAmount())) {
                sac = cs;
            } else {
                return null;
            }
        }
        return sac;
    }

    static boolean dealsDamageOnEnter(final Card c) {
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))
                    || !"Card.Self".equals(t.getParam("ValidCard"))) {
                continue;
            }
            final SpellAbility ab = t.ensureAbility();
            if (ab != null && ab.getApi() == ApiType.DealDamage && ab.usesTargeting()
                    && ab.getTargetRestrictions().canTgtPlayer() && c.getNetPower() > 0) {
                return true;
            }
            // Rule 144: "target opponent loses N life" (Geralf's Messenger) drains just as well as damage.
            if (ab != null && ab.getApi() == ApiType.LoseLife
                    && (ab.usesTargeting() ? ab.getTargetRestrictions().canTgtPlayer()
                            : ab.getParamOrDefault("Defined", "").contains("Opponent"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rule 143: persist brings the creature back with no -1/-1 counter, so it can die again. True when it cannot get
     * -1/-1 counters at all (Melira, static) or when a permanent of its controller replaces "N -1/-1 counters" with
     * "N minus one" on it (Vizier of Remedies, replacement effect: the single persist counter becomes none). Read on
     * the script's shape (AddCounter replacement, M1M1, ReplaceCounter with an amount "minus"), not on card names.
     * Puzzle MT13 (Redcap + Vizier + Cartel Aristocrat, a combo reviewed true by tester): forge-v182 attacked into
     * walls instead of looping.
     */
    static boolean persistsClean(final Player ai, final Card c) {
        if (!c.canReceiveCounters(CounterEnumType.M1M1)) {
            return true;
        }
        for (final Card host : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final ReplacementEffect re : host.getReplacementEffects()) {
                if (re.getMode() != ReplacementType.AddCounter || !"M1M1".equals(re.getParam("ValidCounterType"))
                        || !re.hasParam("ValidCard")
                        || !c.isValid(re.getParam("ValidCard").split(","), host.getController(), host, null)) {
                    continue;
                }
                final SpellAbility rsa = re.ensureAbility();
                if (rsa == null || rsa.getApi() != ApiType.ReplaceCounter) {
                    continue;
                }
                final String amount = rsa.getParamOrDefault("Amount", "");
                final String x = host.getSVar(amount);
                if ("0".equals(amount) || (x != null && x.contains("CounterNum/Minus."))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Rule 144: the creature comes back from the graveyard able to die again. Persist with no -1/-1 counter and
     * {@link #persistsClean}; or undying with no +1/+1 counter on a creature that cannot get +1/+1 counters (Solemnity,
     * static CantPutCounter). Puzzle MT14 (Geralf's Messenger + Solemnity + Cartel Aristocrat, a combo reviewed true):
     * forge-v183 attacked into walls instead of looping.
     */
    static boolean loopsClean(final Player ai, final Card c) {
        if (c.hasKeyword(Keyword.PERSIST) && c.getCounters(CounterEnumType.M1M1) == 0
                && (persistsClean(ai, c) || reloadedOnReturn(ai, c))) {
            return true;
        }
        return c.hasKeyword(Keyword.UNDYING) && c.getCounters(CounterEnumType.P1P1) == 0
                && !c.canReceiveCounters(CounterEnumType.P1P1);
    }

    /**
     * Rule 211: blocking costs this creature nothing it cannot afford, it comes back after the block. Persist with no
     * -1/-1 counter and enough toughness to survive the counter, or a clean loop; undying with no +1/+1 counter.
     */
    public static boolean comesBackFromBlock(final Player ai, final Card c) {
        if (c.hasKeyword(Keyword.UNDYING) && c.getCounters(CounterEnumType.P1P1) == 0) {
            return true;
        }
        if (c.hasKeyword(Keyword.PERSIST) && c.getCounters(CounterEnumType.M1M1) == 0
                && (c.getNetToughness() >= 2 || loopsClean(ai, c))) {
            return true;
        }
        return returnedOnDeath(ai, c);
    }

    /**
     * Rule 242 (tester 06/11, a test game : « bloquer avec Experte => elle revient grace a la phalene »):
     * one of our permanents brings the creature back when it dies, read on the trigger's shape and not on the name:
     * dies trigger whose ability returns the card itself to the battlefield (Luminous Broodmoth) or makes a token copy
     * of it (Nightmare Shepherd, Hofri). A token never comes back; a shrinking copy (SetPower) only for a small creature,
     * a 5/5 does not become a 1/1 for a block. Paid effects are out.
     */
    static boolean returnedOnDeath(final Player ai, final Card c) {
        if (c.isToken()) {
            return false;
        }
        final Game game = ai.getGame();
        for (final Card host : ai.getCardsIn(ZoneType.Battlefield)) {
            if (host.equals(c)) {
                continue; // its own persist or undying is a trigger of this shape too, read by its keyword instead
            }
            for (final Trigger t : host.getTriggers()) {
                if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Origin"))
                        || !"Graveyard".equals(t.getParam("Destination")) || t.hasParam("ActivationLimit")
                        || !t.requirementsCheck(game)) {
                    continue;
                }
                final String valid = t.getParam("ValidCard");
                if (valid != null && c.isValid(valid.split(","), ai, host, null) && bringsItBack(t.ensureAbility(), c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean bringsItBack(final SpellAbility first, final Card c) {
        for (SpellAbility ab = first; ab != null; ab = ab.getSubAbility()) {
            if (ab.hasParam("UnlessCost") || ab.hasParam("Cost")) {
                return false;
            }
            final String defined = ab.getParamOrDefault("Defined", "");
            if (!defined.startsWith("Triggered")) {
                continue;
            }
            if (ab.getApi() == ApiType.ChangeZone && "Battlefield".equals(ab.getParam("Destination"))) {
                // back as something else (Enduring cycle: an enchantment; Ojer gods: a land), or only on a condition
                return !ab.hasParam("Transformed") && !ab.hasParam("AnimateSubAbility") && !ab.hasParam("StaticEffect")
                        && !ab.hasParam("ConditionPresent") && !ab.hasParam("ConditionCheckSVar");
            }
            if (ab.getApi() == ApiType.CopyPermanent) {
                return !ab.hasParam("SetPower") || c.getNetPower() <= 2;
            }
        }
        return false;
    }

    /**
     * Rule 209: a permanent of its controller puts a +1/+1 counter on the persist creature each time it comes back, and
     * that counter cancels the -1/-1 one (Thran Vigil, Cathars' Crusade, Juniper Order Ranger). Read on the trigger's
     * shape: the creature entering or leaving the graveyard, free PutCounter P1P1 that can reach it. D125, tester 05/11
     * (Thran Vigil + Murderous Redcap + Viscera Seer): the AI sacrificed to Birthing Pod instead of looping.
     */
    static boolean reloadedOnReturn(final Player ai, final Card c) {
        if (!c.canReceiveCounters(CounterEnumType.P1P1)) {
            return false;
        }
        final Game game = ai.getGame();
        for (final Card host : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : host.getTriggers()) {
                if ((t.getMode() == TriggerType.ChangesZone || t.getMode() == TriggerType.ChangesZoneAll)
                        && !t.hasParam("ActivationLimit") && t.requirementsCheck(game)
                        && firesOnReturn(t, c, host) && reaches(t.ensureAbility(), c, host)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The trigger fires when c comes back from the graveyard to the battlefield. */
    private static boolean firesOnReturn(final Trigger t, final Card c, final Card host) {
        final String origin = t.getParamOrDefault("Origin", "Any");
        final String destination = t.getParamOrDefault("Destination", "Any");
        if (!("Any".equals(origin) || origin.contains("Graveyard"))
                || !("Any".equals(destination) || destination.contains("Battlefield"))
                || ("Any".equals(destination) && !origin.contains("Graveyard"))) {
            return false;
        }
        final String valid = t.hasParam("ValidCard") ? t.getParam("ValidCard") : t.getParam("ValidCards");
        return valid != null && c.isValid(valid.split(","), host.getController(), host, null);
    }

    /** The ability is a free +1/+1 counter that can land on c. */
    private static boolean reaches(final SpellAbility ab, final Card c, final Card host) {
        if (ab == null || !"P1P1".equals(ab.getParam("CounterType")) || ab.isActivatedAbility()) {
            return false;
        }
        if (ab.getApi() == ApiType.PutCounterAll) {
            return ab.hasParam("ValidCards")
                    && c.isValid(ab.getParam("ValidCards").split(","), host.getController(), host, ab);
        }
        if (ab.getApi() != ApiType.PutCounter) {
            return false;
        }
        if (ab.usesTargeting()) {
            return c.isValid(ab.getTargetRestrictions().getValidTgts(), host.getController(), host, ab);
        }
        final String defined = ab.getParamOrDefault("Defined", "Self");
        return defined.contains("TriggeredCard") || (defined.contains("Self") && host == c);
    }

    /**
     * Rule 208: where a +1/+1 counter does the most. On a persist creature with its single -1/-1 counter, the two cancel
     * and persist works again; one that does something on entering first (the loop's pieces). Null when there is none.
     * D124, tester 05/11: Thran Vigil went on a Safehold Elite with no counter, not on the Kitchen Finks just returned.
     */
    public static Card reloadTarget(final Iterable<Card> options) {
        final CardCollection persist = CardLists.filter(options,
                c -> c.isCreature() && c.hasKeyword(Keyword.PERSIST) && c.getCounters(CounterEnumType.M1M1) == 1
                        && c.canReceiveCounters(CounterEnumType.P1P1));
        if (persist.isEmpty()) {
            return null;
        }
        final CardCollection damage = CardLists.filter(persist, PersistLoopAi::dealsDamageOnEnter);
        if (!damage.isEmpty()) {
            return ComputerUtilCard.getBestCreatureAI(damage);
        }
        final CardCollection life = CardLists.filter(persist, PersistLoopAi::gainsLifeOnEnter);
        return ComputerUtilCard.getBestCreatureAI(life.isEmpty() ? persist : life);
    }

    /** The creature to sacrifice for the loop, or null when there is no winning loop with this ability. */
    public static Card loopCard(final Player ai, final SpellAbility sa) {
        final CostSacrifice sac = freeSacCost(sa);
        if (sac == null) {
            return null;
        }
        final Player opp = ai.getWeakestOpponent();
        if (opp == null || !opp.canLoseLife() || opp.cantLoseForZeroOrLessLife()) {
            return null;
        }
        final Card source = sa.getHostCard();
        CardCollection fodder = CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield), sac.getType().split(";"), ai, source, sa);
        fodder = CardLists.filter(fodder, CardPredicates.canBeSacrificedBy(sa, false));
        for (final Card c : fodder) {
            if (c != source && !c.isToken() && c.getOwner() == ai && loopsClean(ai, c)
                    && dealsDamageOnEnter(c)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Altar of Dementia + Melira + Kitchen Finks: the outlet mills an opponent by the sacrificed creature's power and the
     * persist creature comes back each time. The creature to sacrifice, or null. Each activation empties the library a
     * bit more, so the loop always ends: no runaway check needed.
     */
    /**
     * magic-tools: cards our mill abilities already on the stack will take from this player's library.
     * Without it, the loop kept stacking Altar of Dementia activations (the persist trigger resolves
     * on top, the Altar abilities never do) until the stack passed 999 objects and Forge called a draw.
     */
    public static int pendingMill(final Player ai, final Player target) {
        int total = 0;
        for (final SpellAbilityStackInstance si : ai.getGame().getStack()) {
            final SpellAbility s = si.getSpellAbility();
            if (s == null || s.getApi() != ApiType.Mill || si.getActivatingPlayer() != ai
                    || si.getTargetChoices() == null) {
                continue;
            }
            for (final Player p : si.getTargetChoices().getTargetPlayers()) {
                if (p == target) {
                    int n = 1;
                    if (s.hasParam("NumCards")) {
                        try {
                            n = AbilityUtils.calculateAmount(s.getHostCard(), s.getParam("NumCards"), s);
                        } catch (RuntimeException e) {
                            n = 1;
                        }
                    }
                    total += Math.max(1, n);
                }
            }
        }
        return total;
    }

    public static Card millLoopCard(final Player ai, final SpellAbility sa) {
        if (sa == null || !sa.isActivatedAbility() || sa.getApi() != ApiType.Mill || !sa.usesTargeting()) {
            return null;
        }
        final Cost cost = sa.getPayCosts();
        if (cost == null) {
            return null;
        }
        CostSacrifice sac = null;
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostSacrifice cs && !cs.payCostFromSource() && "1".equals(cs.getAmount())) {
                sac = cs;
            } else {
                return null;
            }
        }
        if (sac == null) {
            return null;
        }
        boolean target = false;
        for (final Player o : ai.getOpponents()) {
            if (sa.canTarget(o) && o.getCardsIn(ZoneType.Library).size() > pendingMill(ai, o)) {
                target = true;
            }
        }
        if (!target) {
            return null;
        }
        final Card source = sa.getHostCard();
        CardCollection fodder = CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield), sac.getType().split(";"), ai, source, sa);
        fodder = CardLists.filter(fodder, CardPredicates.canBeSacrificedBy(sa, false));
        for (final Card c : fodder) {
            if (c != source && !c.isToken() && c.getOwner() == ai && loopsClean(ai, c)
                    && c.getNetPower() > 0) {
                return c;
            }
        }
        return null;
    }

    /**
     * Rule 134: the life the AI loops up to with Melira + Kitchen Finks (each turn, as long as each loop gains it).
     * Tournament v20, Pod: Melira + Finks + Bloodthrone Vampire on the battlefield for 10 turns, never looped.
     */
    public static final int LIFE_GOAL = 60;

    static boolean gainsLifeOnEnter(final Card c) {
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))
                    || !"Card.Self".equals(t.getParam("ValidCard"))) {
                continue;
            }
            final SpellAbility ab = t.ensureAbility();
            if (ab != null && ab.getApi() == ApiType.GainLife && !ab.usesTargeting()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rule 134: free outlet + persist creature that cannot get -1/-1 counters and gains life when it enters (Kitchen
     * Finks + Melira): the creature to sacrifice while the AI is under {@link #LIFE_GOAL}, or null.
     */
    public static Card lifeLoopCard(final Player ai, final SpellAbility sa) {
        final CostSacrifice sac = freeSacCost(sa);
        if (sac == null || ai.getLife() >= LIFE_GOAL || !ai.canGainLife()) {
            return null;
        }
        final Card source = sa.getHostCard();
        CardCollection fodder = CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield), sac.getType().split(";"), ai, source, sa);
        fodder = CardLists.filter(fodder, CardPredicates.canBeSacrificedBy(sa, false));
        for (final Card c : fodder) {
            if (c != source && !c.isToken() && c.getOwner() == ai && loopsClean(ai, c)
                    && gainsLifeOnEnter(c)) {
                return c;
            }
        }
        return null;
    }

    /** Any winning loop with this ability (damage, mill, life): the creature to sacrifice, or null. */
    public static Card anyLoopCard(final Player ai, final SpellAbility sa) {
        Card c = loopCard(ai, sa);
        if (c == null) {
            c = millLoopCard(ai, sa);
        }
        if (c == null) {
            c = lifeLoopCard(ai, sa);
        }
        return c;
    }

    /** Activate the outlet now: the loop is on and nothing is waiting on the stack. */
    public static boolean shouldLoop(final Player ai, final SpellAbility sa) {
        if (!ai.getGame().getStack().isEmpty()) {
            return false;
        }
        final Card loop = loopCard(ai, sa);
        if (loop != null) {
            return makesProgress(ai, loop);
        }
        final Card life = lifeLoopCard(ai, sa);
        return life != null && gainsProgress(ai, life);
    }

    /** Per game and AI player id: turn, timestamp of the life loop card, AI life when that copy entered the loop. */
    private static final Map<Game, Map<Integer, long[]>> LAST_LIFE = new WeakHashMap<>();

    /** False once the life loop card came back in the same turn without the AI gaining life (Sulfuric Vortex). */
    private static boolean gainsProgress(final Player ai, final Card loop) {
        final int turn = ai.getGame().getPhaseHandler().getTurn();
        final long life = ai.getLife();
        synchronized (LAST_LIFE) {
            final Map<Integer, long[]> byPlayer = LAST_LIFE.computeIfAbsent(ai.getGame(), g -> new HashMap<>());
            final long[] prev = byPlayer.get(ai.getId());
            if (prev != null && prev[0] == turn) {
                if (prev[1] == loop.getGameTimestamp()) {
                    return true;
                }
                if (life <= prev[2]) {
                    return false;
                }
            }
            byPlayer.put(ai.getId(), new long[] {turn, loop.getGameTimestamp(), life});
            return true;
        }
    }

    /**
     * Rule 295 (tournoi v28, Pod contre Ajani, tours 26 a 34 : Melira + Murderous Redcap + Bloodthrone Vampire en jeu,
     * le Redcap ne meurt qu'une fois par tour). The Redcap's damage goes to a creature, the opponent's life does not
     * move and the loop was judged dead after one turn of it. Progress counts the opponent's life plus the damage
     * his creatures can still take: killing or wounding a creature is progress too, the next loops go to the face.
     * Lifegain (Auriok Champion) or a creature regenerated / indestructible with no toughness left still stops it.
     */
    static long opponentStanding(final Player ai, final Player opp) {
        long left = opp.getLife();
        if (NewAi.on(ai, 295)) {
            for (final Card c : opp.getCreaturesInPlay()) {
                left += Math.max(0, c.getLethalDamage());
            }
        }
        return left;
    }

    /** False once the loop card came back (new timestamp) in the same turn without the opponent losing ground. */
    private static boolean makesProgress(final Player ai, final Card loop) {
        final int turn = ai.getGame().getPhaseHandler().getTurn();
        final long life = opponentStanding(ai, ai.getWeakestOpponent());
        synchronized (LAST) {
            final Map<Integer, long[]> byPlayer = LAST.computeIfAbsent(ai.getGame(), g -> new HashMap<>());
            final long[] prev = byPlayer.get(ai.getId());
            if (prev != null && prev[0] == turn) {
                if (prev[1] == loop.getGameTimestamp()) {
                    return true; // same copy, evaluated again before it was sacrificed
                }
                if (life >= prev[2]) {
                    return false;
                }
            }
            byPlayer.put(ai.getId(), new long[] {turn, loop.getGameTimestamp(), life});
            return true;
        }
    }
}
