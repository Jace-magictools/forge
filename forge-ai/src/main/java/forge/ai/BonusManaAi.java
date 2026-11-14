package forge.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import forge.card.MagicColor;
import forge.card.mana.ManaCostShard;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 300 (tester 12/11, plateau a test game, turn 4, D169): Barkhide Troll {G}{G}
 * paid with Forest + the Forest enchanted by Wild Growth, one {G} lost. Forge already predicts the extra mana of
 * TapsForMana triggers (Wild Growth, Utopia Sprawl, Overgrowth, Fertile Ground, Mana Flare...) when it pays, but it
 * picks the source without looking at it, so two lands of equal score come in any order.
 * General rule: a source whose tap gives more mana than its own ability (bonus from a trigger) goes first when all
 * that mana can be spent on the unpaid part of the cost (more mana first), and last when part of it would be lost.
 * Sources without a bonus keep Forge's order. The loop stops as soon as the cost is paid, so the extra mana means one
 * source fewer tapped.
 */
public final class BonusManaAi {

    private BonusManaAi() {
    }

    public static Collection<SpellAbility> order(final Player ai, final ManaCostBeingPaid cost, final ManaCostShard toPay,
            final Collection<SpellAbility> maList) {
        if (ai == null || cost == null || toPay == null || maList == null || maList.size() < 2 || !NewAi.on(ai, 300)) {
            return maList;
        }
        List<ManaCostShard> unpaid = cost.getUnpaidShards();
        List<SpellAbility> fits = new ArrayList<>();
        List<Integer> fitsAmount = new ArrayList<>();
        List<SpellAbility> plain = new ArrayList<>();
        List<SpellAbility> wasteful = new ArrayList<>();
        boolean bonus = false;
        for (SpellAbility ma : maList) {
            int base;
            int total;
            String produced;
            try {
                base = count(ma.getManaPart() == null ? "" : forge.game.GameActionUtil.generatedTotalMana(ma));
                produced = ComputerUtilMana.predictMana(ma, ai, toPay);
                total = count(produced);
            } catch (RuntimeException e) {
                plain.add(ma);
                continue;
            }
            if (total <= base || base == 0) {
                plain.add(ma);
                continue;
            }
            bonus = true;
            if (total <= unpaid.size() && allSpent(produced, unpaid)) {
                int i = 0;
                while (i < fits.size() && fitsAmount.get(i) >= total) {
                    i++;
                }
                fits.add(i, ma);
                fitsAmount.add(i, total);
            } else {
                wasteful.add(ma);
            }
        }
        if (!bonus) {
            return maList;
        }
        List<SpellAbility> out = new ArrayList<>(fits);
        out.addAll(plain);
        out.addAll(wasteful);
        return out;
    }

    static int count(final String produced) {
        if (produced == null || produced.isBlank()) {
            return 0;
        }
        return produced.trim().split("\\s+").length;
    }

    /** True when every mana of the prediction finds an unpaid shard (colored shards first, then generic). */
    static boolean allSpent(final String produced, final List<ManaCostShard> unpaid) {
        List<ManaCostShard> left = new ArrayList<>(unpaid);
        for (String m : produced.trim().split("\\s+")) {
            ManaCostShard found = null;
            boolean any = m.length() > 2 || "all".equalsIgnoreCase(m);
            byte color = any ? MagicColor.ALL_COLORS : MagicColor.fromName(m);
            for (ManaCostShard s : left) {
                if (!s.isGeneric() && s != ManaCostShard.X && payable(s, color, any)) {
                    found = s;
                    break;
                }
            }
            if (found == null) {
                for (ManaCostShard s : left) {
                    if (s.isGeneric() || s == ManaCostShard.X) {
                        found = s;
                        break;
                    }
                }
            }
            if (found == null) {
                return false;
            }
            left.remove(found);
        }
        return true;
    }

    private static boolean payable(final ManaCostShard s, final byte color, final boolean any) {
        if (any) {
            return true;
        }
        if (color == MagicColor.COLORLESS) {
            return s.canBePaidWithManaOfColor(MagicColor.COLORLESS);
        }
        for (byte c : MagicColor.WUBRG) {
            if ((color & c) != 0 && s.canBePaidWithManaOfColor(c)) {
                return true;
            }
        }
        return false;
    }
}
