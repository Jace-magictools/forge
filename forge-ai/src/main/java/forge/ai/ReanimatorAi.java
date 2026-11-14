package forge.ai;

import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityCantAttach;
import forge.game.staticability.StaticAbilityMode;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * Reanimator: tutors that put one card in the graveyard and one in hand (Final Parting, AILogic EntombAndTutor).
 * Without this the AI picks two creatures, puts Griselbrand in the graveyard and Akroma in hand, and never finds
 * Animate Dead.
 */
public final class ReanimatorAi {
    public static final String LOGIC = "EntombAndTutor";

    private ReanimatorAi() {
    }

    /** Animate Dead, Necromancy, Reanimate, Exhume...: brings a creature card from a graveyard onto the battlefield. */
    public static boolean isReanimationSpell(final Card c) {
        if ("Reanimate".equals(c.getSVar("AttachAILogic"))) {
            return true;
        }
        for (SpellAbility sa : c.getSpellAbilities()) {
            if (returnsCreatureFromGraveyard(sa)) {
                return true;
            }
        }
        for (Trigger t : c.getTriggers()) {
            if (returnsCreatureFromGraveyard(t.ensureAbility())) {
                return true;
            }
        }
        return false;
    }

    private static boolean returnsCreatureFromGraveyard(SpellAbility sa) {
        for (; sa != null; sa = sa.getSubAbility()) {
            if (sa.getApi() == ApiType.ChangeZone && "Graveyard".equals(sa.getParam("Origin"))
                    && "Battlefield".equals(sa.getParam("Destination"))
                    && (sa.getParamOrDefault("ValidTgts", "") + sa.getParamOrDefault("ChangeType", "")).contains("Creature")) {
                return true;
            }
        }
        return false;
    }

    /** Returns the card to pick, or null to fall back to the usual choice. */
    public static Card choose(final ZoneType destination, final SpellAbility sa, final CardCollection fetchList, final Player ai) {
        if (ai != null && (destination == ZoneType.Graveyard && "Library".equals(sa.getParam("Origin"))
                || destination == ZoneType.Battlefield && "Graveyard".equals(sa.getParam("Origin")))) {
            // Entomb, Exhume: the colour lock or the best fatty (rule 37)
            Card fatty = chooseFatty(ai, CardLists.filter(fetchList, c -> c.isCreature() && c.getOwner() == ai));
            if (fatty != null) {
                return fatty;
            }
        }
        if (!LOGIC.equals(sa.getRootAbility().getParam("AILogic"))) {
            return chooseForTutor(destination, sa, fetchList, ai);
        }
        CardCollection creatures = CardLists.filter(fetchList, CardPredicates.CREATURES);
        CardCollection spells = CardLists.filter(fetchList, ReanimatorAi::isReanimationSpell);
        if (destination == ZoneType.Hand) {
            // one goes to hand, the other to the graveyard: keep the reanimation spell, bury the creature
            if (!creatures.isEmpty() && fetchList.size() > creatures.size()) {
                Card spell = spells.isEmpty() ? null : spells.getFirst();
                return spell != null ? spell : ComputerUtilCard.getBestAI(CardLists.getNotType(fetchList, "Creature"));
            }
            return null;
        }
        if (destination != ZoneType.Library) {
            return null;
        }
        // first pick a creature worth reanimating, then the spell that reanimates it
        CardCollectionView library = ai.getCardsIn(ZoneType.Library);
        CardCollection picked = CardLists.filter(library, c -> !fetchList.contains(c));
        boolean creaturePicked = picked.anyMatch(CardPredicates.CREATURES);
        if (!creaturePicked && !creatures.isEmpty()) {
            Card fatty = chooseFatty(ai, creatures);
            return fatty != null ? fatty : ComputerUtilCard.getBestCreatureAI(creatures);
        }
        if (creaturePicked && !picked.anyMatch(ReanimatorAi::isReanimationSpell) && !spells.isEmpty()) {
            return spells.getFirst();
        }
        return null;
    }

    /**
     * Discard outlets paid with X cards (Sickening Dreams): worth casting for X = 1 when the hand holds a creature worth
     * reanimating (mana value 5 or more, not castable soon) and a reanimation spell, and the graveyard has no target yet.
     */
    public static boolean wantsDiscardForReanimation(final Player ai, final SpellAbility sa) {
        if (hasReanimationTarget(ai)) {
            return false;
        }
        boolean discardsX = false;
        if (sa.getRootAbility().getPayCosts() != null) {
            for (forge.game.cost.CostPart part : sa.getRootAbility().getPayCosts().getCostParts()) {
                if (part instanceof forge.game.cost.CostDiscard && "X".equals(part.getAmount())) {
                    discardsX = true;
                }
            }
        }
        if (!discardsX) {
            return false;
        }
        final Card host = sa.getHostCard();
        final int lands = ai.getLandsInPlay().size();
        CardCollectionView hand = ai.getCardsIn(ZoneType.Hand);
        return hand.anyMatch(c -> c != host && c.isCreature() && c.getCMC() >= 5 && c.getCMC() > lands + 1)
                && hand.anyMatch(c -> c != host && isReanimationSpell(c));
    }

    /**
     * Discard costs (Sparkhunter Masticore, Liliana's Caress decks...): a creature of mana value 5 or more that cannot
     * be cast soon is the card to discard when a reanimation spell of ours is still in hand or library. Returns the
     * best such creature, or null.
     */
    public static Card discardForReanimation(final Player ai, final CardCollection typeList, final Card source) {
        final int lands = ai.getLandsInPlay().size();
        // magic-tools: rule 19b, with the reanimation spell already in hand, a fatty that cannot be cast this turn
        // is worth more in the graveyard (Profanation, 5 lands: Masticore discarding Sanctuary Warden then Exhume;
        // before: nothing cast all game, waiting for the sixth land)
        final boolean spellInHand = ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c != source && isReanimationSpell(c));
        CardCollection fatties = CardLists.filter(typeList, c -> c != source && c.isCreature() && c.getCMC() >= 5
                && (c.getCMC() > lands + 1 || (spellInHand && c.getCMC() > lands)));
        if (fatties.isEmpty()) {
            return null;
        }
        boolean canReanimate = spellInHand || ai.getCardsIn(ZoneType.Library).anyMatch(ReanimatorAi::isReanimationSpell);
        if (!canReanimate) {
            return null;
        }
        Card fatty = chooseFatty(ai, fatties);
        return fatty != null ? fatty : ComputerUtilCard.getBestCreatureAI(fatties);
    }

    /**
     * magic-tools: rule 29, no land drop on our first turn when keeping it puts a fatty in the graveyard at cleanup:
     * the hand is over its maximum size even after the cheap spells, it holds a creature of mana value 5 or more and
     * a reanimation spell (castable or not yet), and the graveyard has no target yet (Profanation, tester 26/10:
     * Griselbrand and Exhume in hand on the draw, skip the land, discard Griselbrand; only on the first turn, later
     * the land drop is worth more).
     */
    public static boolean skipLandDropToDiscard(final Player ai) {
        if (ai.isUnlimitedHandSize() || hasReanimationTarget(ai) || ai.getGame().getPhaseHandler().getTurn() > 2) {
            return false;
        }
        final int lands = ai.getLandsInPlay().size();
        CardCollectionView hand = ai.getCardsIn(ZoneType.Hand);
        if (!hand.anyMatch(c -> c.isCreature() && c.getCMC() >= 5 && c.getCMC() > lands + 1)) {
            return false;
        }
        if (!hand.anyMatch(c -> isReanimationSpell(c) && !c.isCreature())) {
            return false;
        }
        // the cheap spells cast this turn leave the hand anyway
        final int cheap = CardLists.count(hand, c -> !c.isLand() && c.getCMC() <= lands && !isReanimationSpell(c)
                && c.getFirstSpellAbility() != null && ComputerUtilMana.hasEnoughManaSourcesToCast(c.getFirstSpellAbility(), ai));
        return hand.size() - cheap > ai.getMaxHandSize();
    }

    /** A creature card in the AI's graveyard worth reanimating (mana value 5 or more). */
    private static boolean hasReanimationTarget(final Player ai) {
        return ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.isCreature() && c.getCMC() >= 5);
    }

    /**
     * Plain tutors (Demonic Tutor, Vampiric Tutor, Mausoleum Secrets...): with a fatty already in the graveyard and
     * no way to bring it back in hand, fetch the reanimation spell instead of the "best" card (Dark Ritual, Sol Ring).
     */
    private static Card chooseForTutor(final ZoneType destination, final SpellAbility sa, final CardCollection fetchList, final Player ai) {
        boolean toHand = destination == ZoneType.Hand
                || (destination == ZoneType.Library && "0".equals(sa.getParam("LibraryPosition")));
        if (!toHand || !"Library".equals(sa.getParam("Origin"))) {
            return null;
        }
        boolean spellInHand = ai.getCardsIn(ZoneType.Hand).anyMatch(ReanimatorAi::isReanimationSpell);
        CardCollection wanted;
        // magic-tools: rule 94, the missing piece of the plan is the reanimation spell also when the fatty is still in
        // hand and cannot be cast soon: a discard (outlet, cleanup) puts it in the graveyard, the spell brings it back
        // (tester 29/10, Profanation: Vampiric Tutor on turn 1 took Sparkhunter Masticore next to Iona and Sickening
        // Dreams, Iona then stayed in the graveyard all game)
        final int lands = ai.getLandsInPlay().size();
        boolean fattyInHand = ai.getCardsIn(ZoneType.Hand).anyMatch(c -> c.isCreature() && c.getCMC() >= 5 && c.getCMC() > lands + 1);
        if ((hasReanimationTarget(ai) || fattyInHand) && !spellInHand) {
            wanted = CardLists.filter(fetchList, c -> c.getOwner() == ai && isReanimationSpell(c));
        } else if (!hasReanimationTarget(ai) && spellInHand) {
            // the other missing piece: the spell is in hand, the graveyard is empty (Demonic Tutor took Avacyn
            // into hand next to Reanimate): fetch what buries a fatty (Entomb, Buried Alive, Final Parting)
            wanted = CardLists.filter(fetchList, c -> c.getOwner() == ai && buriesFromLibrary(c));
        } else {
            return null;
        }
        if (wanted.isEmpty()) {
            return null;
        }
        // the cheapest one comes down this turn or the next
        return java.util.Collections.min(wanted, java.util.Comparator.comparingInt(Card::getCMC));
    }

    /** Entomb, Buried Alive, Final Parting...: puts cards from our library into our graveyard by choice. */
    static boolean buriesFromLibrary(final Card c) {
        for (SpellAbility sa : c.getSpellAbilities()) {
            for (SpellAbility sub = sa; sub != null; sub = sub.getSubAbility()) {
                if (LOGIC.equals(sub.getParam("AILogic"))) {
                    return true;
                }
                if (sub.getApi() == ApiType.ChangeZone && "Library".equals(sub.getParam("Origin"))
                        && "Graveyard".equals(sub.getParam("Destination")) && sub.hasParam("ChangeType")
                        && !sub.hasParam("Defined")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Share of the opponent's spells a colour lock must stop to be worth more than the best fatty (rule 37). */
    static final double LOCK_SHARE = 0.6;

    /**
     * magic-tools: rule 225. The "if it's on the battlefield" of Necromancy's trigger hides it from checkETBEffects (the
     * card isn't there yet) and its NeedsToPlayVar counts every creature: with Akroma alone it would be cast for nothing.
     */
    public static boolean hasCreatureToWearIt(final Player ai, final Card host) {
        for (final Trigger tr : host.getTriggers()) {
            if (tr.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(tr.getParam("Destination"))) {
                continue;
            }
            final SpellAbility etb = tr.ensureAbility();
            if (etb == null || etb.getApi() != ApiType.ChangeZone || !etb.usesTargeting()
                    || !"Graveyard".equals(etb.getParam("Origin")) || etb.findSubAbilityByType(ApiType.Attach) == null) {
                continue;
            }
            final SpellAbility copy = etb.copy(ai);
            final CardCollection candidates = CardLists.getTargetableCards(ai.getGame().getCardsIn(ZoneType.Graveyard), copy);
            if (candidates.stream().noneMatch(c -> !dropsOwnAttachment(c, copy))) {
                return false;
            }
        }
        return true;
    }

    /**
     * magic-tools: rule 225. A permanent that returns a creature and attaches itself to it (Necromancy) goes to the
     * graveyard at once when the creature can't legally wear it, and the creature is then sacrificed: Akroma, Angel of
     * Wrath under Necromancy cost a Dark Ritual, an Entomb and the Necromancy for nothing (Profanation). Protection is a
     * static ability of the creature, inactive in the graveyard, so it is read on the card itself.
     */
    public static boolean dropsOwnAttachment(final Card creature, final SpellAbility sa) {
        final Card host = sa.getHostCard();
        if (host == null || !creature.isCreature() || !host.isEnchantment() || sa.findSubAbilityByType(ApiType.Attach) == null) {
            return false;
        }
        for (final StaticAbility stAb : creature.getStaticAbilities()) {
            if (stAb.checkMode(StaticAbilityMode.CantAttach)
                    && StaticAbilityCantAttach.applyCantAttachAbility(stAb, host, creature, true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * magic-tools: rule 37, the creature to reanimate (or to bury for it) when a colour lock is among the candidates
     * (Iona, Shield of Emeria: "your opponents can't cast spells of the chosen color"). The lock first when the
     * opponent's spells are concentrated in one colour (mono, or a two-colour deck leaning on one colour: tester
     * 27/10, Profanation), otherwise the best creature that is not a lock (Griselbrand). Returns null when no
     * candidate is a colour lock, so the usual choice stands.
     */
    public static Card chooseFatty(final Player ai, final CardCollection creatures) {
        CardCollection locks = CardLists.filter(creatures, ReanimatorAi::isColourLock);
        if (locks.isEmpty()) {
            return null;
        }
        if (lockedShare(ai) >= LOCK_SHARE) {
            return ComputerUtilCard.getBestCreatureAI(locks);
        }
        CardCollection others = CardLists.filter(creatures, c -> !isColourLock(c) && c.getCMC() >= 5);
        return others.isEmpty() ? null : ComputerUtilCard.getBestCreatureAI(others);
    }

    /** A static "opponents can't cast spells of the chosen color" (Iona). */
    static boolean isColourLock(final Card c) {
        for (forge.game.staticability.StaticAbility st : c.getStaticAbilities()) {
            if (st.checkMode(forge.game.staticability.StaticAbilityMode.CantBeCast)
                    && "Opponent".equals(st.getParam("Caster"))
                    && st.getParamOrDefault("ValidCard", "").contains("ChosenColor")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Best share of the strongest opponent's nonland cards (all zones, as Iona's own colour choice reads them) that
     * a single colour lock would stop.
     */
    static double lockedShare(final Player ai) {
        Player opp = ai.getStrongestOpponent();
        if (opp == null) {
            return 0;
        }
        CardCollection spells = CardLists.filter(CardLists.filterControlledBy(ai.getGame().getCardsInGame(), opp),
                c -> !c.isLand() && !c.isToken());
        if (spells.isEmpty()) {
            return 0;
        }
        int best = 0;
        for (byte colour : forge.card.MagicColor.WUBRG) {
            best = Math.max(best, CardLists.count(spells, c -> c.getColor().hasAnyColor(colour)));
        }
        return (double) best / spells.size();
    }

    /**
     * magic-tools: rule 299. A reanimation that costs life equal to the returned card's mana value (Reanimate) does
     * not take a target that leaves the AI within the opponents' reach: direct damage they can deal (Goblin
     * Bombardment) plus the power of their creatures, minus the biggest one (the returned creature blocks it).
     * Profanation at 9 life reanimated Vilis (8), went to 1 and died to Goblin Bombardment while Iona alone was lethal.
     */
    public static boolean lifeLossTooDangerous(final Player ai, final SpellAbility sa, final Card target) {
        int loss = selfLifeLoss(sa, target);
        if (loss <= 0 || !ai.canLoseLife()) {
            return false;
        }
        return ai.getLife() - loss <= opponentsReach(ai);
    }

    /** Life the controller loses for this target in a sub-ability "you lose life equal to its mana value". */
    static int selfLifeLoss(final SpellAbility sa, final Card target) {
        for (SpellAbility sub = sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.getApi() != ApiType.LoseLife || !"You".equals(sub.getParamOrDefault("Defined", "You"))) {
                continue;
            }
            String amount = sub.getParamOrDefault("LifeAmount", "");
            String x = sub.hasSVar(amount) ? sub.getSVar(amount) : amount;
            if (x.contains("CardManaCost")) {
                return target.getCMC();
            }
        }
        return 0;
    }

    /** Damage the opponents can deal next turn: direct damage plus their creatures' power but the biggest. */
    static int opponentsReach(final Player ai) {
        int reach = 0;
        for (Player opp : ai.getOpponents()) {
            int direct = ComputerUtil.possibleNonCombatDamage(opp, ai);
            int sum = 0;
            int biggest = 0;
            for (Card c : opp.getCreaturesInPlay()) {
                if (c.hasKeyword(forge.game.keyword.Keyword.DEFENDER)) {
                    continue;
                }
                int p = Math.max(0, c.getNetCombatDamage());
                sum += p;
                biggest = Math.max(biggest, p);
            }
            reach = Math.max(reach, direct + sum - biggest);
        }
        return reach;
    }
}
