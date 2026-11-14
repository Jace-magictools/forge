package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
import forge.game.cost.CostTap;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools rule 284 (D165, tester 11/11, plateau, deck Hobbit cus_jrrt): a cheap activated "return target
 * creature you control to its owner's hand" is a card engine with a creature whose arrival pays (draw, token,
 * damage): Mal de la Comté ({W}) + Ancien de Hobbitbourg (W, draws on arrival) = a card for {W}{W}. Forge only
 * bounced its own creature to save it, so the Plains stayed untapped and the mana was lost every turn.
 * Now, with nothing on the stack, an ability (not a spell: a card for a card gains nothing) paid only with mana
 * and {T} returns, at the opponent's end step, our nontoken creature whose arrival is worth more than what it
 * loses (BlinkAi) when we have enough lands to cast it again next turn. Our own main phase is left to stock
 * Forge (it keeps instant-speed abilities for later anyway).
 */
public final class RebuyEtbAi {
    private RebuyEtbAi() { }

    private static boolean manaAndTapOnly(final Cost cost) {
        if (cost == null) {
            return false;
        }
        for (final CostPart p : cost.getCostParts()) {
            if (!(p instanceof CostPartMana) && !(p instanceof CostTap)) {
                return false;
            }
        }
        return true;
    }

    /** Our creature to bounce and cast again, or null. */
    public static Card choose(final Player ai, final SpellAbility sa, final CardCollectionView list) {
        if (!NewAi.on(ai, 284) || sa.isSpell() || sa.isTrigger() || sa.getParent() != null
                || !manaAndTapOnly(sa.getPayCosts()) || sa.getMinTargets() > 1) {
            return null;
        }
        final Game game = ai.getGame();
        if (!game.getStack().isEmpty()) {
            return null;
        }
        final PhaseHandler ph = game.getPhaseHandler();
        final boolean endStep = ph.is(PhaseType.END_OF_TURN) && !ph.isPlayerTurn(ai);
        if (!endStep) {
            return null;
        }
        final int lands = ai.getLandsInPlay().size();
        Card best = null;
        int bestValue = 0;
        for (final Card c : list) {
            if (!c.isCreature() || c.isToken() || !c.getController().equals(ai) || !c.getOwner().equals(ai)
                    || c.getManaCost().countX() > 0 || c.isFaceDown()) {
                continue;
            }
            final int cmc = c.getCMC();
            if (cmc > lands) {
                continue;
            }
            final int v = BlinkAi.etbValue(ai, c) - BlinkAi.leaveCost(ai, c);
            if (v > bestValue || (v == bestValue && best != null && v > 0 && cmc < best.getCMC())) {
                best = c;
                bestValue = v;
            }
        }
        return best;
    }
}
