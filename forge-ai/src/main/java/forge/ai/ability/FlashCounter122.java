package forge.ai.ability;

import java.util.ArrayList;
import java.util.List;

import forge.ai.SpellApiToAi;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 122. A flash creature whose ETB (or ETB charm) can counter a spell (Wesley Crusher,
 * Starfleet Cadet) was cast like any flash creature, at the end of the opponent's turn, so the counter
 * mode never had a target. Forge cast it in response to anything (a creature spell,
 * our own spell), wasting the counter. With a non-empty stack, cast it only when the counter mode itself would
 * counter the top of the stack (CounterAi decides: opponent's spell, valid target, unless cost not payable...),
 * otherwise hold it. With an empty stack it waits for the opponent's turn (their end step at the latest) as long
 * as an opponent holds cards; and
 * let a willing counter mode come first among the charm choices so it is kept.
 */
public final class FlashCounter122 {
    private FlashCounter122() { }

    /**
     * TRUE: cast now (the counter mode would counter the top of the stack). FALSE: hold it (nothing worth
     * countering on the stack; or empty stack on our turn, or on theirs before the end step, while an opponent
     * holds cards). NULL: not concerned, usual logic (their end step, or an empty-handed opponent).
     */
    public static Boolean flashNow(Player ai, Card card) {
        if (!card.hasKeyword(Keyword.FLASH)) {
            return null;
        }
        List<SpellAbility> modes = counterEtbModes(card);
        if (modes.isEmpty()) {
            return null;
        }
        Game game = ai.getGame();
        if (game.getStack().isEmpty()) {
            PhaseHandler ph = game.getPhaseHandler();
            if (!opponentsHoldCards(ai)) {
                return null;
            }
            // our turn: keep it for theirs; their turn: wait for a spell, or cast it at their end step
            if (ph.isPlayerTurn(ai)) {
                return Boolean.FALSE;
            }
            return ph.getPhase().isBefore(PhaseType.END_OF_TURN) ? Boolean.FALSE : null;
        }
        for (SpellAbility mode : modes) {
            mode.setActivatingPlayer(ai);
            boolean ok;
            try {
                ok = SpellApiToAi.Converter.get(mode).canPlayWithSubs(ai, mode).willingToPlay();
            } finally {
                mode.resetTargets();
            }
            if (ok) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    private static boolean opponentsHoldCards(Player ai) {
        for (Player opp : ai.getOpponents()) {
            if (!opp.getCardsIn(ZoneType.Hand).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Counter modes first (stable), so a willing one is not left out when the charm fills up. */
    public static void counterFirst(List<AbilitySub> choices) {
        List<AbilitySub> counters = new ArrayList<>();
        for (AbilitySub sub : choices) {
            if (sub.getApi() == ApiType.Counter) {
                counters.add(sub);
            }
        }
        if (!counters.isEmpty() && counters.size() < choices.size()) {
            choices.removeAll(counters);
            choices.addAll(0, counters);
        }
    }

    private static List<SpellAbility> counterEtbModes(Card card) {
        List<SpellAbility> out = new ArrayList<>();
        for (Trigger tr : card.getTriggers()) {
            if (tr.getMode() != TriggerType.ChangesZone
                    || !ZoneType.Battlefield.toString().equals(tr.getParam("Destination"))
                    || !tr.hasParam("ValidCard") || !tr.getParam("ValidCard").contains("Self")) {
                continue;
            }
            SpellAbility sa = tr.ensureAbility();
            if (sa == null) {
                continue;
            }
            if (sa.getApi() == ApiType.Counter && sa.usesTargeting()) {
                out.add(sa);
            } else if (sa.getApi() == ApiType.Charm) {
                for (AbilitySub sub : sa.getAdditionalAbilityList("Choices")) {
                    if (sub.getApi() == ApiType.Counter && sub.usesTargeting()) {
                        out.add(sub);
                    }
                }
            }
        }
        return out;
    }
}
