package forge.ai;

import java.util.EnumSet;

import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 81 (a tester report 29/10 17:12, Smeagol - carcasse): Altar of Dementia sacrificed the Plant
 * token at Smeagol's own end step, then the opponent attacked with three Skitterbeam Battalions and nothing blocked.
 * The Altar is an instant-speed outlet: a creature that could still block is worth more on the opponent's turn,
 * blocking first and sacrificed once blockers are declared (the attacker stays blocked), or at their end step.
 * So a cheap creature that could block on the opponent's next combat is not offered as fodder on our own turn nor
 * on the opponent's turn before blockers, unless the effect wins now (lethal mill or damage, winning loop).
 * Only for effects that lose nothing by waiting (mill, draw, life, counters, damage aimed at a player): removal trades now.
 * Once blockers are declared, a creature that is blocking goes first.
 */
public final class BlockerFodderAi {
    private BlockerFodderAi() { }

    /** the creature should be kept for blocking now, rather than paid for this outlet */
    public static boolean keepForBlocking(final Player ai, final Card c, final SpellAbility sa) {
        if (sa == null || !c.isCreature() || c.isTapped() || !sa.isActivatedAbility() || sa.isPwAbility()
                || sa.isManaAbility() || c.hasSVar("EndOfTurnLeavePlay") || !CombatUtil.canBlock(c, true)) {
            return false;
        }
        if (sa.getRestrictions() != null && sa.getRestrictions().isSorcerySpeed()) {
            return false; // no later window: the sorcery-speed outlet is now or next turn
        }
        if (!PATIENT.contains(sa.getApi()) || hitsACard(sa)) {
            return false; // removal (Goblin Bombardment on an elf) trades now
        }
        if (!canStillBlockLater(ai)) {
            return false;
        }
        return !winsNow(ai, c, sa);
    }

    /** effects on players or on ourselves, that lose nothing by waiting for the opponent's combat */
    private static final EnumSet<ApiType> PATIENT = EnumSet.of(ApiType.Mill, ApiType.Draw, ApiType.Dig,
            ApiType.Scry, ApiType.Surveil, ApiType.GainLife, ApiType.LoseLife, ApiType.PutCounter,
            ApiType.PutCounterAll, ApiType.DealDamage);

    /** damage or another effect aimed at a card (or not aimed yet, for damage: it may become removal) */
    static boolean hitsACard(final SpellAbility sa) {
        if (sa.usesTargeting() && sa.getTargets() != null && !sa.getTargets().getTargetCards().isEmpty()) {
            return true;
        }
        return sa.getApi() == ApiType.DealDamage
                && (!sa.usesTargeting() || sa.getTargets() == null || !sa.getTargets().getTargetPlayers().iterator().hasNext());
    }

    /** the opponent's next combat (or this one before blockers) is still ahead */
    static boolean canStillBlockLater(final Player ai) {
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        if (ph.isPlayerTurn(ai)) {
            return true;
        }
        return ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_BLOCKERS);
    }

    /** milling an opponent out with this creature's power, or a loop that wins: no time to wait */
    static boolean winsNow(final Player ai, final Card c, final SpellAbility sa) {
        if (PersistLoopAi.anyLoopCard(ai, sa) != null) {
            return true;
        }
        if (sa.getApi() == ApiType.Mill) {
            for (final Player o : ai.getOpponents()) {
                if (o.getCardsIn(ZoneType.Library).size() <= Math.max(0, c.getNetPower())) {
                    return true;
                }
            }
        }
        if (sa.getApi() == ApiType.DealDamage) {
            final int dmg = AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParamOrDefault("NumDmg", "0"), sa);
            for (final Player o : ai.getOpponents()) {
                if (o.canLoseLife() && o.getLife() <= dmg) {
                    return true; // Goblin Bombardment finishing the opponent
                }
            }
        }
        return false;
    }

    /** on the opponent's turn, once blockers are declared: one of ours is blocking */
    public static boolean isBlockingNow(final Player ai, final Card c) {
        final Combat combat = ai.getGame().getCombat();
        return combat != null && !ai.getGame().getPhaseHandler().isPlayerTurn(ai) && combat.isBlocking(c);
    }

    /**
     * magic-tools: rule 310 (Smeagol v335 against Ajani: Gollum, Patient Plotter blocks Ajani's Pridemate, then Altar
     * of Dementia sacrifices it before damage, the Pridemate survives). Before combat damage, a blocker whose damage
     * would destroy one of the attackers it blocks keeps blocking: the sacrifice waits (it may still be paid at the
     * end step if the blocker survives), unless the outlet wins now.
     */
    public static boolean killsItsAttacker(final Player ai, final Card c) {
        if (!NewAi.on(ai, 310)) {
            return false;
        }
        final Game game = ai.getGame();
        final Combat combat = game.getCombat();
        if (combat == null || game.getPhaseHandler().isPlayerTurn(ai) || !combat.isBlocking(c)
                || game.getPhaseHandler().getPhase().isAfter(PhaseType.COMBAT_FIRST_STRIKE_DAMAGE)) {
            return false;
        }
        for (final Card attacker : combat.getAttackersBlockedBy(c)) {
            if (ComputerUtilCombat.canDestroyAttacker(ai, attacker, c, combat, false)) {
                return true;
            }
        }
        return false;
    }

    /** the window where a sacrifice outlet should be used with a blocker: opponent's turn, blockers declared */
    public static boolean blockersDeclaredOnOpponentTurn(final Player ai) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        return !ph.isPlayerTurn(ai) && ai.getGame().getCombat() != null
                && ph.inCombat() && !ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_BLOCKERS)
                && !ph.getPhase().isAfter(PhaseType.COMBAT_DAMAGE);
    }
}
