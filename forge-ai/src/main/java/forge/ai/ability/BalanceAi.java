package forge.ai.ability;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.SpellAbilityAi;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;

public class BalanceAi extends SpellAbilityAi {
    @Override
    protected AiAbilityDecision canPlay(Player aiPlayer, SpellAbility sa) {
        String logic = sa.getParam("AILogic");
        int diff = 0;
        Player opp = aiPlayer.getWeakestOpponent();
        final CardCollectionView compPerms = aiPlayer.getCardsIn(ZoneType.Battlefield);
        for (Player min : aiPlayer.getOpponents()) {
            if (min.getCardsIn(ZoneType.Battlefield).size() < opp.getCardsIn(ZoneType.Battlefield).size()) {
                opp = min;
            }
        }
        final CardCollectionView humPerms = opp.getCardsIn(ZoneType.Battlefield);
        
        if ("BalanceCreaturesAndLands".equals(logic)) {
            // TODO Copied over from hardcoded Balance. We should be checking value of the lands/creatures for each opponent, not just counting
            diff += CardLists.filter(humPerms, CardPredicates.LANDS).size() -
                    CardLists.filter(compPerms, CardPredicates.LANDS).size();
            diff += 1.5 * (CardLists.filter(humPerms, CardPredicates.CREATURES).size() -
                    CardLists.filter(compPerms, CardPredicates.CREATURES).size());
        }
        else if ("BalancePermanents".equals(logic)) {
            // Don't cast if you have to sacrifice permanents
            diff += humPerms.size() - compPerms.size();
        }

        if (diff < 0) {
            // Don't sacrifice permanents even if opponent has a ton of cards in hand
            return new AiAbilityDecision(0, forge.ai.AiPlayDecision.CantPlayAi);
        }

        final CardCollectionView humHand = opp.getCardsIn(ZoneType.Hand);
        final CardCollectionView compHand = aiPlayer.getCardsIn(ZoneType.Hand);
        // magic-tools: rule 232, tournament v24, Dovin at 1 life, no creature against five, held Balance from turn 5 until
        // dead on turn 16: the hand it would discard does not matter when the board kills next turn
        if (diff > 2 && "BalanceCreaturesAndLands".equals(logic) && boardIsLethal(aiPlayer, opp)) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }
        // magic-tools: rule 232, the spell itself leaves the hand, and a clear gain is taken at once rather than drawn each turn
        diff += 0.5 * (humHand.size() - (compHand.size() - (compHand.contains(sa.getHostCard()) ? 1 : 0)));
        if (diff >= 5 && "BalanceCreaturesAndLands".equals(logic)) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }

        // Larger differential == more chance to actually cast this spell
        boolean willPlay = diff > 2 && MyRandom.getRandom().nextInt(100) < diff*10;
        return new AiAbilityDecision(willPlay ? 100 : 0, willPlay ? forge.ai.AiPlayDecision.WillPlay : AiPlayDecision.StopRunawayActivations);
    }

    // magic-tools: rule 232, the opponent's creatures, unblocked, take everything we have left
    private static boolean boardIsLethal(final Player ai, final Player opp) {
        int power = 0;
        for (final forge.game.card.Card c : CardLists.filter(opp.getCardsIn(ZoneType.Battlefield), CardPredicates.CREATURES)) {
            power += Math.max(0, c.getNetCombatDamage());
        }
        return power >= ai.getLife();
    }
}
