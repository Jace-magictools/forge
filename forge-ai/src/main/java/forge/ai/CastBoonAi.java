package forge.ai;

import java.util.Map;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.FileSection;

/**
 * magic-tools: rule 90 (a tester report 29/10 12:37, ElfBall - Snowboard, turn 6): Glimpse of Nature ("whenever you cast
 * a creature spell this turn, draw a card") stayed in hand while the AI cast its elves one by one. Forge has no AI
 * logic for an Effect spell without AILogic: it never casts Glimpse at all. General rule: an Effect spell that gives us
 * a "whenever / when you cast a ... spell" trigger (Glimpse, Swiftspear's Teachings, March Toward Perfection, Ride the
 * Avalanche) is cast when the hand holds the spells that feed it and the mana to cast them after it (two for a
 * repeating trigger, one for a one-shot boon), and it goes before them. Triggers marked Static$ True are only the
 * bookkeeping of a flash enabler (Scout's Warning, Savage Summoning): left to Forge.
 */
public final class CastBoonAi {
    private CastBoonAi() { }

    public static boolean worth(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        if (source == null || !sa.isSpell() || sa.getApi() != forge.game.ability.ApiType.Effect
                || !sa.hasParam("Triggers") || sa.hasParam("AILogic") || !source.isInZone(ZoneType.Hand)) {
            return false;
        }
        final int cost = sa.getPayCosts() == null || sa.getPayCosts().getTotalMana() == null
                ? 0 : sa.getPayCosts().getTotalMana().getCMC();
        final int left = ManlandMain2Ai.untappedMana(ai) + ai.getManaPool().totalMana() - cost;
        for (final String name : sa.getParam("Triggers").split(",")) {
            final String svar = sa.getSVar(name.trim());
            if (svar == null || svar.isEmpty()) {
                continue;
            }
            final Map<String, String> p = FileSection.parseToMap(svar, FileSection.DOLLAR_SIGN_KV_SEPARATOR);
            final String mode = p.get("Mode");
            if (!("SpellCast".equals(mode) || "SpellCastOrCopy".equals(mode)) || !"You".equals(p.get("ValidActivatingPlayer"))
                    || "True".equals(p.get("Static")) || !p.containsKey("ValidCard")) {
                continue;
            }
            final boolean once = "True".equals(p.get("OneOff")) || "True".equals(sa.getParam("Boon"));
            final String[] valid = p.get("ValidCard").split(",");
            int fuel = 0;
            int spend = 0;
            final java.util.List<Card> hand = new java.util.ArrayList<>(ai.getCardsIn(ZoneType.Hand));
            hand.sort(java.util.Comparator.comparingInt(Card::getCMC));
            for (final Card c : hand) {
                if (c.equals(source) || c.isLand() || !c.isValid(valid, ai, source, null)) {
                    continue;
                }
                if (spend + c.getCMC() <= left) {
                    spend += c.getCMC();
                    fuel++;
                }
            }
            if (fuel >= (once ? 1 : 2)) {
                return true;
            }
        }
        return false;
    }
}
