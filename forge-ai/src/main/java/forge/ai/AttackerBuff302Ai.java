package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 302 (tester 12/11, plateau, D170): the AI attacked with a creature that gets a +1/+1 counter
 * whenever another creature of ours enters, then cast that creature in main 2: the attacker missed its counter.
 * Forge only knows it from the BuffedBy hint, missing on many cards. General rule, read on the scripts: a permanent
 * spell is cast in main 1 when one of our creatures about to attack gets bigger from it, either by its own "another
 * creature enters" trigger (+1/+1 counters or pump on itself), or by a static pump of the spell (lord, anthem).
 */
public final class AttackerBuff302Ai {
    private AttackerBuff302Ai() { }

    public static boolean castBeforeAttack(final Player ai, final SpellAbility sa) {
        final Card card = sa.getHostCard();
        if (card == null || !sa.isSpell() || !card.isPermanent() || !NewAi.on(ai, 302)) {
            return false;
        }
        for (final Card c : ai.getCreaturesInPlay()) {
            if (c == card || c.isTapped() || c.isSick()) {
                continue;
            }
            if (!triggerBuff(ai, c, card) && !staticBuff(ai, c, card)) {
                continue;
            }
            if (ComputerUtilCard.doesCreatureAttackAI(ai, c)) {
                return true;
            }
        }
        return false;
    }

    // c grows from its own "another creature/permanent enters" trigger when card enters
    static boolean triggerBuff(final Player ai, final Card c, final Card card) {
        for (final Trigger t : c.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !t.getParamOrDefault("Destination", "").contains("Battlefield")
                    || !t.getParamOrDefault("TriggerZones", "Battlefield").contains("Battlefield")
                    || t.hasParam("CheckSVar") || t.hasParam("IsPresent")) {
                continue;
            }
            final String valid = t.getParamOrDefault("ValidCard", "");
            if (valid.isEmpty() || valid.startsWith("Card.Self")) {
                continue;
            }
            if (!card.isValid(valid.split(","), ai, c, null)) {
                continue;
            }
            final SpellAbility eff = t.ensureAbility();
            if (eff == null) {
                continue;
            }
            final String defined = eff.getParamOrDefault("Defined", "");
            if (!"Self".equals(defined)) {
                continue;
            }
            if (eff.getApi() == ApiType.PutCounter && "P1P1".equals(eff.getParamOrDefault("CounterType", ""))) {
                return true;
            }
            if (eff.getApi() == ApiType.Pump && !eff.getParamOrDefault("NumAtt", "0").startsWith("-")
                    && !"0".equals(eff.getParamOrDefault("NumAtt", "0"))) {
                return true;
            }
        }
        return false;
    }

    // card's static ability raises c's power (lord, anthem)
    static boolean staticBuff(final Player ai, final Card c, final Card card) {
        if (!card.isInZone(ZoneType.Hand)) {
            return false;
        }
        for (final StaticAbility st : card.getStaticAbilities()) {
            if (!st.getMode().contains(StaticAbilityMode.Continuous) || !st.hasParam("Affected")
                    || st.hasParam("CheckSVar") || st.hasParam("IsPresent") || st.hasParam("Condition")) {
                continue;
            }
            final String affected = st.getParam("Affected");
            if (!affected.contains("YouCtrl") || !affected.startsWith("Creature")) {
                continue;
            }
            final String pow = st.getParamOrDefault("AddPower", "0");
            int p;
            try {
                p = Integer.parseInt(pow.trim());
            } catch (NumberFormatException e) {
                p = 0;
            }
            if (p > 0 && c.isValid(affected.split(","), ai, card, null)) {
                return true;
            }
        }
        return false;
    }
}
