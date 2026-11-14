package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 184 (plateau D105, tester 04/11). Éclaireurs Orques: "deals 2 damage to you or target creature you
 * control", scripted as an optional target (Creature.YouCtrl, TargetMin 0) followed by "DealDamage Defined$ You" when
 * nothing was targeted. The AI never targets its own creature, so it always took the 2 itself while its 4/5 Orc Warlord
 * shrugged that off. When the only legal targets are ours and an empty choice sends the damage to our face, put it on a
 * creature that survives it (the widest margin, then the least valuable), or on the cheapest creature if the face
 * damage would kill us; otherwise take it.
 */
public final class ImposedDamageAi {
    private ImposedDamageAi() { }

    /** Damage dealt to us by a sub-ability when the optional target is left empty, or -1 if there is no such fallback. */
    static int selfFallback(final Player ai, final SpellAbility sa) {
        for (SpellAbility sub = sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.getApi() == ApiType.DealDamage && "You".equals(sub.getParam("Defined"))
                    && "Targeted".equals(sub.getParam("ConditionDefined"))) {
                return AbilityUtils.calculateAmount(sa.getHostCard(), sub.getParamOrDefault("NumDmg", "0"), sub);
            }
        }
        return -1;
    }

    public static boolean applies(final Player ai, final SpellAbility sa) {
        if (!sa.usesTargeting() || sa.getMinTargets() != 0 || sa.getMaxTargets() < 1 || selfFallback(ai, sa) < 0) {
            return false;
        }
        for (final Object o : sa.getTargetRestrictions().getAllCandidates(sa)) {
            if (o instanceof Player p ? p != ai : !(o instanceof Card c) || c.getController() != ai) {
                return false;
            }
        }
        return true;
    }

    /** Sets the target (or none); the choice is always legal since the target is optional. */
    public static boolean choose(final Player ai, final SpellAbility sa, final int dmg) {
        sa.resetTargets();
        final Card source = sa.getHostCard();
        Card best = null;
        int bestMargin = 0;
        for (final Card c : CardLists.filter(ai.getCreaturesInPlay(), sa::canTarget)) {
            if (ComputerUtilCombat.getEnoughDamageToKill(c, dmg, source, false) <= dmg) {
                continue;
            }
            final int margin = ComputerUtilCombat.getDamageToKill(c, false) - ComputerUtilCombat.predictDamageTo(c, dmg, source, false);
            if (best == null || margin > bestMargin
                    || margin == bestMargin && ComputerUtilCard.evaluateCreature(c) < ComputerUtilCard.evaluateCreature(best)) {
                best = c;
                bestMargin = margin;
            }
        }
        if (best == null) {
            final int face = ComputerUtilCombat.predictDamageTo(ai, selfFallback(ai, sa), source, false);
            if (face > 0 && face >= ai.getLife() && ai.canLoseLife() && !ai.cantLoseForZeroOrLessLife()) {
                best = ComputerUtilCard.getWorstCreatureAI(CardLists.filter(ai.getCreaturesInPlay(), sa::canTarget));
            }
        }
        if (best != null) {
            sa.getTargets().add(best);
        }
        return true;
    }

    /**
     * magic-tools: rule 186 (plateau D112, tester 04/11). A mandatory "deals N damage to you or another target creature
     * you control" (Orque Berserk: ValidTgts You,Creature.Other+YouCtrl) went to Forge's required-targets fallback, which
     * tries our own creatures before our face: each end step the second Orc killed the first while we stood at 17 life.
     * When every legal target is ours and we are one of them: a creature that survives (widest margin, then least
     * valuable), else our face if life stays comfortable (above twice the damage plus 5 and above what the opponents
     * deal in one unblocked swing), else the least valuable creature, else our face.
     */
    public static boolean appliesSelfTarget(final Player ai, final SpellAbility sa, final boolean mandatory) {
        if (!mandatory || !sa.usesTargeting() || sa.isDividedAsYouChoose() || sa.getMaxTargets() != 1 || !sa.canTarget(ai)) {
            return false;
        }
        for (final Object o : sa.getTargetRestrictions().getAllCandidates(sa)) {
            if (o instanceof Player p ? p != ai : !(o instanceof Card c) || c.getController() != ai) {
                return false;
            }
        }
        return true;
    }

    static boolean faceComfortable(final Player ai, final int dmg) {
        if (dmg <= 0 || !ai.canLoseLife()) {
            return true;
        }
        int threat = 0;
        for (final Player opp : ai.getOpponents()) {
            threat += ComputerUtilCombat.sumDamageIfUnblocked(opp.getCreaturesInPlay(), ai);
        }
        final int after = ai.getLife() - dmg;
        return after > dmg + 5 && after > threat;
    }

    public static boolean chooseSelfTarget(final Player ai, final SpellAbility sa, final int dmg) {
        sa.resetTargets();
        final Card source = sa.getHostCard();
        final java.util.List<Card> mine = CardLists.filter(ai.getCardsIn(forge.game.zone.ZoneType.Battlefield),
                c -> c.isCreature() && sa.canTarget(c));
        Card best = null;
        int bestMargin = 0;
        for (final Card c : mine) {
            if (ComputerUtilCombat.getEnoughDamageToKill(c, dmg, source, false) <= dmg && !c.hasKeyword(forge.game.keyword.Keyword.INDESTRUCTIBLE)) {
                continue;
            }
            final int margin = ComputerUtilCombat.getDamageToKill(c, false) - ComputerUtilCombat.predictDamageTo(c, dmg, source, false);
            if (best == null || margin > bestMargin
                    || margin == bestMargin && ComputerUtilCard.evaluateCreature(c) < ComputerUtilCard.evaluateCreature(best)) {
                best = c;
                bestMargin = margin;
            }
        }
        if (best != null) {
            sa.getTargets().add(best);
            return true;
        }
        final int face = ComputerUtilCombat.predictDamageTo(ai, dmg, source, false);
        if (mine.isEmpty() || faceComfortable(ai, face)) {
            sa.getTargets().add(ai);
            return true;
        }
        sa.getTargets().add(ComputerUtilCard.getWorstCreatureAI(mine));
        return true;
    }
}
