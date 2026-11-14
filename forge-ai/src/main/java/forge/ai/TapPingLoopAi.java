package forge.ai;

import java.util.Map;
import com.google.common.collect.MapMaker;

import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 148, tap-ping loop (combo relue « vraie » Hawkeye's Bow + Pemmin's Aura + Xenobotanist, puzzle MT24).
 * A permanent whose becoming tapped hurts the opponents (Hawkeye's Bow on it, or its own "whenever CARDNAME becomes
 * tapped" trigger), with a "{T}: add mana" ability, and an untap ability for it whose only cost is mana the tap gives
 * (Pemmin's Aura, Freed from the Real: {U}): tap for mana, the trigger hits, the floating mana untaps it, again. Forge never
 * taps a mana source for nothing, so the AI pinged once by attacking. Here, in its own main phase with an empty stack, the
 * AI taps the source for the untap cost's color; UntapAi then untaps the (Defined) host since it is tapped. A guard stops
 * the loop when the opponents' life did not drop since the last tap (damage prevented) and after 200 taps in a turn.
 * Generic: any tap trigger dealing damage or life loss to opponents, any mana-only untapper.
 */
public final class TapPingLoopAi {
    private TapPingLoopAi() { }

    private static final int MAX_PER_TURN = 200;
    /** per player: turn, taps this turn, opponents' total life at the last tap */
    private static final Map<Player, int[]> STATE = new MapMaker().weakKeys().makeMap(); // magic-tools: identity keys (players of two games share ids), thread-safe

    /** a trigger on our battlefield, "becomes tapped" for this host, that hurts opponents */
    static boolean tapHurtsOpponents(final Player ai, final Card host) {
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : c.getTriggers()) {
                if (t.getMode() != TriggerType.Taps || !t.hasParam("ValidCard")) {
                    continue;
                }
                if (!t.matchesValidParam("ValidCard", host, c)) {
                    continue;
                }
                final SpellAbility eff = t.ensureAbility();
                if (eff == null) {
                    continue;
                }
                final ApiType api = eff.getApi();
                final String def = eff.getParamOrDefault("Defined", "");
                if ((api == ApiType.DealDamage || api == ApiType.LoseLife) && def.contains("Opponent")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** "{T}: add mana" with no other cost */
    static boolean isPlainTapMana(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.Mana || !sa.isManaAbility() || sa.hasParam("AILogic")) {
            return false;
        }
        final Cost cost = sa.getPayCosts();
        return sa.getHostCard() != null && cost != null && cost.hasTapCost() && cost.hasNoManaCost()
                && cost.getCostParts().size() == 1;
    }

    /** an untap ability for this host whose only cost is mana, at most what the tap gives; null if none */
    static SpellAbility untapperFor(final Player ai, final Card host, final int gain) {
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final SpellAbility u : c.getSpellAbilities()) {
                if (u.getApi() != ApiType.Untap || !u.isActivatedAbility() || u.isManaAbility()
                        || u.getSubAbility() != null) {
                    continue;
                }
                final Cost cost = u.getPayCosts();
                if (cost == null || !cost.hasManaCost()) {
                    continue;
                }
                boolean manaOnly = true;
                for (final CostPart part : cost.getCostParts()) {
                    if (!(part instanceof CostPartMana)) {
                        manaOnly = false;
                        break;
                    }
                }
                final ManaCost mc = cost.getTotalMana();
                if (!manaOnly || mc.countX() > 0 || mc.getCMC() < 1 || mc.getCMC() > gain) {
                    continue;
                }
                u.setActivatingPlayer(ai);
                if (u.usesTargeting()) {
                    if (!u.canTarget(host)) {
                        continue;
                    }
                } else if (!AbilityUtils.getDefinedCards(c, u.getParam("Defined"), u).contains(host)) {
                    continue;
                }
                return u;
            }
        }
        return null;
    }

    static int oppLife(final Player ai) {
        int life = 0;
        for (final Player o : ai.getOpponents()) {
            life += Math.max(0, o.getLife());
        }
        return life;
    }

    /** the color to tap for (the untap cost's), or null when this source is not a tap-ping loop now */
    static String loopColor(final Player ai, final SpellAbility sa) {
        if (!isPlainTapMana(sa)) {
            return null;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.getPhase().isMain() || !ai.getGame().getStack().isEmpty()) {
            return null;
        }
        final Card host = sa.getHostCard();
        if (host.isTapped() || (host.isCreature() && host.hasSickness())) {
            return null;
        }
        if (!tapHurtsOpponents(ai, host)) {
            return feedColor(ai, sa); // magic-tools: rule 151
        }
        final SpellAbility u = untapperFor(ai, host, UntapManaAi.produced(sa));
        if (u == null) {
            return null;
        }
        final String color = colorFor(u.getPayCosts().getTotalMana());
        final AbilityManaPart part = sa.getManaPart();
        if (part != null && !part.isAnyMana() && !part.isComboMana() && !"C".equals(color)
                && !part.getOrigProduced().contains(color)) {
            return null;
        }
        final int life = oppLife(ai);
        if (life <= 0) {
            return null;
        }
        final int[] st = STATE.get(ai);
        if (st != null && st[0] == ph.getTurn() && (st[1] >= MAX_PER_TURN || life >= st[2])) {
            return null;
        }
        return color;
    }

    /** Called by ManaAi's phase check (no side effect). */
    public static boolean phaseFits(final Player ai, final SpellAbility sa) {
        return loopColor(ai, sa) != null;
    }

    /** Called by ManaAi: tap this source now, its trigger hurts the opponents and its mana untaps it. */
    public static boolean shouldTap(final Player ai, final SpellAbility sa) {
        final String color = loopColor(ai, sa);
        if (color == null) {
            return false;
        }
        final AbilityManaPart part = sa.getManaPart();
        if (part != null && (part.isAnyMana() || part.isComboMana())) {
            part.setExpressChoice(color);
        }
        final int turn = ai.getGame().getPhaseHandler().getTurn();
        if (!tapHurtsOpponents(ai, sa.getHostCard())) {
            FEED.put(ai, new int[] {turn, oppLife(ai)}); // rule 151, a feeding tap
            return true;
        }
        int[] st = STATE.get(ai);
        if (st == null || st[0] != turn) {
            st = new int[] {turn, 0, Integer.MAX_VALUE};
            STATE.put(ai, st);
        }
        st[1]++;
        st[2] = oppLife(ai);
        return true;
    }

    /** Called by SpellAbilityAi: a link of a running tap-ping loop is not a runaway activation (the guard here caps it). */
    public static boolean inLoop(final Player ai, final SpellAbility sa) {
        if (isPlainTapMana(sa)) {
            return phaseFits(ai, sa);
        }
        if (sa.getApi() == ApiType.Untap && sa.isActivatedAbility() && untapLink(ai, sa)) {
            return true; // magic-tools: rule 151
        }
        if (sa.getApi() != ApiType.Untap || !sa.isActivatedAbility() || sa.usesTargeting()) {
            return false;
        }
        final int[] st = STATE.get(ai);
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (st == null || st[0] != ph.getTurn() || st[1] >= MAX_PER_TURN || !ph.isPlayerTurn(ai)) {
            return false;
        }
        for (final Card host : AbilityUtils.getDefinedCards(sa.getHostCard(), sa.getParam("Defined"), sa)) {
            if (host.isTapped() && host.getController() == ai && tapHurtsOpponents(ai, host)) {
                for (final SpellAbility m : host.getManaAbilities()) {
                    if (isPlainTapMana(m) && untapperFor(ai, host, UntapManaAi.produced(m)) == sa) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** rule 151, per player: turn, activations of the tapper at the last link, opponents' life then, links this turn */
    private static final Map<Player, int[]> LINK = new MapMaker().weakKeys().makeMap(); // magic-tools: identity keys

    /** "{T}: untap another target permanent" on a host whose becoming tapped hurts the opponents */
    static boolean isPingTapper(final Player ai, final SpellAbility sa) {
        if (sa.getApi() != ApiType.Untap || !sa.usesTargeting() || sa.getSubAbility() != null) {
            return false;
        }
        final Cost cost = sa.getPayCosts();
        final Card host = sa.getHostCard();
        return host != null && cost != null && cost.hasTapCost() && cost.hasNoManaCost() && cost.getCostParts().size() == 1
                && host.getController() == ai && tapHurtsOpponents(ai, host);
    }

    /** most mana one of our plain {T}-mana permanents gives (what the tapper's untap is worth) */
    static int bestTapGain(final Player ai) {
        int best = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final SpellAbility m : c.getManaAbilities()) {
                if (isPlainTapMana(m)) {
                    best = Math.max(best, UntapManaAi.produced(m));
                }
            }
        }
        return best;
    }

    /**
     * magic-tools: rule 151, untap-ping loop (combo relue « vraie » D'Vana Tendi + Hawkeye's Bow + Pemmin's Aura / Freed from
     * the Real, puzzle MT27). The host taps to untap a mana source (D'Vana: "{T}: untap another target permanent"), its
     * becoming tapped hurts the opponents, and a mana-only untapper (Pemmin's Aura: {U}) that the source pays untaps the
     * host: endless pings. Forge's own choices already run it, but its runaway brake (10 activations a turn) cut it at 9
     * pings. Both links are exempt from the brake while each new tap of the host lowered the opponents' life, 200 a turn.
     */
    static boolean untapLink(final Player ai, final SpellAbility sa) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai)) {
            return false;
        }
        final int turn = ph.getTurn();
        final int life = oppLife(ai);
        if (life <= 0) {
            return false;
        }
        int[] st = LINK.get(ai);
        if (isPingTapper(ai, sa)) {
            final Card host = sa.getHostCard();
            final int gain = bestTapGain(ai);
            if (gain < 1 || untapperFor(ai, host, gain) == null) {
                return false;
            }
            final int n = sa.getActivationsThisTurn();
            if (st == null || st[0] != turn) {
                st = new int[] {turn, n, life, 0};
                LINK.put(ai, st);
                return true;
            }
            if (n == st[1]) {
                return st[3] < MAX_PER_TURN; // the same link, looked at again
            }
            if (life >= st[2] || st[3] >= MAX_PER_TURN) {
                return false; // no progress (damage prevented) or cap
            }
            st[1] = n;
            st[2] = life;
            st[3]++;
            return true;
        }
        if (sa.usesTargeting() || (st != null && st[0] == turn && st[3] >= MAX_PER_TURN)) {
            return false;
        }
        // the mana-only untapper of a tapped host whose tapper is in the loop (the tapper's link carries the progress guard)
        for (final Card host : AbilityUtils.getDefinedCards(sa.getHostCard(), sa.getParam("Defined"), sa)) {
            if (!host.isTapped() || host.getController() != ai) {
                continue;
            }
            for (final SpellAbility t : host.getSpellAbilities()) {
                if (isPingTapper(ai, t) && untapperFor(ai, host, Math.max(1, bestTapGain(ai))) == sa) {
                    return true;
                }
            }
        }
        return false;
    }

    /** rule 151: an untapped ping tapper (not summoning sick) with its mana-only untapper, for this much mana; null if none */
    static SpellAbility pingTapper(final Player ai, final int gain) {
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c.isTapped() || (c.isCreature() && c.hasSickness())) {
                continue;
            }
            for (final SpellAbility t : c.getSpellAbilities()) {
                if (isPingTapper(ai, t) && untapperFor(ai, c, gain) != null) {
                    return t;
                }
            }
        }
        return null;
    }

    /** rule 151, per player: turn, opponents' life at the last feeding tap */
    private static final Map<Player, int[]> FEED = new MapMaker().weakKeys().makeMap(); // magic-tools: identity keys

    /**
     * rule 151: a plain mana source to tap first, for the untapper's color, so that the ping tapper (D'Vana Tendi, Kiora's
     * Follower) has a tapped source to untap: the loop then runs from that floating mana. Not when a tapped one is already
     * there, nor when the opponents' life did not drop since the last feeding tap.
     */
    static String feedColor(final Player ai, final SpellAbility sa) {
        final int gain = UntapManaAi.produced(sa);
        final SpellAbility t = pingTapper(ai, gain);
        if (t == null || !t.canTarget(sa.getHostCard())) {
            return null;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c.isTapped() && c != t.getHostCard() && t.canTarget(c)) {
                for (final SpellAbility m : c.getManaAbilities()) {
                    if (isPlainTapMana(m) && UntapManaAi.produced(m) >= 1) {
                        return null; // already one to untap
                    }
                }
            }
        }
        final SpellAbility u = untapperFor(ai, t.getHostCard(), gain);
        final String color = colorFor(u.getPayCosts().getTotalMana());
        final AbilityManaPart part = sa.getManaPart();
        if (part != null && !part.isAnyMana() && !part.isComboMana() && !"C".equals(color)
                && !part.getOrigProduced().contains(color)) {
            return null;
        }
        final int life = oppLife(ai);
        final int turn = ai.getGame().getPhaseHandler().getTurn();
        final int[] f = FEED.get(ai);
        if (life <= 0 || (f != null && f[0] == turn && life >= f[1])) {
            return null;
        }
        return color;
    }

    /** rule 151, for UntapAi's UntapManaPermanent logic (Kiora's Follower): the tapped mana source a ping tapper untaps */
    public static Card pingTarget(final Player ai, final SpellAbility sa) {
        if (!isPingTapper(ai, sa) || !UntapManaAi.phaseOk(ai) || !untapLink(ai, sa)) {
            return null;
        }
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (c.isTapped() && c != sa.getHostCard() && sa.canTarget(c)) {
                for (final SpellAbility m : c.getManaAbilities()) {
                    if (isPlainTapMana(m) && untapperFor(ai, sa.getHostCard(), UntapManaAi.produced(m)) != null) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    private static String colorFor(final ManaCost cost) {
        for (final ManaCostShard shard : cost) {
            for (final byte c : MagicColor.WUBRG) {
                if ((shard.getColorMask() & c) != 0) {
                    return MagicColor.toShortString(c);
                }
            }
        }
        return "C";
    }
}
