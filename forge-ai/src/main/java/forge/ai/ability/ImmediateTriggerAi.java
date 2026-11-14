package forge.ai.ability;

import forge.ai.*;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;

public class ImmediateTriggerAi extends SpellAbilityAi {
    // TODO: this class is largely reused from DelayedTriggerAi, consider updating

    @Override
    public AiAbilityDecision chkDrawback(Player ai, SpellAbility sa) {
        String logic = sa.getParamOrDefault("AILogic", "");
        if (logic.equals("Always")) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }

        SpellAbility trigsa = sa.getAdditionalAbility("Execute");
        if (trigsa == null) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        trigsa.setActivatingPlayer(ai);

        if (trigsa instanceof AbilitySub) {
            AiAbilityDecision d = SpellApiToAi.Converter.get(trigsa).chkDrawbackWithSubs(ai, (AbilitySub)trigsa);
            // magic-tools: rule 133, a reflexive "when you do" trigger that can only hit the opponent's side is a bonus:
            // judged before resolution (X = the amassed Army's power is still 0) it must not veto the spell
            // (Foray of Orcs was never cast, tournament v19, Sauron)
            if (!d.willingToPlay() && opponentOnly(trigsa)) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
            return d;
        }

        AiPlayDecision decision = ((PlayerControllerAi)ai.getController()).getAi().canPlaySa(trigsa);
        if (decision == AiPlayDecision.WillPlay) {
            return new AiAbilityDecision(100, decision);
        }

        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    @Override
    protected AiAbilityDecision doTriggerNoCost(Player ai, SpellAbility sa, boolean mandatory) {
        // always add to stack, targeting happens after payment
        if (mandatory) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }

        SpellAbility trigsa = sa.getAdditionalAbility("Execute");
        if (trigsa == null) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        AiController aic = ((PlayerControllerAi)ai.getController()).getAi();
        trigsa.setActivatingPlayer(ai);

        return aic.doTrigger(trigsa, !"You".equals(sa.getParamOrDefault("OptionalDecider", "You"))) ? new AiAbilityDecision(100, AiPlayDecision.WillPlay) : new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    @Override
    protected AiAbilityDecision canPlay(Player ai, SpellAbility sa) {
        String logic = sa.getParamOrDefault("AILogic", "");
        if (logic.equals("Always")) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }

        SpellAbility trigsa = sa.getAdditionalAbility("Execute");
        if (trigsa == null) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        if (logic.equals("WeakerCreature")) {
            Card ownCreature = ComputerUtilCard.getWorstCreatureAI(ai.getCreaturesInPlay());
            if (ownCreature == null) {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }

            int eval = ComputerUtilCard.evaluateCreature(ownCreature);
            boolean foundWorse = false;
            for (Card c : ai.getOpponents().getCreaturesInPlay()) {
                if (eval + 100 < ComputerUtilCard.evaluateCreature(c) ) {
                    foundWorse = true;
                    break;
                }
            }
            if (!foundWorse) {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }
        }

        trigsa.setActivatingPlayer(ai);
        return ((PlayerControllerAi)ai.getController()).getAi().canPlaySa(trigsa) == AiPlayDecision.WillPlay ? new AiAbilityDecision(100, AiPlayDecision.WillPlay) : new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }


    // magic-tools: rule 133, every target of the reflexive trigger (and of its subs) is on the opponent's side
    private static boolean opponentOnly(SpellAbility trigsa) {
        boolean targets = false;
        for (SpellAbility a = trigsa; a != null; a = a.getSubAbility()) {
            if (!a.usesTargeting()) {
                if (a.getApi() != null && a.getApi() != forge.game.ability.ApiType.Cleanup) {
                    return false;
                }
                continue;
            }
            targets = true;
            String v = a.getParamOrDefault("ValidTgts", "");
            for (String part : v.split(",")) {
                if (!(part.contains("OppCtrl") || part.equals("Opponent") || part.contains("OppOwn"))) {
                    return false;
                }
            }
        }
        return targets;
    }
}
