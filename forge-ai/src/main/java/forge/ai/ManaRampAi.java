package forge.ai;

import java.util.List;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.cost.CostSacrifice;
import forge.game.zone.ZoneType;

/**
 * magic-tools: cast a mana creature (Birds of Paradise, Llanowar Elves…) before creatures of the same
 * or lower mana value when a bigger spell waits in hand. Stock AI ranks creature spells by their
 * evaluation and cast Viscera Seer before Birds on turn 2 with Kitchen Finks in hand: a turn of ramp lost.
 */
public final class ManaRampAi {
    private ManaRampAi() { }

    public static void prioritize(final Player ai, final List<SpellAbility> all) {
        if (!ai.getGame().getPhaseHandler().isPlayerTurn(ai) || !ai.getGame().getPhaseHandler().getPhase().isMain()) {
            return;
        }
        // rule 71: a mana rock that gives back at least its cost this turn (Sol Ring, Mana Crypt) goes first
        for (int i = 1; i < all.size(); i++) {
            if (rockNet(all.get(i)) >= 0) {
                all.add(0, all.remove(i));
            }
        }
        // rule 264 (tester 09/11): a mana creature goes before a noncreature mana permanent of the same cost
        if (NewAi.on(ai, 264)) {
            sickFirst(ai, all);
        }
        final int lands = CardLists.count(ai.getCardsIn(ZoneType.Battlefield), Card::isLand);
        boolean biggerSpell = false;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (!c.isLand() && c.getCMC() > lands) {
                biggerSpell = true;
                break;
            }
        }
        if (!biggerSpell) {
            return;
        }
        for (int i = 1; i < all.size(); i++) {
            final SpellAbility sa = all.get(i);
            final boolean rock = rockNet(sa) != Integer.MIN_VALUE;
            if (!isRampCreature(sa) && !rock) {
                continue;
            }
            final int cmc = sa.getHostCard().getCMC();
            for (int j = 0; j < i; j++) {
                final SpellAbility other = all.get(j);
                // rule 71: a rock that costs mana this turn (Arcane Signet, Mind Stone) passes the permanents
                // of the same or lower cost, as a mana creature passes the creatures
                if ((other.getApi() == ApiType.PermanentCreature || (rock && other.getApi() == ApiType.PermanentNoncreature))
                        && !isRampCreature(other) && rockNet(other) == Integer.MIN_VALUE
                        && other.getHostCard().getCMC() <= cmc) {
                    all.add(j, all.remove(i));
                    break;
                }
            }
        }
    }

    /**
     * Rule 71 (a tester reports 29/10, Avacyn kept Sol Ring and cast Giada then Serra Avenger): for a noncreature
     * mana permanent cast from hand (no sacrifice, no arrival replacement that could tap it), the mana it gives back
     * this turn minus what it costs (Sol Ring +1, Mana Crypt +2, Arcane Signet -1); Integer.MIN_VALUE otherwise.
     */
    static int rockNet(final SpellAbility sa) {
        if (sa.getApi() != ApiType.PermanentNoncreature || !sa.isSpell() || sa.getHostCard() == null
                || !sa.getHostCard().isInZone(ZoneType.Hand)) {
            return Integer.MIN_VALUE;
        }
        final Card card = sa.getHostCard();
        if (card.isCreature() || card.isLand() || card.getManaAbilities().isEmpty() || card.getManaCost().countX() > 0) {
            return Integer.MIN_VALUE;
        }
        for (final forge.game.replacement.ReplacementEffect re : card.getReplacementEffects()) {
            if (re.getMode() == forge.game.replacement.ReplacementType.Moved) {
                return Integer.MIN_VALUE;
            }
        }
        int best = Integer.MIN_VALUE;
        for (final SpellAbility m : card.getManaAbilities()) {
            if (m.getPayCosts().hasSpecificCostType(CostSacrifice.class)) {
                return Integer.MIN_VALUE;
            }
            final int input = m.getPayCosts().getCostMana() == null ? 0 : m.getPayCosts().getCostMana().getMana().getCMC();
            best = Math.max(best, m.amountOfManaGenerated(true) - input);
        }
        return best == Integer.MIN_VALUE || best <= 0 ? Integer.MIN_VALUE : best - card.getCMC();
    }

    static boolean isRampCreature(final SpellAbility sa) {
        if (sa.getApi() != ApiType.PermanentCreature || sa.getHostCard() == null || !sa.getHostCard().isInZone(ZoneType.Hand)) {
            return false;
        }
        final Card card = sa.getHostCard();
        if (card.getManaAbilities().isEmpty()) {
            return false;
        }
        for (final SpellAbility m : card.getManaAbilities()) {
            if (m.getPayCosts().hasSpecificCostType(CostSacrifice.class)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rule 264 (tester 09/11, linear decks): Wild Growth was cast before Arbor Elf with both in hand and one Forest.
     * The creature has summoning sickness, the aura or the rock does not: of two mana permanents of the same cost the
     * creature goes first when the mana does not pay both this turn (counting what the noncreature gives back at once).
     */
    static void sickFirst(final Player ai, final List<SpellAbility> all) {
        final int avail = ComputerUtilMana.getAvailableManaEstimate(ai, false);
        for (int i = 1; i < all.size(); i++) {
            final SpellAbility sa = all.get(i);
            if (!isManaCreature264(sa)) {
                continue;
            }
            final int cmc = sa.getHostCard().getCMC();
            if (cmc > avail) {
                continue;
            }
            for (int j = 0; j < i; j++) {
                final SpellAbility other = all.get(j);
                final int back = noncreatureManaBack(other, avail);
                if (back == Integer.MIN_VALUE || other.getHostCard().getCMC() != cmc) {
                    continue;
                }
                if (avail >= cmc + other.getHostCard().getCMC() - back) {
                    break; // both get cast this turn, in either order
                }
                all.add(j, all.remove(i));
                break;
            }
        }
    }

    /** rule 264: a creature spell from hand that makes mana, or that untaps a land (Arbor Elf) */
    static boolean isManaCreature264(final SpellAbility sa) {
        if (isRampCreature(sa)) {
            return true;
        }
        if (sa.getApi() != ApiType.PermanentCreature || sa.getHostCard() == null || !sa.getHostCard().isInZone(ZoneType.Hand)) {
            return false;
        }
        for (final SpellAbility ab : sa.getHostCard().getSpellAbilities()) {
            if (ab.getApi() == ApiType.Untap && ab.usesTargeting() && ab.getPayCosts() != null && ab.getPayCosts().hasTapCost()
                    && ab.hasParam("ValidTgts") && isLandFilter(ab.getParam("ValidTgts"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLandFilter(final String v) {
        for (final String t : v.split(",")) {
            final String base = t.split("\\.")[0].trim();
            if (base.equals("Land") || forge.card.CardType.isABasicLandType(base)) {
                return true;
            }
        }
        return false;
    }

    /**
     * rule 264: for a noncreature mana permanent cast from hand, the mana it gives back this turn (a rock: what it taps
     * for minus its activation cost ; a land aura such as Wild Growth: its extra mana if a land stays untapped after
     * paying it, else 0) ; Integer.MIN_VALUE if the spell is not one.
     */
    static int noncreatureManaBack(final SpellAbility sa, final int avail) {
        final int net = rockNet(sa);
        if (net != Integer.MIN_VALUE) {
            return net + sa.getHostCard().getCMC();
        }
        // an Aura spell is an Attach spell
        if ((sa.getApi() != ApiType.PermanentNoncreature && sa.getApi() != ApiType.Attach) || !sa.isSpell()
                || sa.getHostCard() == null || !sa.getHostCard().isInZone(ZoneType.Hand)) {
            return Integer.MIN_VALUE;
        }
        final Card card = sa.getHostCard();
        if (!card.isAura() || card.isCreature()) {
            return Integer.MIN_VALUE;
        }
        for (final forge.game.trigger.Trigger t : card.getTriggers()) {
            if (t.getMode() == forge.game.trigger.TriggerType.TapsForMana && t.hasParam("ValidCard")
                    && t.getParam("ValidCard").contains("AttachedBy")) {
                return avail - card.getCMC() >= 1 ? 1 : 0;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** rule 264: cast in main 1 a mana creature when a noncreature mana permanent of the same cost waits in hand */
    public static boolean sickFirstMain1(final Player ai, final SpellAbility sa) {
        if (!isManaCreature264(sa)) {
            return false;
        }
        final int avail = ComputerUtilMana.getAvailableManaEstimate(ai, false);
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c == sa.getHostCard() || c.isCreature() || c.getCMC() != sa.getHostCard().getCMC()) {
                continue;
            }
            for (final SpellAbility other : c.getSpellAbilities()) {
                if (other.isSpell() && noncreatureManaBack(other, avail) != Integer.MIN_VALUE) {
                    return true;
                }
            }
        }
        return false;
    }
}
