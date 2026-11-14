package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 202, draw before attacking. Stock AI keeps its spells for main 2, so it attacks first and only then
 * looks at the cards it could have had (a tester reports 05/11, ElfBall, a test game: Fyndhorn Elves attacked and
 * Lead the Stampede stayed in hand for lack of the third mana; Gilt-Leaf Archdruid attacked, Leaf-Crowned Visionary and
 * Beast Whisperer waited, "you have mana and a card-drawer, a lot can happen"). What finds cards goes in main 1: a
 * sorcery-speed draw or dig spell, a permanent that draws when it enters, a spell that a card-drawing engine of ours
 * counts, an engine with a card in hand it would count. Lethal on the table still attacks first (rule 52).
 */
public final class DrawFirstAi {
    private DrawFirstAi() { }

    private static boolean findsCards(final SpellAbility sa) {
        return sa != null && (sa.getApi() == ApiType.Draw || sa.getApi() == ApiType.Dig);
    }

    /** the spell itself draws or digs, at sorcery speed (an instant keeps its own timing) */
    public static boolean spellFindsCards(final Player ai, final SpellAbility sa) {
        return sa.isSpell() && findsCards(sa) && SpellAbilityAi.isSorcerySpeed(sa, ai);
    }

    /** the permanent draws when it enters, an engine of ours draws for it, or it is an engine with fuel in hand */
    public static boolean permanentFindsCards(final Player ai, final SpellAbility sa) {
        final Card card = sa.getHostCard();
        if (card == null || card.isLand()) {
            return false;
        }
        for (final Trigger t : card.getTriggers()) {
            if (t.getMode() == TriggerType.ChangesZone && "Battlefield".equals(t.getParam("Destination"))
                    && "Card.Self".equals(t.getParam("ValidCard")) && findsCards(t.ensureAbility())) {
                return true;
            }
        }
        for (final Card engine : ai.getCardsIn(ZoneType.Battlefield)) {
            if (engineCounts(ai, engine, card)) {
                return true;
            }
        }
        for (final Card fuel : ai.getCardsIn(ZoneType.Hand)) {
            if (fuel != card && engineCounts(ai, card, fuel)) {
                return true;
            }
        }
        return false;
    }

    /** {@code engine} has a "whenever you cast ... , draw" trigger that {@code spell} would set off */
    static boolean engineCounts(final Player ai, final Card engine, final Card spell) {
        for (final Trigger t : engine.getTriggers()) {
            if (t.getMode() != TriggerType.SpellCast || !findsCards(t.ensureAbility())) {
                continue;
            }
            final String who = t.getParam("ValidActivatingPlayer");
            if (who != null && !"You".equals(who)) {
                continue;
            }
            final String valid = t.getParam("ValidCard");
            if (valid != null && spell.isValid(valid.split(","), ai, engine, t)) {
                return true;
            }
        }
        return false;
    }
}
