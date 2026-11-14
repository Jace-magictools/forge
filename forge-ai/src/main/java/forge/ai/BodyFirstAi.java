package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 83 (a tester report 29/10 15:37, Gwyll - Dragoonz, turn 3). With two lands, Nip Gwyllion in play and
 * Nightsky Mimic in hand, the AI cast Edge of the Divinity on the Gwyllion in main 1 (1 mana) and kept the Mimic for
 * main 2, where one mana was left: the Mimic never came down that turn. Same family as rule 35 (creature lands).
 * General rule: before main 2 on its own turn, a beneficial aura spell waits when a creature spell in hand is castable
 * now, costs more than the aura, and would no longer be castable once the aura is paid: the body first, the aura with
 * next turn's mana.
 * Rule 88 (a tester report 29/10 16:01, Gwyll - Vampire, turn 13): five lands, Tithe Drinker (extort) in play, Voracious
 * Hatchling and Edge of the Divinity in hand. The AI cast Edge and paid extort: 2 of the 5 mana, the Hatchling (4)
 * stayed in hand. Two additions: the aura's cost counts the extort payments the AI will make (one generic mana per
 * extort instance we control, the aura's own included), and the aura also waits when it would feed a "whenever you
 * cast" trigger of a creature castable now (EngineFirstAi.castFedBy): the Hatchling first, then Edge (white and
 * black) removes two of its -1/-1 counters.
 */
public final class BodyFirstAi {
    private BodyFirstAi() { }

    public static boolean waitsForBody(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (source == null || !sa.isSpell() || !source.isAura() || sa.isCurse() || !source.isInZone(ZoneType.Hand)
                || !ph.isPlayerTurn(ai) || !ph.getPhase().isBefore(PhaseType.MAIN2)) {
            return false;
        }
        final int spent = sa.getPayCosts() == null || sa.getPayCosts().getTotalMana() == null
                ? 0 : sa.getPayCosts().getTotalMana().getCMC();
        if (spent == 0) {
            return false;
        }
        final int available = ManlandMain2Ai.untappedMana(ai) + ai.getManaPool().totalMana();
        final int paid = Math.min(available, spent + extortCount(ai, source));
        for (final Card c : CardLists.filter(ai.getCardsIn(ZoneType.Hand), Card::isCreature)) {
            final int cmc = c.getCMC();
            if (c.equals(source) || cmc > available) {
                continue;
            }
            if (!EngineFirstAi.castFedBy(ai, c, source) && (cmc <= spent || cmc <= available - paid)) {
                continue;
            }
            for (final SpellAbility csa : c.getSpells()) {
                csa.setActivatingPlayer(ai);
                if (csa.isSpell() && csa.canCastTiming(ai) && ComputerUtilMana.canPayManaCost(csa, ai, 0, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    // magic-tools: rule 88, extort instances that trigger on our spell (permanents of ours, the spell itself)
    private static int extortCount(final Player ai, final Card source) {
        int n = source.getAmountOfKeyword(forge.game.keyword.Keyword.EXTORT);
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            n += c.getAmountOfKeyword(forge.game.keyword.Keyword.EXTORT);
        }
        return n;
    }
}
