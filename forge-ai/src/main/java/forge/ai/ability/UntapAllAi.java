package forge.ai.ability;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.SpellAbilityAi;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

public class UntapAllAi extends SpellAbilityAi {
    @Override
    protected AiAbilityDecision canPlay(Player aiPlayer, SpellAbility sa) {
        final Card source = sa.getHostCard();

        // magic-tools : Vitalize, dégager ses créatures de mana pour lancer un sort de plus
        if ("UntapManaCreatures".equals(sa.getParam("AILogic"))) {
            return UntapAi.doUntapAllManaLogic(aiPlayer, sa)
                    ? new AiAbilityDecision(100, AiPlayDecision.WillPlay)
                    : new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        final AbilitySub abSub = sa.getSubAbility();
        if (abSub != null) {
        	if (ApiType.AddPhase == abSub.getApi() 
        			&& source.getGame().getPhaseHandler().getPhase().isBefore(PhaseType.COMBAT_END)) {
        		return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        	}
            CardCollectionView list = CardLists.filter(aiPlayer.getGame().getCardsIn(ZoneType.Battlefield), CardPredicates.TAPPED);
            final String valid = sa.getParamOrDefault("ValidCards", "");
            list = CardLists.getValidCards(list, valid, source.getController(), source, sa);
            // don't untap if only opponent benefits
            if (list.anyMatch(CardPredicates.isControlledByAnyOf(aiPlayer.getYourTeam()))) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            } else {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }
        }
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    @Override
    protected AiAbilityDecision doTriggerNoCost(Player aiPlayer, SpellAbility sa, boolean mandatory) {
        Card source = sa.getHostCard();

        if (sa.hasParam("ValidCards")) {
            String valid = sa.getParam("ValidCards");
            CardCollectionView list = CardLists.filter(aiPlayer.getGame().getCardsIn(ZoneType.Battlefield), CardPredicates.TAPPED);
            list = CardLists.getValidCards(list, valid, source.getController(), source, sa);
            // magic-tools : untapping nothing is harmless (Deepway Navigator stayed in hand as BadEtbEffects),
            // only refuse when it untaps opponents' permanents and none of ours
            boolean onlyOpponents = !list.isEmpty()
                    && !list.anyMatch(CardPredicates.isControlledByAnyOf(aiPlayer.getYourTeam()));
            return (mandatory || !onlyOpponents) ? new AiAbilityDecision(100, AiPlayDecision.WillPlay) : new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        return mandatory ? new AiAbilityDecision(100, AiPlayDecision.WillPlay) : new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }
}
