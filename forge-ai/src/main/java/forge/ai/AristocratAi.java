package forge.ai;

import java.util.EnumSet;

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
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 141, a free sacrifice outlet plus "whenever ... is sacrificed / dies" damage or drain is lethal
 * (deck Sacrfice Mutuel: Mayhem Devil + Woe Strider; Blood Artist, Zulaport Cutthroat...). Forge's AI never pays
 * "sacrifice another creature" for a scry, so it attacked into walls with the lethal on the board (puzzle MT10).
 * When the pings of every creature the outlet can eat reach the life of the weakest opponent, the outlet is played,
 * one creature at a time (stack empty, so the count is redone after each wave of triggers), the payoffs last.
 */
public final class AristocratAi {
    private AristocratAi() { }

    // effects of the outlet itself that never hurt us
    private static final EnumSet<ApiType> HARMLESS = EnumSet.of(ApiType.Token, ApiType.DealDamage, ApiType.Pump,
            ApiType.PutCounter, ApiType.Draw, ApiType.GainLife, ApiType.LoseLife, ApiType.Scry, ApiType.Surveil,
            ApiType.Mana);

    /** The sacrifice part of an outlet whose whole cost is "sacrifice one other creature", else null. */
    static CostSacrifice freeSacCost(final SpellAbility sa) {
        if (sa == null || !sa.isActivatedAbility() || sa.getApi() == null || !HARMLESS.contains(sa.getApi())) {
            return null;
        }
        final Cost cost = sa.getPayCosts();
        if (cost == null) {
            return null;
        }
        CostSacrifice found = null;
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostSacrifice sac && !sac.payCostFromSource() && sac.getType().contains("Creature")
                    && "1".equals(sac.getAmount()) && found == null) {
                found = sac;
            } else {
                return null;
            }
        }
        return found;
    }

    /** Life the opponent loses when this creature of ours is sacrificed, from the triggers of our permanents. */
    static int pingsFor(final Player ai, final Card dying) {
        int total = 0;
        for (final Card host : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : host.getTriggers()) {
                total += ping(ai, host, t, dying);
            }
        }
        if (NewAi.on(ai, 294)) {
            total += ownPings(ai, dying);
        }
        return total;
    }

    /**
     * magic-tools: rule 294 (tournoi v28, Pod contre Ajani : Ajani a 1 PV, Murderous Redcap sans marqueur et Bloodthrone
     * Vampire en jeu, l'IA ne sacrifie pas). What the sacrificed creature does by itself: its own "when this dies" damage
     * or drain, and the enter damage it deals when it comes straight back (persist without a -1/-1 counter, undying
     * without a +1/+1 counter, or a return given by another permanent), with the power it comes back with.
     */
    static int ownPings(final Player ai, final Card dying) {
        if (dying.isToken()) {
            return 0;
        }
        int total = 0;
        final boolean persist = dying.hasKeyword(Keyword.PERSIST) && dying.getCounters(CounterEnumType.M1M1) == 0;
        final boolean undying = dying.hasKeyword(Keyword.UNDYING) && dying.getCounters(CounterEnumType.P1P1) == 0;
        final boolean back = persist || undying || PersistLoopAi.returnedOnDeath(ai, dying);
        int power = dying.getNetPower();
        if (persist && !PersistLoopAi.persistsClean(ai, dying)) {
            power -= 1;
        } else if (undying && !persist) {
            power += 1;
        }
        for (final Trigger t : dying.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Card.Self".equals(t.getParam("ValidCard"))
                    || t.hasParam("Condition") || t.hasParam("ActivationLimit")) {
                continue;
            }
            final String dest = t.getParam("Destination");
            final String origin = t.getParamOrDefault("Origin", "Any");
            final boolean dies = "Battlefield".equals(origin) && "Graveyard".equals(dest);
            final boolean enters = back && "Battlefield".equals(dest) && ("Any".equals(origin) || "Graveyard".equals(origin));
            if (!dies && !enters) {
                continue;
            }
            total += faceAmount(dying, t.ensureAbility(), dies ? dying.getNetPower() : power);
        }
        return total;
    }

    /** Life an opponent loses from this unconditional trigger ability (damage to any target / a player, or drain). */
    private static int faceAmount(final Card c, final SpellAbility exec, final int power) {
        if (exec == null || exec.getApi() == null || exec.hasParam("UnlessCost") || exec.hasParam("ConditionPresent")
                || exec.hasParam("ConditionCheckSVar")) {
            return 0;
        }
        final String tgts = exec.getParamOrDefault("ValidTgts", "") + exec.getParamOrDefault("Defined", "");
        final String amount;
        if (exec.getApi() == ApiType.DealDamage) {
            if (!(tgts.contains("Any") || tgts.contains("Player") || tgts.contains("Opponent"))) {
                return 0;
            }
            amount = exec.getParamOrDefault("NumDmg", "");
        } else if (exec.getApi() == ApiType.LoseLife) {
            if (!(tgts.contains("Player") || tgts.contains("Opponent"))) {
                return 0;
            }
            amount = exec.getParamOrDefault("LifeAmount", "");
        } else {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(amount));
        } catch (NumberFormatException e) {
            final String svar = c.hasSVar(amount) ? c.getSVar(amount) : "";
            return "Count$CardPower".equals(svar) ? Math.max(0, power) : 0;
        }
    }

    private static int ping(final Player ai, final Card host, final Trigger t, final Card dying) {
        if (host.equals(dying) || !t.hasParam("ValidCard")) {
            return 0; // the payoff is kept for the end, it is not counted as fodder
        }
        final TriggerType mode = t.getMode();
        if (mode == TriggerType.ChangesZone) {
            if (!"Battlefield".equals(t.getParam("Origin")) || !"Graveyard".equals(t.getParam("Destination"))) {
                return 0;
            }
        } else if (mode != TriggerType.Sacrificed) {
            return 0;
        }
        if (t.hasParam("ValidPlayer") || t.getParam("ValidCard").contains("OppCtrl")
                || !dying.isValid(t.getParam("ValidCard").split(","), ai, host, null)) {
            return 0;
        }
        final SpellAbility exec = t.ensureAbility();
        if (exec == null) {
            return 0;
        }
        final String amount;
        if (exec.getApi() == ApiType.DealDamage) {
            final String tgts = exec.getParamOrDefault("ValidTgts", "") + exec.getParamOrDefault("Defined", "");
            if (!(tgts.contains("Any") || tgts.contains("Player") || tgts.contains("Opponent"))) {
                return 0;
            }
            amount = exec.getParamOrDefault("NumDmg", "");
        } else if (exec.getApi() == ApiType.LoseLife) {
            final String tgts = exec.getParamOrDefault("ValidTgts", "") + exec.getParamOrDefault("Defined", "");
            if (!(tgts.contains("Player") || tgts.contains("Opponent"))) {
                return 0;
            }
            amount = exec.getParamOrDefault("LifeAmount", "");
        } else {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(amount));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Is this card one of our payoffs (it pings when another creature of ours goes)? */
    static boolean isPayoff(final Player ai, final Card c, final CardCollection others) {
        for (final Trigger t : c.getTriggers()) {
            for (final Card o : others) {
                if (!o.equals(c) && ping(ai, c, t, o) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * magic-tools: rule 147, a closed damage loop on our side: "whenever an opponent is dealt noncombat damage, put a
     * +1/+1 counter on X" (Massacre Girl, Most Wanted) and "whenever +1/+1 counters are put on a creature you control,
     * deal that much damage to target opponent" (Shalai and Hallar). Any ping restarts it until the opponent is dead
     * (puzzle MT22). Returns the two pieces of the loop, or null.
     */
    static CardCollection damageLoop(final Player ai) {
        Card counterOnPing = null;
        Card pingOnCounter = null;
        for (final Card host : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : host.getTriggers()) {
                final SpellAbility exec = t.ensureAbility();
                if (exec == null || exec.getApi() == null) {
                    continue;
                }
                if (t.getMode() == TriggerType.DamageDone && counterOnPing == null
                        && t.getParamOrDefault("ValidTarget", "").contains("Opponent")
                        && !"True".equals(t.getParam("CombatDamage"))
                        && exec.getApi() == ApiType.PutCounter && "P1P1".equals(exec.getParam("CounterType"))
                        && host.isCreature()
                        && ("Self".equals(exec.getParam("Defined")) || exec.usesTargeting())) {
                    counterOnPing = host;
                } else if ((t.getMode() == TriggerType.CounterAddedOnce || t.getMode() == TriggerType.CounterAdded)
                        && pingOnCounter == null && "P1P1".equals(t.getParam("CounterType"))
                        && t.getParamOrDefault("ValidCard", "").contains("Creature.YouCtrl")
                        && exec.getApi() == ApiType.DealDamage) {
                    final String tgts = exec.getParamOrDefault("ValidTgts", "") + exec.getParamOrDefault("Defined", "");
                    if (tgts.contains("Opponent") || tgts.contains("Player") || tgts.contains("Any")) {
                        pingOnCounter = host;
                    }
                }
            }
        }
        if (counterOnPing == null || pingOnCounter == null || counterOnPing.equals(pingOnCounter)) {
            return null;
        }
        final CardCollection loop = new CardCollection();
        loop.add(counterOnPing);
        loop.add(pingOnCounter);
        return loop;
    }

    /** Creatures the outlet may eat, payoffs left out. */
    static CardCollection fodder(final Player ai, final SpellAbility sa, final CostSacrifice sac) {
        final Card source = sa.getHostCard();
        final CardCollection mine = new CardCollection(ai.getCreaturesInPlay());
        CardCollection list = CardLists.getValidCards(mine, sac.getType().split(";"), ai, source, sa);
        list = CardLists.filter(list, CardPredicates.canBeSacrificedBy(sa, false));
        final CardCollection loop = damageLoop(ai);
        // rule 294: an outlet that may eat itself (Bloodthrone Vampire, "sacrifice a creature") does not, the plan needs it
        final boolean keepSource = NewAi.on(ai, 294);
        return CardLists.filter(list, c -> !isPayoff(ai, c, mine) && (loop == null || !loop.contains(c))
                && !(keepSource && c.equals(source)));
    }

    /** Tokens left behind by the fodder when it dies (Grim Initiate amasses, a Goat...): more to sacrifice. */
    static int leftBehind(final Player ai, final CardCollection fodder) {
        int n = 0;
        boolean amass = false;
        for (final Card f : fodder) {
            for (final Trigger t : f.getTriggers()) {
                if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Origin"))
                        || !"Graveyard".equals(t.getParam("Destination"))
                        || !"Card.Self".equals(t.getParam("ValidCard"))) {
                    continue;
                }
                final SpellAbility exec = t.ensureAbility();
                if (exec == null) {
                    continue;
                }
                if (exec.getApi() == ApiType.Amass) {
                    amass = true;
                } else if (exec.getApi() == ApiType.Token) {
                    try {
                        n += Integer.parseInt(exec.getParamOrDefault("TokenAmount", "1"));
                    } catch (NumberFormatException e) {
                        // X tokens: not counted
                    }
                }
            }
        }
        if (amass && !ai.getCreaturesInPlay().anyMatch(c -> c.getType().hasCreatureType("Army"))) {
            n++;
        }
        return n;
    }

    /** Rule 141: play this outlet now, the pings of everything it can eat kill the weakest opponent. */
    public static boolean lethalOutlet(final Player ai, final SpellAbility sa) {
        return ai.getGame().getStack().isEmpty() && lethal(ai, sa);
    }

    static boolean lethal(final Player ai, final SpellAbility sa) {
        final CostSacrifice sac = freeSacCost(sa);
        if (sac == null) {
            return false;
        }
        final CardCollection fodder = fodder(ai, sa, sac);
        if (fodder.isEmpty()) {
            return false;
        }
        int total = 0;
        int least = Integer.MAX_VALUE;
        for (final Card f : fodder) {
            final int p = pingsFor(ai, f);
            total += p;
            least = Math.min(least, p);
        }
        if (total <= 0) {
            return false;
        }
        final Player weakest = ai.getWeakestOpponent();
        if (damageLoop(ai) != null && weakest != null && !weakest.cantLose() && weakest.canLoseLife()) {
            return true; // rule 147: one ping is enough, the loop does the rest
        }
        total += leftBehind(ai, fodder) * least;
        final Player opp = ai.getWeakestOpponent();
        return opp != null && !opp.cantLose() && opp.canLoseLife() && total >= opp.getLife();
    }

    /** Which creature pays the outlet while the lethal plan runs: the weakest non-payoff. */
    public static Card chooseFodder(final Player ai, final SpellAbility sa, final CardCollection typeList) {
        if (!lethal(ai, sa)) {
            return null;
        }
        final CardCollection mine = new CardCollection(ai.getCreaturesInPlay());
        final CardCollection loop = damageLoop(ai);
        final boolean keepSource = NewAi.on(ai, 294);
        final CardCollection ok = CardLists.filter(typeList, c -> !isPayoff(ai, c, mine)
                && (loop == null || !loop.contains(c)) && !(keepSource && c.equals(sa.getHostCard())));
        return ok.isEmpty() ? null : ComputerUtilCard.getWorstAI(ok);
    }
}
