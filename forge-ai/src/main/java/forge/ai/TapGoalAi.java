package forge.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.CostPart;
import forge.game.cost.CostTapType;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 206 (tester 05/11, a test game: « plutôt sortir des petites elfes qu'un 2e feuilldor. En ligne
 * de mire : lui prendre ses terrains »). An ability whose whole cost is « tap N untapped creatures of a type » (Gilt-Leaf
 * Archdruid: seven Druids) has no {T}: creatures cast this turn pay it. When the mana that is not one of those creatures
 * can bring the count to N this turn, cast the cheapest spells of the type first, pay them without tapping the type, and
 * activate as soon as N is there. A draw engine on these casts (Gilt-Leaf itself) counts for one more body. Nothing
 * changes when the goal is out of reach, and it is recomputed at each decision: a miss gives the mana back.
 */
public final class TapGoalAi {
    private TapGoalAi() { }

    private static final int MIN_TAPPED = 4;

    private record Goal(SpellAbility ability, String[] type, int n) { }

    private static CostTapType onlyCost(final SpellAbility sa) {
        if (sa.getPayCosts() == null || sa.getPayCosts().hasManaCost() || sa.getPayCosts().hasTapCost()
                || sa.getPayCosts().getCostParts().size() != 1) {
            return null;
        }
        final CostPart part = sa.getPayCosts().getCostParts().get(0);
        return part instanceof CostTapType ? (CostTapType) part : null;
    }

    /** a real effect behind a creature type: « tap five creatures: untap this » or a life gain are not worth a plan */
    private static Goal goalOf(final SpellAbility sa) {
        if (sa.isManaAbility() || !sa.isActivatedAbility() || sa.getApi() == ApiType.Untap || sa.getApi() == ApiType.GainLife) {
            return null;
        }
        final CostTapType cost = onlyCost(sa);
        if (cost == null || cost.convertAmount() == null || cost.convertAmount() < MIN_TAPPED) {
            return null;
        }
        final String type = cost.getType();
        if (type.contains(";") || type.contains(".") || "Creature".equals(type) || "Permanent".equals(type)) {
            return null;
        }
        return new Goal(sa, new String[] {"Creature." + type}, cost.convertAmount());
    }

    private static int untappedOfType(final Player ai, final Goal g) {
        int n = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c.isUntapped() && c.isValid(g.type, ai, g.ability.getHostCard(), g.ability)) {
                n++;
            }
        }
        return n;
    }

    /** a source that taps nothing of the type: one mana each, its colours are looked at by the spell */
    private static boolean freeSource(final Card c, final Player ai, final Goal g) {
        if (!c.isUntapped() || c.isValid(g.type, ai, g.ability.getHostCard(), g.ability)) {
            return false;
        }
        if (c.isCreature() && c.isSick()) {
            return false;
        }
        for (final SpellAbility ma : c.getManaAbilities()) {
            if (ma.getPayCosts() != null && ma.getPayCosts().hasTapCost() && ma.getPayCosts().getCostParts().size() == 1) {
                return true;
            }
        }
        return false;
    }

    private static int freeMana(final Player ai, final Goal g) {
        int n = ai.getManaPool().totalMana();
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (freeSource(c, ai, g)) {
                n++;
            }
        }
        return n;
    }

    private static boolean drawEngine(final Player ai, final Goal g) {
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : c.getTriggers()) {
                if (t.getMode() != TriggerType.SpellCast || !t.hasParam("ValidCard") || !"You".equals(t.getParam("ValidActivatingPlayer"))) {
                    continue;
                }
                final SpellAbility effect = t.ensureAbility();
                if (effect == null || effect.getApi() != ApiType.Draw) {
                    continue;
                }
                // the type of the goal must be what the engine counts
                final String type = g.type[0].substring("Creature.".length());
                if (t.getParam("ValidCard").contains(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** the spells of the type in the list, cheapest first, that the free mana pays in that order */
    private static List<SpellAbility> payable(final List<SpellAbility> all, final Player ai, final Goal g, final int mana) {
        final List<SpellAbility> spells = new ArrayList<>();
        for (final SpellAbility sa : all) {
            if (sa.isSpell() && sa.getHostCard() != null && sa.getHostCard().isInZone(ZoneType.Hand)
                    && sa.getHostCard().isValid(g.type, ai, g.ability.getHostCard(), g.ability)) {
                spells.add(sa);
            }
        }
        spells.sort(Comparator.comparingInt(sa -> sa.getHostCard().getCMC()));
        final List<SpellAbility> paid = new ArrayList<>();
        int left = mana;
        for (final SpellAbility sa : spells) {
            if (sa.getHostCard().getCMC() > left) {
                break;
            }
            left -= sa.getHostCard().getCMC();
            paid.add(sa);
        }
        return paid;
    }

    private static int leftAfter(final List<SpellAbility> paid, final int mana) {
        int left = mana;
        for (final SpellAbility sa : paid) {
            left -= sa.getHostCard().getCMC();
        }
        return left;
    }

    private static boolean ourMain(final Player ai) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        return ph.isPlayerTurn(ai) && ph.getPhase().isMain() && ai.getGame().getStack().isEmpty();
    }

    /** the goal within reach this turn given the spells of the list, or null */
    private static Goal reachable(final Player ai, final List<SpellAbility> all) {
        for (final SpellAbility sa : all) {
            final Goal g = goalOf(sa);
            if (g == null || !sa.getHostCard().isInPlay()) {
                continue;
            }
            final int have = untappedOfType(ai, g);
            if (have >= g.n) {
                return g;
            }
            final int mana = freeMana(ai, g);
            final List<SpellAbility> paid = payable(all, ai, g, mana);
            if (have + paid.size() >= g.n) {
                return g;
            }
            // each cast draws: two casts and a mana left find one more body more often than not
            if (paid.size() >= 2 && leftAfter(paid, mana) >= 1 && have + paid.size() + 1 >= g.n && drawEngine(ai, g)) {
                return g;
            }
        }
        return null;
    }

    public static void prioritize(final Player ai, final List<SpellAbility> all) {
        if (!ourMain(ai)) {
            return;
        }
        final Goal g = reachable(ai, all);
        if (g == null) {
            return;
        }
        if (untappedOfType(ai, g) >= g.n) {
            all.remove(g.ability);
            all.add(0, g.ability);
            return;
        }
        final List<SpellAbility> paid = payable(all, ai, g, freeMana(ai, g));
        all.removeAll(paid);
        all.addAll(0, paid);
    }

    /**
     * Paying a spell of the type on the way to the goal: the sources of the type stay untapped. Only when the goal is
     * there and the free mana covers this spell, so a payment is never refused for nothing.
     */
    public static boolean spares(final Player ai, final SpellAbility ma, final SpellAbility paying) {
        if (paying == null || !paying.isSpell() || !ourMain(ai)) {
            return false;
        }
        final Card source = ma.getHostCard();
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final SpellAbility sa : c.getSpellAbilities()) {
                final Goal g = goalOf(sa);
                if (g == null || !paying.getHostCard().isValid(g.type, ai, g.ability.getHostCard(), g.ability)) {
                    continue;
                }
                final boolean tapsType = source.isValid(g.type, ai, c, sa)
                        || onlyCost(ma) != null && onlyCost(ma).getType().contains(g.type[0].substring("Creature.".length()));
                if (!tapsType) {
                    continue;
                }
                final List<SpellAbility> list = new ArrayList<>(c.getSpellAbilities());
                list.add(paying);
                for (final Card h : ai.getCardsIn(ZoneType.Hand)) {
                    list.addAll(h.getSpellAbilities());
                }
                if (paying.getHostCard().getCMC() <= freeMana(ai, g) && reachable(ai, list) != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
