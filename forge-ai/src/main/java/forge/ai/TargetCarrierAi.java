package forge.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 28. Some scripts use an empty Pump that only targets an opponent to carry
 * the target of their sub-abilities (Tithe, Reap, Recurring Insight, Curious Herd, The Fall of
 * Kroog, Superior Numbers...). PumpAi saw nothing to pump and never cast them (Tricolore,
 * tri30: Tithe refused 11 turns, never cast). Target an opponent (the one with the most lands,
 * which the "more lands than you" clauses count) and let the sub-abilities decide.
 */
public final class TargetCarrierAi {
    private TargetCarrierAi() { }

    public static boolean isCarrier(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.Pump || sa.getSubAbility() == null || !sa.usesTargeting()) {
            return false;
        }
        if (sa.hasParam("NumAtt") || sa.hasParam("NumDef") || sa.hasParam("KW") || sa.hasParam("AILogic")
                || sa.hasParam("Defined") || sa.isCurse()) {
            return false;
        }
        return "Opponent".equals(sa.getParam("ValidTgts")) || carriesOppCard(sa) || carriesRemoval(sa);
    }

    /**
     * magic-tools: rule 238. The same empty Pump on any permanent, when a sub-ability removes the target (Maelstrom
     * Pulse, Baleful Mastery, Echoing Truth, Sever the Bloodline...): it was judged as a pump on our own creatures
     * and stayed in hand (Baleful Mastery 0 cast, 6 refusals with mana and a target). chooseTarget only looks at the
     * opponents' cards, so nothing of ours goes away.
     */
    static boolean carriesRemoval(final SpellAbility sa) {
        return sa.isSpell() && !sa.hasParam("TargetMax") && !sa.hasParam("TgtZone") && removesTarget(sa.getSubAbility(), 0);
    }

    private static boolean removesTarget(final SpellAbility x, final int depth) {
        if (x == null || depth > 8) {
            return false;
        }
        if (x.getApi() != null) {
            switch (x.getApi()) {
                case Destroy:
                    if ("Targeted".equals(x.getParam("Defined"))) {
                        return true;
                    }
                    break;
                case DestroyAll:
                    if (x.getParamOrDefault("ValidCards", "").contains("Targeted")) {
                        return true;
                    }
                    break;
                case ChangeZone:
                    if ("Targeted".equals(x.getParam("Defined")) && leavesBattlefield(x)) {
                        return true;
                    }
                    break;
                case ChangeZoneAll:
                    if (x.getParamOrDefault("ChangeType", "").contains("Targeted") && leavesBattlefield(x)) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return removesTarget(x.getSubAbility(), depth + 1)
                || removesTarget(x.getAdditionalAbility("TrueSubAbility"), depth + 1)
                || removesTarget(x.getAdditionalAbility("FalseSubAbility"), depth + 1);
    }

    private static boolean leavesBattlefield(final SpellAbility x) {
        return "Battlefield".equals(x.getParam("Origin")) && !"Battlefield".equals(x.getParam("Destination"));
    }

    /**
     * magic-tools: rule 175. The same empty Pump on a permanent an opponent controls (Legions to
     * Ashes, Legion's End, Alpha Brawl, Power of Persuasion...): every listed target type is
     * restricted to OppCtrl, so whatever the sub-abilities do lands on the opponent (ketramos:
     * Legions to Ashes held 10 turns facing Soulfire Grand Master, Narset, Lightning Angel).
     */
    static boolean carriesOppCard(final SpellAbility sa) {
        String valid = sa.getParam("ValidTgts");
        if (valid == null || valid.isEmpty()) {
            return false;
        }
        for (String v : valid.split(",")) {
            if (!v.contains("OppCtrl") || v.startsWith("Player") || v.startsWith("Opponent")) {
                return false;
            }
        }
        return true;
    }

    /** Main phase of our turn, or the end of an opponent's turn for an instant. */
    public static boolean goodTime(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        if (!ai.getGame().getStack().isEmpty()) {
            return false;
        }
        if (ph.isPlayerTurn(ai)) {
            return ph.getPhase().isMain();
        }
        return ph.is(PhaseType.END_OF_TURN) && sa.getHostCard().isInstant();
    }

    public static boolean chooseTarget(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        if (carriesOppCard(sa) || carriesRemoval(sa)) {
            List<Card> cards = new ArrayList<>();
            for (Player p : ai.getOpponents()) {
                for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
                    if (sa.canTarget(c)) {
                        cards.add(c);
                    }
                }
            }
            Card best = ComputerUtilCard.getBestAI(cards);
            if (best == null) {
                return false;
            }
            sa.getTargets().add(best);
            return true;
        }
        List<Player> opps = new ArrayList<>();
        for (Player p : ai.getOpponents()) {
            if (sa.canTarget(p)) {
                opps.add(p);
            }
        }
        if (opps.isEmpty()) {
            return false;
        }
        opps.sort(Comparator.comparingInt((Player p) -> -CardLists.filter(p.getCardsIn(ZoneType.Battlefield), CardPredicates.LANDS).size()));
        sa.getTargets().add(opps.get(0));
        return true;
    }
}
