package forge.ai;

import java.util.List;
import java.util.Map;

import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.cost.CostPart;
import forge.game.cost.CostSacrifice;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementLayer;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 314 (D178, tester 13/11, a test game). An opponent who already meets a loss
 * condition (life at 0 or less, 10 poison counters) and only survives through one permanent "you can't lose the game"
 * (Platinum Angel on their side, Abyssal Persecutor on ours…) loses as soon as that permanent leaves. Any single-target removal that takes it away (destroy,
 * lethal damage, exile, bounce, tuck) is played before anything else, on that permanent, whatever it costs (the AI
 * held Stadium Headliner for four turns: the stock DamageDealAi refused the self-sacrifice and picked other targets).
 */
public final class UnlockWin314Ai {
    private UnlockWin314Ai() { }

    /** The one permanent that keeps an opponent of ai alive, or null. */
    public static Card lock(final Player ai) {
        if (ai == null || !NewAi.on(ai, 314)) {
            return null;
        }
        for (final Player opp : ai.getOpponents()) {
            if (opp.hasLost() || !(opp.getLife() <= 0 && opp.canLoseLife() || opp.getPoisonCounters() >= 10)) {
                continue;
            }
            if (!opp.cantLose()) {
                continue;
            }
            Card found = null;
            int count = 0;
            for (final Card c : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
                if (keepsAlive(c, opp)) {
                    found = c;
                    count++;
                }
            }
            // an effect (Angel's Grace) or two locks: removing one permanent does not win
            if (count == 1 && opp.getGame().getCardsIn(ZoneType.Command).stream().noneMatch(c -> keepsAlive(c, opp))) {
                return found;
            }
        }
        return null;
    }

    private static boolean keepsAlive(final Card c, final Player opp) {
        // "you can't lose" on their side (Platinum Angel) or "your opponents can't lose" on ours (Abyssal Persecutor)
        final Map<AbilityKey, Object> params = AbilityKey.mapFromAffected(opp);
        params.put(AbilityKey.LoseReason, null);
        for (final ReplacementEffect re : c.getReplacementEffects()) {
            if (re.getMode() == ReplacementType.GameLoss && re.getLayer() == ReplacementLayer.CantHappen
                    && !re.isSuppressed() && re.canReplace(params)) {
                return true;
            }
        }
        return false;
    }

    /** Sets sa on the lock permanent when it takes it off the battlefield; true when sa wins that way. */
    public static boolean shouldPlay(final Player ai, final SpellAbility sa) {
        final Card lock = lock(ai);
        if (lock == null || !removes(ai, sa, lock)) {
            return false;
        }
        sa.resetTargets();
        sa.getTargets().add(lock);
        return true;
    }

    static boolean removes(final Player ai, final SpellAbility sa, final Card lock) {
        if (sa == null || sa.getApi() == null || !sa.usesTargeting() || !sa.canTarget(lock)
                || sa.getTargetRestrictions().getMinTargets(sa.getHostCard(), sa) > 1) {
            return false;
        }
        final ApiType api = sa.getApi();
        if (api == ApiType.Destroy) {
            return lock.canBeDestroyed() && !sa.hasParam("Defined")
                    && (sa.hasParam("NoRegen") || lock.getShieldCount() == 0)
                    && lock.getCounters(CounterEnumType.SHIELD) == 0;
        }
        if (api == ApiType.ChangeZone) {
            final String origin = sa.getParamOrDefault("Origin", "");
            final String dest = sa.getParamOrDefault("Destination", "");
            return origin.contains("Battlefield") && !dest.isEmpty() && !dest.contains("Battlefield")
                    && !sa.hasParam("Duration") && !sa.hasParam("AtEOT") && sa.getSubAbility() == null;
        }
        if (api == ApiType.DealDamage && lock.isCreature() && sa.hasParam("NumDmg") && !sa.hasParam("DividedAsYouChoose")) {
            final Card host = sa.getHostCard();
            int dmg = AbilityUtils.calculateAmount(host, sa.getParam("NumDmg"), sa);
            // the count read now still includes the creature sacrificed to pay (Stadium Headliner: creatures you control)
            if (host.isCreature() && sacrificesHost(sa)) {
                dmg--;
            }
            return dmg > 0 && ComputerUtilCombat.getEnoughDamageToKill(lock, dmg, host, false) <= dmg;
        }
        return false;
    }

    private static boolean sacrificesHost(final SpellAbility sa) {
        if (sa.getPayCosts() == null) {
            return false;
        }
        for (final CostPart part : sa.getPayCosts().getCostParts()) {
            if (part instanceof CostSacrifice cs && cs.payCostFromSource()) {
                return true;
            }
        }
        return false;
    }

    /** The removal of the lock goes before every other spell or ability. */
    public static void prioritize(final Player ai, final List<SpellAbility> all) {
        final Card lock = lock(ai);
        if (lock == null) {
            return;
        }
        for (int i = 1; i < all.size(); i++) {
            final SpellAbility sa = all.get(i);
            sa.setActivatingPlayer(ai);
            if (removes(ai, sa, lock)) {
                all.add(0, all.remove(i));
                return;
            }
        }
    }
}
