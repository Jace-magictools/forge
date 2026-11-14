package forge.ai;

import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: stock AI keeps permanents for main 2, but attacks with its mana creatures first
 * (Fyndhorn Elves into an empty board, pumped by Pendelhaven): the mana is gone when main 2 comes
 * and Elvish Harbinger stays in hand. Cast before combat when a mana creature is about to attack.
 */
public final class ManaDorkMain1Ai {
    private ManaDorkMain1Ai() { }

    public static boolean castBeforeCombat(final Player ai, final SpellAbility sa) {
        if (sa.getPayCosts() == null || !sa.getPayCosts().hasManaCost()) {
            return false;
        }
        for (final Card c : ai.getCreaturesInPlay()) {
            if (c.isTapped() || c.isSick() || c.getManaAbilities().isEmpty() || c.hasKeyword(Keyword.VIGILANCE)
                    || c == sa.getHostCard()) {
                continue;
            }
            if (ComputerUtilCard.doesCreatureAttackAI(ai, c)) {
                return true;
            }
        }
        return false;
    }
}
