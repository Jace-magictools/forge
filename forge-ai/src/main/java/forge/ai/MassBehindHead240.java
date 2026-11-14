package forge.ai;

import forge.game.ability.ApiType;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;

/**
 * magic-tools: rule 240, a sweeper written behind a preparation. Hour of Devastation, Spectacular Pileup and Exterminatus
 * open with "all creatures lose indestructible" (AnimateAll, refused without AILogic); Time Wipe opens with "return a
 * creature you control" (hidden ChangeZone, refused when nothing is worth saving). The head alone vetoed the spell: the
 * sweep was never judged, 0 cast at 4 life against 4 creatures (relevé v273, 120 decks). The head says yes, then the
 * sub-ability's own check (DamageAllAi, DestroyAllAi) decides.
 */
public final class MassBehindHead240 {
    private MassBehindHead240() {
    }

    public static boolean applies(SpellAbility sa) {
        final AbilitySub sub = sa.getSubAbility();
        if (sub == null || sa.isTrigger() || sa.hasParam("AILogic")) {
            return false;
        }
        if (sub.getApi() != ApiType.DamageAll && sub.getApi() != ApiType.DestroyAll) {
            return false;
        }
        if (sa.getApi() == ApiType.AnimateAll) {
            return true;
        }
        return sa.getApi() == ApiType.ChangeZone
                && sa.hasParam("Mandatory") && sa.isHidden()
                && "Battlefield".equals(sa.getParam("Origin"))
                && sa.getParamOrDefault("ChangeType", "").endsWith(".YouCtrl");
    }
}
