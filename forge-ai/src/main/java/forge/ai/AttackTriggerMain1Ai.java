package forge.ai;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;

/**
 * magic-tools: stock AI keeps creatures without haste for main 2, so a permanent that triggers when our other
 * creatures attack comes down after combat and misses that attack: Winota, Joiner of Forces was refused in
 * main 1 with four lands and a non-Human attacker 15 times in 10 games against Nothing Gulch. A permanent with
 * an "attacks" trigger on other creatures (or "whenever you attack") is cast in main 1 when one of our
 * creatures that would trigger it is about to attack.
 */
public final class AttackTriggerMain1Ai {
    private AttackTriggerMain1Ai() { }

    public static boolean castBeforeAttack(final Player ai, final SpellAbility sa) {
        final Card card = sa.getHostCard();
        if (card == null || sa.getPayCosts() == null || !sa.getPayCosts().hasManaCost()) {
            return false;
        }
        for (final Trigger t : card.getTriggers()) {
            final String zones = t.getParamOrDefault("TriggerZones", "");
            if (!zones.contains("Battlefield")) {
                continue;
            }
            final String valid;
            if (t.getMode() == TriggerType.Attacks) {
                valid = t.getParamOrDefault("ValidCard", "");
                if (valid.isEmpty() || valid.startsWith("Card.Self")) {
                    continue;
                }
            } else if (t.getMode() == TriggerType.AttackersDeclared
                    && "You".equals(t.getParamOrDefault("AttackingPlayer", ""))) {
                valid = "Creature.YouCtrl";
            } else {
                continue;
            }
            for (final Card c : ai.getCreaturesInPlay()) {
                if (c == card || c.isTapped() || c.isSick()) {
                    continue;
                }
                if (c.isValid(valid.split(","), ai, card, null) && ComputerUtilCard.doesCreatureAttackAI(ai, c)) {
                    return true;
                }
            }
        }
        return false;
    }
}
