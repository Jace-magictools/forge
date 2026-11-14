package forge.ai;

import java.util.List;

import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 107 (a tester report 30/10 10:13, Ork - Profanation turn 1): Profanation cast Vampiric Tutor in its own
 * second main phase. General rule: an instant-speed search that puts the card on top of our library (Vampiric,
 * Mystical, Worldly, Enlightened Tutor) gives nothing before our next draw, so it waits for the opponent's end step:
 * the opponent sees nothing during its turn and the mana stays open for an answer.
 */
public final class TutorTopAi {
    private TutorTopAi() { }

    static boolean searchesToTop(final SpellAbility sa, final List<ZoneType> origin, final String destination) {
        return "Library".equals(destination) && origin != null && origin.contains(ZoneType.Library)
                && "0".equals(sa.getParamOrDefault("LibraryPosition", ""));
    }

    /** true when the tutor should not be used now (not yet the end step before our turn) */
    public static boolean waitsForOpponentEnd(final Player ai, final SpellAbility sa, final List<ZoneType> origin,
            final String destination) {
        if (sa.isTrigger() || !searchesToTop(sa, origin, destination) || SpellAbilityAi.isSorcerySpeed(sa, ai)) {
            return false;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        return !(ph.getNextTurn().equals(ai) && ph.is(PhaseType.END_OF_TURN));
    }
}
