package forge.ai.ability;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.ComputerUtil;
import forge.ai.ComputerUtilCard;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 121. A targeted delayed trigger "when that creature dies this turn, return it"
 * (Dr. Leonard "Bones" McCoy, mode 2) had no AILogic: the generic check asked whether the inner
 * delayed trigger could be played, which never holds, so the mode was never chosen. Read it as the
 * existing SaveCreature logic: choose it (on our best creature) when one of our creatures is
 * predicted to die this turn (spell on the stack or combat). A flash creature whose ETB offers such
 * a mode is cast at that moment.
 */
public final class Rescue121 {
    private Rescue121() { }

    public static boolean isRescue(SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.DelayedTrigger || sa.hasParam("AILogic") || !sa.usesTargeting()) {
            return false;
        }
        String valid = sa.getParam("ValidCard");
        String tgts = sa.getParam("ValidTgts");
        return "ChangesZone".equals(sa.getParam("Mode"))
                && "Battlefield".equals(sa.getParam("Origin"))
                && "Graveyard".equals(sa.getParam("Destination"))
                && valid != null && valid.contains("IsTriggerRemembered")
                && tgts != null && tgts.startsWith("Creature");
    }

    private static CardCollection saveable(Player ai, SpellAbility sa, Card exclude) {
        return CardLists.filter(ai.getCreaturesInPlay(), c -> c != exclude && !c.isToken()
                && (sa == null || sa.canTarget(c))
                && !ComputerUtilCard.isUselessCreature(ai, c)
                && ComputerUtil.predictCreatureWillDieThisTurn(ai, c, sa));
    }

    public static AiAbilityDecision decide(Player ai, SpellAbility sa) {
        sa.resetTargets();
        CardCollection list = saveable(ai, sa, null);
        if (list.isEmpty()) {
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }
        sa.getTargets().add(ComputerUtilCard.getBestAI(list));
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    /**
     * magic-tools: rule 138. Forced into the rescue mode (mandatory charm, the other mode has no
     * target): "up to one" target was left empty. Rather name our best creature, a free insurance
     * (it comes back if it dies this turn), never an opponent's (it would come back to them).
     */
    public static AiAbilityDecision decideTrigger(Player ai, SpellAbility sa, boolean mandatory) {
        AiAbilityDecision d = decide(ai, sa);
        if (d.willingToPlay() || !mandatory) {
            return d;
        }
        sa.resetTargets();
        CardCollection own = CardLists.filter(ai.getCreaturesInPlay(), c -> !c.isToken() && sa.canTarget(c)
                && !ComputerUtilCard.isUselessCreature(ai, c));
        if (!own.isEmpty()) {
            sa.getTargets().add(ComputerUtilCard.getBestAI(own));
        }
        return new AiAbilityDecision(50, AiPlayDecision.WillPlay);
    }

    /** Flash creature whose ETB (or ETB charm) holds a rescue mode, and a creature of ours is about to die. */
    public static boolean flashNow(Player ai, Card card) {
        if (!card.hasKeyword(Keyword.FLASH) || !hasRescueEtb(card)) {
            return false;
        }
        return !saveable(ai, null, card).isEmpty();
    }

    private static boolean hasRescueEtb(Card card) {
        for (Trigger tr : card.getTriggers()) {
            if (tr.getMode() != TriggerType.ChangesZone
                    || !ZoneType.Battlefield.toString().equals(tr.getParam("Destination"))
                    || !tr.hasParam("ValidCard") || !tr.getParam("ValidCard").contains("Self")) {
                continue;
            }
            SpellAbility sa = tr.ensureAbility();
            if (sa == null) {
                continue;
            }
            if (isRescue(sa)) {
                return true;
            }
            if (sa.getApi() == ApiType.Charm) {
                for (AbilitySub sub : sa.getAdditionalAbilityList("Choices")) {
                    if (isRescue(sub)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
