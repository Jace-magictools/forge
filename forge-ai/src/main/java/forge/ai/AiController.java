/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.ai;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import forge.ai.AiCardMemory.MemorySet;
import forge.ai.ability.ChangeZoneAi;
import forge.ai.ability.LearnAi;
import forge.ai.simulation.GameStateEvaluator;
import forge.ai.simulation.OnePlaySafetyChecker;
import forge.ai.simulation.SpellAbilityPicker;
import forge.card.CardStateName;
import forge.card.CardType;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.card.mana.ManaCost;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.*;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.ability.SpellApiBased;
import forge.game.card.*;
import forge.game.combat.AttackRequirement;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.cost.*;
import forge.game.keyword.Keyword;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.PlayerCollection;
import forge.game.replacement.ReplaceMoved;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementLayer;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.*;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityDisableTriggers;
import forge.game.staticability.StaticAbilityMode;
import forge.game.staticability.StaticAbilityMustTarget;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.*;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static forge.ai.ComputerUtilMana.getAvailableManaEstimate;
import static java.lang.Math.max;

/**
 * <p>
 * AiController class.
 * </p>
 *
 * @author Forge
 * @version $Id$
 */
public class AiController {
    private final Player player;
    private final Game game;
    private final AiCardMemory memory;
    private Combat predictedCombat;
    private Combat predictedCombatNextTurn;
    private AIOption simMode;
    private SpellAbilityPicker simPicker;
    private int lastAttackAggression;
    private boolean useLivingEnd;
    private List<SpellAbility> skipped;

    public AiController(final Player computerPlayer, final Game game0) {
        player = computerPlayer;
        game = game0;
        memory = new AiCardMemory();
        simPicker = new SpellAbilityPicker(player);
    }

    public boolean usesHybridSimulation() {
        return simMode == AIOption.USE_HYBRID_SIMULATION;
    }
    public boolean usesFullSimulation() {
        return simMode == AIOption.USE_FULL_SIMULATION;
    }
    public void setUseSimulation(AIOption mode) {
        simMode = mode;
    }

    public int getAttackAggression() {
        return lastAttackAggression;
    }

    public SpellAbilityPicker getSimulationPicker() {
        return simPicker;
    }

    public Game getGame() {
        return game;
    }

    public Player getPlayer() {
        return player;
    }

    public AiCardMemory getCardMemory() {
        return memory;
    }

    public Combat getPredictedCombat() {
        if (predictedCombat == null) {
            AiAttackController aiAtk = new AiAttackController(player);
            predictedCombat = new Combat(player);
            aiAtk.declareAttackers(predictedCombat);
            // ignoring attack costs for performance
        }
        return predictedCombat;
    }

    public Combat getPredictedCombatNextTurn() {
        if (predictedCombatNextTurn == null) {
            AiAttackController aiAtk = new AiAttackController(player, true);
            predictedCombatNextTurn = new Combat(player);
            aiAtk.declareAttackers(predictedCombatNextTurn);
        }
        return predictedCombatNextTurn;
    }

    private List<SpellAbility> getPossibleETBCounters() {
        CardCollection all = new CardCollection(player.getCardsIn(ZoneType.Hand));
        CardCollectionView ccvPlayerLibrary = player.getCardsIn(ZoneType.Library);

        all.addAll(player.getCardsIn(ZoneType.Exile));
        all.addAll(player.getCardsIn(ZoneType.Graveyard));
        if (!ccvPlayerLibrary.isEmpty()) {
            all.add(ccvPlayerLibrary.get(0));
        }

        all.addAll(player.getOpponents().getCardsIn(ZoneType.Exile));

        final List<SpellAbility> spellAbilities = Lists.newArrayList();
        for (final Card c : all) {
            for (final SpellAbility sa : c.getNonManaAbilities()) {
                if (sa instanceof SpellPermanent) {
                    sa.setActivatingPlayer(player);
                    if (checkETBEffects(c, sa, ApiType.Counter)) {
                        spellAbilities.add(sa);
                    }
                }
            }
        }
        return spellAbilities;
    }

    // look for cards on the battlefield that should prevent the AI from using that spellability
    private boolean checkCurseEffects(final SpellAbility sa) {
        CardCollectionView ccvGameBattlefield = CardLists.filter(game.getCardsIn(ZoneType.Battlefield), CardPredicates.hasSVar("AICurseEffect"));
        for (final Card c : ccvGameBattlefield) {
            final String curse = c.getSVar("AICurseEffect");
            final Card host = sa.getHostCard();
            if ("NonActive".equals(curse) && !player.equals(game.getPhaseHandler().getPlayerTurn())) {
                return true;
            } else if ("DestroyCreature".equals(curse) && sa.isSpell() && host.isCreature()
                    && !host.hasKeyword(Keyword.INDESTRUCTIBLE)) {
                return true;
            } else if ("CounterEnchantment".equals(curse) && sa.isSpell() && host.isEnchantment() && sa.isCounterableBy(null)) {
                return true;
            } else if ("ChaliceOfTheVoid".equals(curse) && sa.isSpell() && sa.isCounterableBy(null)
                    && host.getCMC() == c.getCounters(CounterEnumType.CHARGE)) {
                return true;
            } else if ("BazaarOfWonders".equals(curse) && sa.isSpell() && sa.isCounterableBy(null)) {
                String hostName = host.getName();
                for (Card card : ccvGameBattlefield) {
                    if (!card.isToken() && card.sharesNameWith(host)) {
                        return true;
                    }
                }
                if (game.getCardsIn(ZoneType.Graveyard).anyMatch(CardPredicates.nameEquals(hostName))) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkETBEffects(final Card card, final SpellAbility sa, final ApiType api) {
        boolean reset = false;
        if (card.getCastSA() == null) {
            // for xPaid stuff
            card.setCastSA(sa);
            reset = true;
        }
        boolean result = checkETBEffectsPreparedCard(card, sa, api);
        if (reset) {
            card.setCastSA(null);
        }
        return result;
    }

    private boolean checkETBEffectsPreparedCard(final Card card, final SpellAbility sa, final ApiType api) {
        final Player activator = sa.getActivatingPlayer();

        // Replacement effects
        for (final ReplacementEffect re : card.getReplacementEffects()) {
            // These Replacements all care for ETB effects
            if (!(re instanceof ReplaceMoved)) {
                continue;
            }

            if (!ZoneType.Battlefield.toString().equals(re.getParam("Destination"))) {
                continue;
            }

            if (re.hasParam("ValidCard")) {
                String validCard = re.getParam("ValidCard");
                if (!validCard.contains("Self")) {
                    continue;
                }
                if (validCard.contains("!kicked")) {
                    if (sa.isKicked()) {
                        continue;
                    }
                } else if (validCard.contains("kicked")) {
                    if (validCard.contains("kicked ")) { // want a specific kicker
                        String s = validCard.split("kicked ")[1];
                        if ("1".equals(s) && !sa.isOptionalCostPaid(OptionalCost.Kicker1)) continue;
                        if ("2".equals(s) && !sa.isOptionalCostPaid(OptionalCost.Kicker2)) continue;
                    } else if (!sa.isKicked()) { // otherwise just any must be present
                        continue;
                    }
                }
                if (validCard.contains("PromisedGift") && !sa.isOptionalCostPaid(OptionalCost.PromiseGift)) {
                    continue;
                }
            }

            if (!re.requirementsCheck(game)) {
                continue;
            }
            SpellAbility exSA = re.getOverridingAbility();

            if (exSA != null) {
                exSA = exSA.copy(activator);

                // These checks only work if the Executing SpellAbility is an Ability_Sub.
                if (exSA instanceof AbilitySub && !doTrigger(exSA, false)) {
                    // magic-tools: rule 62, "as this enters, choose Sultai or Abzan" (Hollowmurk Siege)
                    if (api == null && HarmlessEtbAi.isHarmless(exSA)) {
                        continue;
                    }
                    // magic-tools: rule 119, an optional "enter as a copy" with nothing to copy just isn't used:
                    // the creature still enters with its own body (Ezri Dax, Newly Joined, 2/2 flash, never cast)
                    if (api == null && re.hasParam("Optional") && card.isCreature() && card.getNetToughness() > 0) {
                        continue;
                    }
                    return false;
                }
            }
        }

        boolean rightapi = api == null;
        // magic-tools: rule 63, an evoked creature is sacrificed at once: its ETB is all we get for a card or more
        // (Solitude evoked on turn 1 with no creature to exile), so no "body is enough" escape and a real target needed
        final boolean evoked = api == null && sa.isEvoke();
        boolean evokeUseful = false;

        // Trigger play improvements
        for (final Trigger tr : card.getTriggers()) {
            // These triggers all care for ETB effects

            if (tr.getMode() != TriggerType.ChangesZone) {
                continue;
            }

            // can't fetch partner isn't problematic
            if (tr.isKeyword(Keyword.PARTNER_WITH)) {
                continue;
            }

            if (!ZoneType.Battlefield.toString().equals(tr.getParam("Destination"))) {
                continue;
            }

            final Map<AbilityKey, Object> runParams = AbilityKey.mapFromCard(tr.getHostCard());
            runParams.put(AbilityKey.Destination, ZoneType.Battlefield.name());
            if (StaticAbilityDisableTriggers.disabled(game, tr, runParams)) {
                return api == null;
            }

            if (tr.hasParam("ValidCard")) {
                String validCard = tr.getParam("ValidCard");
                if (!validCard.contains("Self")) {
                    continue;
                }
                if (validCard.contains("!kicked")) {
                    if (sa.isKicked()) {
                        continue;
                    }
                } else if (validCard.contains("kicked")) {
                    if (validCard.contains("kicked ")) { // want a specific kicker
                        String s = validCard.split("kicked ")[1];
                        if ("1".equals(s) && !sa.isOptionalCostPaid(OptionalCost.Kicker1)) continue;
                        if ("2".equals(s) && !sa.isOptionalCostPaid(OptionalCost.Kicker2)) continue;
                    } else if (!sa.isKicked()) {
                        continue;
                    }
                }
                // magic-tools: rule 45, an ETB that needs the gift (Scrapshooter, 33 cards) doesn't happen when the gift isn't promised
                if (validCard.contains("PromisedGift") && !sa.isOptionalCostPaid(OptionalCost.PromiseGift)) {
                    continue;
                }
            }

            if (!tr.requirementsCheck(game)) {
                continue;
            }

            // if trigger is not mandatory - no problem
            if (tr.hasParam("OptionalDecider") && api == null && !evoked) {
                continue;
            }

            SpellAbility exSA = tr.ensureAbility();
            if (exSA.getApi() == api) {
                rightapi = true;
            } else if (api != null) {
                continue;
            }
            exSA = exSA.copy(activator);

            if (api != null && !(exSA instanceof AbilitySub) && !ComputerUtilCost.canPayCost(exSA, player, true)) {
                return false;
            }

            exSA.setTrigger(tr);
            // need to set TriggeredObject
            exSA.setTriggeringObject(AbilityKey.Card, card);

            // for trigger test, need to ignore the conditions
            SpellAbilityCondition cons = exSA.getConditions();
            if (cons != null) {
                String pres = cons.getIsPresent();
                if (pres != null && pres.matches("Card\\.(Strictly)?Self")) {
                    cons.setIsPresent(null);
                }
            }

            // magic-tools: rule 165, "return an Orc you control" with no other Orc bounces the card itself
            if (api == null && !evoked && SelfBounceEtbAi.onlyItself(card, exSA, activator)) {
                return false;
            }

            // Run non-mandatory trigger.
            // These checks only work if the Executing SpellAbility is an Ability_Sub.
            if (exSA instanceof AbilitySub && !doTrigger(exSA, false)) {
                if (evoked) {
                    continue;
                }
                // AI would not run this trigger if given the chance
                if (api == null && card.isCreature() && !ComputerUtilAbility.isFullyTargetable(exSA) &&
                        (ComputerUtil.aiLifeInDanger(activator, true, 0) || "BadETB".equals(tr.getParam("AILogic")))) {
                    // trigger will not run due to lack of targets and we 1. desperately need a creature or 2. are happy about that
                    continue;
                }
                // magic-tools: a trigger we would skip now but that cannot hurt us does not keep the card in hand
                // (rule 62: nor one that has no legal target at all, the creature comes without it)
                if (api == null && (HarmlessEtbAi.isHarmless(exSA) || HarmlessEtbAi.hasNoTargetAtAll(exSA, card)
                        || HarmlessEtbAi.isSelfTargetablePump(exSA, card))) {
                    continue;
                }
                // magic-tools: Lobelia without a dead creature is still a 2/3 menace (TreasureFlashAi times it)
                if (api == null && TreasureFlashAi.applies(card) && !ComputerUtilAbility.isFullyTargetable(exSA)) {
                    continue;
                }
                return false;
            }
            // (the evoke sacrifice trigger itself is no gain)
            if (evoked && !tr.isKeyword(Keyword.EVOKE) && (!exSA.usesTargeting() || exSA.getTargets().size() > 0)) {
                evokeUseful = true;
            }
        }
        if (evoked && !evokeUseful) {
            return false;
        }

        if (card.isSaga()) {
            for (final Trigger tr : card.getTriggers()) {
                if (!tr.isChapter()) {
                    continue;
                }

                SpellAbility exSA = tr.ensureAbility().copy(activator);

                if (exSA.getApi() == api) {
                    rightapi = true;
                }

                if (exSA instanceof AbilitySub && !doTrigger(exSA, false)) {
                    // magic-tools: rule 62, a chapter that cannot hurt us (Michiko's Reign of Truth: pump target creature)
                    if (api == null && HarmlessEtbAi.isHarmless(exSA)) {
                        break;
                    }
                    // AI would not run this chapter if given the chance
                    // TODO eventually we'll want to consider playing it anyway, especially if Read ahead would still allow an immediate benefit
                    return false;
                }

                // usually later chapters make use of an earlier one
                break;
            }
        }

        return rightapi;
    }

    private static List<SpellAbility> getPlayableCounters(final CardCollection all) {
        final List<SpellAbility> spellAbility = Lists.newArrayList();
        for (final Card c : all) {
            CardState state = c.isForetold() && c.getAlternateState() != null ? c.getAlternateState() : c.getCurrentState();
            for (final SpellAbility sa : state.getNonManaAbilities()) {
                if (sa.getApi() == ApiType.Counter) {
                    spellAbility.add(sa);
                }
            }
        }
        return spellAbility;
    }

    private CardCollection filterLandsToPlay(CardCollection landList) {
        // magic-tools: rule 29, keep the land in hand so that the cleanup discards the fatty to reanimate
        if (ReanimatorAi.skipLandDropToDiscard(player)) {
            return null;
        }
        final CardCollectionView hand = player.getCardsIn(ZoneType.Hand);
        CardCollection nonLandList = CardLists.filter(hand, CardPredicates.NON_LANDS);
        if (landList.size() == 1 && nonLandList.size() < 3) {
            CardCollectionView cardsInPlay = player.getCardsIn(ZoneType.Battlefield);
            CardCollection landsInPlay = CardLists.filter(cardsInPlay, CardPredicates.LANDS);
            CardCollection allCards = new CardCollection(player.getCardsIn(ZoneType.Graveyard));
            allCards.addAll(player.getCardsIn(ZoneType.Command));
            allCards.addAll(cardsInPlay);
            int maxCmcInHand = Aggregates.max(hand, Card::getCMC);
            int max = Math.max(maxCmcInHand, 6);
            // consider not playing lands if there are enough already and an ability with a discard cost is present
            if (landsInPlay.size() + landList.size() > max) {
                for (Card c : allCards) {
                    for (SpellAbility sa : c.getSpellAbilities()) {
                        Cost payCosts = sa.getPayCosts();
                        // magic-tools: rule 212, only an ability usable from where the card is keeps the land in hand
                        if (payCosts != null && DiscardOutletAi.usableFromItsZone(c, sa)) {
                            for (CostPart part : payCosts.getCostParts()) {
                                if (part instanceof CostDiscard) {
                                    return null;
                                }
                            }
                        }
                    }
                }
            }
        }

        landList = ComputerUtilCard.dedupeCards(landList);

        landList = CardLists.filter(landList, c -> {
            String name = c.getName();
            CardCollectionView battlefield = player.getCardsIn(ZoneType.Battlefield);
            // magic-tools: rule 12, no land drop that makes City of Traitors (and the like) sacrifice itself for less mana
            if (LandDropSacAi.losesMana(player, c)) {
                return false;
            }
            if (c.getType().isLegendary() && !name.equals("Flagstones of Trokair")) {
                if (battlefield.anyMatch(CardPredicates.nameEquals(name))) {
                    return false;
                }
            }

            final CardCollectionView hand1 = player.getCardsIn(ZoneType.Hand);
            CardCollection lands = new CardCollection(battlefield);
            lands.addAll(hand1);
            lands = CardLists.filter(lands, CardPredicates.LANDS);
            int maxCmcInHand = Aggregates.max(hand1, Card::getCMC);

            if (lands.size() >= Math.max(maxCmcInHand, 6)) {
                // don't play MDFC land if other side is spell and enough lands are available
                if (!c.isLand() || (c.isModal() && !c.getState(CardStateName.Backside).getType().isLand())) {
                    return false;
                }

                // don't play the land if it has cycling and enough lands are available
                if (c.hasKeyword(Keyword.CYCLING)) {
                    return false;
                }
            }
            return c.getAllPossibleAbilities(player, true).stream().anyMatch(
                    la -> la.isLandAbility() && saSideEffects(c, la).willingToPlay()
            );
        });
        return landList;
    }

    private Card chooseBestLandToPlay(CardCollection landList) {
        if (landList.isEmpty()) {
            return null;
        }

        CardCollection nonLandsInHand = CardLists.filter(player.getCardsIn(ZoneType.Hand), CardPredicates.NON_LANDS);

        // Some considerations for Momir/MoJhoSto
        boolean hasMomir = player.isCardInCommand("Momir Vig, Simic Visionary Avatar");
        if (hasMomir && nonLandsInHand.isEmpty()) {
            // Only do this if we have an all-basic land hand, which covers both stock Momir and MoJhoSto modes
            // and also a custom Vanguard setup with a customized basic land deck and Momir as the avatar.
            String landStrategy = getProperty(AiProps.MOMIR_BASIC_LAND_STRATEGY);
            if (landStrategy.equalsIgnoreCase("random")) {
                // Pick a completely random basic land
                return Aggregates.random(landList);
            } else if (landStrategy.toLowerCase().startsWith("preforder:")) {
                // Pick a basic land in order of preference, or play a random one if nothing is preferred
                String order = landStrategy.substring(10);
                for (char c : order.toCharArray()) {
                    byte color = MagicColor.fromName(c);
                    for (Card land : landList) {
                        for (final SpellAbility m : ComputerUtilMana.getAIPlayableMana(land)) {
                            if (m.canProduce(MagicColor.toShortString(color))) {
                                return land;
                            }
                        }
                    }
                }
                return Aggregates.random(landList);
            }
            // If nothing is done here, proceeds to the default land picking strategy
        }

        //Skip reflected lands.
        CardCollection unreflectedLands = new CardCollection(landList);
        for (Card l : landList) {
            if (l.isReflectedLand()) {
                unreflectedLands.remove(l);
            }
        }
        if (!unreflectedLands.isEmpty()) {
            landList = unreflectedLands;
        }

        // magic-tools: rule 86, two different Urza lands: the missing Tron pieces before anything else (TronAi)
        CardCollection tronPieces = TronAi.piecesFirst(player, landList);
        if (!tronPieces.isEmpty()) {
            landList = tronPieces;
        }

        // try to skip lands that enter the battlefield tapped if we might want to play something this turn
        if (!nonLandsInHand.isEmpty()) {
            CardCollection nonTappedLands = new CardCollection();
            CardCollection shockLands = new CardCollection(); // magic-tools: rule 205
            for (Card land : landList) {
                // check replacement effects if land would enter tapped or not
                final Map<AbilityKey, Object> repParams = AbilityKey.mapFromAffected(land);
                repParams.put(AbilityKey.Origin, land.getZone().getZoneType());
                repParams.put(AbilityKey.Destination, ZoneType.Battlefield);

                // add Params for AddCounter Replacements
                GameEntityCounterTable table = new GameEntityCounterTable();
                repParams.put(AbilityKey.EffectOnly, true);
                repParams.put(AbilityKey.CounterTable, table);
                repParams.put(AbilityKey.CounterMap, table.column(land));

                boolean foundTapped = false;
                for (ReplacementEffect re : player.getGame().getReplacementHandler().getReplacementList(ReplacementType.Moved, repParams, ReplacementLayer.Other)) {
                    SpellAbility reSA = re.ensureAbility();
                    if (reSA == null || !ApiType.Tap.equals(reSA.getApi())) {
                        continue;
                    }
                    reSA.setActivatingPlayer(reSA.getHostCard().getController());
                    if (reSA.metConditions()) {
                        foundTapped = true;
                        if (TapLandColorAi.paysLifeForUntapped(reSA)) {
                            shockLands.add(land);
                        }
                        break;
                    }
                }

                // TODO if this is the only source for a color we need badly prioritize it instead
                if (foundTapped) {
                    continue;
                }

                nonTappedLands.add(land);
            }

            // magic-tools: rule 205, a shockland that casts more than any untapped land would
            CardCollection unlocking = new CardCollection();
            for (Card land : shockLands) {
                if (player.getLife() > 4 && TapLandColorAi.shockUnlocks(player, land, nonTappedLands, nonLandsInHand)) {
                    unlocking.add(land);
                }
            }
            if (!unlocking.isEmpty()) {
                landList = unlocking;
            } else if (!nonTappedLands.isEmpty()) {
                // if we have the choice, see if we can play an untapped land
                // If we have a lot of mana, prefer untapped lands.
                // We're either topdecking or have drawn enough the tempo no longer matters.
                // magic-tools: rule 66, costed mana abilities count only if payable
                int mana_available = TapLandColorAi.landPlayManaEstimate(player);
                if (mana_available > 6) {
                    landList = nonTappedLands;
                } else {
                    // get the costs of the nonland cards in hand and the mana we have available.
                    // If adding one won't make something new castable, then pick a tapland.
                    int max_inc = 0;
                    for (Card c : nonTappedLands) {
                        max_inc = max(max_inc, c.getMaxManaProduced());
                    }
                    // check for lands with no mana abilities
                    if (max_inc > 0) {
                        boolean found = false;
                        for (Card c : nonLandsInHand) {
                            // magic-tools: rule 250, a spell with nothing to target is no use for the mana
                            if (TapLandColorAi.idleCard(player, c)) {
                                continue;
                            }
                            // TODO make this work better with split cards and Monocolored Hybrid
                            ManaCost cost = c.getManaCost();
                            // check for incremental cmc
                            // check for X cost spells
                            // magic-tools: rule 89, or the {2} to foretell it
                            if ((cost.getCMC() - mana_available) * (cost.getCMC() - mana_available - max_inc - 1) < 0 ||
                                    (cost.countX() > 0 && cost.getCMC() >= mana_available)
                                    || TapLandColorAi.foretellsWith(c, mana_available, max_inc)) {
                                found = true;
                                break;
                            }
                        }

                        CardCollection useful = new CardCollection();
                        if (found) {
                            // magic-tools: rule 21, only the untapped lands that bring the colors of that spell
                            useful = TapLandColorAi.usefulUntappedLands(player, nonTappedLands, nonLandsInHand, mana_available);
                            if (!useful.isEmpty()) {
                                landList = useful;
                            }
                        }
                        if (useful.isEmpty()) {
                            // magic-tools: rule 69, nothing new to cast: the slow land first
                            CardCollection slow = TapLandColorAi.slowLandsIfIdle(player, landList, nonTappedLands, nonLandsInHand, mana_available);
                            if (!slow.isEmpty()) {
                                landList = slow;
                            } else {
                                // magic-tools: rule 250, the untapped lands whose mana is spent this turn
                                CardCollection spent = TapLandColorAi.untappedForSpend(player, nonTappedLands, nonLandsInHand, mana_available);
                                if (!spent.isEmpty()) {
                                    landList = spent;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Early out if we only have one card left
        if (landList.size() == 1) {
            return landList.get(0);
        }

        // Choose first land to be able to play a one drop
        if (player.getLandsInPlay().isEmpty()) {
            CardCollection oneDrops = CardLists.filter(nonLandsInHand, CardPredicates.hasCMC(1));
            for (MagicColor.Color color : ColorSet.WUBRG) {
                if (oneDrops.anyMatch(CardPredicates.isColor(color.getColorMask()))) {
                    for (Card land : landList) {
                        if (land.getType().hasSubtype(color.getBasicLandType())) {
                            return land;
                        }
                        for (final SpellAbility m : ComputerUtilMana.getAIPlayableMana(land)) {
                            if (m.canProduce(color.getShortName())) {
                                return land;
                            }
                        }
                    }
                }
            }
        }

        // play lands with a basic type and/or color that is needed the most
        final CardCollectionView landsInBattlefield = player.getLandsInPlay();
        final Set<String> basics = Sets.newHashSet();

        // what colors are available?
        int[] counts = new int[6]; // in WUBRGC order

        for (Card c : player.getCardsIn(ZoneType.Battlefield)) {
            for (SpellAbility m: c.getManaAbilities()) {
                m.setActivatingPlayer(c.getController());
                for (AbilityManaPart mp : m.getAllManaParts()) {
                    for (String part : mp.mana(m).split(" ")) {
                        // TODO handle any
                        int index = ManaAtom.getIndexFromName(part);
                        if (index != -1) {
                            counts[index] += 1;
                        }
                    }
                }
            }
        }

        // what types can I go get?
        for (final String name : MagicColor.Constant.BASIC_LANDS) {
            if (landList.stream().anyMatch(c -> c.getType().hasSubtype(name)) &&
                    landsInBattlefield.stream().anyMatch(c -> c.getType().hasSubtype(name))) {
                basics.add(name);
            }
        }

        // pick the land with the best score.
        // use the evaluation plus a modifier for each new color pip and basic type
        Card toReturn = Aggregates.itemWithMax(IterableUtil.filter(landList, Card::hasPlayableLandFace),
                (card -> {
                    // base score is for the evaluation score
                    int score = GameStateEvaluator.evaluateLand(card);
                    // add for new basic type
                    for (String cardType: card.getType().getLandTypes()) {
                        if (CardType.isABasicLandType(cardType) && !basics.contains(cardType)) {
                            score += 25;
                        }
                    }

                    // TODO handle fetchlands and what they can fetch for
                    // determine new color pips
                    int[] card_counts = new int[6]; // in WUBRGC order
                    for (SpellAbility m: card.getManaAbilities()) {
                        m.setActivatingPlayer(card.getController());
                        for (AbilityManaPart mp : m.getAllManaParts()) {
                            for (String part : mp.mana(m).split(" ")) {
                                // TODO handle any
                                int index = ManaAtom.getIndexFromName(part);
                                if (index != -1) {
                                    card_counts[index] += 1;
                                }
                            }
                        }
                    }

                    // use 1 / x+1 for diminishing returns
                    // TODO use max pips of each color in the deck from deck statistics to weight this
                    for (int i = 0; i < card_counts.length; i++) {
                        int diff = (card_counts[i] * 50) / (counts[i] + 1);
                        score += diff;
                    }

                    // magic-tools: rule 38, a fetchland is worth one mana of the best color it can find
                    score += FetchLandPlayAi.fetchScore(player, card, counts);

                    // TODO utility lands only if we have enough to pay their costs
                    // TODO Tron lands and other lands that care about land counts

                    return score;
                }));
        return toReturn;
    }

    // if return true, go to next phase
    private SpellAbility chooseCounterSpell(final List<SpellAbility> possibleCounters) {
        if (possibleCounters == null || possibleCounters.isEmpty()) {
            return null;
        }
        SpellAbility bestSA = null;
        int bestRestriction = Integer.MIN_VALUE;

        for (final SpellAbility sa : ComputerUtilAbility.getOriginalAndAltCostAbilities(possibleCounters, player)) {
            sa.setActivatingPlayer(player);
            // check everything necessary

            AiPlayDecision opinion = canPlayAndPayFor(sa);
            //PhaseHandler ph = game.getPhaseHandler();
            // System.out.printf("Ai thinks '%s' of %s @ %s %s >>> \n", opinion, sa, Lang.getPossesive(ph.getPlayerTurn().getName()), ph.getPhase());
            if (opinion == AiPlayDecision.WillPlay) {
                final int restrictionLevel = ComputerUtil.counterSpellRestriction(player, sa);
                if (bestSA == null || restrictionLevel > bestRestriction) {
                    bestRestriction = restrictionLevel;
                    bestSA = sa;
                }
            }
        }

        // TODO - "Look" at Targeted SA and "calculate" the threshold
        // if (bestRestriction < targetedThreshold) return false;
        return bestSA;
    }

    public SpellAbility predictSpellToCastInMain2(ApiType exceptSA) {
        return predictSpellToCastInMain2(exceptSA, true);
    }
    private SpellAbility predictSpellToCastInMain2(ApiType exceptSA, boolean handOnly) {
        if (!getBoolProperty(AiProps.PREDICT_SPELLS_FOR_MAIN2)) {
            return null;
        }

        final CardCollectionView cards = handOnly ? player.getCardsIn(ZoneType.Hand) :
                ComputerUtilAbility.getAvailableCards(game, player);

        List<SpellAbility> all = ComputerUtilAbility.getSpellAbilities(cards, player);

        try {
            all.sort(ComputerUtilAbility.saEvaluator); // put best spells first
            ComputerUtilAbility.sortCreatureSpells(all);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            String assertex = ComparatorUtil.verifyTransitivity(ComputerUtilAbility.saEvaluator, all);
            Sentry.captureMessage(ex.getMessage() + "\nAssertionError [verifyTransitivity]: " + assertex);
        }

        for (final SpellAbility sa : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, player)) {
            ApiType saApi = sa.getApi();

            if (saApi == ApiType.Counter || saApi == exceptSA) {
                continue;
            }
            sa.setActivatingPlayer(player);
            // TODO: this currently only works as a limited prediction of permanent spells.
            // Ideally this should cast canPlaySa to determine that the AI is truly able/willing to cast a spell,
            // but that is currently difficult to implement due to various side effects leading to stack overflow.
            Card host = sa.getHostCard();
            if (sa instanceof SpellPermanent && host != null && !host.isLand() && !ComputerUtil.castPermanentInMain1(player, sa) && ComputerUtilCost.canPayCost(sa, player, false)) {
                return sa;
            }
        }
        return null;
    }

    public boolean reserveManaSourcesForNextSpell(SpellAbility sa, SpellAbility exceptForSa) {
        return reserveManaSources(sa, null, false, true, exceptForSa);
    }
    public boolean reserveManaSources(SpellAbility sa) {
        return reserveManaSources(sa, PhaseType.MAIN2, false, false, null);
    }
    public boolean reserveManaSources(SpellAbility sa, PhaseType phaseType, boolean enemy) {
        return reserveManaSources(sa, phaseType, enemy, true, null);
    }
    public boolean reserveManaSources(SpellAbility sa, PhaseType phaseType, boolean enemy, boolean forNextSpell, SpellAbility exceptForThisSa) {
        AiCardMemory.MemorySet memSet = null;
        if (phaseType == null && forNextSpell) {
            memSet = AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL;
        } else if (phaseType != null) {
            switch (phaseType) {
                case MAIN2:
                    // TODO bail if profile chance 0 anyway
                    memSet = AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_MAIN2;
                    break;
                case COMBAT_DECLARE_BLOCKERS:
                    memSet = enemy ? AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_ENEMY_DECLBLK
                            : AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_DECLBLK;
                    break;
                default:
                    System.out.println("Warning: unsupported mana reservation phase specified for reserveManaSources: "
                            + phaseType.name() + ", reserving until Main 2 instead. Consider adding support for the phase if needed.");
                    memSet = AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_MAIN2;
                    break;
            }
        }

        ManaCostBeingPaid cost = ComputerUtilMana.calculateManaCost(sa.getPayCosts(), sa, player, true, 0, false);
        CardCollection manaSources = ComputerUtilMana.getManaSourcesToPayCost(cost, sa, player, false);

        if (manaSources == null || manaSources.isEmpty()) {
            return false;
        }

        // used for chained spells where two spells need to be cast in succession
        if (exceptForThisSa != null) {
            CardCollection exceptSources = ComputerUtilMana.getManaSourcesToPayCost(
                    ComputerUtilMana.calculateManaCost(exceptForThisSa.getPayCosts(), exceptForThisSa, player, true, 0, false),
                    exceptForThisSa, player, false);
            // the other spell needs these too, so they usually can't be promised to both
            if (exceptSources != null && !exceptSources.isEmpty() && manaSources.removeAll(exceptSources) && manaSources.isEmpty()) {
                return false;
            }
        }

        for (Card c : manaSources) {
            memory.rememberCard(c, memSet);
        }
        return true;
    }

    private AiPlayDecision canPlayAndPayFor(final SpellAbility sa) {
        final Card host = sa.getHostCard();

        if (sa instanceof Spell sp) {
            Card altHost = sp.canPlayFromHost();
            if (altHost == null) {
                return AiPlayDecision.CantPlaySa;
            }
            // state needs to be switched here so API checks evaluate the right face
            if (host != altHost) {
                sa.setHostCard(altHost);
            }
            altHost.setCastSA(sa);
        } else if (!sa.canPlay()) {
            return AiPlayDecision.CantPlaySa;
        }

        try {
            return canPlayAndPayForFace(sa);
        } finally {
            // in addition to engine some AI api can also switch host
            if (sa.getHostCard() != host) {
                sa.setHostCard(host);
            }
            if (sa.isSpell()) {
                host.setCastSA(null);
            }
        }
    }

    // This is for playing spells regularly (no Cascade/Ripple etc.)
    private AiPlayDecision canPlayAndPayForFace(final SpellAbility sa) {
        final Card host = sa.getHostCard();

        if (sa.hasParam("AICheckSVar") && !aiShouldRun(sa, sa, host, null)) {
            return AiPlayDecision.NeedsToPlayCriteriaNotMet;
        }

        // this is the "heaviest" check, which also sets up targets, defines X, etc.
        AiPlayDecision canPlay = canPlaySa(sa);

        if (canPlay != AiPlayDecision.WillPlay) {
            return canPlay;
        }

        if (!ComputerUtilCost.canPayCost(sa, player, sa.isTrigger())) {
            // for dependent costs with X, e.g. Repeal, which require a valid target to be specified before a decision can be made
            // on whether the cost can be paid, this can only be checked late after canPlaySa has been run (or the AI will misplay)
            return AiPlayDecision.CantAfford;
        }

        // check if enough left (pass memory indirectly because we don't want to include those)
        Set<Card> tappedForMana = AiCardMemory.getMemorySet(player, MemorySet.PAYS_TAP_COST);
        if (tappedForMana != null && !tappedForMana.isEmpty() &&
                !ComputerUtilCost.checkTapTypeCost(player, sa.getPayCosts(), host, sa, new CardCollection(tappedForMana))) {
            return AiPlayDecision.CantAfford;
        }

        return AiPlayDecision.WillPlay;
    }

    public AiPlayDecision canPlaySa(SpellAbility sa) {
        if (!checkAiSpecificRestrictions(sa)) {
            return AiPlayDecision.CantPlayAi;
        }
        if (sa instanceof WrappedAbility) {
            return canPlaySa(((WrappedAbility) sa).getWrappedAbility());
        }

        if (!sa.canCastTiming(player)) {
            return AiPlayDecision.TimingRestrictions;
        }

        final Card card = sa.getHostCard();

        // Trying to play a card that has Buyback without a Buyback cost, look for possible additional considerations
        if (getBoolProperty(AiProps.TRY_TO_PRESERVE_BUYBACK_SPELLS) && card.hasKeyword(Keyword.BUYBACK)
                && !sa.isBuyback() && !canPlaySpellWithoutBuyback(sa)) {
            return AiPlayDecision.NeedsToPlayCriteriaNotMet;
        }

        if (sa.getApi() != null) {
            String msg = "AiController:canPlaySa: AI checks for if can PlaySa";
            Breadcrumb bread = new Breadcrumb(msg);
            bread.setData("Api", sa.getApi().toString());
            bread.setData("Card", card.getName());
            bread.setData("SA", sa.toString());
            Sentry.addBreadcrumb(bread);

            // add Extra for debugging
            Sentry.setExtra("Card", card.getName());
            Sentry.setExtra("SA", sa.toString());

            // magic-tools: endless persist loop (Melira + Murderous Redcap + free sacrifice outlet)
            boolean canPlay = UnlockWin314Ai.shouldPlay(player, sa) // magic-tools: rule 314, remove the opponent's last "can't lose"
                    || PersistLoopAi.shouldLoop(player, sa)
                    || SacUntapLoopAi.shouldLoop(player, sa) // magic-tools: rule 145, sacrifice-untap loop
                    || DamageTokenLoopAi.shouldLoop(player, sa) // magic-tools: rule 149, damage-token-counter loop
                    || SacManaLoopAi.shouldLoop(player, sa) // magic-tools: rule 152, sacrifice-for-mana drain loop
                    || canPlayXTargets(sa);

            // remove added extra
            Sentry.removeExtra("Card");
            Sentry.removeExtra("SA");

            if (!canPlay) {
                return AiPlayDecision.CantPlayAi;
            }
        } else {
            Cost payCosts = sa.getPayCosts();
            if (payCosts != null) {
                ManaCost mana = payCosts.getTotalMana();
                if (mana.countX() > 0) {
                    // Set PayX here to maximum value.
                    final int xPay = ComputerUtilCost.setMaxXValue(sa, player, sa.isTrigger());
                    if (xPay <= 0) {
                        return AiPlayDecision.CantAffordX;
                    }
                } else if (mana.isZero()) {
                    // if mana is zero, but card mana cost does have X, then something is wrong
                    ManaCost cardCost = card.getManaCost();
                    if (cardCost != null && cardCost.countX() > 0) {
                        return AiPlayDecision.CantPlayAi;
                    }
                }
            }
            // TODO before suspending some spells try to predict if relevant targets can be expected
            if ((sa.isPlotting() || sa.isForetelling() || sa.isKeyword(Keyword.SUSPEND)) && game.getPhaseHandler().getPhase().isBefore(PhaseType.MAIN2)) {
                // don't compete with more important SA AI wants to pay before main2
                return AiPlayDecision.WaitForMain2;
            }
        }
        // TODO maybe other location for this?
        if (!sa.isLegalAfterStack()) {
            return AiPlayDecision.AnotherTime;
        }
        Card spellHost = card;
        if (sa.isSpell()) {
            spellHost = CardCopyService.getLKICopy(spellHost);
            spellHost.setLKICMC(-1); // to reset the cmc
            spellHost.setLastKnownZone(game.getStackZone()); // need to add to stack to make check Restrictions respect stack cmc
            spellHost.setCastFrom(card.getZone());
        }
        if (!sa.checkRestrictions(spellHost, player)) {
            return AiPlayDecision.AnotherTime;
        }
        if (sa.usesTargeting()) {
            if (!sa.isTargetNumberValid() && sa.getTargetRestrictions().getNumCandidates(sa) == 0) {
                return AiPlayDecision.TargetingFailed;
            }
            if (!StaticAbilityMustTarget.meetsMustTargetRestriction(sa)) {
                return AiPlayDecision.TargetingFailed;
            }
        }

        return saSideEffects(spellHost, sa);
    }

    // magic-tools: rule 315, an ability that pays {X} for X targets is never activated at X=0
    private boolean canPlayXTargets(SpellAbility sa) {
        Boolean x = XTargets315Ai.canPlay(player, sa);
        return x != null ? x : SpellApiToAi.Converter.get(sa).canPlayWithSubs(player, sa).willingToPlay();
    }

    // magic-tools: package-private for BourdeWatcher (why a payable permanent stayed in hand)
    AiPlayDecision saSideEffects(final Card card, final SpellAbility sa) {
        if (usesHybridSimulation()) {
            return OnePlaySafetyChecker.isAcceptable(player, sa) ? AiPlayDecision.WillPlay : AiPlayDecision.HybridSimRejected;
        }

        if ((!sa.isSpell() && !sa.isLandAbility()) || usesFullSimulation()) {
            return AiPlayDecision.WillPlay;
        }

        if ("True".equals(card.getSVar("NonStackingEffect")) && ComputerUtilCard.isNonDisabledCardInPlay(player, card.getName())) {
            return AiPlayDecision.DoesntImpactGame;
        }

        if (checkCurseEffects(sa)) {
            return AiPlayDecision.CurseEffects;
        }

        int damage = 0;
        if (!sa.isLandAbility() && !player.cantLoseForZeroOrLessLife() && player.canLoseLife()) {
            damage = ComputerUtil.getDamageForPlaying(player, sa);
            if (damage >= player.getLife()) {
                // TODO even if it doesn't kill AI lower the score
                return AiPlayDecision.CurseEffects;
            }
        }
        if (card.isPermanent() && !sa.isMutate()) {
            damage += ComputerUtil.getDamageFromETB(player, card);
            // magic-tools: rule 170 (relevé « mana perdu » v207, Eldratron - Avacyn t14-46 : à -7 sous Platinum Angel,
            // Eldratron a gardé Darksteel Ingot, Trinket et Trophy Mage 16 tours) : 0 blessure >= -7 vies refusait tout
            // permanent ; ne pas perdre à 0 vie ou moins, ou ne prendre aucune blessure, ne tue pas
            if (damage > 0 && !player.cantLoseForZeroOrLessLife() && damage >= player.getLife()) {
                return AiPlayDecision.BadEtbEffects;
            }
            if (!sa.isLandAbility() && !checkETBEffects(card, sa, null)) {
                return AiPlayDecision.BadEtbEffects;
            }
        }

        if (!PactAi.canPayAllPacts(player, sa)) {
            return AiPlayDecision.CantAfford;
        }

        // magic-tools: rule 109, a free spell waits to be cast before the Storm card of the hand
        if (StormFuelAi.holdForStorm(player, sa)) {
            return AiPlayDecision.AnotherTime;
        }

        // magic-tools: no {3} face-down 2/2 for a cheap creature (Birchlore Rangers)
        if (!MorphAi.worthCastingFaceDown(sa)) {
            return AiPlayDecision.CurseEffects;
        }

        // magic-tools: no creature spell whose forced draws would empty the library (Beast Whisperer, Glimpse of Nature)
        if (DeckOutAi.wouldDeckSelf(player, sa)) {
            return AiPlayDecision.CurseEffects;
        }

        // magic-tools: rule 249, under pressure the mana of the sweeper in hand is not spent on something else
        final Card sweeperKept = SweeperUnderPressure249.sweeperKept(player, sa);
        if (sweeperKept != null) {
            if (AI_TRACE != null && player.getName().contains(AI_TRACE)) {
                System.out.printf("MT Hold: %s keeps mana for %s, holds %s%n", player.getName(), sweeperKept.getName(), card.getName());
            }
            return AiPlayDecision.AnotherTime;
        }

        // magic-tools: rule 251, no defender before a creature that competes for its mana when no creature faces us
        // magic-tools: rule 253, a mana rock that costs mana this turn waits for the bigger spell it would leave unpaid
        final Card rockKept = RockBeforeCurve253.rockKept(player, sa);
        if (rockKept != null) {
            if (AI_TRACE != null && player.getName().contains(AI_TRACE)) {
                System.out.printf("MT Hold: %s keeps mana for %s, holds %s%n", player.getName(), rockKept.getName(), card.getName());
            }
            return AiPlayDecision.AnotherTime;
        }

        // magic-tools: rule 258, a draw spell that overfills the hand waits for the spell it would leave unpaid
        final Card drawKept = DrawFullHand258.drawKept(player, sa);
        if (drawKept != null) {
            if (AI_TRACE != null && player.getName().contains(AI_TRACE)) {
                System.out.printf("MT Hold: %s keeps mana for %s, holds %s%n", player.getName(), drawKept.getName(), card.getName());
            }
            return AiPlayDecision.AnotherTime;
        }

        final Card wallKept = IdleWall251.wallKept(player, sa);
        if (wallKept != null) {
            if (AI_TRACE != null && player.getName().contains(AI_TRACE)) {
                System.out.printf("MT Hold: %s keeps mana for %s, holds %s%n", player.getName(), wallKept.getName(), card.getName());
            }
            return AiPlayDecision.AnotherTime;
        }

        // magic-tools: rule 22, keep the mana of a counterspell in hand for the opponent's turn
        final Card counterKept = CounterReserveAi.counterKept(player, sa);
        if (counterKept != null) {
            if (AI_TRACE != null && player.getName().contains(AI_TRACE)) {
                // read by scripts/forge/partie.py: "garde son mana pour X, ne lance pas Y"
                System.out.printf("MT Hold: %s keeps mana for %s, holds %s%n", player.getName(), counterKept.getName(), card.getName());
            }
            return AiPlayDecision.AnotherTime;
        }

        return ComputerUtilCard.checkNeedsToPlayReqs(card, sa);
    }

    private boolean canPlaySpellWithoutBuyback(SpellAbility sa) {
        int copies = CardLists.count(player.getCardsIn(ZoneType.Hand), CardPredicates.nameEquals(sa.getHostCard().getName()));
        // Have two copies : allow
        if (copies >= 2) {
            return true;
        }

        // About to lose game : allow
        if (ComputerUtil.aiLifeInDanger(player, true, 0)) {
            return true;
        }

        Cost costWithBuyback = sa.getPayCosts().copy();
        for (OptionalCostValue opt : GameActionUtil.getOptionalCostValues(sa)) {
            if (opt.getType() == OptionalCost.Buyback) {
                costWithBuyback.add(opt.getCost());
            }
        }
        costWithBuyback = CostAdjustment.adjust(costWithBuyback, sa, false);
        if (costWithBuyback.hasSpecificCostType(CostPayLife.class)
                || costWithBuyback.hasSpecificCostType(CostDiscard.class)
                || costWithBuyback.hasSpecificCostType(CostSacrifice.class)) {
            // won't be able to afford buyback any time soon
            // if Buyback cost includes sacrifice, life, discard
            return true;
        }

        int neededMana = 0;
        if (costWithBuyback.getCostMana() != null) {
            neededMana = costWithBuyback.getCostMana().getMana().getCMC();
        }
        // Memory Crystal-like effects need special handling
        for (Card c : game.getCardsIn(ZoneType.Battlefield)) {
            for (StaticAbility s : c.getStaticAbilities()) {
                if (s.checkMode(StaticAbilityMode.ReduceCost)
                        && "Spell.Buyback".equals(s.getParam("ValidSpell"))) {
                    neededMana -= AbilityUtils.calculateAmount(c, s.getParam("Amount"), s);
                }
            }
        }
        if (neededMana < 0) {
            neededMana = 0;
        }
        // magic-tools: rule 171 (Spell Burst, X U + buyback 3) : X se paie aussi ; à 4 terrains contre un sort de valeur 3,
        // la version buyback ne pouvait payer que X = 0 et la version simple était refusée : le contre ne partait jamais
        if (sa.getPayCosts().getTotalMana() != null && sa.getPayCosts().getTotalMana().countX() > 0) {
            neededMana += XCounterAi.xWanted(sa);
        }

        int hasMana = getAvailableManaEstimate(player, false);
        if (hasMana < neededMana - 1) {
            return true;
        }

        return false;
    }

    public CardCollection getCardsToDiscard(final int numDiscard, final String[] uTypes, final SpellAbility sa) {
        return getCardsToDiscard(numDiscard, uTypes, sa, CardCollection.EMPTY);
    }
    public CardCollection getCardsToDiscard(final int numDiscard, final String[] uTypes, final SpellAbility sa, final CardCollectionView exclude) {
        boolean noFiltering = sa != null && "DiscardCMCX".equals(sa.getParam("AILogic")); // list AI logic for which filtering is taken care of elsewhere
        CardCollection hand = new CardCollection(player.getCardsIn(ZoneType.Hand));
        hand.removeAll(exclude);
        if (uTypes != null && sa != null && !noFiltering) {
            hand = CardLists.getValidCards(hand, uTypes, sa.getActivatingPlayer(), sa.getHostCard(), sa);
        }
        return getCardsToDiscard(numDiscard, numDiscard, hand, sa);
    }

    public CardCollection getCardsToDiscard(int min, final int max, final CardCollection validCards, final SpellAbility sa) {
        if (validCards.size() <= min) {
            return validCards; //return all valid cards since they will be discarded without filtering needed
        }
        // magic-tools: Magus of the Bazaar discards the embalm / eternalize cards and spare lands first (Oasis)
        if (BazaarAi.isBazaar(sa)) {
            return BazaarAi.chooseDiscards(this, player, sa, min, max, validCards);
        }
        // magic-tools: rule 263, the deck plan keeps its key cards while another card can go
        final CardCollection planKeep = DeckPlanAi.keepForDiscard(player, validCards, min);
        if (planKeep != null) {
            return getCardsToDiscard(min, max, planKeep, sa);
        }

        Card sourceCard = null;
        final CardCollection discardList = new CardCollection();
        int count = 0;
        if (sa != null) {
            String logic = sa.getParamOrDefault("AILogic", "");
            sourceCard = sa.getHostCard();
            if ("Always".equals(logic) && !validCards.isEmpty()) {
                min = 1;
            } else if (logic.startsWith("UnlessAtLife.")) {
                int threshold = AbilityUtils.calculateAmount(sourceCard, logic.substring(logic.indexOf(".") + 1), sa);
                if (player.getLife() <= threshold) {
                    min = 1;
                }
            } else if ("VolrathsShapeshifter".equals(logic)) {
                return SpecialCardAi.VolrathsShapeshifter.targetBestCreature(player, sa);
            } else if ("DiscardCMCX".equals(logic)) {
                final int cmc = sa.getXManaCostPaid();
                CardCollection discards = CardLists.filter(player.getCardsIn(ZoneType.Hand), CardPredicates.hasCMC(cmc));
                if (discards.isEmpty()) {
                    return null;
                }
                return new CardCollection(ComputerUtilCard.getWorstAI(discards));
            }

            if (sa.hasParam("AnyNumber")) {
                if ("DiscardUncastableAndExcess".equals(sa.getParam("AILogic"))) {
                    CardCollection discards = new CardCollection();
                    final CardCollectionView inHand = player.getCardsIn(ZoneType.Hand);
                    final int numLandsOTB = CardLists.count(inHand, CardPredicates.LANDS);
                    int numOppInHand = 0;
                    for (Player p : player.getGame().getPlayers()) {
                        if (p.getCardsIn(ZoneType.Hand).size() > numOppInHand) {
                            numOppInHand = p.getCardsIn(ZoneType.Hand).size();
                        }
                    }
                    for (Card c : inHand) {
                        if (c.hasSVar("DoNotDiscardIfAble") || c.hasSVar("IsReanimatorCard")) {
                            continue;
                        }
                        if (c.isCreature() && !ComputerUtilMana.hasEnoughManaSourcesToCast(c.getSpellPermanent(), player)) {
                            discards.add(c);
                        }
                        if ((c.isLand() && numLandsOTB >= 5) || (c.getFirstSpellAbility() != null && !ComputerUtilMana.hasEnoughManaSourcesToCast(c.getFirstSpellAbility(), player))) {
                            if (discards.size() + 1 <= numOppInHand) {
                                discards.add(c);
                            }
                        }
                    }
                    return discards;
                }
            }
        }

        // look for good discards
        while (count < min) {
            Card prefCard = null;
            if (sa != null && sa.getActivatingPlayer() != null && sa.getActivatingPlayer().isOpponentOf(player)) {
                for (Card c : validCards) {
                    if (c.hasSVar("DiscardMeByOpp")) {
                        prefCard = c;
                        break;
                    }
                }
            }
            if (prefCard == null) {
                prefCard = ComputerUtil.getCardPreference(player, sourceCard, "DiscardCost", validCards);
                // TODO use DiscardMe:0 instead so each card without is just treated as 1
                if (prefCard != null && prefCard.hasSVar("DoNotDiscardIfAble")) {
                    prefCard = null;
                }
            }
            if (prefCard != null) {
                discardList.add(prefCard);
                validCards.remove(prefCard);
                count++;
            } else {
                break;
            }
        }

        // magic-tools: rule 113, a madness card we can cast right away goes first
        while (count < min) {
            final Card madness = MadnessDiscardAi.choose(player, validCards, sa);
            if (madness == null) {
                break;
            }
            discardList.add(madness);
            validCards.remove(madness);
            count++;
        }

        final int discardsLeft = min - count;

        // magic-tools: rule 136, keep the card that discarding makes cheap (Asmoranomardicadaistinaculdacar)
        validCards.removeAll(DiscardEnabledAi.keep(player, validCards, discardsLeft));

        // choose rest
        for (int i = 0; i < discardsLeft; i++) {
            if (validCards.isEmpty()) {
                continue;
            }
            final int numLandsInPlay = CardLists.count(player.getCardsIn(ZoneType.Battlefield), CardPredicates.LANDS_PRODUCING_MANA);
            final CardCollection landsInHand = CardLists.filter(validCards, CardPredicates.LANDS);
            final int numLandsInHand = landsInHand.size();

            // Discard a land
            boolean canDiscardLands = numLandsInHand > 3 || (numLandsInHand > 2 && numLandsInPlay > 0)
                    || (numLandsInHand > 1 && numLandsInPlay > 2) || (numLandsInHand > 0 && numLandsInPlay > 5);

            if (canDiscardLands) {
                discardList.add(landsInHand.get(0));
                validCards.remove(landsInHand.get(0));
            } else { // Discard other stuff
                CardLists.sortByCmcDesc(validCards);
                int numLandsAvailable = numLandsInPlay;
                if (numLandsInHand > 0) {
                    numLandsAvailable++;
                }

                // Discard unplayable card (checks by CMC)
                // But check if there is a card in play that allows casting spells for free!
                // if yes, nothing is unplayable based on CMC alone
                boolean discardedUnplayable = false;
                boolean freeCastAllowed = ComputerUtilCost.isFreeCastAllowedByPermanent(player, null);

                for (int j = 0; j < validCards.size(); j++) {
                    if ((validCards.get(j).getCMC() > numLandsAvailable || freeCastAllowed) && !validCards.get(j).hasSVar("DoNotDiscardIfAble")) {
                        discardList.add(validCards.get(j));
                        validCards.remove(validCards.get(j));
                        discardedUnplayable = true;
                        break;
                    } else if (validCards.get(j).getCMC() <= numLandsAvailable) {
                        // cut short to avoid looping over cards which are guaranteed not to fit the criteria
                        break;
                    }
                }

                if (!discardedUnplayable) {
                    // discard worst card
                    Card worst = ComputerUtilCard.getWorstAI(validCards);
                    if (worst == null) {
                        // there were only instants and sorceries, and maybe cards that are not good to discard, so look
                        // for more discard options
                        worst = ComputerUtilCard.getCheapestSpellAI(validCards);
                    }
                    if (worst == null && !validCards.isEmpty()) {
                        // still nothing chosen, so choose the first thing that works, trying not to make DoNotDiscardIfAble
                        // discards
                        for (Card c : validCards) {
                            if (!c.hasSVar("DoNotDiscardIfAble")) {
                                worst = c;
                                break;
                            }
                        }
                        // Only DoNotDiscardIfAble cards? If we have a duplicate for something, discard it
                        if (worst == null) {
                            for (Card c : validCards) {
                                if (CardLists.count(player.getCardsIn(ZoneType.Hand), CardPredicates.nameEquals(c.getName())) > 1) {
                                    worst = c;
                                    break;
                                }
                            }
                            if (worst == null) {
                                // Otherwise just grab a random card and discard it
                                worst = Aggregates.random(validCards);
                            }
                        }
                    }
                    discardList.add(worst);
                    validCards.remove(worst);
                }
            }
        }
        return discardList;
    }

    public boolean confirmAction(SpellAbility sa, PlayerActionConfirmMode mode, String message, Map<String, Object> params) {
        ApiType api = sa == null ? null : sa.getApi();

        // Abilities without api may also use this routine, However they should provide a unique mode value ?? How could this work?
        if (sa == null || api == null) {
            String exMsg = String.format("AI confirmAction does not know what to decide about %s mode (%s is null).",
                    mode, sa == null ? "SA" : "API");
            throw new IllegalArgumentException(exMsg);
        }
        return SpellApiToAi.Converter.get(api).confirmAction(player, sa, mode, message, params);
    }

    public boolean confirmBidAction(SpellAbility sa, PlayerActionConfirmMode mode, String message, int bid, Player winner) {
        if (mode != null) switch (mode) {
            case BidLife:
                if (sa.hasParam("AIBidMax")) {
                    return !player.equals(winner) && bid < Integer.parseInt(sa.getParam("AIBidMax")) && player.getLife() > bid + 5;
                }
                return false;
            default:
                return false;
        }
        return false;
    }

    public boolean confirmStaticApplication(Card hostCard, String logic) {
        return true;
    }

    public String getProperty(AiProps propName) {
        return AiProfileUtil.getProperty(getPlayer(), propName);
    }
    public int getIntProperty(AiProps propName) {
        return AiProfileUtil.getIntProperty(getPlayer(), propName);
    }
    public boolean getBoolProperty(AiProps propName) {
        return AiProfileUtil.getBoolProperty(getPlayer(), propName);
    }

    public AiPlayDecision canPlayFromEffectAI(Spell spell, boolean mandatory, boolean withoutPayingManaCost) {
        if (spell instanceof SpellApiBased) {
            boolean chance;
            if (withoutPayingManaCost) {
                chance = SpellApiToAi.Converter.get(spell).doTriggerNoCostWithSubs(player, spell, mandatory).willingToPlay();
            } else {
                chance = SpellApiToAi.Converter.get(spell).doTrigger(player, spell, mandatory);
            }
            if (!chance) {
                return AiPlayDecision.TargetingFailed;
            }
        }

        if (mandatory) {
            return AiPlayDecision.WillPlay;
        }

        SpellAbility abilityToCheck = spell;
        if (usesHybridSimulation() && withoutPayingManaCost && !spell.hasParam("WithoutManaCost")) {
            abilityToCheck = spell.copyWithNoManaCost(player);
        }

        return saSideEffects(spell.getHostCard(), abilityToCheck);
    }

    // declares blockers for given defender in a given combat
    public void declareBlockersFor(Player defender, Combat combat) {
        // magic-tools: Odric, Master Tactician: we choose the opponent's blockers, block for our own benefit
        if (OdricBlockAi.declaresForOpponent(player, defender) && OdricBlockAi.declareBlockers(player, defender, combat)) {
            return;
        }
        AiBlockController block = new AiBlockController(defender, player);
        // When player != defender, AI should declare blockers for its benefit.
        block.assignBlockersForCombat(combat);
    }

    public void declareAttackers(Player attacker, Combat combat) {
        // 12/2/10(sol) the decision making here has moved to getAttackers()
        AiAttackController aiAtk = new AiAttackController(attacker);
        lastAttackAggression = aiAtk.declareAttackers(combat);

        aiAtk.reinforceWithBanding(combat);

        // Per CR 508.1d, the decision to pay attack costs (e.g. Propaganda)
        // is made at declaration time. Remove attackers the AI can't pay for.
        removeUnpayableAttackers(combat);

        // magic-tools: rule 112, a battle cry creature with 0 power (Signal Pest) doesn't attack alone
        if (combat.getAttackers().size() == 1) {
            Card lone = combat.getAttackers().get(0);
            AttackRequirement req = combat.getAttackConstraints().getRequirements().get(lone);
            if ((req == null || !req.hasRequirement())
                    && AiAttackController.onlySupportsOtherAttackers(attacker, lone, combat)) {
                combat.removeFromCombat(lone);
            }
        }

        // if invalid: just try an attack declaration that we know to be legal
        if (!CombatUtil.validateAttackers(combat)) {
            combat.clearAttackers();
            final Map<Card, GameEntity> legal = combat.getAttackConstraints().getLegalAttackers().getLeft();
            System.err.println("AI Attack declaration invalid, defaulting to: " + legal);
            for (final Map.Entry<Card, GameEntity> mandatoryAttacker : legal.entrySet()) {
                combat.addAttacker(mandatoryAttacker.getKey(), mandatoryAttacker.getValue());
            }
            if (!CombatUtil.validateAttackers(combat)) {
                aiAtk.declareAttackers(combat);
            }
        }
    }

    private void removeUnpayableAttackers(Combat combat) {
        for (Card attacker : combat.getAttackers().threadSafeIterable()) {
            Cost attackCost = CombatUtil.getAttackCost(game, attacker, combat.getDefenderByAttacker(attacker));
            if (attackCost == null) {
                continue;
            }
            SpellAbility fakeSA = new SpellAbility.EmptySa(attacker, attacker.getController());
            fakeSA.setCardState(attacker.getCurrentState());
            fakeSA.setPayCosts(attackCost);
            fakeSA.setSVar("X", "0");
            if (!ComputerUtilCost.canPayCost(attackCost, fakeSA, player, true)) {
                combat.removeFromCombat(attacker);
            }
        }
    }

    private List<SpellAbility> singleSpellAbilityList(SpellAbility sa) {
        if (sa == null) {
            return null;
        }
        return Lists.newArrayList(sa);
    }

    public List<SpellAbility> chooseSpellAbilityToPlay() {
        AiCache.clear(AiCache.Scope.PRIORITY);
        // Reset cached predicted combat, as it may be stale. It will be
        // re-created if needed and used for any AI logic that needs it.
        predictedCombat = null;
        // Also reset predicted combat for next turn here
        predictedCombatNextTurn = null;

        // Reset priority mana reservation that's meant to work for one spell only
        memory.clearMemorySet(AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL);

        if (usesFullSimulation()) {
            return singleSpellAbilityList(simPicker.chooseSpellAbilityToPlay(null));
        }

        // magic-tools: rule 140, an untap loop with a net mana gain (two Myr Galvanizer + Palladium Myr) as a shortcut
        UntapLoopAi.maybeShortcut(player);

        CardCollection playBeforeLand = CardLists.filter(
                player.getCardsIn(ZoneType.Hand), CardPredicates.hasSVar("PlayBeforeLandDrop")
        );
        // magic-tools: rule 259, a landfall payoff of the hand is offered before the land drop it would count
        for (final Card c : LandfallFirst259.beforeLand(player)) {
            if (!playBeforeLand.contains(c)) {
                playBeforeLand.add(c);
            }
        }
        if (!playBeforeLand.isEmpty()) {
            SpellAbility wantToPlayBeforeLand = chooseSpellAbilityToPlayFromList(
                    ComputerUtilAbility.getSpellAbilities(playBeforeLand, player), false
            );
            if (wantToPlayBeforeLand != null) {
                return singleSpellAbilityList(wantToPlayBeforeLand);
            }
        }

        // magic-tools: rule 278, an ability that puts lands from the hand onto the battlefield goes before the land drop
        final List<SpellAbility> putters278 = LandReplayAi.beforeLand(player);
        if (!putters278.isEmpty()) {
            SpellAbility putter = chooseSpellAbilityToPlayFromList(putters278, false);
            if (putter != null) {
                return singleSpellAbilityList(putter);
            }
        }

        CardCollection landsWannaPlay = ComputerUtilAbility.getAvailableLandsToPlay(game, player);
        if (landsWannaPlay != null) {
            landsWannaPlay = filterLandsToPlay(landsWannaPlay);
            if (landsWannaPlay != null && !landsWannaPlay.isEmpty()) {
                // TODO search for other land it might want to play?
                // magic-tools: rule 103, the lands our payoff in hand would count wait for it when another land can go
                landsWannaPlay = ArrivalPayoffAi.landsAfterPayoff(player, landsWannaPlay);
                // magic-tools: rule 106, no land that feeds the opponent's Land Tax when it brings nothing now
                Card land = LandTaxAi.holdLandDrop(player, landsWannaPlay) ? null : chooseBestLandToPlay(landsWannaPlay);
                // magic-tools: rule 72, a bounce land waits for the spells castable now, and for main 2 (KarooAi)
                if (land != null && KarooAi.isKaroo(land) && game.getPhaseHandler().getPhase().isMain()) {
                    SpellAbility before = getSpellAbilityToPlay();
                    if (before != null || game.getPhaseHandler().is(PhaseType.MAIN1)) {
                        return singleSpellAbilityList(before);
                    }
                }
                if (land != null && (!game.getPhaseHandler().is(PhaseType.MAIN1) || !isSafeToHoldLandDropForMain2(land))) {
                    final List<SpellAbility> abilities = land.getAllPossibleAbilities(player, true);
                    // skip non Land Abilities
                    abilities.removeIf(sa -> !sa.isLandAbility());

                    if (!abilities.isEmpty()) {
                        // TODO extend this logic to evaluate MDFC with both sides land
                        return abilities;
                    }
                }
            }
        }

        return singleSpellAbilityList(getSpellAbilityToPlay());
    }

    private boolean isSafeToHoldLandDropForMain2(Card landToPlay) {
        boolean hasMomir = player.isCardInCommand("Momir Vig, Simic Visionary Avatar");
        if (hasMomir) {
            // Don't do this in Momir variants since it messes with the AI decision making for the avatar.
            return false;
        }

        if (!MyRandom.percentTrue(getIntProperty(AiProps.HOLD_LAND_DROP_FOR_MAIN2_IF_UNUSED))) {
            // check against the chance specified in the profile
            return false;
        }
        if (game.getPhaseHandler().getTurn() <= 2) {
            // too obvious when doing it on the very first turn of the game
            return false;
        }

        CardCollection inHand = CardLists.filter(player.getCardsIn(ZoneType.Hand), CardPredicates.NON_LANDS);
        CardCollectionView otb = player.getCardsIn(ZoneType.Battlefield);

        if (getBoolProperty(AiProps.HOLD_LAND_DROP_ONLY_IF_HAVE_OTHER_PERMS)) {
            if (!otb.anyMatch(CardPredicates.NON_LANDS)) {
                return false;
            }
        }

        // TODO: improve the detection of taplands
        boolean isTapLand = false;
        for (ReplacementEffect repl : landToPlay.getReplacementEffects()) {
            if (repl.getParamOrDefault("Description", "").equals("CARDNAME enters tapped.")) {
                isTapLand = true;
            }
        }

        int totalCMCInHand = Aggregates.sum(inHand, Card::getCMC);
        int minCMCInHand = Aggregates.min(inHand, Card::getCMC);
        if (minCMCInHand == Integer.MAX_VALUE)
            minCMCInHand = 0;
        int predictedMana = getAvailableManaEstimate(player, true);

        boolean canCastWithLandDrop = (predictedMana + 1 >= minCMCInHand) && minCMCInHand > 0 && !isTapLand;
        boolean cantCastAnythingNow = predictedMana < minCMCInHand;

        boolean hasRelevantAbsOTB = otb.anyMatch(card -> {
            boolean isTapLand1 = false;
            for (ReplacementEffect repl : card.getReplacementEffects()) {
                // TODO: improve the detection of taplands
                if (repl.getParamOrDefault("Description", "").equals("CARDNAME enters tapped.")) {
                    isTapLand1 = true;
                }
            }

            for (SpellAbility sa : card.getSpellAbilities()) {
                if (sa.isAbility()
                        && sa.getPayCosts().getCostMana() != null
                        && sa.getPayCosts().getCostMana().getMana().getCMC() > 0
                        && (!sa.getPayCosts().hasTapCost() || !isTapLand1)
                        && (!sa.hasParam("ActivationZone") || sa.getParam("ActivationZone").contains("Battlefield"))) {
                    return true;
                }
            }
            return false;
        });

        boolean hasLandBasedEffect = otb.anyMatch(card -> {
            for (Trigger t : card.getTriggers()) {
                Map<String, String> params = t.getMapParams();
                if ("ChangesZone".equals(params.get("Mode"))
                        && params.containsKey("ValidCard")
                        && (!params.containsKey("AILogic") || !params.get("AILogic").equals("SafeToHold"))
                        && !params.get("ValidCard").contains("nonLand")
                        && ((params.get("ValidCard").contains("Land")) || (params.get("ValidCard").contains("Permanent")))
                        && "Battlefield".equals(params.get("Destination"))) {
                    // Landfall and other similar triggers
                    return true;
                }
            }
            for (String sv : card.getSVars().keySet()) {
                String varValue = card.getSVar(sv);
                if (varValue.equals("Count$Domain")) {
                    for (String type : landToPlay.getType().getLandTypes()) {
                        if (CardType.isABasicLandType(type) && CardLists.getType(otb, type).isEmpty()) {
                            return true;
                        }
                    }
                }
                if (varValue.startsWith("Count$Valid") || sv.equals("BuffedBy")) {
                    if (varValue.contains("Land") || varValue.contains("Plains") || varValue.contains("Forest")
                            || varValue.contains("Mountain") || varValue.contains("Island") || varValue.contains("Swamp")
                            || varValue.contains("Wastes")) {
                        // In presence of various cards that get buffs like "equal to the number of lands you control",
                        // safer for our AI model to just play the land earlier rather than make a blunder
                        return true;
                    }
                }
            }
            return false;
        });

        // TODO: add prediction for effects that will untap a tapland as it enters the battlefield
        if (!canCastWithLandDrop && cantCastAnythingNow && !hasLandBasedEffect && (!hasRelevantAbsOTB || isTapLand)) {
            // Hopefully there's not much to do with the extra mana immediately, can wait for Main 2
            return true;
        }
        if ((predictedMana <= totalCMCInHand && canCastWithLandDrop) || (hasRelevantAbsOTB && !isTapLand) || hasLandBasedEffect) {
            // Might need an extra land to cast something, or for some kind of an ETB ability with a cost or an
            // alternative cost (if we cast it in Main 1), or to use an activated ability on the battlefield
            return false;
        }

        // TODO needed to pay propaganda

        return true;
    }

    private SpellAbility getSpellAbilityToPlay() {
        if (skipped != null) {
            //FIXME: this is for failed SA to skip temporarily, don't know why AI computation for mana fails, maybe due to auto mana compute?
            for (SpellAbility sa : skipped) {
                //System.out.println("Unskip: " + sa.toString() + " (" +  sa.getHostCard().getName() + ").");
                sa.setSkip(false);
            }
        }
        CardCollection cards = ComputerUtilAbility.getAvailableCards(game, player);
        cards = ComputerUtilCard.dedupeCards(cards);
        List<SpellAbility> saList = Lists.newArrayList();

        SpellAbility top = null;
        if (!game.getStack().isEmpty()) {
            top = game.getStack().peekAbility();
        }
        final boolean topOwnedByAI = top != null && top.getActivatingPlayer().equals(player);

        // Must respond: cases where the AI should respond to its own triggers or other abilities (need to add negative stuff to be countered here)
        boolean mustRespond = false;
        if (top != null) {
            mustRespond = top.hasParam("AIRespondsToOwnAbility"); // Forced combos (currently defined for Sensei's Divining Top)
            mustRespond |= top.isTrigger() && top.getTrigger().isKeyword(Keyword.EVOKE); // Evoke sacrifice trigger
            // magic-tools: rule 116, any own mandatory "sacrifice this" (unpaid echo, end step): feed an outlet first
            mustRespond |= OwnSacrificeAi.threatensHost(player, null, top.isWrapper() ? ((WrappedAbility) top).getWrappedAbility() : top);
            // magic-tools: rule 167, our own effect threatens a permanent of ours that can still be used in response
            mustRespond |= topOwnedByAI && OwnThreatResponseAi.mustAnswerOwn(player);
        }

        if (topOwnedByAI) {
            // AI's own spell: should probably let my stuff resolve first, but may want to copy the SA or respond to it
            // in a scripted timed fashion.

            if (!mustRespond) {
                saList = ComputerUtilAbility.getSpellAbilities(cards, player); // get the SA list early to check for copy SAs
                if (ComputerUtilAbility.getFirstCopySASpell(saList) == null) {
                    // Nothing to copy the spell with, so do nothing.
                    return null;
                }
            }
        }

        if (!game.getStack().isEmpty()) {
            SpellAbility counter = chooseCounterSpell(getPlayableCounters(cards));
            if (counter != null) return counter;

            SpellAbility counterETB = chooseSpellAbilityToPlayFromList(getPossibleETBCounters(), false);
            if (counterETB != null)
                return counterETB;
        }

        // magic-tools: rule 52, lethal on the table in main 1: go to combat first, the spells wait for main 2
        // (ElfBall tapped its pumped elves for mana in main 1 and never attacked for 15 to 41 damage)
        if (game.getStack().isEmpty() && game.getPhaseHandler().is(PhaseType.MAIN1, player)
                && new AiAttackController(player).isLethalAssault()) {
            return null;
        }

        if (saList.isEmpty()) {
            saList = ComputerUtilAbility.getSpellAbilities(cards, player);
        }

        saList.removeIf(spellAbility -> {
            // don't include removedAI cards if somehow the AI can play the ability or gain control of unsupported card
            // TODO allow when experimental profile?
            // magic-tools: rule 46, the deck chose the card, so a permanent spell marked AI:RemoveDeck:All is still cast
            // (stats, static and triggers work without the AI); only its activated abilities stay out of reach
            return spellAbility.isLandAbility() || (spellAbility.getHostCard() != null && ComputerUtilCard.isCardRemAIDeck(spellAbility.getHostCard())
                    && !isRemovedPermanentSpell(spellAbility) && !isRemovedPermanentAbility(spellAbility)
                    && !isRemovedSimpleSpell(spellAbility));
        });
        //removed skipped SA
        skipped = saList.stream().filter(SpellAbility::isSkip).collect(Collectors.toList());
        if (!skipped.isEmpty())
            saList.removeAll(skipped);
        //update LivingEndPlayer
        useLivingEnd = IterableUtil.any(player.getZone(ZoneType.Library), CardPredicates.nameEquals("Living End"));

        SpellAbility chosenSa = chooseSpellAbilityToPlayFromList(saList, true);

        if (topOwnedByAI && !mustRespond && chosenSa != ComputerUtilAbility.getFirstCopySASpell(saList)) {
            return null; // not planning to copy the spell and not marked as something the AI would respond to
        }

        return chosenSa;
    }

    // magic-tools: rule 46, the cast of a permanent card from hand (not its abilities)
    /**
     * magic-tools: rule 95, the activated abilities of such a permanent once it is in play (Memory Jar never
     * activated, Dovin and Gideon never used their loyalty, Engineered Explosives never popped): each API's own
     * logic judges them like any other ability. Mana abilities already worked.
     */
    public static boolean isRemovedPermanentAbility(final SpellAbility sa) {
        // magic-tools: rule 219, except a mana ability with a mana cost: the payment code skips it (ManaFilterAi activates
        // it beforehand), so it did not work: Interplanar Beacon never gave Dovin its blue
        return sa.isActivatedAbility() && (!sa.isManaAbility() || ManaFilterAi.isFilter(sa))
                && sa.getHostCard().isInZone(ZoneType.Battlefield);
    }

    /**
     * magic-tools: rule 118, an instant or sorcery marked AI:RemoveDeck:All whose effect the generic AI already judges
     * (damage, destroy, draw) and that costs only mana is cast too: Hobbit's Sting stayed in hand 1 650 steps over
     * 4 games with killable targets, Beast Within and Decree of Pain likewise. Other APIs (choices, sacrifices,
     * animations) stay out of reach.
     */
    public static boolean isRemovedSimpleSpell(final SpellAbility sa) {
        if (!sa.isSpell() || sa.getHostCard() == null || !sa.getHostCard().isInZone(ZoneType.Hand)
                || sa.getCardState() == null || sa.getCardState().getType().isPermanent()) {
            return false;
        }
        // magic-tools: rule 191, -X/-X with a chosen X (Toxic Deluge pays it in life) is judged by ThresholdSweepAi
        if (sa.getApi() == ApiType.PumpAll && ThresholdSweepAi.isMinusXSweep(sa)) {
            return true;
        }
        // magic-tools: rule 192, one permanent sacrificed on top of the mana (Improvised Club, Death Bomb, Vivisection):
        // the general sacrifice rules (FodderAi, RemovalSacAi) only pay it when it is worth it
        if (sa.getPayCosts() != null && isManaAndOneSacrifice(sa.getPayCosts())
                && (sa.getApi() == ApiType.DealDamage || sa.getApi() == ApiType.Destroy || sa.getApi() == ApiType.Draw)) {
            return true;
        }
        // magic-tools: rule 193, damage with X paid in life or from the graveyard (Fire Covenant, Harvest Pyre):
        // DamageDealAi reads X from the cost and pays only what kills
        if (sa.getApi() == ApiType.DealDamage && sa.getPayCosts() != null && DividedBurnAi.isLifeOrGraveX(sa.getPayCosts())) {
            return true;
        }
        if (sa.getPayCosts() == null || !sa.getPayCosts().isOnlyManaCost()) {
            return false;
        }
        final ApiType api = sa.getApi();
        // magic-tools: rule 181, energy that pays a sweep (Wrath of the Skies) is judged by ThresholdSweepAi
        return api == ApiType.DealDamage || api == ApiType.Destroy || api == ApiType.DestroyAll || api == ApiType.Draw
                || (api == ApiType.PutCounter && ThresholdSweepAi.energySweepIn(sa) != null);
    }

    private static boolean isManaAndOneSacrifice(final Cost cost) {
        int sacrifices = 0;
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostSacrifice) {
                if (part.payCostFromSource() || !"1".equals(part.getAmount())) {
                    return false;
                }
                sacrifices++;
            } else if (!(part instanceof CostPartMana)) {
                return false;
            }
        }
        return sacrifices == 1;
    }

    static boolean isRemovedPermanentSpell(final SpellAbility sa) {
        return sa.isSpell() && sa.getHostCard().isInZone(ZoneType.Hand) && sa.getCardState() != null
                && sa.getCardState().getType().isPermanent();
    }

    // Diagnostic local (Magic Tools) : FORGE_AI_TRACE=<nom du joueur> affiche la main, le cimetière et l'avis de l'IA sur chaque sort.
    private static final String AI_TRACE = System.getenv("FORGE_AI_TRACE");

    private SpellAbility chooseSpellAbilityToPlayFromList(final List<SpellAbility> all, boolean skipCounter) {
        if (all == null || all.isEmpty())
            return null;

        try {
            all.sort(ComputerUtilAbility.saEvaluator); // put best spells first
            ComputerUtilAbility.sortCreatureSpells(all);
            // magic-tools: mana creatures before same-cost creatures when a bigger spell waits in hand
            ManaRampAi.prioritize(player, all);
            // magic-tools: rule 263, the spell that completes a combo of the deck plan goes first
            DeckPlanAi.prioritize(player, all);
            // magic-tools: rule 196, the creature spell that starts the longest chain paid by tapping Elves
            TapEngineAi.prioritize(player, all);
            // magic-tools: rule 206, the cheap spells of the type when « tap N of the type » is within reach
            TapGoalAi.prioritize(player, all);
            // magic-tools: assemble the persist loop with Birthing Pod before the mana goes into creature spells
            for (int i = 1; i < all.size(); i++) {
                if (PodComboAi.plan(player, all.get(i)) != null) {
                    all.add(0, all.remove(i));
                }
            }
            // magic-tools: rule 110, a haste equipment goes on the creature cast this turn before the next spell
            HasteAttachAi.prioritize(player, all);
            // magic-tools: rule 108, floating ritual mana goes to the biggest spell of the hand first
            StormRitualAi.prioritize(player, all);
            // magic-tools: rule 265, the blocker of an evasive attacker that kills us next turn goes first
            EvasiveBlocker265.prioritize(player, all);
            // magic-tools: rule 26, a lethal X damage spell goes before the creatures that would eat its mana
            LethalBurnAi.prioritize(player, all);
            // magic-tools: rule 314, removing the permanent that alone keeps a dead opponent alive goes first
            UnlockWin314Ai.prioritize(player, all);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            String assertex = ComparatorUtil.verifyTransitivity(ComputerUtilAbility.saEvaluator, all);
            Sentry.captureMessage(ex.getMessage() + "\nAssertionError [verifyTransitivity]: " + assertex);
        }

        if (AI_TRACE != null && player.getName().contains(AI_TRACE)) {
            // position adverse en fin de ligne (premier adversaire) : créatures, terrains, cartes en main, points de vie
            Player adv = player.getWeakestOpponent();
            System.out.printf("AI_TRACE %s T%d %s main=%s cimetiere=%s creatures=%s terrains_libres=%d soi=%b adv_creatures=%s adv_terrains=%d adv_main=%d vies=%d/%d%n", player.getName(), game.getPhaseHandler().getTurn(),
                    game.getPhaseHandler().getPhase(), player.getCardsIn(ZoneType.Hand), player.getCardsIn(ZoneType.Graveyard),
                    player.getCreaturesInPlay(), CardLists.count(player.getCardsIn(ZoneType.Battlefield), c -> c.isLand() && !c.isTapped()),
                    game.getPhaseHandler().isPlayerTurn(player),
                    adv == null ? "[]" : adv.getCreaturesInPlay(), adv == null ? 0 : adv.getLandsInPlay().size(),
                    adv == null ? 0 : adv.getCardsIn(ZoneType.Hand).size(), player.getLife(), adv == null ? 0 : adv.getLife());
        }
        FutureTask<SpellAbility> future = new FutureTask<>(() -> {
            //avoid ComputerUtil.aiLifeInDanger in loops as it slows down a lot.. call this outside loops will generally be fast...
            boolean isLifeInDanger = useLivingEnd && ComputerUtil.aiLifeInDanger(player, true, 0);
            // magic-tools: rule 108, Storm spells skipped while other spells wait in hand
            final List<SpellAbility> stormWaiting = new ArrayList<>();
            for (final SpellAbility sa : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, player)) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                // Don't add Counterspells to the "normal" playcard lookups
                if (skipCounter && sa.getApi() == ApiType.Counter) {
                    continue;
                }

                if (sa.getHostCard().hasKeyword(Keyword.STORM)
                        && sa.getApi() != ApiType.Counter // AI would suck at trying to deliberately proc a Storm counterspell
                        && player.getZone(ZoneType.Hand).contains(
                                Predicate.not(CardPredicates.LANDS.or(CardPredicates.hasKeyword("Storm")))
                    )) {
                    if (game.getView().getStormCount() < this.getIntProperty(AiProps.MIN_COUNT_FOR_STORM_SPELLS)) {
                        // skip evaluating Storm unless we reached the minimum Storm count
                        stormWaiting.add(sa);
                        continue;
                    }
                }

                // living end AI decks
                // TODO: generalize the implementation so that superfluous logic-specific checks for life, library size, etc. aren't needed
                AiPlayDecision aiPlayDecision = AiPlayDecision.CantPlaySa;
                if (useLivingEnd) {
                    if (sa.isCycling() && sa.canCastTiming(player)
                            && player.getCardsIn(ZoneType.Library).size() >= 10) {
                        if (ComputerUtilCost.canPayCost(sa, player, sa.isTrigger())) {
                            if (sa.getPayCosts() != null && sa.getPayCosts().hasSpecificCostType(CostPayLife.class)
                                    && !player.cantLoseForZeroOrLessLife() && player.getLife() <= sa.getPayCosts()
                                            .getCostPartByType(CostPayLife.class).getAbilityAmount(sa) * 2) {
                                aiPlayDecision = AiPlayDecision.CantAfford;
                            } else {
                                aiPlayDecision = AiPlayDecision.WillPlay;
                            }
                        }
                    } else if (sa.getHostCard().hasKeyword(Keyword.CASCADE)) {
                        if (isLifeInDanger) { // needs more tune up for certain conditions
                            aiPlayDecision = player.getCreaturesInPlay().size() >= 4 ? AiPlayDecision.CantPlaySa
                                    : AiPlayDecision.WillPlay;
                        } else if (CardLists
                                .filter(player.getZone(ZoneType.Graveyard).getCards(), CardPredicates.CREATURES)
                                .size() > 4) {
                            if (player.getCreaturesInPlay().size() >= 4) // it's good minimum
                                continue;
                            else if (!sa.getHostCard().isPermanent() && sa.canCastTiming(player)
                                    && ComputerUtilCost.canPayCost(sa, player, sa.isTrigger()))
                                aiPlayDecision = AiPlayDecision.WillPlay;
                            // needs tuneup for bad matchups like reanimator and other things to check on opponent graveyard
                        } else {
                            continue;
                        }
                    }
                }

                sa.setActivatingPlayer(player);
                SpellAbility root = sa.getRootAbility();

                if (root.isSpell() || root.isTrigger() || root.isReplacementAbility()) {
                    sa.setLastStateBattlefield(game.getLastStateBattlefield());
                    sa.setLastStateGraveyard(game.getLastStateGraveyard());
                }
                //override decision for living end player
                AiPlayDecision opinion = useLivingEnd && AiPlayDecision.WillPlay.equals(aiPlayDecision) ? aiPlayDecision : canPlayAndPayFor(sa);

                // reset LastStateBattlefield
                sa.clearLastState();
                if (AI_TRACE != null && player.getName().contains(AI_TRACE)
                        && (sa.getHostCard().isInZone(ZoneType.Hand) || (sa.isActivatedAbility() && !sa.isManaAbility()) || ManaFilterAi.isFilter(sa))) {
                    // paie=false : aucune source ne donne les couleurs du coût (le relevé confondait ce cas avec un refus de l'IA)
                    System.out.printf("AI_TRACE %s T%d %s : %s%s -> %s cmc=%d soi=%b paie=%b%n", player.getName(), game.getPhaseHandler().getTurn(),
                            game.getPhaseHandler().getPhase(), sa.getHostCard().getName(), sa.isSpell() ? "" : " (capacité)", opinion,
                            sa.getHostCard().getCMC(), game.getPhaseHandler().isPlayerTurn(player),
                            !sa.getPayCosts().hasManaCost() || ComputerUtilMana.hasEnoughManaSourcesToCast(sa, player));
                }
                // PhaseHandler ph = game.getPhaseHandler();
                // System.out.printf("Ai thinks '%s' of %s -> %s @ %s %s >>> \n", opinion, sa.getHostCard(), sa, Lang.getInstance().getPossesive(ph.getPlayerTurn().getName()), ph.getPhase());

                if (opinion != AiPlayDecision.WillPlay) {
                    continue;
                }

                // TODO could continue to try find another with higher rating (weighted by priority ordering)
                return sa;
            }

            // magic-tools: rule 108, nothing else was played: the Storm spell goes alone rather than never
            for (final SpellAbility sa : stormWaiting) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                sa.setActivatingPlayer(player);
                if (sa.getRootAbility().isSpell()) {
                    sa.setLastStateBattlefield(game.getLastStateBattlefield());
                    sa.setLastStateGraveyard(game.getLastStateGraveyard());
                }
                AiPlayDecision opinion = canPlayAndPayFor(sa);
                sa.clearLastState();
                if (opinion == AiPlayDecision.WillPlay) {
                    return sa;
                }
            }

            return null;
        });
        Thread t = new Thread(future, "Game AI Eval");
        t.setDaemon(true);
        t.start();
        try {
            return future.get(game.getAITimeout(), TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            if (e instanceof TimeoutException) {
                // log where the eval thread currently is - each timeout doubles as a
                // profiler sample for diagnosing remaining AI slowdowns from user logs
                StringBuilder sb = new StringBuilder("AI eval thread at timeout:");
                StackTraceElement[] evalStack = t.getStackTrace();
                for (int i = 0; i < Math.min(30, evalStack.length); i++) {
                    sb.append("\n\tat ").append(evalStack[i]);
                }
                System.out.println(sb);
            }
            // ask the eval thread to exit at the next SpellAbility check first: a brutal
            // Thread.stop() mid-evaluation can leave partially mutated shared state behind
            future.cancel(true);
            try {
                t.join(2000); //2 seconds wait
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (t.isAlive()) {
                // last resort, see #8302: the eval thread may be stuck inside a single
                // evaluation or an infinite loop and never reach the cooperative exit
                try {
                    t.stop();
                } catch (UnsupportedOperationException | NoSuchMethodError ex) {
                    // Stop support: dropped by Android and Java 20 / 26 removed it completely - so sadly thread will keep running
                }
            }
            // TODO mark some as skipped to increase chance to find something playable next priority
            return null;
        }
    }

    public CardCollection chooseCardsToDelve(int genericCost, CardCollection grave) {
        CardCollection toExile = new CardCollection();
        int numToExile = Math.min(grave.size(), genericCost);

        for (int i = 0; i < numToExile; i++) {
            Card chosen = null;
            for (final Card c : grave) {
                // Exile noncreatures first in case we can revive
                // Might wanna do some additional checking here for Flashback and the like
                if (!c.isCreature()) {
                    chosen = c;
                    break;
                }
            }
            if (chosen == null) {
                chosen = ComputerUtilCard.getWorstCreatureAI(grave);
            }

            if (chosen == null) {
                // Should never get here but... You know how it is.
                chosen = grave.get(0);
            }

            toExile.add(chosen);
            grave.remove(chosen);
        }
        return toExile;
    }

    public boolean doTrigger(SpellAbility sa, boolean mandatory) {
        if (sa instanceof WrappedAbility)
            return doTrigger(((WrappedAbility) sa).getWrappedAbility(), mandatory);
        if (sa.getApi() != null)
            return SpellApiToAi.Converter.get(sa).doTrigger(player, sa, mandatory);
        if (sa.getPayCosts().isFree() && !sa.usesTargeting()) {
            // For non-converted triggers (such as Cumulative Upkeep) that don't have costs or targets to worry about
            return true;
        }
        return false;
    }

    /**
     * Ai should run.
     *
     * @param sa the sa
     * @return true, if successful
     */
    public final boolean aiShouldRun(final CardTraitBase effect, final SpellAbility sa, final Card host, final GameEntity affected) {
        if (effect.hasParam("AILogic") && effect.getParam("AILogic").equalsIgnoreCase("ProtectFriendly")) {
            final Player controller = host.getController();
            if (affected instanceof Player) {
                return !((Player) affected).isOpponentOf(controller);
            }
            if (affected instanceof Card) {
                return !((Card) affected).getController().isOpponentOf(controller);
            }
        }
        if (effect.hasParam("AICheckSVar")) {
            final String svarToCheck = effect.getParam("AICheckSVar");
            String comparator = "GE";
            int compareTo = 1;

            if (effect.hasParam("AISVarCompare")) {
                final String fullCmp = effect.getParam("AISVarCompare");
                comparator = fullCmp.substring(0, 2);
                final String strCmpTo = fullCmp.substring(2);
                try {
                    compareTo = Integer.parseInt(strCmpTo);
                } catch (final Exception ignored) {
                    if (sa == null) {
                        compareTo = AbilityUtils.calculateAmount(host, host.getSVar(strCmpTo), effect);
                    } else {
                        compareTo = AbilityUtils.calculateAmount(host, host.getSVar(strCmpTo), sa);
                    }
                }
            }

            int left;
            if (sa == null) {
                left = AbilityUtils.calculateAmount(host, svarToCheck, effect);
            } else {
                left = AbilityUtils.calculateAmount(host, svarToCheck, sa);
            }
            return Expressions.compare(left, comparator, compareTo);
        } else if (effect.isKeyword(Keyword.DREDGE)) {
            return player.getCardsIn(ZoneType.Library).size() > 8 || player.isCardInPlay("Laboratory Maniac");
        }
        // magic-tools: rule 123, optional "instead dig until a card of the chosen kind → hand" draw replacement
        if (forge.ai.ability.DrawDig123.accept(effect, sa, player) != null) {
            return true;
        }
        return sa != null && doTrigger(sa, false);
    }

    public List<SpellAbility> chooseSaToActivateFromOpeningHand(List<SpellAbility> usableFromOpeningHand) {
        // AI would play everything. But limits to one copy of (Leyline of Singularity) and (Gemstone Caverns)

        List<SpellAbility> result = Lists.newArrayList();
        for (SpellAbility sa : usableFromOpeningHand) {
            // Is there a better way for the AI to decide this?
            if (doTrigger(sa, false)) {
                result.add(sa);
            }
        }

        boolean hasLeyline1 = false;
        SpellAbility saGemstones = null;

        List<SpellAbility> toRemove = Lists.newArrayList();
        for (SpellAbility sa : result) {
            String srcName = sa.getHostCard().getName();
            if ("Gemstone Caverns".equals(srcName)) {
                if (saGemstones == null)
                    saGemstones = sa;
                else
                    toRemove.add(sa);
            } else if ("Leyline of Singularity".equals(srcName)) {
                if (!hasLeyline1)
                    hasLeyline1 = true;
                else
                    toRemove.add(sa);
            }
        }
        result.removeAll(toRemove);

        // Play them last
        if (saGemstones != null) {
            result.remove(saGemstones);
            result.add(saGemstones);
        }

        return result;
    }

    public int chooseNumber(SpellAbility sa, String title, int min, int max) {
        final Card source = sa.getHostCard();
        // magic-tools: rule 181, the energy paid for a sweep (Wrath of the Skies)
        final int sweep = ThresholdSweepAi.chooseNumber(player, sa, min, max);
        if (sweep >= 0) {
            return sweep;
        }
        final String logic = sa.getParamOrDefault("AILogic", "Max");
        if ("GainLife".equals(logic)) {
            if (player.getLife() < 5 || player.getCardsIn(ZoneType.Hand).size() >= player.getMaxHandSize()) {
                return min;
            }
        } else if ("LoseLife".equals(logic)) {
            if (player.getLife() > 5) {
                return min;
            }
        } else if ("Min".equals(logic)) {
            return min;
        } else if ("DigACard".equals(logic)) {
            int random = MyRandom.getRandom().nextInt(Math.min(4, max)) + 1;
            if (player.getLife() < random + 5) {
                return min;
            } else {
                return random;
            }
        } else if ("Damnation".equals(logic)) {
            int chosenMax = player.getLife() - 1;
            int cardsInPlay = player.getCardsIn(ZoneType.Battlefield).size();
            return Math.min(chosenMax, cardsInPlay);
        } else if ("OptionalDraw".equals(logic)) {
            int cardsInLib = player.getCardsIn(ZoneType.Library).size();
            if (cardsInLib >= max && player.isCardInPlay("Laboratory Maniac")) {
                return max;
            }
            int cardsInHand = player.getCardsIn(ZoneType.Hand).size();
            int maxDraw = Math.min(player.getMaxHandSize() + 2 - cardsInHand, max);
            int maxCheckLib = Math.min(maxDraw, cardsInLib);
            return Math.max(min, maxCheckLib);
        } else if ("RepeatDraw".equals(logic)) {
            int remaining = player.getMaxHandSize() - player.getCardsIn(ZoneType.Hand).size()
                    + MyRandom.getRandom().nextInt(3);
            return Math.max(remaining, min) / 2;
        } else if ("LowestLoseLife".equals(logic)) {
            return MyRandom.getRandom().nextInt(Math.min(player.getLife() / 3, player.getWeakestOpponent().getLife())) + 1;
        } else if ("HighestLoseLife".equals(logic)) {
            return Math.min(player.getLife() - 1, MyRandom.getRandom().nextInt(Math.max(player.getLife() / 3, player.getWeakestOpponent().getLife())) + 1);
        } else if ("HighestGetCounter".equals(logic)) {
            return MyRandom.getRandom().nextInt(3);
        } else if (sa.hasSVar("EnergyToPay")) {
            return AbilityUtils.calculateAmount(source, sa.getSVar("EnergyToPay"), sa);
        } else if ("Vermin".equals(logic)) {
            if (player.getLife() < 5) {
                return min;
            }

            return MyRandom.getRandom().nextInt(Math.max(player.getLife() - 5, 1));
        } else if ("SweepCreatures".equals(logic)) {
            int minAllowedChoice = AbilityUtils.calculateAmount(source, sa.getParam("Min"), sa);
            int choiceLimit = AbilityUtils.calculateAmount(source, sa.getParam("Max"), sa);
            int maxCreatures = 0;
            for (Player opp : player.getOpponents()) {
                maxCreatures = Math.max(maxCreatures, opp.getCreaturesInPlay().size());
            }
            return Math.min(choiceLimit, Math.max(minAllowedChoice, maxCreatures));
        } else if ("Random".equals(logic)) {
            return MyRandom.getRandom().nextInt((max - min) + 1) + min;
        }
        return max;
    }

    public int chooseNumber(SpellAbility sa, String title, List<Integer> options, Player relatedPlayer) {
        switch (sa.getApi()) {
            case SetLife: // Reverse the Sands
                if (relatedPlayer.equals(sa.getHostCard().getController())) {
                    return Collections.max(options);
                } else if (relatedPlayer.isOpponentOf(sa.getHostCard().getController())) {
                    return Collections.min(options);
                } else {
                    return options.get(0);
                }
            case ChooseNumber:
                if (sa.getHostCard().getName().equals("Emissary's Ploy")) {
                    // Count the amount of creatures in each CMC of 1,2,3 and choose that number
                    // If you have multiple ploys, technically AI should choose different numbers
                    // But thats not what happens currently
                    List<Integer> counter = Lists.newArrayList(0,0,0);
                    int max = 0;
                    int slot = 0;
                    for (Card c : relatedPlayer.getZone(ZoneType.Library).getCards()) {
                        if (!c.isCreature()) {
                            continue;
                        }

                        if (c.getCMC() > 0 && c.getCMC() < 4) {
                            counter.set(c.getCMC() - 1, counter.get(c.getCMC() - 1) + 1);
                        }
                    }
                    for(int i = 0; i < counter.size(); i++) {
                        if (counter.get(i) >= max) {
                            max = counter.get(i);
                            slot = i;
                        }
                    }
                    return slot;
                }

                return Aggregates.random(options);
            default:
                return options.get(0);
        }
    }

    public boolean confirmPayment(CostPart costPart) {
        throw new UnsupportedOperationException("AI is not supposed to reach this code at the moment");
    }

    public int attemptToAssist(SpellAbility sa, int max, int request) {
        Player activator = sa.getActivatingPlayer();

        if (game.getPlayers().size() == 2) {
            // Never help your opponent in a 2 player game
            return 0;
        }

        PlayerCollection allies = player.getAllies();

        if (allies.isEmpty()) {
            // AI only has opponents.
            // TODO: Maybe help out someone if it seems good for us, but who knows how you calculate that.
            // Probably needs some specific AI here.
            // If the spell is a creature, probably don't help.
            // If spell is a instant/sorcery, help based on the situation
            return 0;
        } else {
            // AI has allies, don't help out anyone but allies.
            if (!allies.contains(activator)) {
                return 0;
            }
        }

        // AI has decided to help. Now let's figure out how much they can help
        int mana = getAvailableManaEstimate(player, true);

        // TODO We should make a logical guess here, but for now just uh yknow randomly decide?
        // What do I want to play next? Can I still pay for that and have mana left over to help?
        // Is the spell I'm helping cast better for me than the thing I would cast?
        if (MyRandom.percentTrue(80)) {
            return 0;
        }

        int willingToPay = 0;
        if (mana >= request) {
            return request;
        } else {
            return mana;
        }
    }

    public CardCollection chooseCardsForEffect(CardCollectionView pool, SpellAbility sa, int min, int max, boolean isOptional, Map<String, Object> params) {
        if (sa == null || sa.getApi() == null) {
            throw new UnsupportedOperationException();
        }
        CardCollection result = new CardCollection();
        if (sa.hasParam("AIMaxAmount")) {
            max = AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("AIMaxAmount"), sa);
        }
        switch (sa.getApi()) {
            case TwoPiles:
                // TODO: improve AI
                Card biggest = null;
                Card smallest = null;
                biggest = pool.get(0);
                smallest = pool.get(0);

                for (Card c : pool) {
                    if (c.getCMC() >= biggest.getCMC()) {
                        biggest = c;
                    } else if (c.getCMC() <= smallest.getCMC()) {
                        smallest = c;
                    }
                }
                result.add(biggest);

                if (max > 3 && !result.contains(smallest)) {
                    result.add(smallest);
                }
                break;
            case MultiplePiles:
                // Whims of the Fates {all, 0, 0}
                result.addAll(pool);
                break;
            case FlipOntoBattlefield:
                if ("DamageCreatures".equals(sa.getParam("AILogic"))) {
                    int maxToughness = Integer.parseInt(sa.getSubAbility().getParam("NumDmg"));
                    CardCollectionView rightToughness = CardLists.filter(pool, card -> card.getController().isOpponentOf(sa.getActivatingPlayer())
                            && card.getNetToughness() <= maxToughness
                            && card.canBeDestroyed());
                    Card bestCreature = ComputerUtilCard.getBestCreatureAI(rightToughness.isEmpty() ? pool : rightToughness);
                    if (bestCreature != null) {
                        result.add(bestCreature);
                        break;
                    }
                } else {
                    CardCollectionView viableOptions = CardLists.filter(pool, CardPredicates.isControlledByAnyOf(sa.getActivatingPlayer().getOpponents()), Card::canBeDestroyed);
                    Card best = ComputerUtilCard.getBestAI(viableOptions);
                    if (best != null) {
                        result.add(best);
                        break;
                    }
                }
                result.add(Aggregates.random(pool)); // should ideally never get here
                break;
            default:
                CardCollection editablePool = new CardCollection(pool);
                for (int i = 0; i < max; i++) {
                    Card c = player.getController().chooseSingleEntityForEffect(editablePool, sa, null, isOptional, params);
                    if (c == null) {
                        break;
                    }
                    result.add(c);
                    editablePool.remove(c);

                    // Special case for Bow to My Command which simulates a complex tap cost via ChooseCard
                    // TODO: consider enhancing support for tapXType<Any/...> in UnlessCost to get rid of this hack
                    if ("BowToMyCommand".equals(sa.getParam("AILogic"))) {
                        if (!sa.getHostCard().isInZone(ZoneType.Command)) {
                            // Make sure that other opponents do not tap for an already abandoned scheme
                            result.clear();
                            break;
                        }

                        int totPower = 0;
                        for (Card p : result) {
                            totPower += p.getNetPower();
                        }
                        if (totPower >= 8) {
                            break;
                        }
                    }
                }
        }

        // TODO: Hack for Phyrexian Dreadnought. Might need generalization (possibly its own AI logic)
        if ("Phyrexian Dreadnought".equals(ComputerUtilAbility.getAbilitySourceName(sa))) {
            result = SpecialCardAi.PhyrexianDreadnought.reviseCreatureSacList(player, sa, result);
        }

        return result;
    }

    public Map<DeckSection, List<? extends PaperCard>> complainCardsCantPlayWell(Deck myDeck) {
        Map<DeckSection, List<? extends PaperCard>> complaints = new HashMap<>();
        // When using simulation, AI should be able to figure out most cards.
        if (!usesFullSimulation()) {
            complaints = myDeck.getUnplayableAICards().unplayable;
        }
        return complaints;
    }

    // this is where the computer cheats
    // changes AllZone.getComputerPlayer().getZone(Zone.Library)

    /**
     * <p>
     * smoothComputerManaCurve.
     * </p>
     *
     * @param in an array of {@link forge.game.card.Card} objects.
     * @return an array of {@link forge.game.card.Card} objects.
     */
    public CardCollectionView cheatShuffle(CardCollectionView in) {
        if (in.size() < 20 || !getBoolProperty(AiProps.CHEAT_WITH_MANA_ON_SHUFFLE) || !getGame().getRules().isAllowCheatShuffle()) {
            return in;
        }

        final CardCollection library = new CardCollection(in);
        CardLists.shuffle(library);

        // remove all land, keep non-basicland in there, shuffled
        CardCollection land = CardLists.filter(library, CardPredicates.LANDS);
        for (Card c : land) {
            if (c.isLand()) {
                library.remove(c);
            }
        }

        try {
            // mana weave, total of 7 land
            // The Following have all been reduced by 1, to account for the
            // computer starting first.
            library.add(5, land.get(0));
            library.add(6, land.get(1));
            library.add(8, land.get(2));
            library.add(9, land.get(3));
            library.add(10, land.get(4));

            library.add(12, land.get(5));
            library.add(15, land.get(6));
        } catch (final IndexOutOfBoundsException e) {
            System.err.println("Error: cannot smooth mana curve, not enough land");
            return in;
        }

        // add the rest of land to the end of the deck
        for (Card card : land) {
            if (!library.contains(card)) {
                library.add(card);
            }
        }

        return library;
    } // smoothComputerManaCurve()

    public boolean chooseDirection(SpellAbility sa) {
        if (sa == null || sa.getApi() == null) {
            throw new UnsupportedOperationException();
        }
        // Left:True; Right:False
        if ("GainControl".equals(sa.getParam("AILogic")) && game.getPlayers().size() > 2) {
            CardCollection creats = CardLists.getType(game.getCardsIn(ZoneType.Battlefield), "Creature");
            CardCollection left = CardLists.filterControlledBy(creats, game.getNextPlayerAfter(player, Direction.Left));
            CardCollection right = CardLists.filterControlledBy(creats, game.getNextPlayerAfter(player, Direction.Right));
            if (!left.isEmpty() || !right.isEmpty()) {
                CardCollection all = new CardCollection(left);
                all.addAll(right);
                return left.contains(ComputerUtilCard.getBestCreatureAI(all));
            }
        }
        if ("Aminatou".equals(sa.getParam("AILogic")) && game.getPlayers().size() > 2) {
            CardCollection all = CardLists.filter(game.getCardsIn(ZoneType.Battlefield), CardPredicates.NONLAND_PERMANENTS);
            CardCollection left = CardLists.filterControlledBy(all, game.getNextPlayerAfter(player, Direction.Left));
            CardCollection right = CardLists.filterControlledBy(all, game.getNextPlayerAfter(player, Direction.Right));
            return Aggregates.sum(left, Card::getCMC) > Aggregates.sum(right, Card::getCMC);
        }
        return MyRandom.getRandom().nextBoolean();
    }

    public boolean chooseEvenOdd(SpellAbility sa) {
        String aiLogic = sa.getParamOrDefault("AILogic", "");

        if (aiLogic.equals("AlwaysEven")) {
            return false;
        } else if (aiLogic.equals("AlwaysOdd")) {
            return true;
        } else if (aiLogic.equals("Random")) {
            return MyRandom.getRandom().nextBoolean();
        } else if (aiLogic.equals("CMCInHand")) {
            CardCollectionView hand = sa.getActivatingPlayer().getCardsIn(ZoneType.Hand);
            int numEven = CardLists.filter(hand, CardPredicates.evenCMC()).size();
            int numOdd = CardLists.filter(hand, CardPredicates.oddCMC()).size();
            return numOdd > numEven;
        } else if (aiLogic.equals("CMCOppControls")) {
            CardCollectionView hand = sa.getActivatingPlayer().getOpponents().getCardsIn(ZoneType.Battlefield);
            int numEven = CardLists.filter(hand, CardPredicates.evenCMC()).size();
            int numOdd = CardLists.filter(hand, CardPredicates.oddCMC()).size();
            return numOdd > numEven;
        } else if (aiLogic.equals("CMCOppControlsByPower")) {
            // TODO: improve this to check for how dangerous those creatures actually are relative to host card
            CardCollectionView hand = sa.getActivatingPlayer().getOpponents().getCardsIn(ZoneType.Battlefield);
            int powerEven = Aggregates.sum(CardLists.filter(hand, CardPredicates.evenCMC()), Card::getNetPower);
            int powerOdd = Aggregates.sum(CardLists.filter(hand, CardPredicates.oddCMC()), Card::getNetPower);
            return powerOdd > powerEven;
        }
        return MyRandom.getRandom().nextBoolean(); // outside of any specific logic, choose randomly
    }

    public Card chooseCardToHiddenOriginChangeZone(ZoneType destination, List<ZoneType> origin, SpellAbility sa,
                                                   CardCollection fetchList, Player player2, Player decider) {
        if (usesFullSimulation()) {
            return simPicker.chooseCardToHiddenOriginChangeZone(destination, origin, sa, fetchList, player2, decider);
        }

        if (sa.getApi() == ApiType.Learn) {
            return LearnAi.chooseCardToLearn(fetchList, decider, sa);
        } else {
            return ChangeZoneAi.chooseCardToHiddenOriginChangeZone(destination, origin, sa, fetchList, player2, decider);
        }
    }

    public List<SpellAbility> orderPlaySa(List<SpellAbility> activePlayerSAs) {
        // list is only one or empty, no need to filter
        if (activePlayerSAs.size() < 2) {
            return activePlayerSAs;
        }

        // filter list by ApiTypes
        List<SpellAbility> discard = filterListByApi(activePlayerSAs, ApiType.Discard);
        List<SpellAbility> mandatoryDiscard = filterList(discard, SpellAbility::isMandatory);

        List<SpellAbility> draw = filterListByApi(activePlayerSAs, ApiType.Draw);

        List<SpellAbility> putCounter = filterListByApi(activePlayerSAs, ApiType.PutCounter);
        List<SpellAbility> putCounterAll = filterListByApi(activePlayerSAs, ApiType.PutCounterAll);

        List<SpellAbility> evolve = filterList(putCounter, CardTraitPredicates.isKeyword(Keyword.EVOLVE));

        List<SpellAbility> token = filterListByApi(activePlayerSAs, ApiType.Token);
        List<SpellAbility> pump = filterListByApi(activePlayerSAs, ApiType.Pump);
        List<SpellAbility> pumpAll = filterListByApi(activePlayerSAs, ApiType.PumpAll);

        List<SpellAbility> result = Lists.newArrayList(activePlayerSAs);

        // do mandatory discard early if hand is empty or has DiscardMe card
        CardCollectionView playerHand = player.getCardsIn(ZoneType.Hand);
        if (!playerHand.isEmpty() && !playerHand.anyMatch(CardPredicates.hasSVar("DiscardMe"))) {
            result.addAll(mandatoryDiscard);
            mandatoryDiscard.clear();
        }

        // optional Discard, probably combined with Draw
        result.addAll(discard);
        // do Draw before Discard
        result.addAll(draw);

        result.addAll(putCounterAll);
        // do putCounter before Draw/Discard because it can cause a Draw Trigger
        result.addAll(putCounter);
        // do Evolve Trigger before other PutCounter SpellAbilities
        result.addAll(evolve);

        // token should be added first so they might get the pump bonus
        result.addAll(pumpAll);
        result.addAll(pump);
        result.addAll(token);

        result.addAll(mandatoryDiscard);

        return result;
    }

    // TODO move to more common place
    private static <T> List<T> filterList(List<T> input, Predicate<? super T> pred) {
        List<T> filtered = input.stream().filter(pred).collect(Collectors.toList());
        input.removeAll(filtered);
        return filtered;
    }

    // TODO move to more common place
    public static <T extends TriggerReplacementBase> List<T> filterList(List<T> input, Function<SpellAbility, Object> pred, Object value) {
        return filterList(input, trb -> trb.ensureAbility() != null && pred.apply(trb.ensureAbility()) == value);
    }

    public static List<SpellAbility> filterListByApi(List<SpellAbility> input, ApiType type) {
        return filterList(input, SpellAbilityPredicates.isApi(type));
    }

    private <T extends CardTraitBase> List<T> filterListByAiLogic(List<T> list, final String logic) {
        return filterList(list, CardTraitPredicates.hasParam("AILogic", logic));
    }

    public List<AbilitySub> chooseModeForAbility(SpellAbility sa, List<AbilitySub> possible, int min, int num, boolean allowRepeat) {
        if (simPicker != null) {
            return simPicker.chooseModeForAbility(sa, possible, min, num, allowRepeat);
        }
        return null;
    }

    public CardCollectionView chooseSacrificeType(String type, SpellAbility ability, boolean effect, int amount, final CardCollectionView exclude) {
        if (simPicker != null) {
            return simPicker.chooseSacrificeType(type, ability, effect, amount, exclude);
        }
        return ComputerUtil.chooseSacrificeType(player, type, ability, ability.getTargetCard(), effect, amount, exclude);
    }

    private boolean checkAiSpecificRestrictions(final SpellAbility sa) {
        // AI-specific restrictions specified as activation parameters in spell abilities

        if (sa.hasParam("AILifeThreshold")) {
            return player.getLife() > Integer.parseInt(sa.getParam("AILifeThreshold"));
        }

        return true;
    }

    // AI logic for choosing which replacement effect to apply happens here.
    public ReplacementEffect chooseSingleReplacementEffect(List<ReplacementEffect> list) {
        // no need to choose anything
        if (list.size() <= 1) {
            return list.get(0);
        }

        ReplacementType mode = list.get(0).getMode();

        if (mode.equals(ReplacementType.GainLife)) {
            List<ReplacementEffect> noGain = filterListByAiLogic(list, "NoLife");
            List<ReplacementEffect> loseLife = filterListByAiLogic(list, "LoseLife");
            List<ReplacementEffect> doubleLife = filterListByAiLogic(list, "DoubleLife");
            List<ReplacementEffect> lichDraw = filterListByAiLogic(list, "LichDraw");

            if (!noGain.isEmpty()) {
                // no lifegain is better than lose life
                return noGain.get(0);
            } else if (!loseLife.isEmpty()) {
                // lose life before double life to prevent lose double
                return loseLife.get(0);
            } else if (!lichDraw.isEmpty()) {
                // lich draw before double life to prevent to draw to much
                return lichDraw.get(0);
            } else if (!doubleLife.isEmpty()) {
                // other than that, do double life
                return doubleLife.get(0);
            }
        } else if (mode.equals(ReplacementType.DamageDone)) {
            // magic-tools: rule 261, double the damage before Vigor / Purity turn it into counters or life
            final ReplacementEffect increase = PreventionPayoff261.firstIncrease(player, list);
            if (increase != null) {
                return increase;
            }
            List<ReplacementEffect> prevention = filterList(list, CardTraitPredicates.hasParam("Prevent"));

            // TODO when Protection is done as ReplacementEffect do them
            // before normal prevention
            if (!prevention.isEmpty()) {
                return prevention.get(0);
            }
        } else if (mode.equals(ReplacementType.Destroy)) {
            List<ReplacementEffect> shield = filterList(list, CardTraitPredicates.hasParam("ShieldCounter"));
            List<ReplacementEffect> regeneration = filterList(list, CardTraitPredicates.hasParam("Regeneration"));
            List<ReplacementEffect> umbraArmor = filterList(list, CardTraitPredicates.isKeyword(Keyword.UMBRA_ARMOR));
            List<ReplacementEffect> umbraArmorIndestructible = filterList(umbraArmor, x -> x.getHostCard().hasKeyword(Keyword.INDESTRUCTIBLE));

            // Indestructible umbra armor is the best
            if (!umbraArmorIndestructible.isEmpty()) {
                return umbraArmorIndestructible.get(0);
            }

            // then it might be better to remove shield counter if able?
            if (!shield.isEmpty()) {
                return shield.get(0);
            }

            // TODO get the RunParams for Affected to check if the creature already dealt combat damage for Regeneration effects
            // is using a Regeneration Effect better than using a Umbra Armor?
            if (!regeneration.isEmpty()) {
                return regeneration.get(0);
            }

            if (!umbraArmor.isEmpty()) {
                // sort them by cmc
                umbraArmor.sort(Comparator.comparing(CardTraitBase::getHostCard, Comparator.comparing(Card::getCMC)));
                return umbraArmor.get(0);
            }
        } else if (mode.equals(ReplacementType.Draw)) {
            List<ReplacementEffect> winGame = filterList(list, SpellAbility::getApi, ApiType.WinsGame);
            if (!winGame.isEmpty()) {
                return winGame.get(0);
            }
        }

        // TODO always lower counters with Vorinclex first, might turn it from 1 to 0 as final

        return list.get(0);
    }

}
