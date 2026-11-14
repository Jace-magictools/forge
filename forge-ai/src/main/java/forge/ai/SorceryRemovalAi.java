package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 39, sorcery-speed removal. Pox against Imperium (27/10) kept Befoul ("destroy target land or
 * nonblack creature") in hand for turns: at 6 life against a 3/2 flier, and with the opponent on 2 lands and 2 life.
 * Two general flaws of ComputerUtilCard.useRemovalNow / DestroyAi:
 * - the threat of a creature is divided by 10 in our own turn after combat, so that an instant waits for the
 *   opponent's turn; a sorcery has no later chance before that creature attacks, so the threat stays whole;
 * - with mixed targets (land or creature), DestroyAi only weighed the best creature and gave up when it was not
 *   worth removing now, never the land: the land is then tried with the land destruction timing of rule 11
 *   (sorcery-speed land destruction is cast in our second main phase).
 * Rule 43 (tester, 27/10: "Befoul : en priorite sur un terrain"): with mixed targets, an opponent land comes first,
 * in our second main phase (rule 11), unless our life is in danger: then the creature logic of Forge is kept.
 */
public final class SorceryRemovalAi {
    private SorceryRemovalAi() { }

    /** Keep the full threat of a creature: sorcery-speed removal in our own turn after combat. */
    public static boolean lastChanceBeforeAttack(final SpellAbility sa, final Player ai) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        return sa.isSpell() && ph.isPlayerTurn(ai) && ph.getPhase().isAfter(PhaseType.COMBAT_DECLARE_BLOCKERS)
                && SpellAbilityAi.isSorcerySpeed(sa, ai);
    }

    /** The best opponent land of a mixed target list when the creature is held, or null. */
    public static Card landInstead(final Player ai, final SpellAbility sa, final CardCollection list, final Card held) {
        if (held == null || held.isLand()) {
            return null;
        }
        final CardCollection lands = CardLists.filter(list, CardPredicates.LANDS);
        if (lands.isEmpty()) {
            return null;
        }
        final Card land = ComputerUtilCard.getBestLandToRemoveAI(ai, lands, sa);
        return land != null && LandTradeAi.castNow(sa, land, ai) ? land : null;
    }

    /**
     * Rule 43: the opponent land to destroy first for mixed land/creature sorcery-speed removal, or null to keep the
     * creature logic. The caller waits for our second main phase (LandTradeAi.castNow) before casting.
     */
    public static Card landFirst(final Player ai, final SpellAbility sa, final CardCollection list) {
        if (!sa.isSpell() || !LandTradeAi.destroysLand(sa) || !SpellAbilityAi.isSorcerySpeed(sa, ai)
                || ComputerUtil.aiLifeInDanger(ai, false, 0)) {
            return null;
        }
        final CardCollection lands = CardLists.filter(list, CardPredicates.LANDS,
                c -> c.getController().isOpponentOf(ai));
        if (lands.isEmpty()) {
            return null;
        }
        return ComputerUtilCard.getBestLandToRemoveAI(ai, lands, sa);
    }
}
