package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 270 (tester 09/11 12:35, a test game turn 7): the opponent had Righteous Valkyrie
 * and Soul Warden at 26 life, one life short of the Valkyrie's +2/+2 anthem. With three lands the AI cast Drogskol
 * Captain and kept Oblivion Ring for the next turn, when the opponent was at 39 with a pumped team.
 * General rule: an opponent permanent whose conditional anthem (Continuous, Affected creatures it controls, AddPower,
 * CheckSVar) is on, or 3 or less short of its threshold, makes a removal of our hand that can target it go first
 * (sort priority, and main 1 for a removal permanent). An anthem without condition is left to the stock logic.
 */
public final class ThresholdRemoval270 {
    private ThresholdRemoval270() { }

    static final int GAP = 3;

    public static boolean removalNow(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        if (!NewAi.on(ai, 270) || !sa.isSpell() || source == null || !source.isInZone(ZoneType.Hand)) {
            return false;
        }
        final SpellAbility removal = removalAbility(sa, source);
        if (removal == null) {
            return false;
        }
        removal.setActivatingPlayer(ai);
        for (final Player opp : ai.getOpponents()) {
            for (final Card c : opp.getCardsIn(ZoneType.Battlefield)) {
                if (anthemNear(c) && removal.canTarget(c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRemoval(final SpellAbility a) {
        if (a == null || !a.usesTargeting()) {
            return false;
        }
        if (a.getApi() == ApiType.Destroy) {
            return true;
        }
        if (a.getApi() == ApiType.ChangeZone && a.hasParam("Origin") && a.getParam("Origin").contains("Battlefield")) {
            final String dest = a.getParamOrDefault("Destination", "");
            return dest.equals("Exile") || dest.equals("Hand") || dest.equals("Library") || dest.equals("Graveyard");
        }
        return false;
    }

    private static SpellAbility removalAbility(final SpellAbility sa, final Card source) {
        if (isRemoval(sa)) {
            return sa;
        }
        if (!source.isPermanent() || source.isCreature()) {
            return null;
        }
        for (final Trigger t : source.getTriggers()) {
            if (t.getMode() != TriggerType.ChangesZone || !"Battlefield".equals(t.getParam("Destination"))
                    || !"Card.Self".equals(t.getParam("ValidCard"))) {
                continue;
            }
            final SpellAbility a = t.ensureAbility();
            if (isRemoval(a)) {
                return a;
            }
        }
        return null;
    }

    static boolean anthemNear(final Card c) {
        for (final StaticAbility st : c.getStaticAbilities()) {
            if (!st.checkMode(StaticAbilityMode.Continuous) || !st.hasParam("CheckSVar") || !st.hasParam("AddPower")
                    || !st.getParamOrDefault("Affected", "").contains("Creature")
                    || !st.getParam("Affected").contains("YouCtrl")) {
                continue;
            }
            try {
                final int v = AbilityUtils.calculateAmount(c, st.getParam("CheckSVar"), st);
                final String cmp = st.getParamOrDefault("SVarCompare", "GE1");
                final String op = cmp.substring(0, 2);
                final int rhs = AbilityUtils.calculateAmount(c, cmp.substring(2), st);
                if ("GE".equals(op) && v + GAP >= rhs) {
                    return true;
                }
                if ("GT".equals(op) && v + GAP > rhs) {
                    return true;
                }
            } catch (RuntimeException e) {
                // an amount that cannot be computed here: leave the card to the stock logic
            }
        }
        return false;
    }
}
