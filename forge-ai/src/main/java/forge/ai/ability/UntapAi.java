package forge.ai.ability;

import forge.ai.UntapManaAi;

import java.util.List;
import java.util.Map;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.ComputerUtil;
import forge.ai.ComputerUtilAbility;
import forge.ai.ComputerUtilCard;
import forge.ai.ComputerUtilCombat;
import forge.ai.ComputerUtilCost;
import forge.ai.ComputerUtilMana;
import forge.ai.SpellAbilityAi;
import forge.card.mana.ManaCostShard;
import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.combat.Combat;
import forge.game.cost.Cost;
import forge.game.cost.CostPartMana;
import forge.game.cost.CostTap;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerCollection;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;

public class UntapAi extends SpellAbilityAi {
    @Override
    protected boolean checkAiLogic(final Player ai, final SpellAbility sa, final String aiLogic) {
        if ("PoolExtraMana".equals(aiLogic)) {
            // magic-tools: rule 207, rule 146's plan works in main 1 too (Arbor Elf, tester a test game)
            return UntapManaAi.hasManaPlan(ai, sa) || doPoolExtraManaLogic(ai, sa);
        }
        if ("UntapManaCreature".equals(aiLogic)) {
            return doUntapManaCreatureLogic(ai, sa);
        }
        if ("UntapManaPermanent".equals(aiLogic)) {
            return doUntapManaPermanentLogic(ai, sa);
        }
        if ("PreventCombatDamage".equals(aiLogic)) {
            return doPreventCombatDamageLogic(ai, sa);
            // In the future if you want to give Pseudo vigilance to a creature you attacked with
            // activate during your own during the end of combat step
        }

        return super.checkAiLogic(ai, sa, aiLogic);
    }

    @Override
    protected boolean willPayCosts(final Player payer, final SpellAbility sa, final Cost cost, final Card source) {
        if (!ComputerUtilCost.checkAddM1M1CounterCost(cost, source)) {
            return false;
        }

        return ComputerUtilCost.checkDiscardCost(payer, cost, source, sa);
    }

    @Override
    protected AiAbilityDecision checkApiLogic(Player ai, SpellAbility sa) {
        final Card source = sa.getHostCard();

        // magic-tools: rule 146, the land just tapped for a cost one land short gets untapped to be tapped again
        // (or, Krosan Restorer with threshold, the best tapped lands when their mana reaches a wanted cost)
        if (UntapManaAi.declineForSibling(ai, sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        final List<Card> manaLands = UntapManaAi.chooseLands(ai, sa);
        if (manaLands != null) {
            sa.resetTargets();
            for (final Card land : manaLands) {
                sa.getTargets().add(land);
            }
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }
        if (UntapManaAi.saveForEngine(ai, sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        if (sa.usesTargeting()) {
            if (untapPrefTargeting(ai, sa, false)) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }

        final List<Card> pDefined = AbilityUtils.getDefinedCards(source, sa.getParam("Defined"), sa);
        if (pDefined.isEmpty() || (pDefined.get(0).isTapped() && pDefined.get(0).getController() == ai)) {
            // If the defined card is tapped, or if there are no defined cards, we can play this ability
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        }
        return new AiAbilityDecision(0, AiPlayDecision.MissingNeededCards);
    }

    @Override
    protected AiAbilityDecision doTriggerNoCost(Player ai, SpellAbility sa, boolean mandatory) {
        if (!sa.usesTargeting()) {
            if (mandatory) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
            if ("Never".equals(sa.getParam("AILogic"))) {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }
            // magic-tools: rule 160, "untap up to N lands" (UntapType) has no Defined card: Defined names the player
            // who untaps, and the old fallback to Self (the card, untapped in hand) refused the trigger, so a creature
            // with this ETB (Peregrine Drake) was never cast. We gain when we (or a teammate) choose what untaps.
            if (sa.hasParam("UntapType")) {
                for (final Player p : AbilityUtils.getDefinedPlayers(sa.getHostCard(), sa.getParam("Defined"), sa)) {
                    if (!p.isOpponentOf(ai)) {
                        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
                    }
                }
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }

            final List<Card> pDefined = AbilityUtils.getDefinedCards(sa.getHostCard(), sa.getParam("Defined"), sa);
            if (pDefined.isEmpty() || (pDefined.get(0).isTapped() && pDefined.get(0).getController() == ai)) {
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
            return new AiAbilityDecision(0, AiPlayDecision.MissingNeededCards);
        } else if (untapPrefTargeting(ai, sa, mandatory)) {
            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
        } else if (mandatory) {
            // not enough preferred targets, but mandatory so keep going:
            if (untapUnpreferredTargeting(sa, mandatory)) {
                return new AiAbilityDecision(50, AiPlayDecision.MandatoryPlay);
            }
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }

        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    @Override
    public AiAbilityDecision chkDrawback(Player ai, SpellAbility sa) {
        if (!sa.usesTargeting()) {
            // who cares if its already untapped, it's only a subability?
        } else {
            if (!untapPrefTargeting(ai, sa, false)) {
                return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
            }
        }

        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    /**
     * <p>
     * untapPrefTargeting.
     * </p>
     * 
     * @param sa
     *            a {@link forge.game.spellability.SpellAbility} object.
     * @param mandatory
     *            a boolean.
     * @return a boolean.
     */
    private static boolean untapPrefTargeting(final Player ai, final SpellAbility sa, final boolean mandatory) {
        final Card source = sa.getHostCard();

        if (alreadyAssignedTarget(sa)) {
            if (sa.getTargets().size() > 0) {
                // If we selected something lets assume its valid
                return true;
            }
        }
        sa.resetTargets();

        final PlayerCollection targetController;
        if (sa.isCurse() || (sa.getSubAbility() != null && sa.getSubAbility().getApi() == ApiType.GainControl)) {
            targetController = ai.getOpponents();
        } else {
            targetController = ai.getYourTeam();
        }

        CardCollection list = CardLists.getTargetableCards(targetController.getCardsIn(ZoneType.Battlefield), sa);

        if (!sa.isCurse()) {
            list = ComputerUtil.getSafeTargets(ai, sa, list);
        }

        if (list.isEmpty()) {
            return false;
        }

        // For some abilities, it may be worth to target even an untapped card if we're targeting mostly for the subability
        boolean targetUntapped = false;
        if (sa.getSubAbility() != null) {
            SpellAbility subSa = sa.getSubAbility();
            if (subSa.getApi() == ApiType.RemoveFromCombat && "RemoveBestAttacker".equals(subSa.getParam("AILogic"))) {
                targetUntapped = true;
                Combat combat = ai.getGame().getCombat();
                if (combat == null) {
                    return false;
                }
                list = CardLists.filter(list, c -> combat.isAttacking(c, ai));
                if (list.isEmpty()) {
                    return false;
                }
            }
        }

        CardCollection untapList = targetUntapped ? list : CardLists.filter(list, CardPredicates.TAPPED);
        // filter out enchantments and planeswalkers, their tapped state doesn't matter.
        final String[] tappablePermanents = {"Creature", "Land", "Artifact"};
        untapList = CardLists.getValidCards(untapList, tappablePermanents, source.getController(), source, sa);

        // Try to avoid potential infinite recursion,
        // e.g. Kiora's Follower untapping another Kiora's Follower and repeating infinitely
        if (sa.getPayCosts().hasOnlySpecificCostType(CostTap.class)) {
            CardCollection toRemove = new CardCollection();
            for (Card c : untapList) {
                for (SpellAbility ab : c.getAllSpellAbilities()) {
                    if (ab.getApi() == ApiType.Untap
                            && ab.getPayCosts().hasOnlySpecificCostType(CostTap.class)
                            && ab.canTarget(source)) {
                        toRemove.add(c);
                        break;
                    }
                }
            }
            untapList.removeAll(toRemove);
        }

        //try to exclude things that will already be untapped due to something on stack or because something is
        //already targeted in a parent or sub SA
        if (!sa.isTrigger() || mandatory) { // but if just confirming trigger no need to look for other targets and might still help anyway
            CardCollection toExclude = ComputerUtilAbility.getCardsTargetedWithApi(ai, untapList, sa, ApiType.Untap);
            untapList.removeAll(toExclude);
        }

        while (sa.canAddMoreTarget()) {
            Card choice = null;

            if (untapList.isEmpty()) {
                // Animate untapped lands (Koth of the Hammer)
                if (sa.getSubAbility() != null && sa.getSubAbility().getApi() == ApiType.Animate && !list.isEmpty()
                        && ai.getGame().getPhaseHandler().getPhase().isBefore(PhaseType.COMBAT_DECLARE_ATTACKERS)) {
                    choice = ComputerUtilCard.getWorstPermanentAI(list, false, false, false, false);
                } else if (!sa.isMinTargetChosen() || sa.isZeroTargets()) {
                    // check if the cost is acceptable anyway (e.g. Planeswalker +Loyalty)
                    if (ComputerUtil.activateForCost(sa, ai)) {
                        return true;
                    }
                    sa.resetTargets();
                    return false;
                } else {
                    // TODO is this good enough? for up to amounts?
                    break;
                }
            } else {
                choice = detectPriorityUntapTargets(untapList);

                if (choice == null) {
                    if (CardLists.getNotType(untapList, "Creature").isEmpty()) {
                        choice = ComputerUtilCard.getBestCreatureAI(untapList); // if only creatures take the best
                    } else if (!sa.getPayCosts().hasManaCost() || sa.isTrigger()
                            || "Always".equals(sa.getParam("AILogic"))) {
                        choice = ComputerUtilCard.getMostExpensivePermanentAI(untapList);
                    }
                }
            }

            if (choice == null) { // can't find anything left
                if (!sa.isMinTargetChosen() || sa.isZeroTargets()) {
                    sa.resetTargets();
                    return false;
                } else {
                    // TODO is this good enough? for up to amounts?
                    break;
                }
            }

            untapList.remove(choice);
            list.remove(choice);
            // TODO ComputerUtilCard.willUntap(ai, choice)
            sa.getTargets().add(choice);
        }
        return true;
    }

    /**
     * <p>
     * untapUnpreferredTargeting.
     * </p>
     *
     * @param sa
     *            a {@link forge.game.spellability.SpellAbility} object.
     * @param mandatory
     *            a boolean.
     * @return a boolean.
     */
    private boolean untapUnpreferredTargeting(final SpellAbility sa, final boolean mandatory) {
        final Card source = sa.getHostCard();
        final TargetRestrictions tgt = sa.getTargetRestrictions();

        CardCollection list = CardLists.getTargetableCards(source.getGame().getCardsIn(ZoneType.Battlefield), sa);

        // filter by enchantments and planeswalkers, their tapped state doesn't matter.
        final String[] tappablePermanents = { "Enchantment", "Planeswalker" };
        CardCollection tapList = CardLists.getValidCards(list, tappablePermanents, source.getController(), source, sa);

        if (untapTargetList(source, sa, mandatory, tapList)) {
            return true;
        }

        // try to just tap already tapped things
        tapList = CardLists.filter(list, CardPredicates.UNTAPPED);

        if (untapTargetList(source, sa, mandatory, tapList)) {
            return true;
        }

        // just tap whatever we can
        tapList = list;

        return untapTargetList(source, sa, mandatory, tapList);
    }

    private boolean untapTargetList(final Card source, final SpellAbility sa, final boolean mandatory,
            final CardCollection tapList) {
        tapList.removeAll(sa.getTargets().getTargetCards());

        if (tapList.isEmpty()) {
            return false;
        }

        while (sa.canAddMoreTarget()) {
            Card choice = null;

            if (tapList.isEmpty()) {
                if (sa.getTargets().size() < sa.getMinTargets() || sa.getTargets().size() == 0) {
                    if (!mandatory) {
                        sa.resetTargets();
                    }
                    return false;
                } else {
                    // TODO is this good enough? for up to amounts?
                    break;
                }
            }

            choice = ComputerUtilCard.getBestAI(tapList);

            if (choice == null) { // can't find anything left
                if (sa.getTargets().size() < sa.getMinTargets() || sa.getTargets().size() == 0) {
                    if (!mandatory) {
                        sa.resetTargets();
                    }
                    return false;
                } else {
                    // TODO is this good enough? for up to amounts?
                    break;
                }
            }

            tapList.remove(choice);
            sa.getTargets().add(choice);
        }

        return true;
    }

    @Override
    public Card chooseSingleCard(Player ai, SpellAbility sa, Iterable<Card> list, boolean isOptional, Player targetedPlayer, Map<String, Object> params) {
        CardCollection filteredList = CardLists.filterControlledBy(list, ai.getYourTeam());
        if (!filteredList.isEmpty()) {
            return ComputerUtilCard.getBestAI(filteredList);
        }
        if (isOptional) {
            return null;
        }
        return ComputerUtilCard.getWorstAI(list);
    }

    private static Card detectPriorityUntapTargets(final List<Card> untapList) {
        // See if there are cards that are *especially* worth untapping, like Time Vault
        for (Card c : untapList) {
            if ("True".equals(c.getSVar("UntapMe"))) {
                return c;
            }
        }

        // See if there's anything to untap that is tapped and that doesn't untap during the next untap step by itself
        CardCollection noAutoUntap = CardLists.filter(untapList, c -> !c.canUntap(c.getController(), true));
        if (!noAutoUntap.isEmpty()) {
            return ComputerUtilCard.getBestAI(noAutoUntap);
        }

        return null;
    }

    private boolean doPreventCombatDamageLogic(final Player ai, final SpellAbility sa) {
        // Only Maze of Ith and Maze of Shadows uses this. Feel free to use it aggressively.
        Game game = ai.getGame();
        sa.resetTargets();

        if (!game.getPhaseHandler().getPlayerTurn().isOpponentOf(ai)) {
            return false;
        }

        Combat activeCombat = game.getCombat();
        if (activeCombat == null) {
            return false;
        }

        CardCollection list = CardLists.getTargetableCards(activeCombat.getAttackers(), sa);
        list = CardLists.filter(list, c -> activeCombat.isAttacking(c, ai));

        if (list.isEmpty()) {
            return false;
        }

        if (game.getPhaseHandler().is(PhaseType.COMBAT_DECLARE_BLOCKERS)) {
            // Blockers already set. Are there any dangerous unblocked creatures? Sort by creature that will deal the most damage?
            Card card = ComputerUtilCombat.mostDangerousAttacker(list, ai, activeCombat, true);

            if (card == null) { return false; }

            sa.getTargets().add(card);
            return true;
        }

        return false;
    }

    private static boolean alreadyAssignedTarget(final SpellAbility sa) {
        if (sa.hasParam("AILogic")) {
            String aiLogic = sa.getParam("AILogic");
            return "PreventCombatDamage".equals(aiLogic) || "UntapManaCreature".equals(aiLogic)
                    || "UntapManaPermanent".equals(aiLogic);
        }
        return false;
    }

    /**
     * Quirion Ranger, Wirewood Symbiote : dégager une créature de mana engagée (ex. Elvish Archdruid)
     * seulement si, une fois dégagée (et la carte du coût renvoyée), un sort de la main qu'on ne peut
     * pas payer maintenant devient payable. La cible est choisie ici.
     * Limite : la carte renvoyée compte encore pour les créatures qui comptent les Elfes.
     */
    private boolean doUntapManaCreatureLogic(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || !game.getStack().isEmpty()) {
            return false;
        }

        List<SpellAbility> wanted = unpayableSpellsInHand(ai, null);
        if (wanted.isEmpty()) {
            return false;
        }

        CardCollection tapped = CardLists.filter(ai.getCreaturesInPlay(), CardPredicates.TAPPED);
        tapped = CardLists.getTargetableCards(tapped, sa);
        tapped = CardLists.filter(tapped, c -> manaProducedBy(ai, c) > 0);
        CardLists.sortByPowerDesc(tapped);
        // règle 68 : le plus de mana d'abord (Elvish Archdruid avant Llanowar Elves, même à force égale)
        tapped.sort(java.util.Comparator.comparingInt((Card c) -> -manaProducedBy(ai, c)));
        for (Card c : tapped) {
            CardCollection payment = CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield),
                    returnCostType(sa).split(";"), ai, source, sa);
            payment.remove(c);
            if (payment.isEmpty()) {
                continue;
            }
            Card paid = orderReturnPayment(ai, payment).getFirst();
            boolean paidWasUntapped = paid.isUntapped();
            // simulation : la cible dégagée, la carte du coût engagée (elle quitte le champ de bataille)
            c.setTapped(false);
            paid.setTapped(true);
            try {
                for (SpellAbility ab : wanted) {
                    if (ComputerUtilMana.canPayManaCost(ab, ai, 0, false)) {
                        sa.resetTargets();
                        sa.getTargets().add(c);
                        return true;
                    }
                }
            } finally {
                paid.setTapped(!paidWasUntapped);
                c.setTapped(true);
            }
        }
        return false;
    }

    /**
     * Kiora's Follower ({T} : dégagez un autre permanent) : en phase principale de l'IA, pile vide, dégager
     * le permanent engagé qui donne le plus de mana (terrain enchanté par Wild Growth, Cloudpost, créature de
     * mana) seulement si un sort de la main qu'on ne peut pas payer maintenant devient payable.
     */
    private boolean doUntapManaPermanentLogic(final Player ai, final SpellAbility sa) {
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || !game.getStack().isEmpty()) {
            return false;
        }
        // magic-tools: rule 151, a ping tapper (Hawkeye's Bow on it) untaps the mana that untaps it
        final Card pingTgt = forge.ai.TapPingLoopAi.pingTarget(ai, sa);
        if (pingTgt != null) {
            sa.resetTargets();
            sa.getTargets().add(pingTgt);
            return true;
        }
        List<SpellAbility> wanted = unpayableSpellsInHand(ai, null);
        if (wanted.isEmpty()) {
            return false;
        }
        CardCollection tapped = CardLists.filter(ai.getCardsIn(ZoneType.Battlefield), CardPredicates.TAPPED);
        tapped = CardLists.getTargetableCards(tapped, sa);
        tapped = CardLists.filter(tapped, c -> manaProducedBy(ai, c) > 0);
        // le plus de mana d'abord ; un terrain enchanté (Wild Growth, Utopia Sprawl) donne un mana de plus
        tapped.sort(java.util.Comparator.comparingInt((Card c) -> -(manaProducedBy(ai, c) + c.getEnchantedBy().size())));
        for (Card c : tapped) {
            c.setTapped(false);
            try {
                for (SpellAbility ab : wanted) {
                    if (ComputerUtilMana.canPayManaCost(ab, ai, 0, false)) {
                        sa.resetTargets();
                        sa.getTargets().add(c);
                        return true;
                    }
                }
            } finally {
                c.setTapped(true);
            }
        }
        return false;
    }

    /** Sorts de la main (hors terrains et hors {@code except}) qu'on ne peut pas payer maintenant. */
    private static List<SpellAbility> unpayableSpellsInHand(final Player ai, final Card except) {
        List<SpellAbility> wanted = new java.util.ArrayList<>();
        for (Card c : CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.NON_LANDS)) {
            if (c == except) {
                continue;
            }
            for (SpellAbility ab : c.getAllPossibleAbilities(ai, false)) {
                ab.setActivatingPlayer(ai);
                if (ab.isSpell() && ab.isBasicSpell() && ab.getPayCosts().getCostMana() != null
                        && !ComputerUtilMana.canPayManaCost(ab, ai, 0, false)) {
                    wanted.add(ab);
                }
            }
        }
        return wanted;
    }

    /**
     * Vitalize (« dégagez toutes les créatures que vous contrôlez ») : en phase principale de l'IA, pile vide,
     * seulement si, une fois ses créatures de mana engagées dégagées, un sort de la main qu'on ne peut pas payer
     * maintenant devient payable en plus du coût de Vitalize (compté comme 1 mana générique en plus).
     */
    public static boolean doUntapAllManaLogic(final Player ai, final SpellAbility sa) {
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || !game.getStack().isEmpty()) {
            return false;
        }
        CardCollection tapped = CardLists.filter(ai.getCreaturesInPlay(), CardPredicates.TAPPED);
        tapped = CardLists.filter(tapped, c -> manaProducedBy(ai, c) > 0);
        if (tapped.isEmpty()) {
            return false;
        }
        List<SpellAbility> wanted = unpayableSpellsInHand(ai, sa.getHostCard());
        if (wanted.isEmpty()) {
            return false;
        }
        for (Card c : tapped) {
            c.setTapped(false);
        }
        try {
            for (SpellAbility ab : wanted) {
                if (ComputerUtilMana.canPayManaCost(ab, ai, sa.getPayCosts().getTotalMana().getCMC(), false)) {
                    return true;
                }
            }
        } finally {
            for (Card c : tapped) {
                c.setTapped(true);
            }
        }
        return false;
    }

    /** Ordre de paiement d'un coût « renvoyer » pour UntapManaCreature : engagé d'abord, puis le moins de mana, puis le plus petit. */
    public static CardCollection orderReturnPayment(final Player ai, final CardCollection list) {
        CardCollection res = new CardCollection(list);
        res.sort(java.util.Comparator.comparing(Card::isUntapped)
                .thenComparingInt(c -> manaProducedBy(ai, c))
                .thenComparingInt(Card::getNetPower));
        return res;
    }

    private static String returnCostType(final SpellAbility sa) {
        for (forge.game.cost.CostPart part : sa.getPayCosts().getCostParts()) {
            if (part instanceof forge.game.cost.CostReturn) {
                return part.getType();
            }
        }
        return "Permanent.YouCtrl";
    }

    private static int manaProducedBy(final Player ai, final Card c) {
        int max = 0;
        for (SpellAbility ma : c.getManaAbilities()) {
            ma.setActivatingPlayer(ai);
            if (ma.getPayCosts().hasManaCost()) {
                continue;
            }
            max = Math.max(max, ma.amountOfManaGenerated(true));
        }
        return max;
    }

    private boolean doPoolExtraManaLogic(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        final PhaseHandler ph = source.getGame().getPhaseHandler();
        final Game game = ai.getGame();

        if (source.isTapped()) {
            return true;
        }

        // Check if something is playable if we untap for an additional mana with this, then proceed
        CardCollection inHand = CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.NON_LANDS);
        // The AI is not very good at timing non-permanent spells this way, so filter them out
        // (it may actually be possible to enable this for sorceries, but that'll need some canPlay shenanigans)
        CardCollection playable = CardLists.filter(inHand, CardPredicates.PERMANENTS);

        CardCollection untappingCards = CardLists.filter(ai.getCardsIn(ZoneType.Battlefield), card -> {
            boolean hasUntapLandLogic = false;
            for (SpellAbility sa1 : card.getSpellAbilities()) {
                if ("PoolExtraMana".equals(sa1.getParam("AILogic"))) {
                    hasUntapLandLogic = true;
                    break;
                }
            }
            return hasUntapLandLogic && card.isUntapped();
        });

        // TODO: currently limited to Main 2, somehow improve to let the AI use this SA at other time?
        if (ph.is(PhaseType.MAIN2, ai)) {
            for (Card c : playable) {
                for (SpellAbility ab : c.getAllPossibleAbilities(ai, true)) {
                    if (!ab.isBasicSpell()) {
                        continue;
                    }
                    if (!ComputerUtilMana.hasEnoughManaSourcesToCast(ab, ai)) {
                        // TODO: Currently limited to predicting something that can be paid with any color,
                        // can ideally be improved to work by color.
                        ManaCostBeingPaid reduced = new ManaCostBeingPaid(ab.getPayCosts().getCostMana().getManaCostFor(ab));
                        reduced.decreaseShard(ManaCostShard.GENERIC, untappingCards.size());
                        if (ComputerUtilMana.canPayManaCost(reduced, ab, ai, false)) {
                            CardCollection manaLandsTapped = CardLists.filter(ai.getCardsIn(ZoneType.Battlefield),
                                    CardPredicates.LANDS_PRODUCING_MANA, CardPredicates.TAPPED);
                            manaLandsTapped = CardLists.getValidCards(manaLandsTapped, sa.getParam("ValidTgts"), ai, source, null);

                            if (!manaLandsTapped.isEmpty()) {
                                // already have a tapped land, so agree to proceed with untapping it
                                return true;
                            }

                            // pool one additional mana by tapping a land to try to ramp to something
                            CardCollection manaLands = CardLists.filter(ai.getCardsIn(ZoneType.Battlefield),
                                    CardPredicates.LANDS_PRODUCING_MANA, CardPredicates.CAN_TAP);
                            manaLands = CardLists.getValidCards(manaLands, sa.getParam("ValidTgts"), ai, source, null);

                            if (manaLands.isEmpty()) {
                                // nothing to untap
                                return false;
                            }

                            Card landToPool = manaLands.getFirst();
                            SpellAbility manaAb = landToPool.getManaAbilities().getFirst();

                            ComputerUtil.playNoStack(ai, manaAb, game, false);

                            return true;
                        }
                    }
                }
            }
        }

        // no harm in doing this past declare blockers during the opponent's turn and right before our turn,
        // maybe we'll serendipitously untap into something like a removal spell or burn spell that'll help
        return ph.getNextTurn() == ai
                && (ph.is(PhaseType.COMBAT_DECLARE_BLOCKERS) || ph.getPhase().isAfter(PhaseType.COMBAT_DECLARE_BLOCKERS));
    }

    @Override
    public boolean willPayUnlessCost(Player payer, SpellAbility sa, Cost cost, boolean alreadyPaid, FCollectionView<Player> payers) {
        // Paralyze effects
        if (sa.hasParam("UnlessSwitched")) {
            final Card host = sa.getHostCard();
            final Game game = host.getGame();
            for (Card card : AbilityUtils.getDefinedCards(host, null, sa)) {
                final Card gameCard = game.getCardState(card, null);
                if (gameCard == null
                        || !gameCard.isInPlay() // not in play
                        || gameCard.isUntapped() // already untapped
                        ) {
                    return false;
                }

                // if the ManaCost would cost more than the creatures CMC, it is not worth it
                CostPartMana mana = cost.getCostMana();
                if (mana != null && mana.getManaCostFor(sa).getCMC() > card.getCMC()) {
                    return false;
                }
            }
        }

        return super.willPayUnlessCost(payer, sa, cost, alreadyPaid, payers);
    }
}
