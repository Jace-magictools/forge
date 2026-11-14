package forge.ai;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 267 (tester 09/11 12:21, a test game turn 5): with Mesmeric Orb and Maddening
 * Cacophony in hand (both cost 2), the AI cast the sorcery and kept the Orb for two turns. The Orb mills every turn,
 * the sorcery mills once and loses nothing by waiting. General rule: a permanent in hand whose battlefield trigger
 * repeats an effect (same API: mill, damage, draw...) that an instant or sorcery in our hand does once goes before
 * that spell, in main 1 (a noncreature permanent would otherwise wait for main 2, after the spell took the mana).
 * An enters-the-battlefield trigger of the permanent itself does not count (it acts once too).
 */
public final class RecurringFirst267 {
    private RecurringFirst267() { }

    private static final Pattern API = Pattern.compile("^(?:SP|DB|AB)\\$ ?(\\w+)");

    public static int priority(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        if (!NewAi.on(ai, 267) || !sa.isSpell() || source == null || !source.isPermanent()
                || !source.isInZone(ZoneType.Hand)) {
            return 0;
        }
        final Set<String> repeated = recurringApis(source);
        if (repeated.isEmpty()) {
            return 0;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c.equals(source) || !(c.isInstant() || c.isSorcery())) {
                continue;
            }
            for (final String api : apis(c)) {
                if (repeated.contains(api)) {
                    return 1;
                }
            }
        }
        return 0;
    }

    static Set<String> recurringApis(final Card c) {
        final Set<String> out = new HashSet<>();
        for (final Trigger t : c.getTriggers()) {
            if (!"Battlefield".equals(t.getParam("TriggerZones")) || !t.hasParam("Execute")) {
                continue;
            }
            if (t.getMode() == TriggerType.ChangesZone && "Self".equals(t.getParam("ValidCard"))) {
                continue;
            }
            String name = t.getParam("Execute");
            int depth = 0;
            while (name != null && depth++ < 10) {
                final String svar = c.getSVar(name);
                name = null;
                if (svar == null) {
                    break;
                }
                final Matcher m = API.matcher(svar);
                if (m.find()) {
                    out.add(m.group(1));
                }
                for (final String part : svar.split("\\|")) {
                    final String kv = part.trim();
                    if (kv.startsWith("SubAbility$")) {
                        name = kv.substring("SubAbility$".length()).trim();
                    }
                }
            }
        }
        out.remove("Pump");
        out.remove("PutCounter");
        out.remove("GainLife");
        return out;
    }

    // every API named by the spell or its SVars (RepeatEach and sub-abilities included)
    static Set<String> apis(final Card c) {
        final Set<String> out = new HashSet<>();
        for (final SpellAbility s : c.getSpellAbilities()) {
            if (s.getApi() != null) {
                out.add(s.getApi().name());
            }
        }
        for (final Map.Entry<String, String> e : c.getSVars().entrySet()) {
            final Matcher m = API.matcher(e.getValue());
            if (m.find()) {
                out.add(m.group(1));
            }
        }
        return out;
    }
}
