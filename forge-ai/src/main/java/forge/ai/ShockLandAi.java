package forge.ai;

import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: a shockland (Overgrown Tomb) is paid untapped for a permanent the AI only holds back until Main 2.
 * Lands are played in Main 1, where every creature answers "wait for Main 2" (MissingPhaseRestrictions):
 * ComputerUtil.hasReasonToPlayCardThisTurn said no, the land entered tapped and Kitchen Finks stayed in hand.
 */
public final class ShockLandAi {
    private ShockLandAi() { }

    public static boolean hasReasonToPlayCardThisTurn(final Player ai, final Card c) {
        if (ComputerUtil.hasReasonToPlayCardThisTurn(ai, c)) {
            return true;
        }
        if (c.isLand() || !c.isPermanent() || !ai.getGame().getPhaseHandler().is(PhaseType.MAIN1, ai)) {
            return false;
        }
        for (final SpellAbility sa : c.getAllPossibleAbilities(ai, true)) {
            if (sa.isSpell() && SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).decision()
                    == AiPlayDecision.MissingPhaseRestrictions) {
                return true;
            }
        }
        return false;
    }
}
