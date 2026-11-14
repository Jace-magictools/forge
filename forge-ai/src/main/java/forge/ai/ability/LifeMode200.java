package forge.ai.ability;

import forge.ai.SpellApiToAi;
import forge.game.ability.ApiType;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.List;

/**
 * magic-tools: rule 200, with a healthy life total a modal choice does not take the life gain over looking at cards.
 * Stock CharmAi shuffles the modes and takes the first one willing; ScryAi says yes two times out of three, GainLifeAi
 * always: tester 02/11 (a test game), Charming Prince on turn 3 at 20 life gained 3 instead of scrying 2.
 */
final class LifeMode200 {
    private LifeMode200() { }

    /** The mode to take instead of the gain, null to leave the choice to the usual order. */
    static AbilitySub pick(final Player ai, final SpellAbility sa, final List<AbilitySub> choices, final int num) {
        if (num != 1 || ai.getLife() < 10 || ai.getCardsIn(ZoneType.Library).size() <= 3 || !ai.equals(sa.getActivatingPlayer())
                || choices.stream().noneMatch(m -> m.getApi() == ApiType.GainLife)) {
            return null;
        }
        AbilitySub look = null;
        for (final AbilitySub mode : choices) {
            if (mode.getApi() == ApiType.GainLife) {
                continue;
            }
            if (isLook(mode)) {
                look = mode;
                continue;
            }
            mode.setActivatingPlayer(ai);
            if (SpellApiToAi.Converter.get(mode).canPlayWithSubs(ai, mode).willingToPlay()) {
                return mode;
            }
        }
        return look;
    }

    private static boolean isLook(final AbilitySub mode) {
        final ApiType api = mode.getApi();
        return (api == ApiType.Scry || api == ApiType.Surveil || api == ApiType.Draw) && !mode.usesTargeting();
    }
}
