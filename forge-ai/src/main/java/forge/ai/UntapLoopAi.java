package forge.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.game.Game;
import forge.game.GameLogEntryType;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
import forge.game.cost.CostTap;
import forge.game.mana.Mana;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 140, untap loop with a net mana gain (deck Myr: two Myr Galvanizer + Palladium Myr).
 * An engine "{N}, {T}: untap each other X you control" (UntapAll) that untaps a mana producer making more than N
 * (and the other engine, or itself) is unbounded colorless mana. Forge's AI never floats mana, so it never ran the loop:
 * it attacked with the Myr instead. Like a player announcing a shortcut (rule 727), the AI adds the loop's mana to its
 * pool (up to three times per main phase, each time the pool is spent) when something can use it (a spell it cannot pay, a repeatable ability such as Myr Matrix);
 * the last engine of a pair ends tapped, as after the real loop. Unspent mana empties with the phase.
 */
public final class UntapLoopAi {
    private UntapLoopAi() { }

    /** Mana given by one shortcut: plenty for a hand and a dozen activations, finite as a shortcut must be. */
    static final int SHORTCUT_MANA = 40;

    /** At most this many shortcuts per phase (120 mana): the loop is endless, the game must stay finite. */
    static final int MAX_PER_PHASE = 3;
    /** The loop runs again once the pool is down to less than this (a few mana left over by a land or Palladium tapped on the side). */
    static final int REOPEN_BELOW = 10;

    private static final Map<Game, Map<Integer, int[]>> DONE = new WeakHashMap<>();
    private static final Map<Game, Card> LEFT_TAPPED = new WeakHashMap<>();

    /** Generic mana N of an engine "{N}, {T}: UntapAll", or -1. */
    static int engineCost(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.UntapAll || !sa.isActivatedAbility() || !sa.hasParam("ValidCards")
                || sa.getSubAbility() != null) {
            return -1;
        }
        final Cost cost = sa.getPayCosts();
        if (cost == null) {
            return -1;
        }
        boolean tap = false;
        int generic = 0;
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostTap) {
                tap = true;
            } else if (part instanceof CostPartMana pm) {
                final ManaCost mc = pm.getMana();
                if (!mc.isPureGeneric() || mc.countX() > 0) {
                    return -1;
                }
                generic += mc.getGenericCost();
            } else {
                return -1;
            }
        }
        return tap ? generic : -1;
    }

    static boolean sick(final Card c) {
        return c.isCreature() && c.hasSickness();
    }

    static boolean canTapNow(final Card c) {
        return c.isUntapped() && !sick(c);
    }

    /** Most mana a "{T}: add" ability of the card makes, 0 if none. */
    static int tapMana(final Player ai, final Card c) {
        int max = 0;
        for (final SpellAbility ma : c.getManaAbilities()) {
            final Cost cost = ma.getPayCosts();
            if (cost == null || cost.getCostParts().size() != 1 || !(cost.getCostParts().get(0) instanceof CostTap)) {
                continue;
            }
            ma.setActivatingPlayer(ai);
            max = Math.max(max, ma.amountOfManaGenerated(true));
        }
        return max;
    }

    private static boolean untaps(final Player ai, final SpellAbility engine, final Card c) {
        return c.isValid(engine.getParam("ValidCards").split(","), ai, engine.getHostCard(), engine);
    }

    /** The engine card left tapped after the loop, the engine itself if it untaps itself; null when there is no loop. */
    static Card findLoop(final Player ai) {
        final CardCollection mine = new CardCollection(ai.getCardsIn(ZoneType.Battlefield));
        for (final Card e1 : mine) {
            if (!canTapNow(e1)) {
                continue;
            }
            for (final SpellAbility s1 : e1.getSpellAbilities()) {
                final int n1 = engineCost(s1);
                if (n1 < 0) {
                    continue;
                }
                s1.setActivatingPlayer(ai);
                final boolean self = untaps(ai, s1, e1);
                for (final Card m : mine) {
                    // a tapped producer works too: the first activation untaps it, if something else pays that one
                    if (m == e1 || !untaps(ai, s1, m) || !(canTapNow(m) || (!sick(m)
                            && ComputerUtilMana.getAvailableManaEstimate(ai, false) >= n1))) {
                        continue;
                    }
                    final int out = tapMana(ai, m);
                    if (out <= 0) {
                        continue;
                    }
                    if (self && out > n1) {
                        return e1;
                    }
                    for (final Card e2 : mine) {
                        if (e2 == e1 || e2 == m || sick(e2) || !untaps(ai, s1, e2)) {
                            continue;
                        }
                        for (final SpellAbility s2 : e2.getSpellAbilities()) {
                            final int n2 = engineCost(s2);
                            if (n2 < 0) {
                                continue;
                            }
                            s2.setActivatingPlayer(ai);
                            if (untaps(ai, s2, e1) && untaps(ai, s2, m) && 2 * out > n1 + n2) {
                                return e2;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Something the loop's mana pays for: a spell in hand not payable now, or a repeatable mana-only ability. */
    static boolean hasSink(final Player ai) {
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isLand()) {
                continue;
            }
            for (final SpellAbility sa : c.getSpellAbilities()) {
                if (!sa.isSpell()) {
                    continue;
                }
                sa.setActivatingPlayer(ai);
                if (!ComputerUtilMana.canPayManaCost(sa, ai, 0, false)) {
                    return true;
                }
            }
        }
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final SpellAbility sa : c.getSpellAbilities()) {
                if (!sa.isActivatedAbility() || sa.isManaAbility() || sa.getApi() == ApiType.UntapAll) {
                    continue;
                }
                final Cost cost = sa.getPayCosts();
                if (cost == null || cost.getCostParts().size() != 1
                        || !(cost.getCostParts().get(0) instanceof CostPartMana pm) || pm.getMana().isZero()) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /** Runs the shortcut up to MAX_PER_PHASE times per main phase of ours, again once the pool is nearly spent; true if mana was added. */
    public static boolean maybeShortcut(final Player ai) {
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || !game.getStack().isEmpty()) {
            return false;
        }
        final int key = ph.getTurn() * 100 + ph.getPhase().ordinal();
        final Map<Integer, int[]> done = DONE.computeIfAbsent(game, g -> new HashMap<>());
        int[] last = done.get(ai.getId());
        if (last == null || last[0] != key) {
            last = new int[] {key, 0};
        } else if (last[1] >= MAX_PER_PHASE || ai.getManaPool().totalMana() >= REOPEN_BELOW) {
            return false;
        }
        // the engine left tapped by the previous shortcut is part of the same endless loop
        final Card before = LEFT_TAPPED.get(game);
        final boolean reopen = last[1] > 0 && before != null && before.isTapped() && before.isInPlay();
        if (reopen) {
            before.setTapped(false);
        }
        final Card tapped = findLoop(ai);
        if (tapped == null || !hasSink(ai)) {
            if (reopen) {
                before.setTapped(true);
            }
            return false;
        }
        last[1]++;
        done.put(ai.getId(), last);
        for (int i = 0; i < SHORTCUT_MANA; i++) {
            ai.getManaPool().addMana(new Mana(MagicColor.COLORLESS, tapped, null, ai));
        }
        if (!untapsItself(ai, tapped)) {
            tapped.setTapped(true);
            LEFT_TAPPED.put(game, tapped);
        }
        game.getGameLog().add(GameLogEntryType.MANA, ai.getName() + " repeats the untap loop of " + tapped.getName()
                + " (rule 140): " + SHORTCUT_MANA + " colorless mana");
        return true;
    }

    /** Mana floating in our pool during our main phase pays this ability's whole mana cost: spend it before it empties. */
    public static boolean floatingPaysFor(final Player ai, final SpellAbility sa) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || sa.getPayCosts() == null) {
            return false;
        }
        final int pool = ai.getManaPool().totalMana();
        final int cmc = sa.getPayCosts().getTotalMana().getCMC();
        return pool > 0 && cmc > 0 && pool >= cmc;
    }

    private static boolean untapsItself(final Player ai, final Card c) {
        for (final SpellAbility sa : c.getSpellAbilities()) {
            if (engineCost(sa) >= 0 && untaps(ai, sa, c)) {
                return true;
            }
        }
        return false;
    }
}
