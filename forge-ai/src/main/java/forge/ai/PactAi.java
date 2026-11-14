package forge.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;

import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostParser;
import forge.card.mana.ManaCostShard;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;

/**
 * Pacts (Summoner's Pact, Pact of Negation...): "At the beginning of your next upkeep, pay X. If you don't,
 * you lose the game." The AI may cast one only if its lands can pay, at its next upkeep, every pact
 * still pending plus this one.
 */
public final class PactAi {
    private static final String[] COLORS = { MagicColor.Constant.WHITE, MagicColor.Constant.BLUE,
            MagicColor.Constant.BLACK, MagicColor.Constant.RED, MagicColor.Constant.GREEN };
    private static final ManaCostShard[] SHARDS = { ManaCostShard.WHITE, ManaCostShard.BLUE,
            ManaCostShard.BLACK, ManaCostShard.RED, ManaCostShard.GREEN };

    private PactAi() {
    }

    /** The upkeep cost of a pact spell, or null if sa is not a pact. */
    static ManaCost pactCost(final SpellAbility sa) {
        for (SpellAbility sub = sa; sub != null; sub = sub.getSubAbility()) {
            if (sub.getApi() == ApiType.DelayedTrigger && sub.hasAdditionalAbility("Execute")) {
                final ManaCost cost = loseGameUnlessCost(sub.getAdditionalAbility("Execute"));
                if (cost != null) {
                    return cost;
                }
            }
        }
        return null;
    }

    private static ManaCost loseGameUnlessCost(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.LosesGame || !sa.hasParam("UnlessCost")) {
            return null;
        }
        try {
            return new ManaCost(new ManaCostParser(sa.getParam("UnlessCost")));
        } catch (RuntimeException e) {
            return null; // not a plain mana cost
        }
    }

    public static boolean canPayAllPacts(final Player ai, final SpellAbility sa) {
        final ManaCost cost = pactCost(sa);
        if (cost == null) {
            return true;
        }
        final List<ManaCost> costs = new ArrayList<>();
        costs.add(cost);
        for (final Trigger t : ai.getGame().getTriggerHandler().getDelayedTriggers()) {
            final ManaCost pending = loseGameUnlessCost(t.getOverridingAbility());
            final SpellAbility spawn = t.getSpawningAbility();
            final Player owner = spawn != null ? spawn.getActivatingPlayer() : t.getHostCard().getController();
            if (pending != null && ai.equals(owner)) {
                costs.add(pending);
            }
        }

        // all lands untap before upkeep
        final List<Card> lands = Lists.newArrayList();
        for (final Card land : ai.getLandsInPlay()) {
            if (!land.getManaAbilities().isEmpty()) {
                lands.add(land);
            }
        }
        int total = 0;
        for (final ManaCost c : costs) {
            total += c.getCMC();
        }
        if (total > lands.size()) {
            return false;
        }
        for (int i = 0; i < COLORS.length; i++) {
            int needed = 0;
            for (final ManaCost c : costs) {
                needed += c.getShardCount(SHARDS[i]);
            }
            if (needed == 0) {
                continue;
            }
            int sources = 0;
            for (final Card land : lands) {
                if (land.canProduceColorMana(Set.of(COLORS[i]))) {
                    sources++;
                }
            }
            if (needed > sources) {
                return false;
            }
        }
        return true;
    }
}
