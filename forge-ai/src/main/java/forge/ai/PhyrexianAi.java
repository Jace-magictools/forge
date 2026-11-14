package forge.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Multimap;

import forge.card.mana.ManaCostShard;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: pay Phyrexian shards with life first when the mana sources can't cover the whole cost.
 * The stock payment loop pays {G/P} with a land (colored shards first), then lacks one generic mana:
 * Birthing Pod ({3}{G/P}) stayed in hand with three Forests.
 */
public final class PhyrexianAi {
    private PhyrexianAi() { }

    /** Number of Phyrexian shards to pay with 2 life each before tapping sources (0 if the mana is enough). */
    public static int shardsToPayWithLife(final Player ai, final SpellAbility sa, final ManaCostBeingPaid cost,
            final Multimap<ManaCostShard, SpellAbility> sourcesForShards, final int lifeAlreadyCounted) {
        if (sourcesForShards == null || "Never".equals(sa.getParam("AIPhyrexianPayment"))) {
            return 0;
        }
        int phyrexian = 0;
        for (final ManaCostShard shard : cost.getUnpaidShards()) {
            if (shard.isPhyrexian()) {
                phyrexian++;
            }
        }
        if (phyrexian == 0) {
            return 0;
        }
        final Set<Card> sources = new HashSet<>();
        for (final SpellAbility source : sourcesForShards.values()) {
            sources.add(source.getHostCard());
        }
        int missing = cost.getConvertedManaCost() - sources.size();
        int n = 0;
        while (missing > 0 && n < phyrexian) {
            final int life = lifeAlreadyCounted + 2 * (n + 1);
            if (!ai.canPayLife(life, false, sa) || (ai.getLife() <= life && !ai.cantLoseForZeroOrLessLife())) {
                break;
            }
            n++;
            missing--;
        }
        return missing > 0 ? 0 : n;
    }

    /** Life floor kept when paying Phyrexian shards only to save mana (rule 79). */
    static final int SPARE_LIFE_FLOOR = 10;

    /**
     * magic-tools rule 79 (tester 29/10 12:16, Vault Skirge): in our own main phase, pay Phyrexian shards with life when
     * the mana saved lets us cast one more spell from hand or pay one more equip this turn (Dark Ritual, Vault Skirge
     * paid with BB, then Bonesplitter cast but never equipped). Keeps at least {@link #SPARE_LIFE_FLOOR} life.
     */
    public static int shardsToPayForSpareMana(final Player ai, final SpellAbility sa, final ManaCostBeingPaid cost,
            final Multimap<ManaCostShard, SpellAbility> sourcesForShards, final int floating) {
        if ("Never".equals(sa.getParam("AIPhyrexianPayment")) || !sa.isSpell()) {
            return 0;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain()) {
            return 0;
        }
        int phyrexian = 0;
        for (final ManaCostShard shard : cost.getUnpaidShards()) {
            if (shard.isPhyrexian()) {
                phyrexian++;
            }
        }
        if (phyrexian == 0) {
            return 0;
        }
        final Set<Card> sources = new HashSet<>();
        if (sourcesForShards != null) {
            for (final SpellAbility source : sourcesForShards.values()) {
                sources.add(source.getHostCard());
            }
        }
        final int spare = floating + sources.size() - cost.getConvertedManaCost();
        if (spare < 0) {
            return 0; // not enough mana: shardsToPayWithLife decides
        }
        final List<Integer> demand = otherManaDemand(ai, sa.getHostCard());
        final int base = fit(demand, spare);
        for (int n = 1; n <= phyrexian; n++) {
            final int lifeAfter = ai.getLife() - 2 * n;
            if (!ai.canPayLife(2 * n, false, sa) || lifeAfter < SPARE_LIFE_FLOOR) {
                return 0;
            }
            if (fit(demand, spare + n) > base) {
                return n;
            }
        }
        return 0;
    }

    /** Mana costs of what else we could spend mana on this turn: other spells in hand, equip abilities. */
    static List<Integer> otherManaDemand(final Player ai, final Card host) {
        final List<Integer> demand = new ArrayList<>();
        final boolean haveCreature = host.isCreature() || !ai.getCreaturesInPlay().isEmpty();
        for (final Card c : ai.getCardsIn(ZoneType.Hand)) {
            if (c == host || c.isLand() || c.getManaCost().countX() > 0) {
                continue;
            }
            final int cmc = c.getManaCost().getCMC();
            if (cmc > 0) {
                demand.add(cmc);
            }
            addEquip(c, haveCreature, demand);
        }
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (!c.isEquipping()) {
                addEquip(c, haveCreature, demand);
            }
        }
        Collections.sort(demand);
        return demand;
    }

    private static void addEquip(final Card c, final boolean haveCreature, final List<Integer> demand) {
        if (!haveCreature || !c.isEquipment()) {
            return;
        }
        int best = -1;
        for (final SpellAbility eq : c.getSpellAbilities()) {
            if (eq.isEquip() && eq.getPayCosts() != null && eq.getPayCosts().isOnlyManaCost()) {
                final int cmc = eq.getPayCosts().getTotalMana().getCMC();
                best = best < 0 ? cmc : Math.min(best, cmc);
            }
        }
        if (best > 0) {
            demand.add(best);
        }
    }

    /** How many of the (sorted) costs fit in the given mana. */
    static int fit(final List<Integer> sortedCosts, final int mana) {
        int left = mana;
        int n = 0;
        for (final int c : sortedCosts) {
            if (c > left) {
                break;
            }
            left -= c;
            n++;
        }
        return n;
    }
}
