package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 247 (D140, tester 08/11, a test game T2): the AI cast Marauding Raptor, then
 * Belligerent Yearling (3/2), which the Raptor's "whenever another creature you control enters, deal 2 damage to it"
 * killed on the spot. General rule, for any permanent of ours whose arrival trigger deals damage to the card that
 * enters:
 * 1. a creature that would die from the sum of those triggers (toughness after statics, not indestructible) is not
 *    cast, unless it gets something out of it (a trigger of its own: ETB, dies, enrage);
 * 2. such a permanent is not a payoff for the creatures it would kill (rule 103 made the Raptor go first), and it
 *    waits in hand while a creature it would kill can be cast now.
 */
public final class HarmfulArrival247 {
    private HarmfulArrival247() { }

    /** damage the arrival triggers of {@code host} deal to {@code card} entering under our control (0 if none) */
    public static int damageFrom(final Player ai, final Card host, final Card card) {
        if (host == card) {
            return 0;
        }
        int dmg = 0;
        for (final Trigger t : host.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))) {
                continue;
            }
            final String zones = t.getParam("TriggerZones");
            if (zones == null || !zones.contains("Battlefield") || t.hasParam("OptionalDecider")) {
                continue;
            }
            final String valid = t.getParam("ValidCard");
            if (valid == null || valid.startsWith("Card.Self") || !card.isValid(valid.split(","), ai, host, t)) {
                continue;
            }
            final SpellAbility trigSa = t.ensureAbility();
            if (trigSa == null || trigSa.getApi() != ApiType.DealDamage || !trigSa.hasParam("NumDmg")) {
                continue;
            }
            final String def = trigSa.getParam("Defined");
            if (def == null || !(def.startsWith("TriggeredCard") || def.startsWith("TriggeredNewCard"))
                    || def.startsWith("TriggeredCardController")) {
                continue;
            }
            dmg += AbilityUtils.calculateAmount(host, trigSa.getParam("NumDmg"), trigSa);
        }
        return dmg;
    }

    /** the card gets something out of being dealt damage or dying (a trigger of its own) */
    static boolean hasOwnValue(final Card card) {
        if (card.hasETBTrigger(false) || card.hasSVar("SacMe")) {
            return true;
        }
        for (final Trigger t : card.getTriggers()) {
            final String valid = t.hasParam("ValidCard") ? t.getParam("ValidCard") : t.getParam("ValidTarget");
            if (valid != null && valid.startsWith("Card.Self")) {
                return true;
            }
        }
        return false;
    }

    /** the creature would die on entry from the damage of {@code extraHost} (may be null) and our permanents */
    public static boolean diesOnEntry(final Player ai, final Card card, final Card extraHost) {
        if (!card.isCreature()) {
            return false;
        }
        int dmg = 0;
        Card source = null;
        for (final Card host : ai.getCardsIn(ZoneType.Battlefield)) {
            final int d = damageFrom(ai, host, card);
            if (d > 0) {
                dmg += d;
                source = host;
            }
        }
        if (extraHost != null) {
            final int d = damageFrom(ai, extraHost, card);
            if (d > 0) {
                dmg += d;
                source = extraHost;
            }
        }
        if (dmg <= 0) {
            return false;
        }
        final Card copy = CardCopyService.getLKICopy(card);
        ComputerUtilCard.applyStaticContPT(ai.getGame(), copy, null);
        if (copy.hasKeyword(Keyword.INDESTRUCTIBLE)) {
            return false;
        }
        dmg = ComputerUtilCombat.predictDamageTo(copy, dmg, source, false);
        return dmg > 0 && dmg >= copy.getNetToughness();
    }

    /** rule 247.1: don't cast a creature our own permanents would kill as it enters */
    public static boolean refuse(final Player ai, final SpellAbility sa) {
        final Card card = sa.getHostCard();
        return sa.isSpell() && card.isCreature() && !hasOwnValue(card) && diesOnEntry(ai, card, null);
    }

    /** rule 247.2: the harmful permanent waits while a creature it would kill can be cast now */
    public static boolean holdForVictim(final Player ai, final SpellAbility sa) {
        final Card host = sa.getHostCard();
        if (!sa.isSpell() || !host.isPermanent()) {
            return false;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c == host || !c.isCreature() || hasOwnValue(c) || damageFrom(ai, host, c) <= 0) {
                continue;
            }
            if (!diesOnEntry(ai, c, host) || diesOnEntry(ai, c, null)) {
                continue;
            }
            for (final SpellAbility csa : c.getSpells()) {
                csa.setActivatingPlayer(ai);
                if (csa.isSpell() && csa.canPlay() && ComputerUtilMana.canPayManaCost(csa, ai, 0, false)) {
                    return true;
                }
            }
        }
        return false;
    }
}
