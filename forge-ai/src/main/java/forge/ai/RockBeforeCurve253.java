package forge.ai;

import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 253 (a tester report 08/11, D144, a test game turn 7): with four lands, Talisman of
 * Progress was cast in the first main phase and Elite Guardmage ({2}{W}{U}, kept for the second main phase as every
 * creature without haste) could no longer be paid. A mana rock that costs mana this turn (rule 71: Talisman, Signet,
 * Mind Stone) waits when the mana it eats would leave unpaid a bigger spell of the hand that can be paid now; it is
 * cast after that spell if the mana remains, or next turn.
 */
public final class RockBeforeCurve253 {
    private RockBeforeCurve253() { }

    private static int cost(final SpellAbility sa) {
        return sa.getPayCosts() != null && sa.getPayCosts().getTotalMana() != null
                ? sa.getPayCosts().getTotalMana().getCMC() : sa.getHostCard().getCMC();
    }

    /** rule 253: the spell of the hand that the rock {@code sa} would leave unpaid this turn, or null */
    public static Card rockKept(final Player ai, final SpellAbility sa) {
        if (sa == null || !sa.isSpell()) {
            return null;
        }
        final int net = ManaRampAi.rockNet(sa);
        if (net == Integer.MIN_VALUE || net >= 0) {
            return null;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain()) {
            return null;
        }
        // one mana per untapped source: getAvailableManaEstimate counts « Combo W B » as three (Caves of Koilos)
        final int available = ComputerUtilMana.getAvailableManaSources(ai, true).size() + ai.getManaPool().totalMana();
        final int rock = cost(sa);
        final int after = available + net;
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c == sa.getHostCard() || c.isLand() || c.isInstant() || c.hasKeyword(forge.game.keyword.Keyword.FLASH)) {
                continue;
            }
            for (final SpellAbility s : c.getSpells()) {
                s.setActivatingPlayer(ai);
                final int need = cost(s);
                if (need <= rock || need <= after || need > available) {
                    continue;
                }
                if (s.canPlay() && ComputerUtilMana.canPayManaCost(s, ai, 0, false)) {
                    return c;
                }
            }
        }
        return null;
    }
}
