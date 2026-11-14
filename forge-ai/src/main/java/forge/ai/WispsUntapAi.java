package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: Cerulean Wisps (Crépitou) was marked AI:RemoveDeck:All. In that deck it untaps a Gelectrode
 * twice (its own "you cast an instant" trigger, then the spell) and draws a card. With AILogic$ UntapTapper:
 * cast on one of our tapped creatures with a {T} ability that is not a mana ability (Gelectrode, pingers),
 * whenever there is one; otherwise kept, and cast as a cantrip on one of our creatures at the end of the
 * opponent's turn when we have mana left.
 */
public final class WispsUntapAi {
    private WispsUntapAi() { }

    public static boolean isWisps(final SpellAbility sa) {
        return "UntapTapper".equals(sa.getParam("AILogic"));
    }

    private static boolean hasTapAbility(final Card c) {
        for (final SpellAbility ab : c.getSpellAbilities()) {
            if (ab.isActivatedAbility() && !ab.isManaAbility() && ab.getPayCosts() != null && ab.getPayCosts().hasTapCost()) {
                return true;
            }
        }
        return false;
    }

    public static boolean chooseTarget(final Player ai, final SpellAbility sa) {
        final CardCollection ours = CardLists.filter(ai.getCreaturesInPlay(), c -> sa.canTarget(c));
        CardCollection tappers = CardLists.filter(ours, c -> c.isTapped() && hasTapAbility(c));
        Card target = null;
        if (!tappers.isEmpty()) {
            ComputerUtilCard.sortByEvaluateCreature(tappers);
            target = tappers.getFirst();
        } else {
            final PhaseHandler ph = ai.getGame().getPhaseHandler();
            if (ph.getPlayerTurn() == ai || !ph.is(PhaseType.END_OF_TURN) || !ai.getGame().getStack().isEmpty()) {
                return false;
            }
            if (ours.isEmpty()) {
                return false;
            }
            // untapping one of ours does no harm; prefer a tapped one
            final CardCollection tapped = CardLists.filter(ours, Card::isTapped);
            target = tapped.isEmpty() ? ours.getFirst() : tapped.getFirst();
        }
        sa.resetTargets();
        sa.getTargets().add(target);
        return true;
    }
}
