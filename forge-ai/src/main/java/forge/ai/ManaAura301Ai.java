package forge.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;

/**
 * magic-tools: rule 301 (tester 12/11, plateau, D170): the AI tapped a land for mana, then cast Wild Growth on that
 * same land, so the extra mana of the turn was lost. General rule for every aura that adds mana when the enchanted
 * permanent is tapped (Wild Growth, Utopia Sprawl, Overgrowth, Fertile Ground...): it goes on an untapped land rather
 * than a tapped one, and the land it is cast on pays its cost last (another source pays when there is one).
 */
public final class ManaAura301Ai {
    private ManaAura301Ai() { }

    public static boolean isManaBonusAura(final Card source) {
        if (source == null || !source.isAura()) {
            return false;
        }
        for (final Trigger t : source.getTriggers()) {
            if (t.getMode() == TriggerType.TapsForMana && t.getParamOrDefault("ValidCard", "").contains("AttachedBy")) {
                return true;
            }
        }
        return false;
    }

    /** The attach target: an untapped land of ours instead of a tapped one. */
    public static Card retarget(final Player ai, final Card source, final List<Card> candidates, final Card chosen) {
        final Card stacked = stackOnUntapped(ai, source, candidates);
        if (stacked != null) {
            return stacked;
        }
        if (chosen == null || !chosen.isTapped() || !NewAi.on(ai, 301) || !isManaBonusAura(source)) {
            return chosen;
        }
        for (final Card c : candidates) {
            if (c.isLand() && c.isUntapped() && c.getController() == ai) {
                return c;
            }
        }
        return chosen;
    }

    /** Mana auras already on this land. */
    static int bonusAuras(final Card land) {
        int n = 0;
        for (final Card a : land.getEnchantedBy()) {
            if (isManaBonusAura(a)) {
                n++;
            }
        }
        return n;
    }

    /**
     * magic-tools: rule 308 (D171 point 4, tester 12/11: Arbor Elf + two Wild Growth). When we control a creature
     * whose "{T}: untap target land" can target the land, every mana aura on that land pays again on each untap: the
     * aura goes on the land our untapper can target that already carries the most mana auras (untapped first).
     */
    static Card stackOnUntapped(final Player ai, final Card source, final List<Card> candidates) {
        if (candidates == null || !NewAi.on(ai, 308) || !isManaBonusAura(source)) {
            return null;
        }
        final List<SpellAbility> untappers = new ArrayList<>();
        for (final Card c : ai.getCreaturesInPlay()) {
            for (final SpellAbility u : c.getSpellAbilities()) {
                if (UntapManaAi.isLandUntapper(u)) {
                    u.setActivatingPlayer(ai);
                    untappers.add(u);
                }
            }
        }
        if (untappers.isEmpty()) {
            return null;
        }
        Card best = null;
        int bestScore = -1;
        for (final Card land : candidates) {
            if (!land.isLand() || land.getController() != ai) {
                continue;
            }
            boolean reachable = false;
            for (final SpellAbility u : untappers) {
                if (u.canTarget(land)) {
                    reachable = true;
                    break;
                }
            }
            if (!reachable) {
                continue;
            }
            final int score = bonusAuras(land) * 2 + (land.isUntapped() ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = land;
            }
        }
        return best;
    }

    /** The payment: the land the aura is cast on taps last. */
    public static Collection<SpellAbility> payLast(final Player ai, final SpellAbility sa, final Collection<SpellAbility> maList) {
        if (sa == null || maList == null || maList.size() < 2 || sa.getApi() != ApiType.Attach || !NewAi.on(ai, 301)
                || !isManaBonusAura(sa.getHostCard())) {
            return maList;
        }
        final Card target = sa.getTargetCard();
        if (target == null) {
            return maList;
        }
        final List<SpellAbility> first = new ArrayList<>();
        final List<SpellAbility> last = new ArrayList<>();
        for (final SpellAbility ma : maList) {
            (ma.getHostCard() == target ? last : first).add(ma);
        }
        if (last.isEmpty() || first.isEmpty()) {
            return maList;
        }
        first.addAll(last);
        return first;
    }
}
