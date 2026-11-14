package forge.ai;

import java.util.Map;
import com.google.common.collect.MapMaker;

import forge.game.GameEntity;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostRemoveCounter;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 149, damage-token-counter loop (combo relue « vraie » Druid's Call + Rosie Cotton of South Lane +
 * Walking Ballista / Triskelion, puzzle MT25). A "remove a +1/+1 counter: deal N damage to any target" ability, a creature
 * of ours that makes tokens when it is dealt damage (Druid's Call on it), and a "whenever you create a token, put a +1/+1
 * counter on target creature you control" refund: shooting that creature costs nothing (the counter comes back) and
 * makes a token each time, until the damage would kill it. Finite (the damage stays until cleanup), so it is run where
 * the tokens cost nothing: our second main phase, or the opponent's end step (tokens ready to attack on our turn).
 * The refund trigger puts its counter back on the shooter. Generic: read from the scripts, no card named.
 * Guards: the target must survive the shot (and, when it is the shooter, the counter paid), the token count must grow,
 * at most 200 shots a turn; never when the shooter's counters already kill an opponent.
 * Rule 150 (Darien, King of Kjeldor + Rosie + Walking Ballista, combo relue « vraie », MT26): the same loop on ourselves
 * when "whenever you're dealt damage, create that many tokens" is ours; each shot costs 1 life, so it stops at a reserve
 * of 10 life plus the power of the opponents' creatures. Creatures first: they cost no life.
 */
public final class DamageTokenLoopAi {
    private DamageTokenLoopAi() { }

    private static final int MAX_PER_TURN = 200;
    /** per player: turn, shots this turn, our token count at the last shot */
    private static final Map<Player, int[]> STATE = new MapMaker().weakKeys().makeMap(); // magic-tools: identity keys (players of two games share ids), thread-safe
    /** per player: the shooter waiting for its counter back */
    private static final Map<Player, Card> PENDING = new MapMaker().weakKeys().makeMap(); // magic-tools: identity keys (players of two games share ids), thread-safe

    /** "remove one +1/+1 counter" as the whole cost; else null */
    static CostRemoveCounter p1p1Cost(final SpellAbility sa) {
        final Cost cost = sa.getPayCosts();
        if (cost == null || cost.getCostParts().size() != 1) {
            return null;
        }
        final CostPart part = cost.getCostParts().get(0);
        if (!(part instanceof CostRemoveCounter) || !((CostRemoveCounter) part).counter.is(CounterEnumType.P1P1)
                || !"1".equals(part.getAmount())) {
            return null;
        }
        return (CostRemoveCounter) part;
    }

    /** a "whenever you create a token, put a +1/+1 counter on target creature you control" that can reach the shooter */
    static boolean hasRefund(final Player ai, final Card shooter) {
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : c.getTriggers()) {
                if (t.getMode() != TriggerType.TokenCreated) {
                    continue;
                }
                final SpellAbility eff = t.ensureAbility();
                if (isRefund(ai, eff) && eff.canTarget(shooter)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isRefund(final Player ai, final SpellAbility eff) {
        if (eff == null || eff.getApi() != ApiType.PutCounter || !eff.usesTargeting()
                || !"P1P1".equals(eff.getParam("CounterType"))) {
            return false;
        }
        eff.setActivatingPlayer(ai);
        return true;
    }

    /** rule 150: life we keep when shooting ourselves */
    static int lifeReserve(final Player ai) {
        int power = 0;
        for (final Player o : ai.getOpponents()) {
            for (final Card c : o.getCreaturesInPlay()) {
                power += Math.max(0, c.getNetCombatDamage());
            }
        }
        return 10 + power;
    }

    /** our creature (or we) dealt damage makes tokens for us (Druid's Call on it, its own trigger, Darien) */
    static boolean damageMakesTokens(final Player ai, final GameEntity target) {
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : c.getTriggers()) {
                if ((t.getMode() != TriggerType.DamageDoneOnce && t.getMode() != TriggerType.DamageDone)
                        || !t.hasParam("ValidTarget") || !t.matchesValidParam("ValidTarget", target, c)) {
                    continue;
                }
                final SpellAbility eff = t.ensureAbility();
                if (eff != null && eff.getApi() == ApiType.Token) {
                    return true;
                }
            }
        }
        return false;
    }

    static int tokens(final Player ai) {
        int n = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c.isToken()) {
                n++;
            }
        }
        return n;
    }

    /** the creature (or ourselves, rule 150) to shoot now, or null */
    static GameEntity loopTarget(final Player ai, final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.DealDamage || !sa.isActivatedAbility() || !sa.usesTargeting()
                || sa.getSubAbility() != null || p1p1Cost(sa) == null) {
            return null;
        }
        final Card shooter = sa.getHostCard();
        if (shooter == null || !shooter.isCreature() || shooter.getController() != ai
                || shooter.getCounters(CounterEnumType.P1P1) < 1 || !ai.getGame().getStack().isEmpty()) {
            return null;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        final boolean ourMain2 = ph.isPlayerTurn(ai) && ph.is(PhaseType.MAIN2);
        final boolean theirEnd = !ph.isPlayerTurn(ai) && ph.is(PhaseType.END_OF_TURN);
        if (!ourMain2 && !theirEnd) {
            return null;
        }
        final int dmg = AbilityUtils.calculateAmount(shooter, sa.getParamOrDefault("NumDmg", "1"), sa);
        if (dmg < 1) {
            return null;
        }
        // the shooter's counters kill an opponent: the usual logic shoots the face
        for (final Player o : ai.getOpponents()) {
            if (o.canLoseLife() && o.getLife() <= dmg * shooter.getCounters(CounterEnumType.P1P1)) {
                return null;
            }
        }
        sa.setActivatingPlayer(ai);
        if (!hasRefund(ai, shooter)) {
            return null;
        }
        final int[] st = STATE.get(ai);
        if (st != null && st[0] == ph.getTurn() && (st[1] >= MAX_PER_TURN || tokens(ai) <= st[2])) {
            return null;
        }
        Card best = null;
        for (final Card c : ai.getCreaturesInPlay()) {
            if (!sa.canTarget(c) || !damageMakesTokens(ai, c)) {
                continue;
            }
            final int dealt = ComputerUtilCombat.predictDamageTo(c, dmg, shooter, false);
            if (dealt < 1) {
                continue;
            }
            final int toughness = c.getNetToughness() - (c == shooter ? 1 : 0);
            final boolean survives = toughness > 0
                    && (c.hasKeyword(Keyword.INDESTRUCTIBLE) || c.getDamage() + dealt < toughness);
            if (!survives) {
                continue;
            }
            // the shooter last: its own toughness is the loop's fuel
            if (best == null || best == shooter) {
                best = c;
            }
        }
        if (best == null && shooter.getNetToughness() > 1 && sa.canTarget(ai) && damageMakesTokens(ai, ai)
                && ai.canLoseLife()) {
            final int dealt = ComputerUtilCombat.predictDamageTo(ai, dmg, shooter, false);
            if (dealt >= 1 && ai.getLife() - dealt >= lifeReserve(ai)) {
                return ai;
            }
        }
        return best;
    }

    /** Called by AiController before the usual decision: shoot our token-making creature now. */
    public static boolean shouldLoop(final Player ai, final SpellAbility sa) {
        final GameEntity target = loopTarget(ai, sa);
        if (target == null) {
            return false;
        }
        sa.resetTargets();
        sa.getTargets().add(target);
        final int turn = ai.getGame().getPhaseHandler().getTurn();
        int[] st = STATE.get(ai);
        if (st == null || st[0] != turn) {
            st = new int[] {turn, 0, -1};
            STATE.put(ai, st);
        }
        st[1]++;
        st[2] = tokens(ai);
        PENDING.put(ai, sa.getHostCard());
        return true;
    }

    /** Called by CountersPutAi on a token-created refund: the counter goes back on the shooter. */
    public static Card refundTarget(final Player ai, final SpellAbility sa) {
        final Card shooter = PENDING.get(ai);
        if (shooter == null || !sa.usesTargeting() || !"P1P1".equals(sa.getParam("CounterType"))) {
            return null;
        }
        final Card live = ai.getGame().getCardState(shooter);
        if (live == null || !live.isInPlay() || live.getController() != ai || !sa.canTarget(live)) {
            PENDING.remove(ai);
            return null;
        }
        PENDING.remove(ai);
        return live;
    }
}
