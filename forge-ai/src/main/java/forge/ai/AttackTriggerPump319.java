package forge.ai;

import java.util.List;

import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.combat.Combat;
import forge.game.player.Player;

/**
 * magic-tools: rule 319 (D204, a human game 16/11: the AI kept Akrasan Squire 1/1 home with two exalted, 3/3
 * alone, against a lone Augur of Bolas 1/3). Before attacking, the AI asks whether the opponent can block the
 * attacker profitably by simulating the block with the attacker already declared: its own attack triggers
 * (exalted, "whenever this attacks, it gets +X/+X") count as already resolved and are left out, so the block is
 * judged against the bare creature. General rule: when the attacker's attack triggers pump it, the profitable
 * block test uses the pumped creature: single blockers are already judged with the bonus (canDestroyAttacker),
 * so only a gang block remains, and it is profitable when the blockers' total power kills the pumped attacker.
 * Second part (same game, turn 10: no blocker across, Rafiq of the Many and five exalted): when nothing can block,
 * the AI sent everyone (5 damage, exalted lost) instead of one creature (7, 14 with Rafiq's double strike). When
 * the attackers it is about to send together cannot be blocked, it sends the best one alone if that deals at
 * least as much, unless only the group is lethal or another attacker brings an attack effect.
 */
public final class AttackTriggerPump319 {
    private AttackTriggerPump319() { }

    /** Null when the rule does not apply (no pump from attack triggers): the caller keeps the original test. */
    public static Boolean blockedProfitably(final Player ai, final Card attacker, final List<Card> validBlockers,
            final Combat combat) {
        if (!NewAi.on(ai, 319)) {
            return null;
        }
        final int power = ComputerUtilCombat.predictPowerBonusOfAttacker(attacker, null, combat, false);
        final int toughness = ComputerUtilCombat.predictToughnessBonusOfAttacker(attacker, null, combat, false);
        if (power <= 0 && toughness <= 0) {
            return null;
        }
        if (validBlockers.size() < 2) {
            return false; // the lone blocker was judged with the bonus
        }
        return CardLists.getTotalPower(validBlockers, null) >= attacker.getNetToughness() + toughness;
    }

    /** The lone attacker to keep instead of the group (all of it unblockable), or null to keep the group. */
    public static Card loneInstead(final Player ai, final GameEntity defender, final List<Card> group) {
        if (!NewAi.on(ai, 319) || group.size() < 2 || !(defender instanceof Player opp)) {
            return null;
        }
        final int exalted = AiAttackController.countExaltedBonus(ai);
        if (exalted <= 0) {
            return null;
        }
        final Combat empty = new Combat(ai); // nobody declared: every estimate counts the exalted bonus
        int together = 0;
        int bestAlone = 0;
        Card best = null;
        for (final Card c : group) {
            final int alone = ComputerUtilCombat.damageIfUnblocked(c, opp, empty, false);
            final int hits = AttackStrike255.hasDoubleStrike(c) ? 2 : 1;
            together += Math.max(0, alone - exalted * hits);
            if (alone > bestAlone) {
                bestAlone = alone;
                best = c;
            }
        }
        if (best == null || (together >= opp.getLife() && bestAlone < opp.getLife())) {
            return null;
        }
        for (final Card c : group) {
            if (c != best && ("TRUE".equals(c.getSVar("HasAttackEffect")) || ComputerUtilCombat.poisonIfUnblocked(c, opp) > 0)) {
                return null;
            }
        }
        return bestAlone >= together ? best : null;
    }

    /** Applies the second part to the attackers already declared against the defender. */
    public static void keepLone(final Player ai, final GameEntity defender, final Combat combat, final List<Card> blockers) {
        final List<Card> group = CardLists.filter(combat.getAttackersOf(defender), c -> true);
        if (group.size() < 2) {
            return;
        }
        for (final Card c : group) {
            if (forge.game.combat.CombatUtil.canBeBlocked(c, blockers, combat)) {
                return;
            }
        }
        final Card lone = loneInstead(ai, defender, group);
        if (lone == null) {
            return;
        }
        for (final Card c : group) {
            if (c != lone) {
                combat.removeFromCombat(c);
            }
        }
    }
}
