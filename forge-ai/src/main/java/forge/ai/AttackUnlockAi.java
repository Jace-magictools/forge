package forge.ai;

import java.util.List;

import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.util.Expressions;

/**
 * magic-tools rule 216 (tester 02/11, a test game): stock AI keeps creatures without haste for
 * main 2, so a second Pugnacious Hammerskull stayed in hand while the first one attacked alone and got stunned.
 * Reads the scripts rather than card names: a permanent spell goes in main 1 when, once on the battlefield, it
 * lifts what limits the attack of one of our ready creatures: "can't attack unless you control ..." (static
 * CantAttack with IsPresent on our side) or a stun counter for attacking without ... (Attacks trigger on itself).
 * Attack triggers that reward the absence (Darkstar, Devil token) are left alone: the spell would lose the bonus.
 */
public final class AttackUnlockAi {
    private AttackUnlockAi() { }

    public static boolean castBeforeAttack(final Player ai, final SpellAbility sa) {
        final Card card = sa.getHostCard();
        if (card == null || !card.isPermanent() || sa.getPayCosts() == null || !sa.getPayCosts().hasManaCost()) {
            return false;
        }
        for (final Card c : ai.getCreaturesInPlay()) {
            if (c == card || c.isTapped() || c.isSick()) {
                continue;
            }
            for (final StaticAbility st : c.getStaticAbilities()) {
                if (isCantAttack(st) && lifts(ai, c, card, st.getParam("IsPresent"), st.getParamOrDefault("PresentCompare", "GE1"))) {
                    return true;
                }
            }
            for (final Trigger t : c.getTriggers()) {
                if (isStunForAttacking(c, t)
                        && lifts(ai, c, card, t.getParam("IsPresent"), t.getParamOrDefault("PresentCompare", "GE1"))
                        && ComputerUtilCard.doesCreatureAttackAI(ai, c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCantAttack(final StaticAbility st) {
        if (!st.hasParam("IsPresent") || !"Card.Self".equals(st.getParam("ValidCard"))) {
            return false;
        }
        for (final String mode : st.getParamOrDefault("Mode", "").split(",")) {
            if ("CantAttack".equals(mode.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStunForAttacking(final Card c, final Trigger t) {
        if (t.getMode() != TriggerType.Attacks || !t.hasParam("IsPresent")
                || !t.getParamOrDefault("ValidCard", "").startsWith("Card.Self") || !t.hasParam("Execute")) {
            return false;
        }
        final String effect = c.getSVar(t.getParam("Execute"));
        return effect.contains("PutCounter") && effect.contains("CounterType$ Stun");
    }

    /** The condition holds now (the limit applies) and no longer holds with the spell on our battlefield. */
    private static boolean lifts(final Player ai, final Card limited, final Card spell, final String isPresent, final String compare) {
        if (isPresent == null || !isPresent.contains("YouCtrl") || compare.length() < 3) {
            return false;
        }
        final String op = compare.substring(0, 2);
        final int operand;
        try {
            operand = Integer.parseInt(compare.substring(2));
        } catch (final NumberFormatException e) {
            return false;
        }
        final String[] valid = isPresent.split(",");
        if (!spell.isValid(valid, ai, limited, null)) {
            return false;
        }
        final List<Card> present = CardLists.getValidCards(ai.getGame().getCardsIn(ZoneType.Battlefield), valid, ai, limited, null);
        return Expressions.compare(present.size(), op, operand) && !Expressions.compare(present.size() + 1, op, operand);
    }
}
