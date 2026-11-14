package forge.ai;

import java.util.ArrayList;
import java.util.List;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityCantAttackBlock;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 265 (tester 09/11, a test game turn 7): at 6 life against Oona's Prowler 7/2 with
 * flying, the AI cast Maned Serval (1/4, no reach) and kept Inspiring Overseer (2/1 flying) in hand: dead next turn.
 * When the next attack of the opponent kills us, a creature spell that can block an attacker none of our creatures
 * can block (flying, horsemanship, "can't be blocked except by…") goes first, and is cast in main 1 too.
 */
public final class EvasiveBlocker265 {
    private EvasiveBlocker265() { }

    /** the opposing creatures able to attack next turn that none of our creatures in play can block */
    static List<Card> unblockedThreats(final Player ai) {
        final List<Card> out = new ArrayList<>();
        for (final Player opp : ai.getOpponents()) {
            for (final Card att : opp.getCreaturesInPlay()) {
                if (att.getNetCombatDamage() <= 0 || !ComputerUtilCombat.canAttackNextTurn(att, ai)) {
                    continue;
                }
                boolean blocked = false;
                for (final Card b : ai.getCreaturesInPlay()) {
                    if (forge.game.combat.CombatUtil.canBlock(att, b, true)) {
                        blocked = true;
                        break;
                    }
                }
                if (!blocked) {
                    out.add(att);
                }
            }
        }
        return out;
    }

    /** the creature spell {@code sa} (from hand) could block one of {@code threats} */
    static boolean blocksOne(final SpellAbility sa, final List<Card> threats) {
        if (sa == null || sa.getApi() != ApiType.PermanentCreature || !sa.isSpell() || sa.getHostCard() == null
                || !sa.getHostCard().isInZone(ZoneType.Hand) || threats.isEmpty()) {
            return false;
        }
        final Card c = sa.getHostCard();
        for (final Card att : threats) {
            if (!StaticAbilityCantAttackBlock.cantBlockBy(att, c)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownMain(final Player ai) {
        return ai.getGame().getPhaseHandler().isPlayerTurn(ai) && ai.getGame().getPhaseHandler().getPhase().isMain();
    }

    /** moves the first creature spell that blocks an unblocked threat to the front when the next attack kills us */
    public static void prioritize(final Player ai, final List<SpellAbility> all) {
        if (!NewAi.on(ai, 265) || !ownMain(ai) || all.size() < 2) {
            return;
        }
        List<Card> threats = null;
        for (int i = 0; i < all.size(); i++) {
            final SpellAbility sa = all.get(i);
            if (sa.getApi() != ApiType.PermanentCreature) {
                continue;
            }
            if (threats == null) {
                threats = unblockedThreats(ai);
                if (threats.isEmpty() || !ComputerUtil.aiLifeInDanger(ai, true, 0)) {
                    return;
                }
            }
            if (blocksOne(sa, threats)) {
                if (i > 0) {
                    all.add(0, all.remove(i));
                }
                return;
            }
        }
    }

    /** castPermanentInMain1: the blocker is not kept for main 2 when the next attack kills us */
    public static boolean castNow(final Player ai, final SpellAbility sa) {
        if (!NewAi.on(ai, 265) || !ownMain(ai) || sa.getApi() != ApiType.PermanentCreature) {
            return false;
        }
        final List<Card> threats = unblockedThreats(ai);
        return blocksOne(sa, threats) && ComputerUtil.aiLifeInDanger(ai, true, 0);
    }
}
