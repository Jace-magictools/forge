package forge.ai;

import java.util.Arrays;
import java.util.List;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 179. A fight spell that also gives our fighter indestructible or deathtouch before the
 * fight (Blizzard Brawl with three snow permanents, Outmuscle) was judged on bare power and toughness:
 * Drow held Blizzard Brawl for 4 turns with a deathtouch archer and 4 snow lands while a 4/5 beat it down,
 * because the archer "would die". Read the keywords the spell grants before the Fight sub-ability, when
 * their condition holds now.
 */
public final class FightGrantAi {
    private FightGrantAi() { }

    /** keywords granted to the fighter (sa = the ability that targets our creature) before the fight */
    public static boolean[] grants(final SpellAbility sa) {
        boolean indestructible = false;
        boolean deathtouch = false;
        for (SpellAbility x = sa.getRootAbility(); x != null; x = x.getSubAbility()) {
            if (x.getApi() == ApiType.Fight || x.getApi() == ApiType.DealDamage) {
                break;
            }
            final boolean onFighter = x == sa
                    || x.getApi() == ApiType.PumpAll && x.getParamOrDefault("ValidCards", "").contains("YouCtrl");
            if (!onFighter || !x.hasParam("KW") || !conditionsHold(x)) {
                continue;
            }
            final List<String> kws = Arrays.asList(x.getParam("KW").split(" & "));
            indestructible |= kws.contains("Indestructible");
            deathtouch |= kws.contains("Deathtouch");
        }
        return new boolean[] {indestructible, deathtouch};
    }

    /** an ancestor of this Fight sub-ability picked the fight through FightAi.canFight */
    public static boolean parentChoseFight(final SpellAbility fight) {
        for (SpellAbility x = fight.getParent(); x != null; x = x.getParent()) {
            final String logic = x.getParamOrDefault("AILogic", "");
            if ("Fight".equals(logic) || "PowerDmg".equals(logic) || implicitFight(x)) {
                return true;
            }
        }
        return false;
    }

    /**
     * magic-tools: rule 187. "Target creature you control fights ..." scripted as a Pump without any AILogic
     * (Wayta, Trainer Prodigy, Prey Upon, Pounce, Titanic Brawl): judged as a plain pump, a +0/+0 was never
     * worth playing. Wayta sat 4 games out of 4 with its only activated ability unused. The Fight sub-ability
     * on the pump's target says what the card does: same judgement as AILogic Fight.
     */
    public static boolean implicitFight(final SpellAbility sa) {
        if (sa.getApi() != ApiType.Pump || sa.hasParam("AILogic") || !sa.usesTargeting() || sa.isCurse()) {
            return false;
        }
        for (SpellAbility x = sa.getSubAbility(); x != null; x = x.getSubAbility()) {
            if (x.getApi() == ApiType.Fight) {
                return "ParentTarget".equals(x.getParam("Defined"));
            }
        }
        return false;
    }

    /** a later sub-ability picks a fight through FightAi.canFight (AILogic Fight / PowerDmg) */
    public static boolean hasFightSub(final SpellAbility sa) {
        for (SpellAbility x = sa.getSubAbility(); x != null; x = x.getSubAbility()) {
            final String logic = x.getParamOrDefault("AILogic", "");
            if ("Fight".equals(logic) || "PowerDmg".equals(logic)) {
                return true;
            }
        }
        return false;
    }

    public static boolean conditionsHold(final SpellAbility x) {
        return x.getConditions() == null || x.metConditions();
    }

    /** a deathtouch fighter with power kills anything that can be destroyed */
    public static boolean deathtouchKills(final Card fighter, final Card opponent, final int pumpAttack) {
        return fighter.getNetPower() + pumpAttack > 0
                && !opponent.hasKeyword(Keyword.INDESTRUCTIBLE)
                && opponent.getShieldCount() == 0
                && !ComputerUtil.canRegenerate(opponent.getController(), opponent);
    }
}
