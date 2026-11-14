package forge.ai;

import java.util.ArrayList;
import java.util.List;

import forge.card.MagicColor;
import forge.card.mana.ManaCostShard;
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
 * magic-tools: rule 196 (a tester reports 03/11, ElfBall). A mana ability that taps other creatures of a type
 * (Birchlore Rangers: two Elves; Heritage Druid: three Elves) has no {T}: summoning-sick creatures pay it. Spell by
 * spell the AI does not see it: with Pendelhaven + Llanowar Elves it cast Elvish Mystic + Heritage Druid and stopped,
 * where Mystic + Birchlore + a third Elf was there. So, when such an engine is in play or in hand, cast first the
 * creature spell that starts the longest chain of creature spells this turn. Costs are counted, colours are not.
 * The stock order stays unless the chain is strictly longer.
 */
public final class TapEngineAi {
    private TapEngineAi() { }

    private static final int MAX_SPELLS = 7;

    /** tap n creatures of the type: k mana */
    private record Engine(int n, int k) { }

    /** what is left to pay with this turn */
    private record State(int pool, int plain, int dorks, int free, List<Engine> engines) {
        /** mana still obtainable, the free tappers first (they make nothing otherwise) */
        int capacity() {
            State s = this;
            int total = 0;
            while (true) {
                if (s.pool > 0) {
                    total += s.pool;
                    s = new State(0, s.plain, s.dorks, s.free, s.engines);
                }
                final State next = s.convert();
                if (next == null) {
                    return total + s.plain + s.dorks;
                }
                s = next;
            }
        }

        /** the best conversion that gains mana, or null */
        State convert() {
            Engine best = null;
            int bestGain = 0;
            for (final Engine e : engines) {
                final int dorksUsed = Math.max(0, e.n - free);
                if (dorksUsed > dorks) {
                    continue;
                }
                final int gain = e.k - dorksUsed;
                if (gain > bestGain) {
                    best = e;
                    bestGain = gain;
                }
            }
            if (best == null) {
                return null;
            }
            final int dorksUsed = Math.max(0, best.n - free);
            return new State(pool + best.k, plain, dorks - dorksUsed, free - Math.min(free, best.n), engines);
        }

        State pay(final int cost) {
            State s = this;
            int left = cost;
            while (left > 0) {
                if (s.pool > 0) {
                    final int p = Math.min(left, s.pool);
                    left -= p;
                    s = new State(s.pool - p, s.plain, s.dorks, s.free, s.engines);
                } else if (s.free > 0 && s.convert() != null && s.dorksFor(s.convert()) == 0) {
                    s = s.convert();
                } else if (s.plain > 0) {
                    left--;
                    s = new State(0, s.plain - 1, s.dorks, s.free, s.engines);
                } else if (s.dorks > 0 && s.convert() == null) {
                    left--;
                    s = new State(0, s.plain, s.dorks - 1, s.free, s.engines);
                } else if (s.convert() != null) {
                    s = s.convert();
                } else {
                    return null;
                }
            }
            return s;
        }

        private int dorksFor(final State converted) {
            return dorks - converted.dorks;
        }
    }

    /**
     * magic-tools: rule 203 (tester 03/11, a test game: « utiliser en priorité les sentinelles pour la mana : elles se
     * dégagent ensuite »). This creature untaps itself when you cast a spell, and such a spell is on the stack or in hand:
     * tapping it to pay costs nothing (Nettle Sentinel; only an Untap of itself counts).
     */
    public static boolean untapsOnCast(final Player ai, final Card c) {
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() != TriggerType.SpellCast || !t.hasParam("ValidCard") || !"You".equals(t.getParam("ValidActivatingPlayer"))) {
                continue;
            }
            final SpellAbility effect = t.ensureAbility();
            if (effect == null || effect.getApi() != ApiType.Untap || !"Self".equals(effect.getParam("Defined"))) {
                continue;
            }
            final String[] valid = t.getParam("ValidCard").split(",");
            for (final Card spell : ai.getCardsIn(ZoneType.Hand)) {
                if (!spell.isLand() && spell.isValid(valid, ai, c, null)) {
                    return true;
                }
            }
            for (final Card spell : ai.getGame().getCardsIn(ZoneType.Stack)) {
                if (spell.getController() == ai && spell.isValid(valid, ai, c, null)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** the tap-others mana ability of a card, or null */
    private static CostTapType tapCost(final SpellAbility ma) {
        if (ma.getPayCosts() == null || ma.getPayCosts().hasManaCost() || ma.getPayCosts().hasTapCost()) {
            return null;
        }
        for (final CostPart part : ma.getPayCosts().getCostParts()) {
            if (part instanceof CostTapType) {
                return (CostTapType) part;
            }
        }
        return null;
    }

    private static SpellAbility engineAbility(final Card c) {
        for (final SpellAbility ma : c.getManaAbilities()) {
            final CostTapType cost = tapCost(ma);
            if (cost != null && ma.getPayCosts().getCostParts().size() == 1 && cost.convertAmount() != null) {
                return ma;
            }
        }
        return null;
    }

    private static Engine engine(final SpellAbility ma) {
        int k = 1;
        try {
            k = Integer.parseInt(ma.getParamOrDefault("Amount", "1"));
        } catch (final NumberFormatException e) {
            k = 1;
        }
        return new Engine(tapCost(ma).convertAmount(), k);
    }

    private static boolean ofType(final Card c, final SpellAbility ma, final Player ai) {
        return c.isValid(tapCost(ma).getType().split(";"), ai, ma.getHostCard(), ma);
    }

    /** the longest chain of spells from this state, each entry being the host of a castable spell */
    private static int longest(final State s, final List<Card> spells, final SpellAbility model, final Player ai) {
        int best = 0;
        final int capacity = s.capacity();
        for (int i = 0; i < spells.size(); i++) {
            final Card c = spells.get(i);
            if (c.getCMC() > capacity) {
                continue;
            }
            final int n = cast(s, c, model, ai, spells, i);
            if (n > best) {
                best = n;
            }
        }
        return best;
    }

    private static int cast(final State s, final Card c, final SpellAbility model, final Player ai,
            final List<Card> spells, final int index) {
        final State paid = s.pay(c.getCMC());
        if (paid == null) {
            return 0;
        }
        final List<Engine> engines = new ArrayList<>(paid.engines);
        final SpellAbility ma = engineAbility(c);
        if (ma != null) {
            engines.add(engine(ma));
        }
        // the creature just cast is summoning sick: a tapper if it has the type, nothing more
        final int free = paid.free + (ofType(c, model, ai) ? 1 : 0);
        final List<Card> rest = new ArrayList<>(spells);
        rest.remove(index);
        return 1 + longest(new State(paid.pool, paid.plain, paid.dorks, free, engines), rest, model, ai);
    }

    /** an engine of this card: its tap choices start with what makes no mana itself */
    public static boolean isEngine(final Card c) {
        return c != null && engineAbility(c) != null;
    }

    /**
     * Would this mana ability burn creatures that make as much mana on their own (Birchlore Rangers on two untapped
     * Llanowar Elves, or on one and a sick Elf that could pay later)? They tap for themselves instead.
     */
    public static boolean wasteful(final Player ai, final SpellAbility ma, final java.util.Collection<Card> alreadyTapped,
            final ManaCostShard toPay) {
        if (engineAbility(ma.getHostCard()) != ma) {
            return false;
        }
        final Engine e = engine(ma);
        int free = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (!c.isCreature() || c.isTapped() || !ofType(c, ma, ai) || alreadyTapped != null && alreadyTapped.contains(c)) {
                continue;
            }
            // rule 207: an untapper ready on a land makes mana too (Arbor Elf). The plain AI only: it can untap first, the
            // board's human (a subclass) cannot act during a payment, and refusing would hide the spell
            if (ai.getController().getClass() == PlayerControllerAi.class && UntapManaAi.isReadyUntapper(ai, c)) {
                continue;
            }
            // rule 201: a dork that cannot make this colour loses nothing (Birchlore + Llanowar for {W})
            if (!ManaBaseFirstAi.simpleManaSource(c) || c.isSick() || !makesColorOf(c, toPay)) {
                free++;
            }
        }
        return e.k <= Math.max(0, e.n - free);
    }

    /** magic-tools: rule 207, the mana of the engines in play the payment would refuse, counted by the available estimate */
    public static int refusedMana(final Player ai) {
        int refused = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            final SpellAbility ma = engineAbility(c);
            if (ma != null && ma.canPlay() && wasteful(ai, ma, null, null)) {
                refused += engine(ma).k;
            }
        }
        return refused;
    }

    /** rule 207: order of the tap choices of an engine: what the spell untaps, what makes nothing, then what makes mana */
    public static int tapRank(final Player ai, final Card c) {
        if (untapsOnCast(ai, c)) {
            return -1;
        }
        return ManaBaseFirstAi.simpleManaSource(c) && !c.isSick() || UntapManaAi.isReadyUntapper(ai, c) ? 1 : 0;
    }

    /** can this creature's own mana ability pay a coloured shard? Generic shards: always */
    private static boolean makesColorOf(final Card c, final ManaCostShard toPay) {
        if (toPay == null || toPay.isGeneric() || toPay.isOr2Generic() || toPay.getColorMask() == 0) {
            return true;
        }
        for (final SpellAbility own : c.getManaAbilities()) {
            for (final byte color : MagicColor.WUBRG) {
                if ((toPay.getColorMask() & color) != 0 && own.getManaPart().canProduce(MagicColor.toShortString(color), own)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** a tap-others mana ability in play or in hand, or null */
    private static SpellAbility anyEngine(final Player ai) {
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            final SpellAbility ma = engineAbility(c);
            if (ma != null) {
                return ma;
            }
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isCreature() && engineAbility(c) != null) {
                return engineAbility(c);
            }
        }
        return null;
    }

    private static State currentState(final Player ai, final SpellAbility model) {
        int plain = 0;
        int dorks = 0;
        int free = 0;
        final List<Engine> engines = new ArrayList<>();
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            final SpellAbility ma = engineAbility(c);
            if (ma != null) {
                engines.add(engine(ma));
            }
            if (c.isTapped()) {
                continue;
            }
            final boolean selfMana = ManaBaseFirstAi.simpleManaSource(c) && !(c.isCreature() && c.isSick())
                    || c.isLand() && !c.getManaAbilities().isEmpty();
            final boolean type = c.isCreature() && ofType(c, model, ai);
            if (type) {
                if (selfMana) {
                    dorks++;
                } else {
                    free++;
                }
            } else if (selfMana) {
                plain++;
            }
        }
        return new State(ai.getManaPool().totalMana(), plain, dorks, free, engines);
    }

    private static boolean candidate(final SpellAbility sa) {
        final Card c = sa.getHostCard();
        return sa.isSpell() && c != null && c.isCreature() && c.isInZone(ZoneType.Hand) && !sa.isCastFaceDown()
                && c.getCMC() > 0 && sa.getPayCosts().getCostMana() != null
                && sa.getPayCosts().getCostMana().getAmountOfX() == 0;
    }

    /** Puts first the creature spell that starts the longest chain, if strictly longer than the stock first one. */
    public static void prioritize(final Player ai, final List<SpellAbility> all) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || !ai.getGame().getStack().isEmpty()) {
            return;
        }
        final SpellAbility model = anyEngine(ai);
        if (model == null) {
            return;
        }
        final List<Card> spells = new ArrayList<>();
        final List<SpellAbility> sas = new ArrayList<>();
        int firstAt = -1;
        for (int i = 0; i < all.size(); i++) {
            final SpellAbility sa = all.get(i);
            if (candidate(sa) && !spells.contains(sa.getHostCard()) && spells.size() < MAX_SPELLS) {
                if (firstAt < 0) {
                    firstAt = i;
                }
                spells.add(sa.getHostCard());
                sas.add(sa);
            }
        }
        if (spells.size() < 2) {
            return;
        }
        final State now = currentState(ai, model);
        final int capacity = now.capacity();
        int stock = 0;
        int best = 0;
        int bestAt = -1;
        for (int i = 0; i < spells.size(); i++) {
            if (spells.get(i).getCMC() > capacity) {
                continue;
            }
            final int n = cast(now, spells.get(i), model, ai, spells, i);
            if (i == 0) {
                stock = n;
            }
            if (n > best) {
                best = n;
                bestAt = i;
            }
        }
        if (bestAt > 0 && best > stock) {
            all.remove(sas.get(bestAt));
            all.add(firstAt, sas.get(bestAt));
        }
    }

    /** The next spell of a chain of at least two: main 1, the attack would tap the Elves that pay for it. */
    public static boolean startsChain(final Player ai, final SpellAbility sa) {
        if (!candidate(sa)) {
            return false;
        }
        final SpellAbility model = anyEngine(ai);
        if (model == null) {
            return false;
        }
        final List<Card> spells = new ArrayList<>();
        spells.add(sa.getHostCard());
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c != sa.getHostCard() && c.isCreature() && c.getCMC() > 0 && spells.size() < MAX_SPELLS) {
                spells.add(c);
            }
        }
        final State now = currentState(ai, model);
        return sa.getHostCard().getCMC() <= now.capacity() && cast(now, sa.getHostCard(), model, ai, spells, 0) >= 2;
    }
}
