package forge.ai.ability;

import forge.ai.ComputerUtilMana;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 235. A flash creature with no ETB waits for the opponent's end step, but nothing keeps its mana
 * ("TODO: maybe implement a way to reserve mana for this" in doAdvancedFlashLogic): in our second main phase the other
 * spells of the hand take the mana, and it never comes (tournament v24 traces: 17 of 19 end steps out of mana after a
 * main phase that could pay; Enduring Curiosity x2 in hand from turn 1 to turn 21). Cast it in our second main phase
 * when the sorcery-speed spells we can pay would leave less than its cost.
 */
public final class FlashStarved235 {
    private FlashStarved235() { }

    public static boolean castBeforeTheManaGoes(Player ai, Card card) {
        PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.is(PhaseType.MAIN2, ai) || !ai.getGame().getStack().isEmpty()) {
            return false;
        }
        int mana = ComputerUtilMana.getAvailableManaEstimate(ai, false);
        int others = 0;
        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.equals(card) || c.isLand() || c.isInstant() || c.hasKeyword(Keyword.FLASH)) {
                continue;
            }
            int cmc = c.getCMC();
            if (cmc == 0 || cmc > mana) {
                continue;
            }
            SpellAbility sa = c.getFirstSpellAbility();
            if (sa == null) {
                continue;
            }
            sa.setActivatingPlayer(ai);
            if (ComputerUtilMana.canPayManaCost(sa, ai, 0, false)) {
                others += cmc;
            }
        }
        return others > mana - card.getCMC();
    }
}
