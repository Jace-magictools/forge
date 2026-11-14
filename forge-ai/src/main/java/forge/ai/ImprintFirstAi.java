package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 96, Patrilock - Vampire (30/10, forge-v109): Isochron Scepter cast on turn 5 with no instant
 * in hand, an empty Scepter for the rest of the game. A permanent that imprints a card from our hand when it
 * enters (script: ChangesZone trigger on itself, ChangeZone Hand to Exile with Imprint) and whose every ability
 * works on the imprinted card (Isochron Scepter, Chrome Mox, Prototype Portal, Semblance Anvil, Soul Foundry)
 * waits in hand until the hand holds a card it can imprint.
 */
public final class ImprintFirstAi {
    private ImprintFirstAi() { }

    /** The imprint type (ChangeType of the enter trigger), or null when the card is not such a permanent. */
    static String imprintType(final Card host) {
        String type = null;
        for (final Trigger t : host.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))
                    || !"Card.Self".equals(t.getParam("ValidCard")) || !t.hasParam("Execute")) {
                continue;
            }
            final String exec = host.getSVar(t.getParam("Execute"));
            if (exec.contains("Imprint$ True") && exec.contains("Origin$ Hand") && exec.contains("Destination$ Exile")) {
                final java.util.regex.Matcher m = java.util.regex.Pattern.compile("ChangeType\\$ ([^|]+)").matcher(exec);
                type = m.find() ? m.group(1).trim() : "Card";
            }
        }
        if (type == null) {
            return null;
        }
        boolean any = false;
        for (final SpellAbility a : host.getSpellAbilities()) {
            if (a.isSpell()) {
                continue;
            }
            if (!a.getMapParams().toString().contains("Imprinted")) {
                return null;
            }
            any = true;
        }
        for (final StaticAbility s : host.getStaticAbilities()) {
            if (!s.getMapParams().toString().contains("Imprinted")) {
                return null;
            }
            any = true;
        }
        return any ? type : null;
    }

    /**
     * magic-tools: rule 230, Patrilock (6/11, tournament v24): 65 turns ended with a Scepter and an instant to imprint
     * in hand and no Scepter in play. Stock AI waits for main 2 and casts creatures first, so all the mana is gone
     * (CantAfford in main 2, on 41 of 66 decisions traced). What the permanent repeats is worth more than one body:
     * with something to imprint, it comes down first, in main 1.
     */
    public static boolean goesFirst(final Player ai, final SpellAbility sa) {
        return sa.isSpell() && imprintType(sa.getHostCard()) != null && worthCasting(ai, sa.getHostCard());
    }

    /** False when the permanent would enter with nothing to imprint. */
    public static boolean worthCasting(final Player ai, final Card host) {
        final String type = imprintType(host);
        if (type == null) {
            return true;
        }
        final CardCollectionView hand = CardLists.filter(ai.getCardsIn(ZoneType.Hand), c -> !c.equals(host));
        return !CardLists.getValidCards(hand, type.split(","), ai, host, null).isEmpty();
    }
}
