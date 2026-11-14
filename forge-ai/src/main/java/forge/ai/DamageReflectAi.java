package forge.ai;

import java.util.List;

import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools, rules 213-214 (tester 03/11, a test game): "whenever this creature is dealt damage, it
 * deals that much damage to target opponent" (Donna Noble, Brash Taunter, Boros Reckoner, Stuffy Doll, Spitemare...).
 * Lumra 17/17 blocked a 2/4 Donna Noble paired with Wayta: 17 twice, and the blocker's controller died from 20.
 * What a blocker deals comes back at its controller. Read from the trigger's script, for every card written that way.
 */
public final class DamageReflectAi {
    private DamageReflectAi() { }

    /** Life left under which a block is not worth what comes back, when it costs more than taking the hit. */
    private static final int LIFE_FLOOR = 4;

    /** How many times one damage dealt to this creature comes back at this player (0: not a reflecting creature). */
    public static int reflectCount(final Card damaged, final Player victim) {
        if (damaged == null || victim == null) {
            return 0;
        }
        int count = 0;
        final Game game = damaged.getGame();
        for (final Card host : damaged.getController().getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : host.getTriggers()) {
                if ((t.getMode() != TriggerType.DamageDone && t.getMode() != TriggerType.DamageDoneOnce)
                        || !t.matchesValidParam("ValidTarget", damaged) || !hitsVictim(t, victim)) {
                    continue;
                }
                count += 1 + extraTriggers(game, t, damaged);
            }
        }
        return count;
    }

    private static boolean hitsVictim(final Trigger t, final Player victim) {
        if (!t.hasParam("Execute") || t.hasParam("ValidSource")) {
            return false;
        }
        final Card host = t.getHostCard();
        final SpellAbility sa = t.ensureAbility();
        if (sa == null || (sa.getApi() != ApiType.DealDamage && sa.getApi() != ApiType.LoseLife)) {
            return false;
        }
        final String amount = sa.getParamOrDefault(sa.getApi() == ApiType.DealDamage ? "NumDmg" : "LifeAmount", "");
        if (!"TriggerCount$DamageAmount".equals(host.getSVar(amount))) {
            return false;
        }
        final String tgts = sa.getParamOrDefault("ValidTgts", "");
        if (tgts.contains("Opponent") || tgts.contains("Player") || tgts.contains("Any")) {
            return victim.isOpponentOf(host.getController());
        }
        return "ChosenPlayer".equals(sa.getParam("Defined")) && victim.equals(host.getChosenPlayer());
    }

    /** Wayta, Trainer Prodigy and the like: that ability triggers an additional time. */
    private static int extraTriggers(final Game game, final Trigger t, final Card damaged) {
        int extra = 0;
        for (final Card c : game.getCardsIn(ZoneType.Battlefield)) {
            for (final StaticAbility st : c.getStaticAbilities()) {
                if (st.checkMode(StaticAbilityMode.Panharmonicon) && st.hasParam("ValidMode")
                        && st.getParam("ValidMode").contains(t.getMode().toString())
                        && st.matchesValidParam("ValidCard", t.getHostCard())
                        && st.matchesValidParam("ValidTarget", damaged)) {
                    extra++;
                }
            }
        }
        return extra;
    }

    /** Rule 213: blockers to take back, the strongest first, when what comes back kills us or nearly so. */
    public static void dropCostlyBlocks(final Player ai, final Combat combat, final List<Card> attackers) {
        for (final Card attacker : attackers) {
            final int times = reflectCount(attacker, ai);
            if (times == 0) {
                continue;
            }
            final List<Card> blockers = new java.util.ArrayList<>(combat.getBlockers(attacker));
            blockers.removeIf(b -> !b.getController().equals(ai));
            blockers.sort((a, b) -> b.getNetCombatDamage() - a.getNetCombatDamage());
            for (final Card blocker : blockers) {
                int back = 0;
                for (final Card b : combat.getBlockers(attacker)) {
                    back += Math.max(0, b.getNetCombatDamage()) * times;
                }
                if (back == 0 || !tooCostly(ai, back, attacker)) {
                    break;
                }
                combat.removeFromCombat(blocker);
                if (forge.game.combat.CombatUtil.validateBlocks(combat, ai) != null) {
                    combat.addBlocker(attacker, blocker);
                    break;
                }
            }
        }
    }

    private static boolean tooCostly(final Player ai, final int back, final Card attacker) {
        final int life = ai.getLife();
        return back >= life || (life - back < LIFE_FLOOR && back > attacker.getNetCombatDamage());
    }

    /**
     * Rule 214: a blocker that would die of its own damage holds nothing back. True when something can kill this attacker
     * alone and every such blocker would kill its own controller by doing so.
     */
    public static boolean killersWouldDie(final Player ai, final Card attacker, final List<Card> blockers, final Combat combat, final Player defender) {
        final int times = reflectCount(attacker, defender);
        if (times == 0) {
            return false;
        }
        boolean anyKiller = false;
        for (final Card blocker : blockers) {
            if (ComputerUtilCombat.canDestroyAttacker(ai, attacker, blocker, combat, false)) {
                if (blocker.getNetCombatDamage() * times < defender.getLife()) {
                    return false;
                }
                anyKiller = true;
            }
        }
        return anyKiller;
    }
}
