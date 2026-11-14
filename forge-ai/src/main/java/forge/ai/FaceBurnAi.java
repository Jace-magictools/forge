package forge.ai;

import forge.game.GameEntity;
import forge.game.GameObject;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.card.Card;
import forge.game.spellability.TargetRestrictions;

import java.util.ArrayList;
import java.util.List;

/**
 * magic-tools: rule 40, stock AI burns a player only when it drops him under 5 life, or by chance
 * (damage / life, floor 0.2: never for 3 damage at 20 life), so Sorcier kept Lava Spike 14 turns in 10 games
 * against Nothing Gulch. A damage spell that cannot target creatures has no better use than the face:
 * it is cast in our main 2 (sorcery) or at the end of the opponent's turn (instant), the mana is not lost
 * and the card feeds the graveyard (Dreadhorde Arcanist). Planeswalkers are still chosen first.
 * Rule 44 (tester, 27/10: "Burn : en priorite au visage"): a damage spell that can hit a creature or a player
 * (Lightning Bolt) goes to the face too, with the same timing, unless our life is in danger (then Forge still
 * removes the threatening creature). Spells with X, divided damage or a special AILogic keep Forge's logic.
 * Rule 48: nor a spell whose sub-ability picks its own target (Arc Trail, Cone of Flame, Seismic Wave): with the
 * face taken first, the "other target" found nothing worth hitting and the spell stayed in hand all game
 * (BlastBurn died with Arc Trail in hand, r8); Forge's logic aims the big part at a creature and the rest at the face.
 * Rule 54: burn that kills the opponent now is cast now, at his face, whatever the timing above; the second target
 * of the spell (Arc Trail's "1 damage to any other target") may then be anything legal. In the v7 tournament
 * Red Star kept Magma Jet 6 turns against an opponent at 2 life (mana spent in its own turn, nothing left at his
 * end step) and Arc Trail stayed in hand when the other target had nothing worth 1 damage (33 cases in 14 883 games).
 * Rule 97: with our life in danger, rule 44 steps aside so Forge can kill the threat; when no creature is worth the
 * damage (Jace's Phantasm 5/5 against Lightning Helix), Forge held the spell and died with it: 616 deaths with a
 * payable burn in hand in the v12 tournament (Fireball, Bolt, Electrolyze, Helix...). Then the spell goes to the face:
 * at once if it also gains us life (Lightning Helix: three more life before the attack), at the usual face timing
 * otherwise (the card does nothing else before we die).
 * Rule 111: in danger, an instant goes to the face at any time in the opponent's turn, not only at his end step.
 * Rule 98: the same for divided damage (Electrolyze 50, Forked Bolt: 2 damage among one or two targets, excluded from 97):
 * the creatures worth it first, the rest at the face. Fireball (X, divided evenly) keeps rules 25-26.
 * Rule 262: rule 48 left Arc Trail to Forge, which casts it only when the 2 damage kill a creature. Against tough
 * creatures (Triskaidekaphile 1/3, Enduring Curiosity 4/3) BlastBurn kept it from turn 5 to its death on turn 20 with up
 * to 7 untapped lands (visionneur, 9/11). With no creature worth it, at the face timing, the first target is the
 * opponent's face and the "other target" any permanent of his (best creature first); without one the spell waits.
 * Only fixed damage with a first target that may be a creature: Searing Blaze (player, then his creature, 3 with
 * landfall) went off for 1 + 1 without a land and BlastBurn lost 5 games (cmp295, z -2.2).
 */
public final class FaceBurnAi {
    private FaceBurnAi() { }

    public static boolean isFaceOnly(final SpellAbility sa) {
        if (!sa.isSpell() || sa.getApi() != ApiType.DealDamage || !sa.usesTargeting()
                || "X".equals(sa.getParam("NumDmg")) || sa.isDividedAsYouChoose()
                || "OpponentGainLife".equals(sa.getParam("AILogic"))) {
            return false;
        }
        final TargetRestrictions tgt = sa.getTargetRestrictions();
        return tgt.canTgtPlayer() && !tgt.canTgtCreature();
    }

    /** Rule 44: a spell that can hit creatures and players, aimed at the face first. */
    public static boolean isFaceFirst(final Player ai, final SpellAbility sa) {
        if (!sa.isSpell() || sa.getApi() != ApiType.DealDamage || !sa.usesTargeting()
                || "X".equals(sa.getParam("NumDmg")) || sa.isDividedAsYouChoose() || sa.hasParam("DivideEvenly")
                || sa.hasParam("AILogic") || sa.getMaxTargets() != 1 || hasTargetedSub(sa)) {
            return false;
        }
        final TargetRestrictions tgt = sa.getTargetRestrictions();
        return tgt.canTgtPlayer() && tgt.canTgtCreature() && !ComputerUtil.aiLifeInDanger(ai, false, 0)
                && !defending(ai);
    }

    /**
     * magic-tools rule 283 (Patrilock, 11/11): rule 44 sent the Isochron Scepter copies (Lightning Helix, Lightning
     * Bolt) at the face 73 and 86 times out of 100, 58 times with a creature of toughness 3 or less in front. When we
     * are the defender (their creatures hit harder than ours) and the deck does not win by reach (burn, mill, drain),
     * the face is no longer first: Forge picks the target (a creature worth the damage, else the face).
     */
    public static boolean defending(final Player ai) {
        if (!NewAi.on(ai, 283) || "reach".equals(DeckPlanAi.route(ai))) {
            return false;
        }
        int ours = 0;
        for (final Card c : ai.getCreaturesInPlay()) {
            ours += Math.max(0, c.getNetCombatDamage());
        }
        int theirs = 0;
        for (final Player o : ai.getOpponents()) {
            for (final Card c : o.getCreaturesInPlay()) {
                theirs += Math.max(0, c.getNetCombatDamage());
            }
        }
        return theirs > ours;
    }

    /** Rule 97: life in danger and no creature worth the damage: the face, now if the spell gains us life. */
    public static boolean dangerFace(final Player ai, final SpellAbility sa, final Player enemy, final int dmg) {
        // rule 98: divided damage too (Electrolyze, Forked Bolt), what is left after the creatures goes to the face
        // rule 99: X damage divided evenly (Fireball) too, as its only target: X is already the most we can pay
        final boolean evenly = sa.hasParam("DivideEvenly");
        if (!sa.isSpell() || sa.getApi() != ApiType.DealDamage || !sa.usesTargeting()
                || ("X".equals(sa.getParam("NumDmg")) && !"Count$xPaid".equals(sa.getSVar("X")))
                || (evenly && !sa.getTargets().isEmpty())
                || sa.hasParam("AILogic") || (!sa.isDividedAsYouChoose() && !evenly && sa.getMaxTargets() != 1) || hasTargetedSub(sa)
                || sa.getTargets().contains(enemy)
                || enemy == null || !sa.canTarget(enemy) || !enemy.canLoseLife()
                || !ComputerUtil.aiLifeInDanger(ai, false, 0)
                || ComputerUtilCombat.predictDamageTo(enemy, dmg, sa.getHostCard(), false) <= 0) {
            return false;
        }
        return gainsUsLife(sa) || faceTiming(ai, sa) || dangerTiming(ai, sa);
    }

    /**
     * Rule 111: in danger, an instant waits no more for the opponent's end step: his attack killed us first
     * (Lightning Bolt held to death 27 times in the v14 tournament, drawn in our turn, dead in his). Any time in his turn,
     * our mana untapped: the card does nothing else before we die.
     */
    static boolean dangerTiming(final Player ai, final SpellAbility sa) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        return !SpellAbilityAi.isSorcerySpeed(sa, ai) && !ph.isPlayerTurn(ai);
    }

    /** Rule 97: an untargeted sub-ability that gains us life (Lightning Helix). */
    static boolean gainsUsLife(final SpellAbility sa) {
        for (SpellAbility sub = sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.getApi() == ApiType.GainLife && !sub.usesTargeting()
                    && "You".equals(sub.getParamOrDefault("Defined", "You"))) {
                return true;
            }
        }
        return false;
    }

    /** Rule 48: a sub-ability with its own target ("and 1 damage to any other target"). */
    static boolean hasTargetedSub(final SpellAbility sa) {
        for (SpellAbility sub = sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.usesTargeting()) {
                return true;
            }
        }
        return false;
    }

    /** Rule 54: the damage of this spell kills that opponent now (no prevention, no "can't lose"). */
    public static boolean killsNow(final SpellAbility sa, final Player enemy, final int dmg) {
        if (enemy == null || !sa.usesTargeting() || !sa.getTargetRestrictions().canTgtPlayer() || !sa.canTarget(enemy)
                || !enemy.canLoseLife() || enemy.cantLoseForZeroOrLessLife() || "OpponentGainLife".equals(sa.getParam("AILogic"))) {
            return false;
        }
        return dmg > 0 && ComputerUtilCombat.predictDamageTo(enemy, dmg, sa.getHostCard(), false) >= enemy.getLife();
    }

    /** Rule 54: a sub-ability whose parent already deals lethal damage to an opponent (Arc Trail's second target). */
    public static boolean parentKillsOpponent(final Player ai, final SpellAbility sa) {
        for (SpellAbility p = sa.getParent(); p != null; p = p.getParent()) {
            if (p.getApi() != ApiType.DealDamage || !p.usesTargeting() || !p.hasParam("NumDmg")) {
                continue;
            }
            final int dmg = AbilityUtils.calculateAmount(p.getHostCard(), p.getParam("NumDmg"), p);
            for (final GameObject e : p.getTargets()) {
                if (e instanceof Player pl && pl.isOpponentOf(ai) && pl.canLoseLife() && !pl.cantLoseForZeroOrLessLife()
                        && ComputerUtilCombat.predictDamageTo(pl, dmg, p.getHostCard(), false) >= pl.getLife()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The moment to burn the face: our main 2 for a sorcery, the end of the opponent's turn otherwise. */
    public static boolean faceTiming(final Player ai, final SpellAbility sa) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (SpellAbilityAi.isSorcerySpeed(sa, ai)) {
            return ph.isPlayerTurn(ai) && ph.is(PhaseType.MAIN2);
        }
        return ph.is(PhaseType.END_OF_TURN) && ph.getNextTurn().equals(ai);
    }

    /** Rule 262: Arc Trail's shape: fixed damage, one target that may be a creature or a player, a targeted damage sub. */
    static boolean trailShape(final SpellAbility sa) {
        if (!sa.isSpell() || sa.getParent() != null || sa.getApi() != ApiType.DealDamage || !sa.usesTargeting()
                || !sa.getParamOrDefault("NumDmg", "").matches("\\d+") || sa.isDividedAsYouChoose()
                || sa.hasParam("DivideEvenly") || sa.hasParam("AILogic") || sa.getMaxTargets() != 1 || !hasTargetedSub(sa)
                || !sa.getTargetRestrictions().canTgtCreature() || !sa.getTargetRestrictions().canTgtPlayer()) {
            return false;
        }
        for (SpellAbility sub = sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.usesTargeting() && sub.getApi() != ApiType.DealDamage) {
                return false;
            }
        }
        return true;
    }

    /** Rule 262: Arc Trail with nothing worth killing: the main damage at the face, the sub at an opponent's permanent. */
    public static boolean isTrailFace(final Player ai, final SpellAbility sa, final Player enemy, final int dmg) {
        if (!trailShape(sa) || enemy == null || !sa.canTarget(enemy) || sa.getTargets().contains(enemy)
                || !enemy.canLoseLife() || !faceTiming(ai, sa) || dmg <= 0
                || ComputerUtilCombat.predictDamageTo(enemy, dmg, sa.getHostCard(), false) <= 0) {
            return false;
        }
        for (SpellAbility sub = sa.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
            if (sub.usesTargeting() && trailTargets(ai, sub, enemy).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Rule 262: the parent of this sub-ability has Arc Trail's shape and already aims at an opponent's face. */
    public static boolean parentAtFace(final Player ai, final SpellAbility sa) {
        final SpellAbility p = sa.getParent();
        if (p == null || !trailShape(p)) {
            return false;
        }
        for (final GameObject e : p.getTargets()) {
            if (e instanceof Player pl && pl.isOpponentOf(ai)) {
                return true;
            }
        }
        return false;
    }

    /** Rule 262: the opponent's permanents this sub-ability may hit, best creature first, then the other opponents. */
    public static List<GameEntity> trailTargets(final Player ai, final SpellAbility sub, final Player face) {
        final List<Card> cards = new ArrayList<>();
        final List<GameEntity> players = new ArrayList<>();
        for (final GameEntity e : sub.getTargetRestrictions().getAllCandidates(sub)) {
            if (e == face || sub.getTargets().contains(e)) {
                continue;
            }
            if (e instanceof Card c && c.getController().isOpponentOf(ai)) {
                cards.add(c);
            } else if (e instanceof Player pl && pl.isOpponentOf(ai)) {
                players.add(pl);
            }
        }
        cards.sort((a, b) -> Integer.compare(value(b), value(a)));
        final List<GameEntity> out = new ArrayList<>(cards);
        out.addAll(players);
        return out;
    }

    private static int value(final Card c) {
        return c.isCreature() ? ComputerUtilCard.evaluateCreature(c) : c.getCMC();
    }
}
