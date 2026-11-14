package forge.ai;

import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: stock AI keeps creatures without haste for main 2, but a spells deck (Sorcier) spends its
 * mana on burn during combat: Balmor, Battlemage Captain was refused in main 1 then unaffordable in main 2
 * 19 times in 10 games. A creature that triggers on our own instants and sorceries (Prowess, "whenever you
 * cast an instant or sorcery spell") is cast in main 1 when we hold one, so the spells cast later pump it.
 */
public final class SpellCastMain1Ai {
    private SpellCastMain1Ai() { }

    public static boolean castBeforeSpells(final Player ai, final SpellAbility sa) {
        final Card card = sa.getHostCard();
        if (card == null || !card.isCreature() || card.hasKeyword(Keyword.DEFENDER) || !triggersOnOwnSpells(card)) {
            return false;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c != card && (c.isInstant() || c.isSorcery())) {
                return true;
            }
        }
        return false;
    }

    static boolean triggersOnOwnSpells(final Card card) {
        if (card.hasKeyword(Keyword.PROWESS)) {
            return true;
        }
        for (final Trigger t : card.getTriggers()) {
            if (t.getMode() != TriggerType.SpellCast) {
                continue;
            }
            final String valid = t.getParamOrDefault("ValidCard", "");
            final String who = t.getParamOrDefault("ValidActivatingPlayer", "");
            final String zones = t.getParamOrDefault("TriggerZones", "Battlefield");
            if ((valid.contains("Instant") || valid.contains("Sorcery")) && "You".equals(who)
                    && zones.contains("Battlefield")) {
                return true;
            }
        }
        return false;
    }
}
