package forge.ai;

import forge.game.Game;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 171 (relevé « mana perdu » v209, Eldratron - Avacyn t6-8). Spell Burst and Spell Blast (X U,
 * "counter target spell with mana value X", Spell Burst with buyback 3) never countered anything and kept the mana of
 * Culling Scales: the AI checked buyback and targets with X = 0, so with four lands it only offered the buyback
 * version, which could pay X = 0. The X a spell would pay is the mana value of the spell it counters, else 1.
 */
public final class XCounterAi {
    private XCounterAi() { }

    /** "Counter target spell with mana value X" where X is the mana paid. */
    public static boolean isXCounter(final SpellAbility sa) {
        return sa.getParamOrDefault("ValidTgts", "").contains("cmcEQX") && "Count$xPaid".equals(sa.getSVar("X"));
    }

    /** The X {@code sa} would pay now: the mana value of the top of the stack for an X counter, else at least 1. */
    public static int xWanted(final SpellAbility sa) {
        final Game game = sa.getHostCard() == null ? null : sa.getHostCard().getGame();
        if (game != null && isXCounter(sa) && !game.getStack().isEmpty()) {
            final SpellAbility top = game.getStack().peekAbility();
            if (top != null && top.getHostCard() != null) {
                return Math.max(1, top.getHostCard().getCMC());
            }
        }
        return 1;
    }
}
