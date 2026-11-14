package forge.ai;

import java.util.EnumSet;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 114 (a tester report 30/10 19:03, Gobelin - Nothing Gulch, turn 5). The AI always paid echo when it
 * could: {1}{R} for Mogg War Marshal (a 1/1 that makes a Goblin when it dies) took all its mana, Goblin Bombardment
 * stayed in hand.
 * General rule: the AI does not pay the echo of a permanent that gives something when it dies (token, card, damage,
 * life, return) if paying would keep it from casting a spell of its hand it can cast now. Letting it go gives the
 * death value now and keeps the mana for the spell.
 */
public final class EchoPayAi {
    private EchoPayAi() { }

    private static final EnumSet<ApiType> DEATH_VALUE = EnumSet.of(ApiType.Token, ApiType.Draw, ApiType.DealDamage,
            ApiType.GainLife, ApiType.LoseLife, ApiType.ChangeZone, ApiType.Dig, ApiType.PutCounter);

    /** false = do not pay this echo. */
    public static boolean shouldPay(final Player ai, final SpellAbility sa, final Cost cost) {
        final Card host = sa.getHostCard();
        if (host == null || !sa.hasParam("Echo") || !ai.equals(host.getController()) || !host.isInPlay()) {
            return true;
        }
        if (!host.hasSVar("SacMe") && !hasDeathValue(host)) {
            return true;
        }
        if (cost.getTotalMana() == null) {
            return true;
        }
        final int echo = cost.getTotalMana().getCMC();
        final int avail = ComputerUtilMana.getAvailableManaEstimate(ai, false);
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.isLand()) {
                continue;
            }
            final int cmc = c.getCMC();
            if (cmc <= 0 || cmc > avail || cmc <= avail - echo) {
                continue;
            }
            for (final SpellAbility spell : c.getSpells()) {
                spell.setActivatingPlayer(ai);
                if (ComputerUtilCost.canPayCost(spell, ai, false)) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean hasDeathValue(final Card host) {
        for (final Trigger t : host.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone) {
                continue;
            }
            final String origin = t.getParam("Origin");
            if (!"Graveyard".equals(t.getParam("Destination")) || origin == null
                    || !(origin.contains("Battlefield") || "Any".equals(origin))) {
                continue;
            }
            final String valid = t.getParam("ValidCard");
            if (valid == null || !valid.startsWith("Card.Self")) {
                continue;
            }
            final SpellAbility exec = t.ensureAbility();
            if (exec != null && exec.getApi() != null && DEATH_VALUE.contains(exec.getApi())) {
                return true;
            }
        }
        return false;
    }
}
