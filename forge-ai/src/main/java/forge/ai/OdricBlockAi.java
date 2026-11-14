package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;

/**
 * magic-tools: Odric, Master Tactician (Soldats) was marked AI:RemoveDeck:All: when the AI declared the
 * opponent's blockers (Odric's trigger), AiController reused the defender's own blocking logic, i.e. the
 * best blocks for the opponent. Now the attacking AI blocks for its own benefit: nobody blocks if our
 * unblocked damage is lethal; otherwise each blocker is put only in front of an attacker that kills it and
 * survives (first strike Odric against a 2/2...), one blocker per attacker, everyone else lets damage through.
 */
public final class OdricBlockAi {
    private OdricBlockAi() { }

    public static boolean declaresForOpponent(final Player ai, final Player defender) {
        // only when an effect really hands us the declaration (Odric): a caller asking for the defender's own
        // blocks from another player's AI keeps the defender's logic (BasicBlockTests, red without this guard)
        return defender != ai && ai.isOpponentOf(defender) && defender.getDeclaresBlockers() == ai;
    }

    /** @return false if no legal declaration was found (the caller keeps Forge's default) */
    public static boolean declareBlockers(final Player ai, final Player defender, final Combat combat) {
        final CardCollection attackers = CardLists.filter(combat.getAttackersOf(defender), c -> c.getController() == ai);
        final CardCollection blockers = CardLists.filter(defender.getCreaturesInPlay(), c -> CombatUtil.canBlock(c, combat));
        for (final Card b : blockers) {
            combat.undoBlockingAssignment(b);
        }
        if (CombatUtil.validateBlocks(combat, defender) != null) {
            return false; // must-block requirements: leave them to Forge
        }
        int damage = 0;
        for (final Card a : attackers) {
            damage += ComputerUtilCombat.damageIfUnblocked(a, defender, combat, false);
        }
        if (damage >= defender.getLife() && defender.canLoseLife() && !defender.cantLoseForZeroOrLessLife()) {
            return true;
        }
        ComputerUtilCard.sortByEvaluateCreature(blockers); // best blockers first: the most valuable ones to kill
        final CardCollection used = new CardCollection();
        for (final Card b : blockers) {
            for (final Card a : attackers) {
                if (used.contains(a) || !CombatUtil.canBlock(a, b, combat)) {
                    continue;
                }
                if (ComputerUtilCombat.canDestroyBlocker(ai, b, a, combat, false)
                        && !ComputerUtilCombat.canDestroyAttacker(ai, a, b, combat, false)) {
                    combat.addBlocker(a, b);
                    if (CombatUtil.validateBlocks(combat, defender) != null) {
                        combat.removeBlockAssignment(a, b);
                        continue;
                    }
                    used.add(a);
                    break;
                }
            }
        }
        return true;
    }
}
