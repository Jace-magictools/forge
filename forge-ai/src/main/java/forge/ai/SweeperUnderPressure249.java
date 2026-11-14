package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 249 (D141, tester 08/11, a test game T6): at 7 life against Pugnacious Hammerskull
 * (6/6) and two Realmwalkers, the AI cast Darksteel Ingot and Expedition Map with its 4 mana instead of
 * Nevinyrral's Disk, which would have destroyed the opposing board the next turn; the Disk came down one turn later,
 * at 3 life. Cause: a permanent waits for main 2 by default, the mana rock goes in main 1 and takes the mana.
 * General rule, for a sweeper in hand (a spell that destroys all creatures, or a permanent whose ability does) when
 * we are under pressure (the opposing creatures deal our life in two attacks) and the sweep kills at least half the
 * opposing power and more of theirs than of ours (Pyroclasm against a 6/6 does not count):
 * 1. a sweeper permanent is cast in main 1 (its value comes later, it must come down now);
 * 2. a development spell (not a creature: a blocker is defense too) that would leave too little mana to cast the
 *    sweeper this turn waits (tournament v282: holding the creatures cost Flibuste and Toxico).
 */
public final class SweeperUnderPressure249 {
    private SweeperUnderPressure249() { }

    static boolean isSweepApi(final ApiType api) {
        return api == ApiType.DestroyAll || api == ApiType.DamageAll;
    }

    /** the ability that sweeps creatures: a spell, or a non-mana activated ability of a permanent; null if none */
    static SpellAbility sweepAbility(final Card c) {
        if (c == null || c.isLand()) {
            return null;
        }
        for (final SpellAbility s : c.getSpells()) {
            if (isSweepApi(s.getApi())) {
                return s;
            }
        }
        if (c.isPermanent()) {
            for (final SpellAbility s : c.getNonManaAbilities()) {
                if (s.isActivatedAbility() && isSweepApi(s.getApi())
                        && s.getParamOrDefault("ValidCards", "").contains("Creature")) {
                    return s;
                }
            }
        }
        return null;
    }

    public static boolean isSweeper(final Card c) {
        return sweepAbility(c) != null;
    }

    /** the creature dies to the sweep */
    static boolean dies(final Player ai, final SpellAbility sweep, final Card c) {
        final String valid = sweep.getParamOrDefault("ValidCards", "Creature");
        if (!c.isValid(valid.split(","), ai, sweep.getHostCard(), sweep)) {
            return false;
        }
        if (sweep.getApi() == ApiType.DestroyAll) {
            return !c.hasKeyword(Keyword.INDESTRUCTIBLE);
        }
        if (!sweep.hasParam("NumDmg")) {
            return false;
        }
        final int dmg = AbilityUtils.calculateAmount(sweep.getHostCard(), sweep.getParam("NumDmg"), sweep);
        return dmg > 0 && ComputerUtilCombat.getDamageToKill(c, false) <= dmg && !c.hasKeyword(Keyword.INDESTRUCTIBLE);
    }

    /** the opposing creatures deal our life in two attacks */
    public static boolean underPressure(final Player ai) {
        int power = 0;
        for (final Player o : ai.getOpponents()) {
            for (final Card c : o.getCreaturesInPlay()) {
                power += Math.max(0, c.getNetCombatDamage());
            }
        }
        return power > 0 && 2 * power >= ai.getLife();
    }

    /** the sweep kills at least half the opposing power, and more value of theirs than of ours */
    static boolean relieves(final Player ai, final Card sweeper) {
        final SpellAbility sweep = sweepAbility(sweeper);
        if (sweep == null) {
            return false;
        }
        int power = 0;
        int killedPower = 0;
        final CardCollection theirs = new CardCollection();
        for (final Player o : ai.getOpponents()) {
            for (final Card c : o.getCreaturesInPlay()) {
                final int p = Math.max(0, c.getNetCombatDamage());
                power += p;
                if (dies(ai, sweep, c)) {
                    killedPower += p;
                    theirs.add(c);
                }
            }
        }
        if (theirs.isEmpty() || 2 * killedPower < power) {
            return false;
        }
        final CardCollection ours = CardLists.filter(ai.getCreaturesInPlay(), c -> dies(ai, sweep, c));
        return ComputerUtilCard.evaluateCreatureList(theirs) > ComputerUtilCard.evaluateCreatureList(ours);
    }

    /** rule 249.1: the sweeper permanent goes in main 1 under pressure */
    public static boolean castInMain1(final Player ai, final SpellAbility sa) {
        return sa.isSpell() && isSweeper(sa.getHostCard()) && underPressure(ai) && relieves(ai, sa.getHostCard());
    }

    private static int cost(final SpellAbility sa) {
        return sa.getPayCosts() != null && sa.getPayCosts().getTotalMana() != null
                ? sa.getPayCosts().getTotalMana().getCMC() : sa.getHostCard().getCMC();
    }

    /** rule 249.2: the sweeper in hand whose mana {@code sa} would take, or null */
    public static Card sweeperKept(final Player ai, final SpellAbility sa) {
        if (sa == null || !sa.isSpell() || sa.getHostCard() == null || sa.getHostCard().isLand()
                || sa.getHostCard().isCreature() || isSweeper(sa.getHostCard())
                || !ai.getGame().getPhaseHandler().isPlayerTurn(ai)) {
            return null;
        }
        final ApiType api = sa.getApi();
        if (api == ApiType.Destroy || api == ApiType.DealDamage || api == ApiType.Counter || api == ApiType.Fog) {
            return null;
        }
        if (!sa.getHostCard().getManaAbilities().isEmpty() && sa.getHostCard().getCMC() == 0) {
            return null;
        }
        final CardCollection sweepers = CardLists.filter(ai.getCardsIn(ZoneType.Hand),
                c -> c != sa.getHostCard() && isSweeper(c) && relieves(ai, c));
        if (sweepers.isEmpty() || !underPressure(ai)) {
            return null;
        }
        final int available = ComputerUtilMana.getAvailableManaEstimate(ai, true);
        final int spent = cost(sa);
        for (final Card c : sweepers) {
            for (final SpellAbility s : c.getSpells()) {
                s.setActivatingPlayer(ai);
                if (!s.canPlay() || !ComputerUtilMana.canPayManaCost(s, ai, 0, false)) {
                    continue;
                }
                if (available - spent < cost(s)) {
                    return c;
                }
            }
        }
        return null;
    }
}
