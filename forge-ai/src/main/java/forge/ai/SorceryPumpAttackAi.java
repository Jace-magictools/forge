package forge.ai;

import java.util.List;

import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 180. A sorcery-speed pump (Assault Strobe) cannot wait for blockers: the only moment to use it
 * is before attacks, on our turn. When it turns a creature that stays home into a creature that attacks, Forge cast
 * it on a coin flip weighted by damage / life (2/17 for Assault Strobe on a 1/2), and it also ignored what the target
 * gains from the cast itself (Kiln Fiend +3/+0, prowess). Poudlar held Assault Strobe for whole games with Kiln Fiend
 * in play facing a Kor Blademaster. Count the target's own cast triggers, and cast when the pumped creature attacks
 * and would deal a real share of the opponent's life if unblocked.
 */
public final class SorceryPumpAttackAi {
    private SorceryPumpAttackAi() { }

    /** extra power / toughness the target gets from its own cast triggers when this spell is cast from hand */
    public static int[] castTriggerBonus(final Player ai, final SpellAbility sa, final Card c) {
        final Card host = sa.getHostCard();
        int att = 0;
        int def = 0;
        if (host == null || !sa.isSpell() || sa.isCopied() || sa.isTrigger() || !host.isInZone(ZoneType.Hand)
                || !c.getController().equals(ai)) {
            return new int[] {0, 0};
        }
        if (!host.isCreature()) {
            final int prowess = c.getAmountOfKeyword(Keyword.PROWESS);
            att += prowess;
            def += prowess;
        }
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() != TriggerType.SpellCast && t.getMode() != TriggerType.SpellCastOrCopy) {
                continue;
            }
            final String who = t.getParam("ValidActivatingPlayer");
            if (who != null && !"You".equals(who)) {
                continue;
            }
            final String valid = t.getParam("ValidCard");
            if (valid == null || !host.isValid(valid.split(","), ai, c, t)) {
                continue;
            }
            final String exec = t.getParam("Execute");
            final String body = exec == null ? null : c.getSVar(exec);
            if (body == null || !body.contains("DB$ Pump") || !body.contains("Defined$ Self")) {
                continue;
            }
            att += readInt(body, "NumAtt$");
            def += readInt(body, "NumDef$");
        }
        return new int[] {att, def};
    }

    private static int readInt(final String body, final String key) {
        final int i = body.indexOf(key);
        if (i < 0) {
            return 0;
        }
        String v = body.substring(i + key.length()).trim();
        final int bar = v.indexOf('|');
        if (bar >= 0) {
            v = v.substring(0, bar);
        }
        v = v.trim().replace("+", "");
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** true: cast the sorcery-speed pump now, the pumped creature attacks and hits hard enough */
    public static boolean castNow(final Player ai, final SpellAbility sa, final Card c, final int toughness,
            final int power, final List<String> keywords, final Player opp, final Combat combat) {
        if (!SpellAbilityAi.isSorcerySpeed(sa, ai) || c.hasSickness() && !c.hasKeyword(Keyword.HASTE)
                && (keywords == null || !keywords.contains("Haste")) || c.isTapped()) {
            return false;
        }
        // rule 183: a bare attack made only because it is free (a wall stops it) is not a real attack
        if (ComputerUtilCard.doesCreatureAttackAI(ai, c) && !FreeAttackAi.stoppedForFree(ai, c, opp, combat)) {
            return false; // handled by the existing checks (rule 57 for keyword-only pumps)
        }
        final int[] bonus = castTriggerBonus(ai, sa, c);
        final Card pumped = ComputerUtilCard.getPumpedCreature(ai, sa, c, toughness + bonus[1], power + bonus[0], keywords);
        if (!ComputerUtilCard.doesSpecifiedCreatureAttackAI(ai, pumped) || FreeAttackAi.stoppedForFree(ai, pumped, opp, combat)) {
            return false;
        }
        final int dmg = ComputerUtilCombat.damageIfUnblocked(pumped, opp, combat, true);
        return dmg >= 4 || dmg * 4 >= opp.getLife();
    }
}
