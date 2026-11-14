package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;

/**
 * magic-tools: rule 311 (D175, plateau, tester 12/11: the AI at 2 life keeps Voracious Hatchling 8/8 lifelink home
 * against four 1-power creatures and dies next turn). When the AI decides which creatures it must hold back as
 * blockers, it now counts the life the attack itself gives back: lifelink (blocked or not, the damage is dealt) and
 * "whenever this creature attacks, you gain N life". tester: at very low life, staying home is still the default,
 * except for the attacker that gains life.
 */
public final class LifelinkAttack311 {
    private LifelinkAttack311() { }

    /** life the AI gains from the attack of these creatures (only ones it controls, not already counted elsewhere) */
    public static int gain(final Player ai, final Iterable<Card> attackers) {
        if (!NewAi.on(ai, 311) || !ai.canGainLife()) {
            return 0;
        }
        int gain = 0;
        for (final Card c : attackers) {
            if (c.getController() != ai || !c.isCreature()) {
                continue;
            }
            if (!ComputerUtil.lifegainPositive(ai, c)) {
                return 0;
            }
            if (c.hasKeyword(Keyword.LIFELINK)) {
                int dmg = c.getNetCombatDamage();
                if (AttackStrike255.hasDoubleStrike(c)) {
                    dmg *= 2;
                }
                gain += Math.max(0, dmg);
            }
            for (final Trigger t : c.getTriggers()) {
                if (t.getMode() != TriggerType.Attacks || !"Card.Self".equals(t.getParam("ValidCard"))
                        || t.hasParam("OptionalDecider")) {
                    continue;
                }
                final SpellAbility sa = t.ensureAbility();
                if (sa == null || sa.getApi() != ApiType.GainLife || sa.hasParam("UnlessCost")
                        || (sa.hasParam("Defined") && !"You".equals(sa.getParam("Defined")))) {
                    continue;
                }
                gain += Math.max(0, AbilityUtils.calculateAmount(c, sa.getParamOrDefault("LifeAmount", "0"), sa));
            }
        }
        return gain;
    }
}
