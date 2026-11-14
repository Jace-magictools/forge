package forge.ai;

import java.util.List;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 153 (banc « IA contre décisions humaines », Plateau 02/11, puzzle MT32). The opponent is at 1 life
 * and Blood Artist drains on every death: any attack wins (blocked, someone dies and the drain finishes; unblocked,
 * the damage does), yet Forge attacked with nobody because every attacker would die to a bigger blocker.
 * Lethal when every attacker of a set S is "covered" (each blocker able to block it dies or kills it, and that death
 * drains the opponent to 0) and S unblocked deals lethal damage.
 */
public final class DeathDrainAttackAi {
    private DeathDrainAttackAi() { }

    /** Life the opponent loses from our death triggers when {@code dying} dies. */
    static int drainOn(final Player ai, final Player opp, final Card dying) {
        int total = 0;
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            for (final Trigger t : c.getTriggers()) {
                if (t.getMode() != TriggerType.ChangesZone || !"Graveyard".equals(t.getParam("Destination"))
                        || !t.getParamOrDefault("Origin", "Any").matches("Battlefield|Any")
                        || t.hasParam("OptionalDecider") || !t.hasParam("ValidCard")) {
                    continue;
                }
                if (!t.matchesValidParam("ValidCard", dying)) {
                    continue;
                }
                for (SpellAbility ab = t.ensureAbility(); ab != null; ab = ab.getSubAbility()) {
                    final boolean lose = ab.getApi() == ApiType.LoseLife;
                    final boolean dmg = ab.getApi() == ApiType.DealDamage;
                    if (!lose && !dmg) {
                        continue;
                    }
                    final boolean hits;
                    if (ab.usesTargeting()) {
                        final String v = ab.getParamOrDefault("ValidTgts", "");
                        hits = v.contains("Player") || v.contains("Opponent") || v.equals("Any");
                    } else {
                        hits = ab.getParamOrDefault("Defined", "").contains("Opponent");
                    }
                    if (!hits) {
                        continue;
                    }
                    final String amount = ab.getParam(lose ? "LifeAmount" : "NumDmg");
                    if (amount != null) {
                        total += Math.max(0, amountFor(c, amount, ab, dying));
                    }
                    break;
                }
            }
        }
        return total;
    }

    /**
     * magic-tools: rule 155. Amounts read from the dying card ("TriggeredCard$CardPower", Dreadhorde Butcher) have no
     * triggered object before the death: read them on the card that would die (its power now).
     */
    static int amountFor(final Card source, final String amount, final SpellAbility ab, final Card dying) {
        final String expr = source.hasSVar(amount) ? source.getSVar(amount) : amount;
        if (expr.startsWith("TriggeredCard$CardPower") || expr.startsWith("TriggeredNewCardLKICopy$CardPower")) {
            return dying.getNetPower();
        }
        if (expr.startsWith("TriggeredCard$CardToughness") || expr.startsWith("TriggeredNewCardLKICopy$CardToughness")) {
            return dying.getNetToughness();
        }
        return AbilityUtils.calculateAmount(source, amount, ab);
    }

    /**
     * magic-tools: rule 155 (banc « IA contre décisions humaines », Plateau 02/11, Dreadhorde Butcher a test game
     * et -031). A creature whose own death deals damage or drains (Butcher: damage equal to its power) loses nothing
     * in a trade: Forge, in a cautious mood, kept a 3/3 Butcher home against a 3/1 flyer, and a 6/6 one (opponent at
     * 5) against four small flyers that could only kill it by a gang block, eating 6 to the face.
     * Attack when it kills every single blocker and its death pays, and either no single blocker kills it, or the
     * trade is accepted (cautious mood 2 and up).
     */
    static boolean deathPaysAttacker(final Player ai, final forge.game.GameEntity defender, final Card attacker,
            final boolean canKillAll, final boolean canBeKilledByOne, final int aggression) {
        final Player opp = defender instanceof Player ? (Player) defender
                : defender instanceof Card ? ((Card) defender).getController() : null;
        if (opp == null || !opp.isOpponentOf(ai) || !canKillAll || attacker.getNetCombatDamage() <= 0
                || !opp.canLoseLife()) {
            return false;
        }
        if (canBeKilledByOne && aggression < 2) {
            return false;
        }
        return drainOn(ai, opp, attacker) >= 1;
    }

    /**
     * magic-tools: rule 154 (banc « IA contre décisions humaines », Plateau 02/11, puzzle MT33). A token (Jadar's decayed
     * Zombie) whose death drains the opponent costs nothing to attack with: unblocked it deals its damage, blocked and
     * killed the drain hits anyway. Forge kept it home because a bigger blocker kills it.
     */
    static boolean freeAttacker(final Player ai, final forge.game.GameEntity defender, final Card attacker) {
        final Player opp = defender instanceof Player ? (Player) defender
                : defender instanceof Card ? ((Card) defender).getController() : null;
        if (opp == null || !opp.isOpponentOf(ai) || !attacker.isToken() || attacker.getNetCombatDamage() <= 0
                || !opp.canLoseLife()) {
            return false;
        }
        return drainOn(ai, opp, attacker) >= 1;
    }

    static boolean lethal(final Player ai, final Player opp, final List<Card> attackers, final List<Card> blockers) {
        if (opp == null || !opp.canLoseLife() || opp.cantLoseForZeroOrLessLife() || attackers.isEmpty()) {
            return false;
        }
        final int life = opp.getLife();
        final List<Card> covered = new java.util.ArrayList<>();
        for (final Card a : attackers) {
            final boolean attackerDeathWins = drainOn(ai, opp, a) >= life;
            boolean ok = true;
            for (final Card b : blockers) {
                if (!CombatUtil.canBlock(a, b)) {
                    continue;
                }
                final boolean killsB = ComputerUtilCombat.canDestroyBlocker(ai, b, a, null, false)
                        && drainOn(ai, opp, b) >= life;
                final boolean killsA = attackerDeathWins && ComputerUtilCombat.canDestroyAttacker(ai, a, b, null, false);
                if (!killsB && !killsA) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                covered.add(a);
            }
        }
        return !covered.isEmpty() && ComputerUtilCombat.sumDamageIfUnblocked(covered, opp) >= life;
    }
}
