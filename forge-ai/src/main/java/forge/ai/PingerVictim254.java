package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.cost.Cost;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 254 (D143 point 2, tester 08/11, a test game and a test game): the AI
 * cast Dauthi Horror (2/1) facing Gelectrode ("{T}: 1 damage to any target"), which killed it at once: "suicidal".
 * General rule: a creature is not cast while an opponent controls a repeatable pinger (an activated DealDamage with
 * {T} in its cost, no sacrifice, that can target any creature, not only an attacker like Desert) dealing at least its toughness, unless the creature gets
 * something out of it (arrival, death or damage trigger, rule 247), has haste, can't be targeted by the pinger
 * (hexproof, shroud, protection) or the AI is under pressure (rule 249: it needs blockers). The creature waits, so a
 * removal spell in hand that can kill the pinger goes first.
 * Fix 11/11: a pinger counts only if its activation condition (hellbent, threshold...) holds now.
 * Fix 11/11 (2): no wait when we can field more victims (in play + castable from hand) than the opponent has pings.
 */
public final class PingerVictim254 {
    private PingerVictim254() { }

    /** damage of the repeatable pinger ability {@code sa} (0 if it is not one) */
    static int pingDamage(final SpellAbility sa) {
        if (sa.getApi() != ApiType.DealDamage || !sa.isActivatedAbility() || !sa.usesTargeting() || !sa.hasParam("NumDmg")) {
            return 0;
        }
        final Cost cost = sa.getPayCosts();
        if (cost == null || !cost.hasTapCost() || cost.hasSpecificCostType(forge.game.cost.CostSacrifice.class)) {
            return 0;
        }
        final TargetRestrictions tr = sa.getTargetRestrictions();
        if (tr == null || tr.getMinTargets(sa.getHostCard(), sa) > 1) {
            return 0;
        }
        boolean creature = false;
        for (final String v : tr.getValidTgts()) {
            if (v.contains("attacking") || v.contains("blocking")) {
                continue;
            }
            creature |= v.startsWith("Any") || v.startsWith("Creature") || v.startsWith("Permanent") || v.startsWith("Card");
        }
        if (!creature || sa.hasParam("DividedAsYouChoose")) {
            return 0;
        }
        return AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("NumDmg"), sa);
    }

    /** the opposing pinger that would kill {@code card} once in play, or null */
    public static Card killer(final Player ai, final Card card) {
        final Card copy = CardCopyService.getLKICopy(card);
        ComputerUtilCard.applyStaticContPT(ai.getGame(), copy, null);
        if (copy.hasKeyword(Keyword.INDESTRUCTIBLE) || copy.hasKeyword(Keyword.HEXPROOF) || copy.hasKeyword(Keyword.SHROUD)
                || copy.getNetToughness() <= 0) {
            return null;
        }
        for (final Player o : ai.getOpponents()) {
            for (final Card p : o.getCardsIn(ZoneType.Battlefield)) {
                for (final SpellAbility sa : p.getSpellAbilities()) {
                    final int dmg = pingDamage(sa);
                    if (dmg <= 0 || !copy.canBeTargetedBy(sa)) {
                        continue;
                    }
                    // fix 11/11: a pinger whose activation condition is not met now (Keldon Megaliths, hellbent) does not count
                    if (!activableNow(p, sa, o)) {
                        continue;
                    }
                    if (ComputerUtilCombat.predictDamageTo(copy, dmg, p, false) >= copy.getNetToughness()) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    /** the activation conditions of the pinger (hellbent, threshold, metalcraft, delirium, IsPresent…) hold for its controller now */
    static boolean activableNow(final Card host, final SpellAbility sa, final Player controller) {
        try {
            return sa.getRestrictions() == null || sa.getRestrictions().checkOtherRestrictions(host, sa, controller);
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean refuse(final Player ai, final SpellAbility sa) {
        final Card card = sa.getHostCard();
        if (!sa.isSpell() || !card.isCreature() || card.hasKeyword(Keyword.HASTE) || HarmfulArrival247.hasOwnValue(card)) {
            return false;
        }
        if (SweeperUnderPressure249.underPressure(ai)) {
            return false;
        }
        if (killer(ai, card) == null) {
            return false;
        }
        // fix 11/11 (bourdes v321, Zombie held Champion of the Perished, Headless Rider and Midnight Reaper 6 turns):
        // a pinger kills one creature per untap; when we can field more victims than the opponent has pings, we saturate it and cast
        return victims(ai, card) <= pings(ai);
    }

    /** opposing permanents with an active pinger ability (one ping each per turn) */
    static int pings(final Player ai) {
        int n = 0;
        for (final Player o : ai.getOpponents()) {
            for (final Card p : o.getCardsIn(ZoneType.Battlefield)) {
                for (final SpellAbility sa : p.getSpellAbilities()) {
                    if (pingDamage(sa) > 0 && activableNow(p, sa, o)) {
                        n++;
                        break;
                    }
                }
            }
        }
        return n;
    }

    /** the pinger's possible victims if {@code card} is cast: it, our creatures in play it would kill, and the other such creatures of our hand the mana left pays for */
    static int victims(final Player ai, final Card card) {
        int n = 1;
        for (final Card c : ai.getCreaturesInPlay()) {
            if (killer(ai, c) != null) {
                n++;
            }
        }
        int budget = ComputerUtilMana.getAvailableManaEstimate(ai, false) - card.getCMC();
        final java.util.List<Card> others = new java.util.ArrayList<>();
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c != card && c.isCreature() && !c.hasKeyword(Keyword.HASTE) && killer(ai, c) != null) {
                others.add(c);
            }
        }
        others.sort(java.util.Comparator.comparingInt(Card::getCMC));
        for (final Card c : others) {
            if (c.getCMC() > budget) {
                break;
            }
            budget -= c.getCMC();
            n++;
        }
        return n;
    }
}
