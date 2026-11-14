package forge.ai.ability;

import com.google.common.collect.Iterables;
import forge.ai.*;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.PlayerCollection;
import forge.game.player.PlayerPredicates;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.TextUtil;

import java.util.Map;

public class DigAi extends SpellAbilityAi {
    /* (non-Javadoc)
     * @see forge.card.abilityfactory.SpellAiLogic#canPlayAI(forge.game.player.Player, java.util.Map, forge.card.spellability.SpellAbility)
     */
    @Override
    protected AiAbilityDecision checkApiLogic(Player ai, SpellAbility sa) {
        final Game game = ai.getGame();
        Player opp = AiAttackController.choosePreferredDefenderPlayer(ai);
        final Card host = sa.getHostCard();
        Player libraryOwner = ai;

        if (sa.usesTargeting()) {
            sa.resetTargets();
            if (!sa.canTarget(opp)) {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }
            sa.getTargets().add(opp);
            libraryOwner = opp;
        }

        // return false if nothing to dig into
        if (libraryOwner.getCardsIn(ZoneType.Library).isEmpty()) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        // don't deck yourself
        if (sa.hasParam("DestinationZone2") && !"Library".equals(sa.getParam("DestinationZone2"))) {
            int numToDig = AbilityUtils.calculateAmount(host, sa.getParam("DigNum"), sa);
            if (libraryOwner == ai && ai.getCardsIn(ZoneType.Library).size() <= numToDig + 2) {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }
        }

        // magic-tools: the rest goes back to the library, but what is taken can still empty it
        // (Lead the Stampede with 3 creatures left: ElfBall lost at its next draw step)
        if (libraryOwner == ai && digCanEmptyOwnLibrary(ai, sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        // Don't use draw abilities before main 2 if possible
        if (game.getPhaseHandler().getPhase().isBefore(PhaseType.MAIN2) && !sa.hasParam("ActivationPhases")
                && !sa.hasParam("DestinationZone") && !ComputerUtil.castSpellInMain1(ai, sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        final String num = sa.getParam("DigNum");
        final boolean payXLogic = sa.hasParam("AILogic") && sa.getParam("AILogic").startsWith("PayX");
        if (num != null && (num.equals("X") && sa.getSVar(num).equals("Count$xPaid")) || payXLogic) {
            // By default, set PayX here to maximum value.
            SpellAbility root = sa.getRootAbility();
            if (root.getXManaCostPaid() == null) {
                int manaToSave = 0;

                // Special logic that asks the AI to conserve a certain amount of mana when paying X
                if (sa.hasParam("AILogic") && sa.getParam("AILogic").startsWith("PayXButSaveMana")) {
                    manaToSave = Integer.parseInt(TextUtil.split(sa.getParam("AILogic"), '.')[1]);
                }

                int numCards = ComputerUtilCost.setMaxXValue(sa, ai, sa.isTrigger()) - manaToSave;
                if (numCards <= 0) {
                    return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
                }
            }
        }

        if (playReusable(ai, sa)) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }

        if ((!game.getPhaseHandler().getNextTurn().equals(ai)
                || game.getPhaseHandler().getPhase().isBefore(PhaseType.END_OF_TURN))
            && !sa.hasParam("PlayerTurn") && !isSorcerySpeed(sa, ai)
            && (ai.getCardsIn(ZoneType.Hand).size() > 1 || game.getPhaseHandler().getPhase().isBefore(PhaseType.DRAW))
            && !ComputerUtil.activateForCost(sa, ai)) {
        	return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        if ("MadSarkhanDigDmg".equals(sa.getParam("AILogic"))) {
            return SpecialCardAi.SarkhanTheMad.considerDig(ai, sa);
        }

        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    // magic-tools: "look at the top N, take any number" whose taken cards could leave no card for the next draw step
    static boolean digCanEmptyOwnLibrary(final Player ai, final SpellAbility sa) {
        if ("Library".equals(sa.getParamOrDefault("DestinationZone", "Hand")) || ai.isCardInPlay("Laboratory Maniac")) {
            return false;
        }
        final String digNum = sa.getParam("DigNum");
        if (digNum == null || digNum.startsWith("X")) {
            return false;
        }
        final Card host = sa.getHostCard();
        final int numToDig = AbilityUtils.calculateAmount(host, digNum, sa);
        final String changeNum = sa.getParamOrDefault("ChangeNum", "1");
        final int maxTaken = "Any".equals(changeNum) || "All".equals(changeNum) ? numToDig
                : Math.min(numToDig, AbilityUtils.calculateAmount(host, changeNum, sa));
        return maxTaken > 0 && ai.getCardsIn(ZoneType.Library).size() <= maxTaken;
    }

    @Override
    public AiAbilityDecision chkDrawback(Player aiPlayer, SpellAbility sa) {
        // magic-tools: rule 166, the price of a mana ability ("{T}: add {W}, {G} or {R}, exile the bottom three cards of
        // your library", Port des Havres Gris): canPlay refused it at instant speed with more than one card in hand,
        // so the land was never activated and the AI cast nothing (plateau, Uruk, 04/11; CR 605.1a makes it a
        // stack ability, activated beforehand by ManaFilterAi). Only the "don't deck yourself" guard is kept.
        if (sa.getRootAbility().getApi() == ApiType.Mana && !sa.usesTargeting()
                && "You".equals(sa.getParamOrDefault("Defined", "You"))) {
            final String digNum = sa.getParamOrDefault("DigNum", "1");
            final int numToDig = digNum.matches("\\d+") ? Integer.parseInt(digNum)
                    : AbilityUtils.calculateAmount(sa.getHostCard(), digNum, sa);
            if (aiPlayer.getCardsIn(ZoneType.Library).size() <= numToDig + 2 && !aiPlayer.isCardInPlay("Laboratory Maniac")) {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }
        // TODO: improve this check in ways that may be specific to a subability
        return canPlay(aiPlayer, sa);
    }

    @Override
    protected AiAbilityDecision doTriggerNoCost(Player ai, SpellAbility sa, boolean mandatory) {
        final SpellAbility root = sa.getRootAbility();
        PlayerCollection targetableOpps = ai.getOpponents().filter(PlayerPredicates.isTargetableBy(sa));
        Player opp = targetableOpps.min(PlayerPredicates.compareByLife());
        if (sa.usesTargeting()) {
            sa.resetTargets();
            if (mandatory && opp != null) {
                sa.getTargets().add(opp);
            } else if (mandatory && sa.canTarget(ai)) {
                sa.getTargets().add(ai);
            }
        }

        // Triggers that ask to pay {X} (e.g. Depala, Pilot Exemplar).
        if (sa.hasParam("AILogic") && sa.getParam("AILogic").startsWith("PayXButSaveMana")) {
            int manaToSave = Integer.parseInt(TextUtil.split(sa.getParam("AILogic"), '.')[1]);
            int numCards = ComputerUtilCost.setMaxXValue(sa, ai, true) - manaToSave;
            if (numCards <= 0) {
                if (mandatory) {
                    return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                }
                return new AiAbilityDecision(100, AiPlayDecision.CantPlayAi);
            }
            root.setXManaCostPaid(numCards);
        }

        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }
    
    @Override
    public Card chooseSingleCard(Player ai, SpellAbility sa, Iterable<Card> valid, boolean isOptional, Player relatedPlayer, Map<String, Object> params) {
        // magic-tools: rule 135, digging a card onto our battlefield never picks a second copy of a legend
        // we already control when another choice exists (tournament v20, Pod: Birthing Ritual put a second
        // Melira, Sylvok Outcast onto the battlefield, the legend rule sent one to the graveyard)
        if ("Battlefield".equals(sa.getParam("DestinationZone")) && sa.getActivatingPlayer() == ai) {
            CardCollection noDupLegend = CardLists.filter(valid,
                    c -> !c.getType().isLegendary() || c.ignoreLegendRule() || !ai.isCardInPlay(c.getName()));
            if (!noDupLegend.isEmpty()) {
                valid = noDupLegend;
            }
        }
        if ("DigForCreature".equals(sa.getParam("AILogic"))) {
            Card bestChoice = ComputerUtilCard.getBestCreatureAI(valid);
            if (bestChoice == null) {
                // no creatures, but maybe there's a morphable card that can be played as a creature?
                CardCollection morphs = CardLists.getKeyword(valid, Keyword.MORPH);
                if (!morphs.isEmpty()) {
                    bestChoice = ComputerUtilCard.getBestAI(morphs);
                }
            }

            // still nothing, so return the worst card since it'll be unplayable from exile (e.g. Vivien, Champion of the Wilds)
            return bestChoice != null ? bestChoice : ComputerUtilCard.getWorstAI(valid);
        } else if ("EmulateScry".equals(sa.getParam("AILogic"))) {
            for (Card choice : valid) {
                if (ComputerUtil.scryWillMoveCardToBottomOfLibrary(ai, choice)) {
                    return choice;
                }
            }
            return null;
        }

        if (sa.getActivatingPlayer().isOpponentOf(ai) && relatedPlayer.isOpponentOf(ai)) {
            return ComputerUtilCard.getWorstPermanentAI(valid, false, true, false, false);
        }
        return ComputerUtilCard.getBestAI(valid);
    }

    /* (non-Javadoc)
     * @see forge.card.ability.SpellAbilityAi#chooseSinglePlayer(forge.game.player.Player, forge.card.spellability.SpellAbility, java.util.List)
     */
    @Override
    public Player chooseSinglePlayer(Player ai, SpellAbility sa, Iterable<Player> options, Map<String, Object> params) {
        if (params != null && params.containsKey("Attacker")) {
            return (Player) ComputerUtilCombat.addAttackerToCombat(sa, (Card) params.get("Attacker"), options);
        }
        // an opponent choose a card from
        return Iterables.getFirst(options, null);
    }

    @Override
    protected GameEntity chooseSingleAttackableEntity(Player ai, SpellAbility sa, Iterable<GameEntity> options, Map<String, Object> params) {
        if (params != null && params.containsKey("Attacker")) {
            return ComputerUtilCombat.addAttackerToCombat(sa, (Card) params.get("Attacker"), options);
        }
        // should not be reached
        return super.chooseSingleAttackableEntity(ai, sa, options, params);
    }

    /* (non-Javadoc)
     * @see forge.card.ability.SpellAbilityAi#confirmAction(forge.card.spellability.SpellAbility, forge.game.player.PlayerActionConfirmMode, java.lang.String)
     */
    @Override
    public boolean confirmAction(Player player, SpellAbility sa, PlayerActionConfirmMode mode, String message, Map<String, Object> params) {
        Card topc = player.getZone(ZoneType.Library).get(0);

        if (ComputerUtilAbility.getAbilitySourceName(sa).equals("Explorer's Scope")) {
            // for Explorer's Scope, always put a land on the battlefield tapped
            // (TODO: might not always be a good idea, e.g. when a land ETBing can have detrimental effects)
            return true;
        } else if ("AlwaysConfirm".equals(sa.getParam("AILogic"))) {
            return true;
        }

        // looks like perfect code for Delver of Secrets, but what about other cards? 
        return topc.isInstant() || topc.isSorcery();
    }
}
