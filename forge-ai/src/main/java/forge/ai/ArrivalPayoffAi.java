package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 103 (a tester reports 29/10 18:57 and 19:11, Kavaron - Nothing Gulch): Kavaron had Weapons
 * Manufacturing in hand ("whenever a nontoken artifact you control enters, create a Munitions") and still put Great
 * Furnace and Rabbit Battery down first, losing the Munitions. General rule: a permanent in our hand whose trigger
 * fires when a card of ours of some kind enters (the payoff) goes first. The cards it would count (spells and lands)
 * wait in hand while the payoff can come down this turn or the next one: a land drop picks another land, a spell
 * waits unless it is a mana source the payoff still needs, a creature we need to survive, a payoff itself (Guide of
 * Souls, rule 70) or the hand overflows. The payoff goes in main 1 when such a card waits.
 */
public final class ArrivalPayoffAi {
    private ArrivalPayoffAi() { }

    private static boolean asking = false;

    /** a trigger of the payoff (on the battlefield) that counts {@code card} entering under our control */
    static boolean counts(final Player ai, final Card payoff, final Card card) {
        if (payoff == card || payoff.getName().equals(card.getName())) {
            return false;
        }
        for (final Trigger t : payoff.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))) {
                continue;
            }
            // magic-tools: rule 269 (tester 09/11 12:26, Wolfir Silverheart before Gigantosaurus): soulbond pairs
            // when either creature enters, so it loses nothing if the other one comes first; it is no arrival payoff,
            // and the bigger body that can attack next turn keeps its place
            if (t.isKeyword(Keyword.SOULBOND) && NewAi.on(ai, 269)) {
                continue;
            }
            final String zones = t.getParam("TriggerZones");
            if (zones != null && !zones.contains("Battlefield")) {
                continue;
            }
            final String valid = t.getParam("ValidCard");
            if (valid == null || valid.startsWith("Card.Self")) {
                continue;
            }
            if (card.isValid(valid.split(","), ai, payoff, t)) {
                // magic-tools: rule 247, a trigger that damages the card entering (Marauding Raptor) is no payoff
                return HarmfulArrival247.damageFrom(ai, payoff, card) <= 0;
            }
        }
        return false;
    }

    /** the card is itself a payoff (Guide of Souls): an engine of its own, the other rules order it */
    static boolean isPayoff(final Card card) {
        for (final Trigger t : card.getTriggers()) {
            final String valid = t.getParam("ValidCard");
            if (t.getMode() == TriggerType.ChangesZone && "Battlefield".equals(t.getParam("Destination"))
                    && valid != null && !valid.startsWith("Card.Self")) {
                return true;
            }
        }
        return false;
    }

    /** the trigger of the payoff that counts {@code card} fires only once each turn (Kili the Resourceful) */
    static boolean countsOncePerTurn(final Player ai, final Card payoff, final Card card) {
        for (final Trigger t : payoff.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))
                    || !"1".equals(t.getParam("ActivationLimit"))) {
                continue;
            }
            final String valid = t.getParam("ValidCard");
            if (valid != null && !valid.startsWith("Card.Self") && card.isValid(valid.split(","), ai, payoff, t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * magic-tools: rule 159 (bench vs human decisions a test game): a payoff that triggers only once each turn
     * and can't come down this turn needs one card to count when it arrives, not all of them. Another card it would
     * count stays in hand, so this one comes down now instead of wasting the mana (Lavaspur Boots with Lost Jitte and
     * Kili the Resourceful in hand).
     */
    static boolean anotherWaits(final Player ai, final Card payoff, final Card card) {
        if (!countsOncePerTurn(ai, payoff, card)) {
            return false;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c != card && c != payoff && !c.isLand() && !isPayoff(c) && counts(ai, payoff, c)) {
                return true;
            }
        }
        return false;
    }

    /** a payoff for {@code card} in our hand, not in play yet */
    public static Card payoffInHand(final Player ai, final Card card) {
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (!c.isPermanent() || c.isLand() || c.getFirstSpellAbility() == null) {
                continue;
            }
            if (ai.isCardInPlay(c.getName())) {
                continue;
            }
            if (counts(ai, c, card)) {
                return c;
            }
        }
        return null;
    }

    /** the payoff has in our hand a card it would count (it goes in main 1, before them) */
    public static boolean countsCardInHand(final Player ai, final Card payoff) {
        if (!payoff.isInZone(ZoneType.Hand) || payoff.isLand()) {
            return false;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (counts(ai, payoff, c) && !isPayoff(c)) {
                return true;
            }
        }
        return false;
    }

    private static int manaSources(final Player ai) {
        return CardLists.filter(ai.getCardsIn(ZoneType.Battlefield),
                c -> c.isLand() || (!c.isCreature() && !c.getManaAbilities().isEmpty())).size();
    }

    /** the payoff can come down now (and the AI wants it) or at the next turn with one more land */
    static boolean payoffSoon(final Player ai, final Card payoff) {
        final SpellAbility psa = payoff.getFirstSpellAbility();
        psa.setActivatingPlayer(ai);
        if (ComputerUtilMana.canPayManaCost(psa, ai, 0, false)) {
            if (asking || !(ai.getController() instanceof PlayerControllerAi pc)) {
                return true;
            }
            asking = true;
            try {
                return pc.getAi().canPlaySa(psa) == AiPlayDecision.WillPlay;
            } finally {
                asking = false;
            }
        }
        return isNextTurn(ai, payoff);
    }

    /**
     * magic-tools: rule 215 (tester 02/11, a test game): Stadium Headliner stayed in hand on turns 1 and 2
     * for Elas il-Kor {W}{B}, with Inspiring Vantage, a Plains and no black source in play or in hand. The payoff comes
     * next turn only if the land drop exists and the colors are there: every source we have untaps, plus one land of
     * the hand.
     */
    public static boolean isNextTurn(final Player ai, final Card payoff) {
        final CardCollection landsInHand = CardLists.filter(ai.getCardsIn(ZoneType.Hand), Card::isLand);
        if (payoff.getCMC() > manaSources(ai) + (landsInHand.isEmpty() ? 0 : 1)) {
            return false;
        }
        final java.util.List<Byte> sources = TapLandColorAi.allSourceMasks(ai, null);
        if (TapLandColorAi.payableWithSources(payoff.getManaCost(), sources)) {
            return true;
        }
        for (final Card land : landsInHand) {
            if (TapLandColorAi.payableWithSources(payoff.getManaCost(), TapLandColorAi.allSourceMasks(ai, land))) {
                return true;
            }
        }
        return false;
    }

    /** a spell that waits for its payoff */
    public static boolean holdBack(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        final Card card = sa.getHostCard();
        if (card == null || !sa.isSpell() || card.isLand() || !card.isInZone(ZoneType.Hand) || !ph.isPlayerTurn(ai)) {
            return false;
        }
        final Card payoff = payoffInHand(ai, card);
        if (payoff == null || isPayoff(card)) {
            return false;
        }
        if (!ai.isUnlimitedHandSize() && ai.getCardsIn(ZoneType.Hand).size() > ai.getMaxHandSize()) {
            return false;
        }
        if (card.isCreature() && ComputerUtil.aiLifeInDanger(ai, false, 0)) {
            return false;
        }
        payoff.getFirstSpellAbility().setActivatingPlayer(ai);
        final boolean now = ComputerUtilMana.canPayManaCost(payoff.getFirstSpellAbility(), ai, 0, false);
        // a mana source that brings the payoff closer comes down first
        if (!now && !card.getManaAbilities().isEmpty()) {
            return false;
        }
        if (!now && anotherWaits(ai, payoff, card)) {
            return false;
        }
        if (!now && attackerLosesTheCurve(ai, payoff, card)) {
            return false;
        }
        return payoffSoon(ai, payoff);
    }

    /**
     * magic-tools: rule 222 (lost mana report, Bunny Burrow - eowyn t3): Inti {1}{R} stayed in hand with two free lands
     * for Warleader's Call {1}{R}{W} next turn, and the next turns' mana went to bigger spells: Inti came down last, or
     * never. A creature that attacks doesn't wait for a payoff that can't come this turn when next turn's mana can't
     * pay both: one trigger isn't worth a body two turns late and this turn's mana lost.
     */
    static boolean attackerLosesTheCurve(final Player ai, final Card payoff, final Card card) {
        if (!card.isCreature() || card.getNetCombatDamage() <= 0 || card.hasKeyword(Keyword.DEFENDER)) {
            return false;
        }
        final boolean landDrop = !CardLists.filter(ai.getCardsIn(ZoneType.Hand), Card::isLand).isEmpty();
        return payoff.getCMC() + card.getCMC() > manaSources(ai) + (landDrop ? 1 : 0);
    }

    /** the land drop: another land than the ones the payoff in hand would count */
    public static CardCollection landsAfterPayoff(final Player ai, final CardCollection lands) {
        final CardCollection others = new CardCollection();
        boolean waits = false;
        for (final Card l : lands) {
            final Card payoff = payoffInHand(ai, l);
            if (payoff != null && payoffSoon(ai, payoff)) {
                waits = true;
            } else {
                others.add(l);
            }
        }
        return waits && !others.isEmpty() ? others : lands;
    }
}
