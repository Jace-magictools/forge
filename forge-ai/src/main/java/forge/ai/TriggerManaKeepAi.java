package forge.ai;

import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 88 (a tester report 29/10 16:01, Gwyll - Vampire, turn 13). Five lands, Tithe Drinker in play: the
 * AI paid extort on each spell and lost the mana of the next one (Voracious Hatchling, then Edge of the Divinity).
 * General rule: on its own turn, before the end step, the AI does not pay the mana cost of an optional triggered
 * ability (extort, "you may pay {1}") when that payment would leave a spell of our hand, castable with the current
 * mana, without enough mana. The spell is worth more than the trigger.
 */
public final class TriggerManaKeepAi {
    private TriggerManaKeepAi() { }

    public static boolean keepsMana(final Player ai, final SpellAbility sa) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        final Cost cost = sa.getPayCosts();
        if (!sa.isTrigger() || cost == null || cost.getTotalMana() == null || !ph.isPlayerTurn(ai)
                || !ph.getPhase().isBefore(PhaseType.END_OF_TURN)) {
            return false;
        }
        final int spent = cost.getTotalMana().getCMC();
        if (spent == 0) {
            return false;
        }
        final int available = ManlandMain2Ai.untappedMana(ai) + ai.getManaPool().totalMana();
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            final int cmc = c.getCMC();
            if (c.isLand() || cmc == 0 || cmc > available || cmc <= available - spent) {
                continue;
            }
            for (final SpellAbility csa : c.getSpells()) {
                csa.setActivatingPlayer(ai);
                if (csa.isSpell() && ComputerUtilMana.canPayManaCost(csa, ai, 0, false)) {
                    return true;
                }
            }
        }
        return false;
    }
}
