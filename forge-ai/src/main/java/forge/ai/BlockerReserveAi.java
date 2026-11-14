package forge.ai;

import java.util.ArrayList;
import java.util.List;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: keep the blockers we need to survive the next attack. Forge taps any creature for mana (Heritage
 * Druid, Birchlore Rangers, Llanowar Elves...) to cast more creatures, even when the opponent's board kills us next
 * turn if our creatures are tapped (ElfBall against Heliode: 15 elves tapped in main 2, one blocker left, dead to
 * a 35-damage swing at 17 life). General rule: while the opponent's next attack would be lethal, a creature is not
 * tapped for mana if that leaves fewer untapped creatures than the chump blocks needed to survive.
 */
public final class BlockerReserveAi {
    private BlockerReserveAi() { }

    private static String cacheKey;
    private static CardCollection cacheOurs;
    private static int cacheNeeded;
    /** Reentrancy guard: damageIfUnblocked prices the attacker's pump abilities, which asks us again (Verdu vs elfe). */
    private static boolean computing;

    /** True if the mana ability taps its host creature and that creature is a needed blocker. */
    public static boolean reservedSource(final Player ai, final SpellAbility ma) {
        if (ma == null || ma.getPayCosts() == null || !ma.getPayCosts().hasTapCost()) {
            return false;
        }
        final Card host = ma.getHostCard();
        return host != null && host.isCreature() && reserved(ai).contains(host);
    }

    /** Creatures of ai that must stay untapped (empty if no danger or no hope). */
    public static CardCollection reserved(final Player ai) {
        return spare(ai) <= 0 ? cacheOurs : new CardCollection();
    }

    /**
     * Too many of our untapped potential blockers in a tap-cost choice for a mana ability (Heritage Druid:
     * "tap three untapped Elves")?
     */
    public static boolean tapsTooMany(final Player ai, final SpellAbility sa, final CardCollection toTap) {
        if (sa == null || !sa.isManaAbility() || toTap == null) {
            return false;
        }
        final int spare = spare(ai);
        if (spare == Integer.MAX_VALUE) {
            return false;
        }
        int n = 0;
        for (final Card c : toTap) {
            if (cacheOurs.contains(c)) {
                n++;
            }
        }
        return n > spare;
    }

    /** Untapped potential blockers we can tap and still survive the next attack (MAX_VALUE: no danger). */
    public static int spare(final Player ai) {
        final Game game = ai.getGame();
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ph.isPlayerTurn(ai) && ph.getPhase().isAfter(PhaseType.COMBAT_DECLARE_ATTACKERS)) {
            return Integer.MAX_VALUE;
        }
        if (computing) {
            return Integer.MAX_VALUE;
        }
        final String key = key(ai);
        if (!key.equals(cacheKey)) {
            final CardCollection ours = new CardCollection();
            computing = true;
            try {
                cacheNeeded = compute(ai, ours);
            } finally {
                computing = false;
            }
            cacheOurs = ours;
            cacheKey = key;
        }
        return cacheNeeded <= 0 ? Integer.MAX_VALUE : cacheOurs.size() - cacheNeeded;
    }

    private static String key(final Player ai) {
        final Game game = ai.getGame();
        final StringBuilder sb = new StringBuilder();
        sb.append(game.getId()).append('/').append(ai.getId()).append('/').append(ph(game)).append('/').append(ai.getLife());
        for (final Player p : game.getPlayers()) {
            for (final Card c : p.getCreaturesInPlay()) {
                sb.append(',').append(c.getId()).append(c.isTapped() ? 't' : 'u').append(c.getNetPower());
            }
        }
        return sb.toString();
    }

    private static String ph(final Game game) {
        return game.getPhaseHandler().getTurn() + ":" + game.getPhaseHandler().getPhase();
    }

    /** Fills ours with our untapped potential blockers; returns the chump blocks needed to survive (0: none). */
    static int compute(final Player ai, final CardCollection ours) {
        final boolean theirTurn = !ai.getGame().getPhaseHandler().isPlayerTurn(ai);
        for (final Card c : ai.getCreaturesInPlay()) {
            if (!c.isTapped() && CombatUtil.canBlock(c, true)) {
                ours.add(c);
            }
        }
        if (ours.isEmpty()) {
            return 0;
        }
        int unblockable = 0;
        final List<Integer> blockable = new ArrayList<>();
        for (final Player opp : ai.getOpponents()) {
            for (final Card att : opp.getCreaturesInPlay()) {
                if (att.hasKeyword(Keyword.DEFENDER)) {
                    continue;
                }
                if (theirTurn && !CombatUtil.canAttack(att, ai)) {
                    continue;
                }
                final int dmg = ComputerUtilCombat.damageIfUnblocked(att, ai, null, false);
                if (dmg <= 0) {
                    continue;
                }
                boolean canBeBlocked = false;
                for (final Card b : ours) {
                    if (CombatUtil.canBlock(att, b, true)) {
                        canBeBlocked = true;
                        break;
                    }
                }
                if (canBeBlocked) {
                    blockable.add(dmg);
                } else {
                    unblockable += dmg;
                }
            }
        }
        final int life = ai.getLife();
        if (unblockable >= life) {
            return 0; // dead anyway, blockers do not help
        }
        blockable.sort((a, b) -> b - a);
        int total = unblockable;
        for (final int d : blockable) {
            total += d;
        }
        int needed = 0;
        while (total >= life && needed < blockable.size()) {
            total -= blockable.get(needed);
            needed++;
        }
        if (total >= life || needed > ours.size()) {
            return 0; // not enough blockers to survive anyway
        }
        return needed;
    }
}
