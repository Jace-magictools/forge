package forge.ai.ability;


import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.ComputerUtilCard;
import forge.ai.SpecialAiLogic;
import forge.ai.SpecialCardAi;
import forge.ai.SpellAbilityAi;
import forge.ai.SpellApiToAi;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.combat.Combat;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.spellability.SpellAbility;

import forge.util.Expressions;

import java.util.Map;

public class BranchAi extends SpellAbilityAi {
    /* (non-Javadoc)
     * @see forge.card.abilityfactory.SpellAiLogic#canPlayAI(forge.game.player.Player, java.util.Map, forge.card.spellability.SpellAbility)
     */
    @Override
    protected AiAbilityDecision canPlay(Player aiPlayer, SpellAbility sa) {
        final String aiLogic = sa.getParamOrDefault("AILogic", "");
        if ("GrislySigil".equals(aiLogic)) {
            boolean result = SpecialCardAi.GrislySigil.consider(aiPlayer, sa);
            return new AiAbilityDecision(result ? 100 : 0, result ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
        } else if ("BranchCounter".equals(aiLogic)) {
            boolean result = SpecialAiLogic.doBranchCounterspellLogic(aiPlayer, sa);
            return new AiAbilityDecision(result ? 100 : 0, result ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
        } else if ("TgtAttacker".equals(aiLogic)) {
            final Combat combat = aiPlayer.getGame().getCombat();
            if (combat == null || combat.getAttackingPlayer() != aiPlayer) {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }

            final CardCollection attackers = combat.getAttackers();
            final CardCollection attackingBattle = CardLists.filter(attackers, card -> {
                final GameEntity def = combat.getDefenderByAttacker(combat.getBandOfAttacker(card));
                return def instanceof Card && ((Card)def).isBattle();
            });

            if (!attackingBattle.isEmpty()) {
                sa.getTargets().add(ComputerUtilCard.getBestCreatureAI(attackingBattle));
            } else {
                sa.getTargets().add(ComputerUtilCard.getBestCreatureAI(attackers));
            }

            return new AiAbilityDecision(sa.isTargetNumberValid() ? 100 : 0, sa.isTargetNumberValid() ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
        }

        // magic-tools: rule 61, a branch that carries its own target (Rancid Earth, Epicenter...) never chose one:
        // the AI of the branch that will resolve picks the target and decides
        final SpellAbilityAi delegate = targetedBranchAi(sa);
        if (delegate != null) {
            return delegate.canPlayWithSubs(aiPlayer, sa);
        }

        // TODO: expand for other cases where the AI is needed to make a decision on a branch
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    /** The AI of the branch that will resolve, when the branch itself targets and has no special logic. */
    static SpellAbilityAi targetedBranchAi(final SpellAbility sa) {
        if (!sa.usesTargeting() || sa.hasParam("AILogic")) {
            return null;
        }
        final String branchCompare = sa.getParamOrDefault("BranchConditionSVarCompare", "GE1");
        final int svarValue = AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("BranchConditionSVar"), sa);
        final int operandValue = AbilityUtils.calculateAmount(sa.getHostCard(), branchCompare.substring(2), sa);
        final SpellAbility sub = sa.getAdditionalAbility(
                Expressions.compare(svarValue, branchCompare.substring(0, 2), operandValue) ? "TrueSubAbility" : "FalseSubAbility");
        if (sub == null || sub.getApi() == null || sub.getApi() == ApiType.Branch) {
            return null;
        }
        return SpellApiToAi.Converter.get(sub.getApi());
    }

    @Override
    protected AiAbilityDecision doTriggerNoCost(Player aiPlayer, SpellAbility sa, boolean mandatory) {
        // magic-tools: rule 61, same delegation for a triggered branch with its own target
        final SpellAbilityAi delegate = targetedBranchAi(sa);
        if (delegate != null) {
            return delegate.doTriggerNoCostWithSubs(aiPlayer, sa, mandatory);
        }
        AiAbilityDecision decision = canPlay(aiPlayer, sa);
        if (decision.willingToPlay() || mandatory) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    @Override
    public boolean confirmAction(Player player, SpellAbility sa, PlayerActionConfirmMode mode, String message, Map<String, Object> params) {
        return true;
    }
}
