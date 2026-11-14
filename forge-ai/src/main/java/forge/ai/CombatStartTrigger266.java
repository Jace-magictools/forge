package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 266 (tester 09/11, a test game turn 9 and a test game turn 8): Surrak,
 * the Hunt Caller was kept for main 2 ; cast before combat, its formidable trigger gives it haste and it attacks.
 * A creature with an "at the beginning of combat on your turn" trigger is cast in main 1: in main 2 the trigger of
 * this turn is lost.
 */
public final class CombatStartTrigger266 {
    private CombatStartTrigger266() { }

    public static boolean castNow(final Player ai, final SpellAbility sa) {
        if (!NewAi.on(ai, 266) || sa.getApi() != ApiType.PermanentCreature || sa.getHostCard() == null
                || !sa.getHostCard().isInZone(ZoneType.Hand)) {
            return false;
        }
        if (!ai.getGame().getPhaseHandler().is(PhaseType.MAIN1, ai)) {
            return false;
        }
        return hasOwnCombatStartTrigger(sa.getHostCard());
    }

    static boolean hasOwnCombatStartTrigger(final Card c) {
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() != TriggerType.Phase || !t.hasParam("Phase") || !t.getParam("Phase").contains("BeginCombat")) {
                continue;
            }
            final String who = t.hasParam("ValidPlayer") ? t.getParam("ValidPlayer") : "You";
            final String zones = t.hasParam("TriggerZones") ? t.getParam("TriggerZones") : "Battlefield";
            if (who.startsWith("You") && zones.contains("Battlefield")) {
                return true;
            }
        }
        return false;
    }

    /**
     * rule 266: for a pump that only grants haste on our turn before attackers, whether {@code c} is a target worth it
     * (a creature with summoning sickness, untapped, with power) ; null when the rule does not apply.
     */
    public static Boolean hasteTarget(final Player ai, final Card c, final int toughness, final int power,
            final java.util.List<String> keywords) {
        if (!NewAi.on(ai, 266) || keywords == null || keywords.isEmpty() || power > 0 || toughness > 0) {
            return null;
        }
        for (final String k : keywords) {
            if (!"Haste".equals(k)) {
                return null;
            }
        }
        final forge.game.phase.PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || ph.getPhase().isAfter(PhaseType.COMBAT_BEGIN)) {
            return null;
        }
        return c.isSick() && !c.isTapped() && c.getNetCombatDamage() > 0;
    }
}
