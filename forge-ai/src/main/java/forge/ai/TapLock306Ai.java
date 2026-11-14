package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 306 (D174, tester 12/11, plateau, a test game turn 12): Dungeon Geists (tap target
 * creature, it doesn't untap while you control the Geists) locked Frédégar Bolger, a 0/2 whose only ability needs no
 * tap, instead of Sam Gamegie (vigilance, grows with each life gain: the best attacker and blocker). When no blocker
 * of our attack can be tapped, Forge falls back on the most expensive permanent: four two-drops, a tie, the first one
 * won. General rule for every "tap target creature, it doesn't untap" (Dungeon Geists, Frost Lynx, Ice Cave…): the lock
 * takes the creature that weighs most (Forge's creature value, plus its abilities that need {T}, which the lock shuts
 * down too), skipping creatures already locked.
 */
public final class TapLock306Ai {
    private TapLock306Ai() { }

    /** the tap is followed by a "doesn't untap" effect on the same target */
    public static boolean isLock(final SpellAbility sa) {
        for (AbilitySub sub = sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.getApi() == ApiType.Effect && sub.hasParam("ReplacementEffects")) {
                for (final String name : sub.getParam("ReplacementEffects").split(",")) {
                    final String re = sub.getHostCard().getSVar(name.trim());
                    if (re.contains("Event$ Untap")) {
                        return true;
                    }
                }
            }
            if (sub.getApi() == ApiType.Pump && sub.hasParam("KW") && sub.getParam("KW").contains("untap")) {
                return true;
            }
        }
        return false;
    }

    static int weight(final Card c) {
        int value = ComputerUtilCard.evaluateCreature(c);
        for (final SpellAbility ab : c.getSpellAbilities()) {
            if (ab.isAbility() && !ab.isManaAbility() && ab.getPayCosts() != null && ab.getPayCosts().hasTapCost()) {
                value += 40;
            }
        }
        if (c.hasKeyword(Keyword.VIGILANCE)) {
            value += 10; // a vigilant creature both attacks and blocks: tapped, it does neither
        }
        return value;
    }

    /** the creature to lock among the targets, or null when the rule does not apply */
    public static Card choose(final Player ai, final SpellAbility sa, final CardCollection tapList) {
        if (!NewAi.on(ai, 306) || !isLock(sa)) {
            return null;
        }
        CardCollection creatures = CardLists.filter(tapList, CardPredicates.CREATURES);
        final CardCollection free = CardLists.filter(creatures, c -> c.canUntap(c.getController(), true));
        if (!free.isEmpty()) {
            creatures = free;
        }
        if (creatures.isEmpty()) {
            return null;
        }
        Card best = null;
        int bestValue = Integer.MIN_VALUE;
        for (final Card c : creatures) {
            final int v = weight(c);
            if (v > bestValue) {
                bestValue = v;
                best = c;
            }
        }
        return best;
    }
}
