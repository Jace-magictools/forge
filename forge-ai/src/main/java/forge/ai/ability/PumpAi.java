package forge.ai.ability;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import forge.ai.*;
import forge.card.CardType;
import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.combat.CombatUtil;
import forge.game.cost.Cost;
import forge.game.cost.CostTapType;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class PumpAi extends PumpAiBase {

    @Override
    protected boolean checkAiLogic(final Player ai, final SpellAbility sa, final String aiLogic) {
        if ("MoveCounter".equals(aiLogic)) {
            final Game game = ai.getGame();
            List<Card> tgtCards = CardLists.filter(game.getCardsIn(ZoneType.Battlefield),
                    CardPredicates.isTargetableBy(sa));
            if (tgtCards.isEmpty()) {
                return false;
            }
            SpellAbility moveSA = null;
            SpellAbility sub = sa.getSubAbility();
            while (sub != null) {
                if (ApiType.MoveCounter.equals(sub.getApi())) {
                    moveSA = sub;
                    break;
                }
                sub = sub.getSubAbility();
            }

            if (moveSA == null) {
                System.err.println("MoveCounter AiLogic without MoveCounter SubAbility!");
                return false;
            }
        } else if ("Aristocrat".equals(aiLogic)) {
            return SpecialAiLogic.doAristocratLogic(ai, sa);
        } else if (aiLogic.startsWith("AristocratCounters")) {
            return SpecialAiLogic.doAristocratWithCountersLogic(ai, sa).willingToPlay();
        } else if (aiLogic.equals("SwitchPT")) {
            // Some more AI would be even better, but this is a good start to prevent spamming
            if (sa.isActivatedAbility() && sa.getActivationsThisTurn() > 0 && !sa.usesTargeting()) {
                // Will prevent flipping back and forth
                return false;
            }
        }

        return super.checkAiLogic(ai, sa, aiLogic);
    }

    @Override
    protected boolean checkPhaseRestrictions(final Player ai, final SpellAbility sa, final PhaseHandler ph,
                                             final String logic) {
        // special Phase check for various AI logics
        if (logic.equals("MoveCounter")) {
            if (ph.inCombat() && ph.getPlayerTurn().isOpponentOf(ai)) {
                return true;
            }

            return isSorcerySpeed(sa, ai) || (ph.getNextTurn().equals(ai) && !ph.getPhase().isBefore(PhaseType.END_OF_TURN));
        } else if (logic.equals("Aristocrat")) {
            final boolean isThreatened = ComputerUtil.predictThreatenedObjects(ai, null, true).contains(sa.getHostCard());
            // magic-tools: or after combat, to eat a creature stolen until end of turn
            if (!ph.is(PhaseType.COMBAT_DECLARE_BLOCKERS) && !isThreatened
                    && !ThreatenSacAi.hasStolenFodder(ai, sa.getHostCard())) {
                return false;
            }
        } else if (logic.equals("SwitchPT")) {
            // Some more AI would be even better, but this is a good start to prevent spamming
            if (ph.getPhase().isAfter(PhaseType.COMBAT_FIRST_STRIKE_DAMAGE) || !ph.inCombat()) {
                return false;
            }
        }
        return super.checkPhaseRestrictions(ai, sa, ph);
    }

    @Override
    protected boolean checkPhaseRestrictions(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        // magic-tools: rule 28, an empty Pump that only carries an opponent target (TargetCarrierAi)
        if (TargetCarrierAi.isCarrier(sa)) {
            return TargetCarrierAi.goodTime(ai, sa, ph);
        }
        // magic-tools: rule 56, a pump that draws is cast for the card when the mana is free (CantripPumpAi)
        if (CantripPumpAi.isCantripPump(sa) && CantripPumpAi.goodTime(ai, sa, ph)) {
            return true;
        }
        final Game game = ai.getGame();
        // magic-tools: rule 187, a fight is removal, not a combat trick: after our combat (the creature that taps
        // for it did not attack), or at the end of the opponent's turn
        if (FightGrantAi.implicitFight(sa) && game.getStack().isEmpty()
                && (ph.is(PhaseType.MAIN2, ai) || ph.is(PhaseType.END_OF_TURN) && ph.getPlayerTurn().isOpponentOf(ai))) {
            return true;
        }
        // magic-tools: rule 292 (plateau, Ugin 2d95dbcc7; tester 11/11: Uruk-hai captain cast without the haste the
        // Wargs could tap to give): a haste pump goes in main 1, before attacks, when a creature that just arrived would
        // attack with it; a creature that taps for it only gives it if it would not attack itself and is worth less
        if (NewAi.on(ai, 292) && hasteBeforeAttack292(ai, sa, ph)) {
            return true;
        }
        if (game.getStack().isEmpty()) {
            boolean isBeforeMyAttack = ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS) && ph.isPlayerTurn(ai);
            boolean isBeforeOppCombat = ph.getPhase().isBefore(PhaseType.COMBAT_BEGIN) && ph.getPlayerTurn().isOpponentOf(ai);
            CostTapType tapType = sa.getPayCosts().getCostPartByType(CostTapType.class);
            if ((isBeforeMyAttack || isBeforeOppCombat) &&
                    tapType != null && (tapType.getType().startsWith("Creature") || CardType.isACreatureType(tapType.getType().split("\\.")[0]))) {
                return false;
            }
            Card host = sa.getHostCard();
            // wait until AI has decided if creature should participate in combat instead
            if (host.isCreature() && sa.getPayCosts().hasTapCost() &&
                    ((isBeforeMyAttack && CombatUtil.canAttack(host)) || (isBeforeOppCombat && CombatUtil.canBlock(host)))) {
                return false;
            }
        }
        if (game.getStack().isEmpty() && (ph.getPhase().isBefore(PhaseType.COMBAT_BEGIN)
                || ph.getPhase().isAfter(PhaseType.COMBAT_DECLARE_BLOCKERS))) {
            boolean main1Preferred = "Main1IfAble".equals(sa.getParam("AILogic")) && ph.is(PhaseType.MAIN1, ai);
            // save tricks until last moment
            return sa.isCurse() || isSorcerySpeed(sa, ai) || main1Preferred;
        }
        return true;
    }

    /** magic-tools rule 292: is it main 1 and would a summoning-sick creature attack if this pump gave it haste? */
    public static boolean hasteBeforeAttack292(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        final Game game = ai.getGame();
        if (!game.getStack().isEmpty() || !ph.is(PhaseType.MAIN1, ai) || !sa.usesTargeting()
                || sa.getParam("KW") == null || !sa.getParam("KW").contains("Haste") || sa.isCurse()) {
            return false;
        }
        final Card host = sa.getHostCard();
        final boolean tapsHost = host != null && host.isCreature() && host.isInPlay()
                && sa.getPayCosts() != null && sa.getPayCosts().hasTapCost();
        if (tapsHost) {
            if (host.isTapped() || host.hasSickness()) {
                return false;
            }
            final forge.game.combat.Combat c0 = new forge.game.combat.Combat(ai);
            new AiAttackController(ai).declareAttackers(c0);
            if (c0.isAttacking(host)) {
                return false; // the source attacks: keep it for combat
            }
        }
        for (final Card t : ai.getCreaturesInPlay()) {
            if (t == host || t.isTapped() || !t.hasSickness() || t.getNetPower() <= 0 || !sa.canTarget(t)) {
                continue;
            }
            if (tapsHost && ComputerUtilCard.evaluateCreature(t) <= ComputerUtilCard.evaluateCreature(host)) {
                continue;
            }
            // haste assumed (summoning sickness lifted) and the source tapped, for the time of the attack plan
            final boolean hostWasTapped = tapsHost && host.isTapped();
            boolean attacks;
            t.setSickness(false);
            if (tapsHost) {
                host.setTapped(true);
            }
            try {
                final forge.game.combat.Combat c1 = new forge.game.combat.Combat(ai);
                new AiAttackController(ai).declareAttackers(c1);
                attacks = c1.isAttacking(t);
            } finally {
                t.setSickness(true);
                if (tapsHost) {
                    host.setTapped(hostWasTapped);
                }
            }
            if (attacks) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected AiAbilityDecision checkApiLogic(Player ai, SpellAbility sa) {
        // magic-tools: Atog / Arcbound Ravager eat a nontoken artifact only for lethal (ArtifactSacAi)
        if (TargetCarrierAi.isCarrier(sa)) {
            return TargetCarrierAi.chooseTarget(ai, sa) ? new AiAbilityDecision(100, AiPlayDecision.WillPlay)
                    : new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }
        if (CantripPumpAi.isCantripPump(sa) && CantripPumpAi.goodTime(ai, sa, ai.getGame().getPhaseHandler())) {
            return CantripPumpAi.chooseTarget(ai, sa, ai.getGame().getPhaseHandler())
                    ? new AiAbilityDecision(100, AiPlayDecision.WillPlay)
                    : new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }
        final Boolean artifactSac = ArtifactSacAi.decide(ai, sa);
        if (artifactSac != null) {
            return artifactSac ? new AiAbilityDecision(100, AiPlayDecision.WillPlay)
                    : new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        final Game game = ai.getGame();
        final Card source = sa.getHostCard();
        final SpellAbility root = sa.getRootAbility();
        final List<String> keywords = sa.hasParam("KW") ? Arrays.asList(sa.getParam("KW").split(" & "))
                : Lists.newArrayList();
        final String numDefense = sa.getParamOrDefault("NumDef", "");
        final String numAttack = sa.getParamOrDefault("NumAtt", "");

        final String aiLogic = sa.getParamOrDefault("AILogic", "");

        final boolean isFight = "Fight".equals(aiLogic) || "PowerDmg".equals(aiLogic)
                || FightGrantAi.implicitFight(sa); // magic-tools: rule 187
        final boolean isBerserk = "Berserk".equals(aiLogic);

        if ("Pummeler".equals(aiLogic)) {
            return SpecialCardAi.ElectrostaticPummeler.consider(ai, sa);
        } else if (aiLogic.startsWith("AristocratCounters")) {
            // the preconditions to this are already tested in checkAiLogic
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        } else if ("Aristocrat".equals(aiLogic) && ThreatenSacAi.hasStolenFodder(ai, source)) {
            // magic-tools: tested in checkAiLogic; the pump itself does not matter after combat
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        } else if ("GideonBlackblade".equals(aiLogic)) {
            return SpecialCardAi.GideonBlackblade.consider(ai, sa);
        } else if ("MoveCounter".equals(aiLogic)) {
            final SpellAbility moveSA = sa.findSubAbilityByType(ApiType.MoveCounter);

            if (moveSA == null) {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }

            final String counterType = moveSA.getParam("CounterType");
            final String amountStr = moveSA.getParamOrDefault("CounterNum", "1");
            final CounterType cType = "Any".equals(counterType) ? null : CounterType.getType(counterType);

            final PhaseHandler ph = game.getPhaseHandler();
            if (ph.inCombat() && ph.getPlayerTurn().isOpponentOf(ai)) {
                CardCollection attr = ph.getCombat().getAttackers();
                attr = CardLists.getTargetableCards(attr, sa);

                if (cType != null) {
                    attr = CardLists.filter(attr, CardPredicates.hasCounter(cType));
                    if (attr.isEmpty()) {
                        return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
                    }
                    CardCollection best = CardLists.filter(attr, card -> {
                        int amount = 0;
                        if (StringUtils.isNumeric(amountStr)) {
                            amount = AbilityUtils.calculateAmount(source, amountStr, moveSA);
                        } else if (source.hasSVar(amountStr)) {
                            if ("Count$ChosenNumber".equals(source.getSVar(amountStr))) {
                                amount = card.getCounters(cType);
                            }
                        }

                        int i = card.getCounters(cType);
                        if (i < amount) {
                            return false;
                        }

                        final Card srcCardCpy = CardCopyService.getLKICopy(card);
                        // can't use subtract on Copy
                        srcCardCpy.setCounters(cType, srcCardCpy.getCounters(cType) - amount);

                        if (cType.is(CounterEnumType.P1P1) && srcCardCpy.getNetToughness() <= 0) {
                            return srcCardCpy.getCounters(cType) > 0 || !card.hasKeyword(Keyword.UNDYING)
                                    || card.isToken();
                        }
                        return false;
                    });

                    if (best.isEmpty()) {
                        best = attr;
                    }

                    final Card card = ComputerUtilCard.getBestCreatureAI(best);
                    sa.getTargets().add(card);
                    return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                }
            } else {
                final boolean sameCtrl = moveSA.getTargetRestrictions().isSameController();

                List<Card> list = CardLists.getTargetableCards(game.getCardsIn(ZoneType.Battlefield), sa);
                if (cType != null) {
                    list = CardLists.filter(list, CardPredicates.hasCounter(cType));
                    if (list.isEmpty()) {
                        return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
                    }
                    List<Card> oppList = CardLists.filterControlledBy(list, ai.getOpponents());
                    if (!oppList.isEmpty() && !sameCtrl) {
                        List<Card> best = CardLists.filter(oppList, card -> {
                            int amount = 0;
                            if (StringUtils.isNumeric(amountStr)) {
                                amount = AbilityUtils.calculateAmount(source, amountStr, moveSA);
                            } else if (source.hasSVar(amountStr)) {
                                if ("Count$ChosenNumber".equals(source.getSVar(amountStr))) {
                                    amount = card.getCounters(cType);
                                }
                            }

                            int i = card.getCounters(cType);
                            if (i < amount) {
                                return false;
                            }

                            final Card srcCardCpy = CardCopyService.getLKICopy(card);
                            // can't use subtract on Copy
                            srcCardCpy.setCounters(cType, srcCardCpy.getCounters(cType) - amount);

                            if (cType.is(CounterEnumType.P1P1) && srcCardCpy.getNetToughness() <= 0) {
                                return srcCardCpy.getCounters(cType) > 0 || !card.hasKeyword(Keyword.UNDYING)
                                        || card.isToken();
                            }
                            return true;
                        });

                        if (best.isEmpty()) {
                            best = oppList;
                        }

                        final Card card = ComputerUtilCard.getBestCreatureAI(best);
                        sa.getTargets().add(card);
                        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                    }
                }

            }
        } else if (aiLogic.startsWith("Donate")) {
            // Donate step 1 - try to target an opponent, preferably one who does not have a donate target yet
            return SpecialCardAi.Donate.considerTargetingOpponent(ai, sa);
        } else if (aiLogic.equals("InfernoOfTheStarMounts")) {
            int numRedMana = ComputerUtilMana.determineLeftoverMana(new SpellAbility.EmptySa(source), ai, "R", false);
            int currentPower = source.getNetPower();
            if (currentPower < 20 && currentPower + numRedMana >= 20) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
        }

        if (!game.getStack().isEmpty() && !sa.isCurse() && !isFight) {
            // magic-tools: Undying Evil answers removal (UndyingSaveAi)
            if (sa.usesTargeting() && UndyingSaveAi.grantsUndying(keywords) && UndyingSaveAi.chooseTarget(ai, sa)) {
                return new AiAbilityDecision(100, AiPlayDecision.ResponseToStackResolve);
            }
            return ComputerUtilCard.canPumpAgainstRemoval(ai, sa);
        }

        if (sa.hasParam("ConditionActivationLimit")) {
            final int sacActivations = Integer.parseInt(sa.getParam("ConditionActivationLimit").substring(2));
            final int activations = sa.getActivationsThisTurn();
            // don't risk sacrificing a creature just to pump it
            if (activations >= sacActivations - 1) {
                return new AiAbilityDecision(0, AiPlayDecision.ConditionsNotMet);
            }
        }

        if (sa.getSVar("X").equals("Count$xPaid") && !forge.ai.XTargets315Ai.keepX(ai, sa)) { // magic-tools: rule 315
            root.setXManaCostPaid(null);
        }

        int defense;
        if (numDefense.contains("X") && sa.getSVar("X").equals("Count$xPaid")) {
            defense = ComputerUtilCost.setMaxXValue(sa, ai, sa.isTrigger());
            if (numDefense.equals("-X")) {
                defense = -defense;
            }
        } else {
            defense = DoublePumpAi.fixedAmount(sa, numDefense); // magic-tools: rule 189
            if (numDefense.contains("X") && sa.getSVar("X").equals("Count$CardsInYourHand") && source.isInZone(ZoneType.Hand)) {
                defense--; // the card will be spent casting the spell, so actual toughness is 1 less
            }
        }

        int attack;
        if (numAttack.contains("X") && sa.getSVar("X").equals("Count$xPaid")) {
            if (root.getXManaCostPaid() == null) {
                attack = ComputerUtilCost.setMaxXValue(root, ai, sa.isTrigger());
            } else {
                attack = root.getXManaCostPaid();
            }
        } else {
            attack = DoublePumpAi.fixedAmount(sa, numAttack); // magic-tools: rule 189
            if (numAttack.contains("X") && sa.getSVar("X").equals("Count$CardsInYourHand") && source.isInZone(ZoneType.Hand)) {
                attack--; // the card will be spent casting the spell, so actual power is 1 less
            }
        }

        if ((numDefense.contains("X") && defense == 0) || (numAttack.contains("X") && attack == 0 && !isBerserk)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        if (!sa.usesTargeting()) {
            final List<Card> cards = AbilityUtils.getDefinedCards(sa.getHostCard(), sa.getParam("Defined"), sa);

            if (cards.isEmpty()) {
                return new AiAbilityDecision(0, AiPlayDecision.MissingNeededCards);
            }

            // when this happens we need to expand AI to consider if its ok for everything?
            for (final Card card : cards) {
                if (sa.isCurse()) {
                    if (!card.getController().isOpponentOf(ai)) {
                        continue;
                    }

                    if (!containsUsefulKeyword(ai, keywords, card, sa, attack)) {
                        continue;
                    }

                    return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                }
                if (!card.getController().isOpponentOf(ai)) {
                    if (ComputerUtilCard.shouldPumpCard(ai, sa, card, DoublePumpAi.defenseOn(sa, card, defense),
                            DoublePumpAi.attackOn(sa, card, attack), keywords, false)) { // magic-tools: rule 189
                        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                    }
                    if (containsUsefulKeyword(ai, keywords, card, sa, attack)) {
                        if (game.getPhaseHandler().is(PhaseType.MAIN1) && isSorcerySpeed(sa, ai) ||
                                game.getPhaseHandler().is(PhaseType.COMBAT_DECLARE_ATTACKERS, ai) ||
                                game.getPhaseHandler().is(PhaseType.COMBAT_BEGIN, ai)) {
                            Card pumped = ComputerUtilCard.getPumpedCreature(ai, sa, card, 0, 0, keywords);
                            if (ComputerUtilCard.doesSpecifiedCreatureAttackAI(ai, pumped)) {
                                // If the AI can attack with the pumped creature, then it is worth playing
                                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                            }
                            return new AiAbilityDecision(0, AiPlayDecision.DoesntImpactCombat);
                        }

                        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                    }
                    if (grantsUsefulExtraBlockOpts(ai, sa, card, keywords)) {
                        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                    }
                }
            }
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        if (pumpTgtAI(ai, sa, defense, attack, false, false)) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }
        // magic-tools: rule 221
        if (castForRiderAlone(ai, sa, defense, attack, keywords)) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }

        return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
    }

    /**
     * magic-tools: rule 221. "Up to one target creature gets -2/-2. Amass 2": the second half is worth the card
     * whatever the first one finds. When no creature is worth cursing, cast it without a target in main 2.
     * Only for riders that do not read the target; a target worth cursing keeps the normal way, which may hold it.
     */
    private boolean castForRiderAlone(final Player ai, final SpellAbility sa, final int defense, final int attack,
                                      final List<String> keywords) {
        final Game game = ai.getGame();
        if (!sa.isSpell() || !sa.isCurse() || sa.getMinTargets() != 0 || !game.getStack().isEmpty()
                || !game.getPhaseHandler().is(PhaseType.MAIN2, ai)) {
            return false;
        }
        final SpellAbility rider = sa.getSubAbility();
        if (rider == null || rider.getApi() == null || rider.hasParam("Condition") || rider.hasParam("ConditionCheckSVar")) {
            return false;
        }
        switch (rider.getApi()) {
            case Amass:
            case Draw:
            case Discover:
                break;
            case Token:
                if (!"You".equals(rider.getParamOrDefault("TokenOwner", "You"))) {
                    return false;
                }
                break;
            default:
                return false;
        }
        if (!getCurseCreatures(ai, sa, defense, attack, keywords).isEmpty()) {
            return false;
        }
        sa.resetTargets();
        return true;
    }

    private boolean pumpTgtAI(final Player ai, final SpellAbility sa, final int defense, final int attack, final boolean mandatory,
                              boolean immediately) {
        final List<String> keywords = sa.hasParam("KW") ? Arrays.asList(sa.getParam("KW").split(" & "))
                : Lists.newArrayList();
        final Game game = ai.getGame();
        final Card source = sa.getHostCard();
        final boolean isFight = "Fight".equals(sa.getParam("AILogic")) || "PowerDmg".equals(sa.getParam("AILogic"))
                || FightGrantAi.implicitFight(sa); // magic-tools: rule 187

        immediately = immediately || ComputerUtil.playImmediately(ai, sa);

        if (!mandatory
                && !immediately
                && (game.getPhaseHandler().getPhase().isAfter(PhaseType.COMBAT_DECLARE_BLOCKERS) && !"AnyPhase".equals(sa.getParam("AILogic")))
                && !(sa.isCurse() && defense < 0)
                && !containsNonCombatKeyword(keywords)
                && !"UntilYourNextTurn".equals(sa.getParam("Duration"))
                && !"ReplaySpell".equals(sa.getParam("AILogic"))
                && !isFight) {
            return false;
        }

        final TargetRestrictions tgt = sa.getTargetRestrictions();
        sa.resetTargets();

        if ("PowerStruggle".equals(sa.getParam("AILogic"))) {
            return SpecialCardAi.PowerStruggle.considerFirstTarget(ai, sa);
        }

        if (sa.hasParam("TargetingPlayer") && sa.getActivatingPlayer().equals(ai) && !sa.isTrigger()) {
            Player targetingPlayer = AbilityUtils.getDefinedPlayers(source, sa.getParam("TargetingPlayer"), sa).get(0);
            sa.setTargetingPlayer(targetingPlayer);
            if (CardLists.getTargetableCards(ai.getGame().getCardsIn(sa.getTargetRestrictions().getZone()), sa).isEmpty()) {
                return false;
            }
            return true;
        }

        // magic-tools: rule 187, a Pump without AILogic that carries a Fight (Wayta, Prey Upon)
        if (FightGrantAi.implicitFight(sa)) {
            return FightAi.canFight(ai, sa, attack, defense).willingToPlay();
        }

        CardCollection list;
        if (sa.hasParam("AILogic")) {
            if (sa.getParam("AILogic").equals("HighestPower") || sa.getParam("AILogic").equals("ContinuousBonus")) {
                list = CardLists.getValidCards(CardLists.filter(game.getCardsIn(ZoneType.Battlefield), CardPredicates.CREATURES), tgt.getValidTgts(), ai, source, sa);
                list = CardLists.getTargetableCards(list, sa);
                CardLists.sortByPowerDesc(list);

                if (list.contains(source) && source.hasKeyword("You may choose not to untap CARDNAME during your untap step.") && sa.getPayCosts().hasTapCost()) {
                    list.remove(source); // don't tap a card that will be tapped as a part of the cost and won't untap normally.
                }

                // Try not to kill own creatures with this pump
                CardCollection canDieToPump = new CardCollection();
                for (Card c : list) {
                    if (c.isCreature() && c.getController() == ai
                            && c.getNetToughness() - c.getTempToughnessBoost() + defense <= 0) {
                        canDieToPump.add(c);
                    }
                    // Also, don't pump itself if the SA involves a sacrifice self cost
                    if (sa.getHostCard().equals(c) && ComputerUtilCost.isSacrificeSelfCost(sa.getPayCosts())) {
                        canDieToPump.add(c);
                    }
                }
                list.removeAll(canDieToPump);

                // Generally, don't pump anything that your opponents control
                if ("ContinuousBonus".equals(sa.getParam("AILogic"))) {
                    // TODO: make it possible for the AI to use this logic to kill opposing creatures
                    // when a toughness debuff is applied
                    list = CardLists.filter(list, CardPredicates.isController(ai));
                }

                if (!list.isEmpty()) {
                    sa.getTargets().add(list.get(0));
                    return true;
                }
                return false;
            } else if (sa.getParam("AILogic").equals("SameName")) {
                return doSameNameLogic(ai, sa);
            } else if (sa.getParam("AILogic").equals("SacOneEach")) {
                // each player sacrifices one permanent, e.g. Vaevictis, Asmadi the Dire - grab the worst for allied and
                // the best for opponents
                return SacrificeAi.doSacOneEachLogic(ai, sa).willingToPlay();
            } else if (sa.getParam("AILogic").equals("Destroy")) {
                List<Card> tgts = CardLists.getTargetableCards(game.getCardsIn(ZoneType.Battlefield), sa);
                if (tgts.isEmpty()) {
                    return false;
                }

                List<Card> alliedTgts = CardLists.filter(tgts, CardPredicates.isControlledByAnyOf(ai.getAllies()).or(CardPredicates.isController(ai)));
                List<Card> oppTgts = CardLists.filter(tgts, CardPredicates.isControlledByAnyOf(ai.getOpponents()));

                Card destroyTgt = null;
                if (!oppTgts.isEmpty()) {
                    destroyTgt = ComputerUtilCard.getBestAI(oppTgts);
                } else {
                    // TODO: somehow limit this so that the AI doesn't always destroy own stuff when able?
                    destroyTgt = ComputerUtilCard.getWorstAI(alliedTgts);
                }

                if (destroyTgt != null) {
                    sa.getTargets().add(destroyTgt);
                    return true;
                }

                return false;
            }

            if (isFight) {
                return FightAi.canFight(ai, sa, attack, defense).willingToPlay();
            }
        }

        if (sa.isCurse()) {
            for (final Player opp : ai.getOpponents()) {
                if (sa.canTarget(opp)) {
                    sa.getTargets().add(opp);
                    return true;
                }
            }
            list = getCurseCreatures(ai, sa, defense, attack, keywords);
        } else {
            if (sa.canTarget(ai)) {
                sa.getTargets().add(ai);
                return true;
            }
            if (tgt.canTgtCreature()) {
                list = getPumpCreatures(ai, sa, defense, attack, keywords, immediately);
            } else {
                list = CardLists.getTargetableCards(game.getCardsIn(tgt.getZone()), sa);
            }
        }

        if (game.getStack().isEmpty() && sa.getPayCosts().hasTapCost()) {
            if (game.getPhaseHandler().getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS)
                    && game.getPhaseHandler().isPlayerTurn(ai)) {
                list.remove(source);
            }
            if (game.getPhaseHandler().getPhase().isBefore(PhaseType.COMBAT_DECLARE_BLOCKERS)
                    && game.getPhaseHandler().getPlayerTurn().isOpponentOf(ai)) {
                list.remove(source);
            }
        }

        // Filter AI-specific targets if provided
        list = ComputerUtil.filterAITgts(sa, ai, list, true);

        if (list.isEmpty() && (mandatory || ComputerUtil.activateForCost(sa, ai))) {
            return pumpMandatoryTarget(ai, sa);
        }

        if (!sa.isCurse()) {
            // Don't target cards that will die.
            list = ComputerUtil.getSafeTargets(ai, sa, list);
        }

        if ("BetterCreatureThanSource".equals(sa.getParam("AILogic"))) {
            // Don't target cards that are not better in value than the targeting card
            final int sourceValue = ComputerUtilCard.evaluateCreature(source);
            list = CardLists.filter(list, card -> card.isCreature() && ComputerUtilCard.evaluateCreature(card) > sourceValue + 30);
        }

        if ("ReplaySpell".equals(sa.getParam("AILogic"))) {
            if (!ComputerUtil.targetPlayableSpellCard(ai, list, sa, false, mandatory)) {
                return false;
            }
        }

        while (sa.canAddMoreTarget()) {
            Card t = null;
            // boolean goodt = false;

            list = CardLists.canSubsequentlyTarget(list, sa);

            if (list.isEmpty()) {
                if (!sa.isMinTargetChosen() || sa.isZeroTargets()) {
                    if (mandatory || ComputerUtil.activateForCost(sa, ai)) {
                        return pumpMandatoryTarget(ai, sa);
                    }

                    sa.resetTargets();
                    return false;
                } else {
                    // TODO is this good enough? for up to amounts?
                    break;
                }
            }

            t = ComputerUtilCard.getBestAI(list);
            //option to hold removal instead only applies for single targeted removal
            if (!immediately && sa.getMaxTargets() == 1 && sa.isCurse() && defense < 0) {
                if (!ComputerUtilCard.useRemovalNow(sa, t, -defense, ZoneType.Graveyard)
                        && !ComputerUtil.activateForCost(sa, ai)) {
                    return false;
                }
            }
            sa.getTargets().add(t);
            list.remove(t);
        }

        return true;
    }

    private boolean pumpMandatoryTarget(final Player ai, final SpellAbility sa) {
        List<Card> list = CardUtil.getValidCardsToTarget(sa);

        if (list.size() < sa.getMinTargets()) {
            sa.resetTargets();
            return false;
        }

        CardCollection pref;
        CardCollection forced;

        if (sa.isCurse()) {
            pref = CardLists.filterControlledBy(list, ai.getOpponents());
            forced = CardLists.filterControlledBy(list, ai.getYourTeam());
        } else {
            pref = CardLists.filterControlledBy(list, ai.getYourTeam());
            forced = CardLists.filterControlledBy(list, ai.getOpponents());
        }

        while (sa.canAddMoreTarget()) {
            if (pref.isEmpty()) {
                break;
            }

            Card c = ComputerUtilCard.getBestAI(pref);
            pref.remove(c);
            sa.getTargets().add(c);
        }

        while (!sa.isMinTargetChosen()) {
            if (forced.isEmpty()) {
                break;
            }

            Card c;
            if (CardLists.getNotType(forced, "Creature").isEmpty()) {
                c = ComputerUtilCard.getWorstCreatureAI(forced);
            } else {
                c = ComputerUtilCard.getCheapestPermanentAI(forced, sa, false);
            }

            forced.remove(c);
            sa.getTargets().add(c);
        }

        if (!sa.isMinTargetChosen()) {
            sa.resetTargets();
            return false;
        }

        return true;
    }

    @Override
    protected AiAbilityDecision doTriggerNoCost(Player ai, SpellAbility sa, boolean mandatory) {
        final SpellAbility root = sa.getRootAbility();
        final String numDefense = sa.getParamOrDefault("NumDef", "");
        final String numAttack = sa.getParamOrDefault("NumAtt", "");

        if (sa.getSVar("X").equals("Count$xPaid")) {
            sa.setXManaCostPaid(null);
        }

        int defense;
        if (numDefense.contains("X") && sa.getSVar("X").equals("Count$xPaid")) {
            // Set PayX here to maximum value.
            if (root.getXManaCostPaid() == null) {
                defense = ComputerUtilCost.setMaxXValue(root, ai, true);
            } else {
                defense = root.getXManaCostPaid();
            }
        } else {
            defense = DoublePumpAi.fixedAmount(sa, numDefense); // magic-tools: rule 189
        }

        int attack;
        if (numAttack.contains("X") && sa.getSVar("X").equals("Count$xPaid")) {
            // Set PayX here to maximum value.
            if (root.getXManaCostPaid() == null) {
                attack = ComputerUtilCost.setMaxXValue(root, ai, true);
            } else {
                attack = root.getXManaCostPaid();
            }
        } else {
            attack = DoublePumpAi.fixedAmount(sa, numAttack); // magic-tools: rule 189
        }

        if (!sa.usesTargeting()) {
            if (mandatory) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        if (pumpTgtAI(ai, sa, defense, attack, mandatory, true)) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }

        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    @Override
    public AiAbilityDecision chkDrawback(Player ai, SpellAbility sa) {
        final SpellAbility root = sa.getRootAbility();
        final Card source = sa.getHostCard();

        final String numDefense = sa.getParamOrDefault("NumDef", "");
        final String numAttack = sa.getParamOrDefault("NumAtt", "");

        if (numDefense.equals("-X") && sa.getSVar("X").equals("Count$ChosenNumber")) {
            int energy = ai.getCounters(CounterEnumType.ENERGY);
            for (SpellAbility s : source.getSpellAbilities()) {
                if ("PayEnergy".equals(s.getParam("AILogic"))) {
                    energy += AbilityUtils.calculateAmount(source, s.getParam("CounterNum"), sa);
                    break;
                }
            }
            int minus = 0;
            for (; energy > 0; energy--) {
                if (pumpTgtAI(ai, sa, -energy, -energy, false, true)) {
                    minus = sa.getTargetCard().getNetToughness();
                    if (minus > energy || minus < 1) {
                        continue; // in case the calculation gets messed up somewhere
                    }
                    root.setSVar("EnergyToPay", "Number$" + minus);
                    return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                }
            }
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        int attack;
        if (numAttack.contains("X") && sa.getSVar("X").equals("Count$xPaid")) {
            if (root.getXManaCostPaid() == null) {
                attack = ComputerUtilCost.setMaxXValue(sa, ai, sa.isTrigger());
                root.setXManaCostPaid(attack);
            } else {
                attack = root.getXManaCostPaid();
            }
        } else {
            attack = DoublePumpAi.fixedAmount(sa, numAttack); // magic-tools: rule 189
        }

        int defense;
        if (numDefense.contains("X") && sa.getSVar("X").equals("Count$xPaid")) {
            if (root.getXManaCostPaid() == null) {
                defense = ComputerUtilCost.setMaxXValue(sa, ai, sa.isTrigger());
            } else {
                defense = root.getXManaCostPaid();
            }
        } else {
            defense = DoublePumpAi.fixedAmount(sa, numDefense); // magic-tools: rule 189
        }

        if (sa.usesTargeting()) {
            if (pumpTgtAI(ai, sa, defense, attack, false, true)) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }

        if (source.isCreature()) {
            if (!source.hasKeyword(Keyword.INDESTRUCTIBLE) && source.getNetToughness() + defense <= source.getDamage()) {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }
            if (source.getNetToughness() + defense > 0) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    @Override
    public boolean confirmAction(Player player, SpellAbility sa, PlayerActionConfirmMode mode, String message, Map<String, Object> params) {
        //TODO Add logic here if necessary but I think the AI won't cast
        //the spell in the first place if it would curse its own creature
        //and the pump isn't mandatory
        return true;
    }

    private boolean doSameNameLogic(Player aiPlayer, SpellAbility sa) {
        final Game game = aiPlayer.getGame();
        final Card source = sa.getHostCard();
        final TargetRestrictions tgt = sa.getTargetRestrictions();
        final ZoneType origin = ZoneType.listValueOf(sa.getSubAbility().getParam("Origin")).get(0);
        CardCollection list = CardLists.getValidCards(game.getCardsIn(origin), tgt.getValidTgts(), aiPlayer,
                source, sa);
        list = CardLists.filterControlledBy(list, aiPlayer.getOpponents());
        if (list.isEmpty()) {
            return false; // no valid targets
        }

        Map<Player, Map.Entry<String, Integer>> data = Maps.newHashMap();

        // need to filter for the opponents first
        for (final Player opp : aiPlayer.getOpponents()) {
            CardCollection oppList = CardLists.filterControlledBy(list, opp);

            // no cards
            if (oppList.isEmpty()) {
                continue;
            }

            // Compute value for each possible target
            Map<String, Integer> values = ComputerUtilCard.evaluateCreatureListByName(oppList);

            // reject if none of them can be targeted
            oppList = CardLists.filter(oppList, CardPredicates.isTargetableBy(sa));
            // none can be targeted
            if (oppList.isEmpty()) {
                continue;
            }

            List<String> toRemove = Lists.newArrayList();
            for (final String name : values.keySet()) {
                if (!oppList.anyMatch(CardPredicates.nameEquals(name))) {
                    toRemove.add(name);
                }
            }
            values.keySet().removeAll(toRemove);

            data.put(opp, Collections.max(values.entrySet(), Map.Entry.comparingByValue()));
        }

        if (!data.isEmpty()) {
            Map.Entry<Player, Map.Entry<String, Integer>> max = Collections.max(data.entrySet(), Comparator.comparingInt(o -> o.getValue().getValue()));

            // filter list again by the opponent and a creature of the wanted name that can be targeted
            list = CardLists.filter(CardLists.filterControlledBy(list, max.getKey()),
                    CardPredicates.nameEquals(max.getValue().getKey()), CardPredicates.isTargetableBy(sa));

            // list should contain only one element or zero
            sa.resetTargets();
            for (Card c : list) {
                sa.getTargets().add(c);
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean willPayCosts(final Player payer, final SpellAbility sa, final Cost cost, final Card source) {
        if (!ComputerUtilCost.checkExileFromGraveCost(cost, payer, sa)) {
            return false;
        }

        return super.willPayCosts(payer,sa, cost, source);
    }
}
