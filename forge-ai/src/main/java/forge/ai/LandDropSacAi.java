package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 12, a land drop that sacrifices one of our permanents. City of Traitors ("when you play another
 * land, sacrifice City of Traitors") was thrown away by the next land drop in 3 games out of 5 (Casse Terrain
 * against Avacyn), often for a land that makes less mana. General rule: don't play a land that makes one of our
 * permanents sacrifice itself, unless the new land makes more mana than the one lost.
 */
public final class LandDropSacAi {
    private LandDropSacAi() { }

    public static boolean losesMana(final Player ai, final Card land) {
        for (final Card p : ai.getCardsIn(ZoneType.Battlefield)) {
            if (p.equals(land)) {
                continue;
            }
            for (final Trigger t : p.getTriggers()) {
                if (t.getMode() != TriggerType.LandPlayed || !sacrificesHost(t.ensureAbility())) {
                    continue;
                }
                if (t.hasParam("ValidCard") && !land.isValid(t.getParam("ValidCard").split(","), ai, p, null)) {
                    continue;
                }
                if (manaMade(land) <= manaMade(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sacrificesHost(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.Sacrifice) {
            return false;
        }
        return "Self".equals(sa.getParamOrDefault("SacValid", "Self")) && !sa.hasParam("Optional");
    }

    private static int manaMade(final Card c) {
        int best = 0;
        for (final SpellAbility m : c.getManaAbilities()) {
            int amount = 1;
            if (m.hasParam("Amount")) {
                try {
                    amount = AbilityUtils.calculateAmount(c, m.getParam("Amount"), m);
                } catch (RuntimeException e) {
                    amount = 1;
                }
            }
            best = Math.max(best, amount);
        }
        return best;
    }
}
