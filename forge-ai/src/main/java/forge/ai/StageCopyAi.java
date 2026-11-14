package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardUtil;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: Thespian's Stage (Ashnod, with Cloudpost, Glimmerpost and Trenchpost). CloneAi only
 * targets with AILogic CloneBestCreature and only acts in the opponent's declare attackers step,
 * so the Stage stayed a colorless land. A Stage that copies a Locus while we control a Cloudpost
 * adds one mana to each Cloudpost; copying Cloudpost itself comes first (tester, 24/10).
 * Copy once, in our second main phase or at the opponent's end step, never a land without gain.
 */
public final class StageCopyAi {
    private StageCopyAi() { }

    public static boolean applies(final SpellAbility sa) {
        return sa.getApi() == ApiType.Clone && sa.usesTargeting() && sa.hasParam("GainThisAbility")
                && sa.getHostCard().isLand();
    }

    public static boolean phaseOk(final Player ai, final PhaseHandler ph) {
        return ph.is(PhaseType.MAIN2, ai) || (ph.is(PhaseType.END_OF_TURN) && ph.getPlayerTurn().isOpponentOf(ai));
    }

    private static boolean isLocus(final Card c) {
        return c.getType().hasSubtype("Locus");
    }

    private static boolean isCloudpost(final Card c) {
        return "Cloudpost".equals(c.getName());
    }

    public static Card chooseTarget(final Player ai, final SpellAbility sa) {
        final Card host = sa.getHostCard();
        if (isLocus(host)) {
            return null;
        }
        final boolean ownCloudpost = ai.getCardsIn(ZoneType.Battlefield).anyMatch(StageCopyAi::isCloudpost);
        CardCollection targets = CardUtil.getValidCardsToTarget(sa);
        targets.remove(host);
        targets = CardLists.filter(targets, c -> isLocus(c) && (ownCloudpost || isCloudpost(c)));
        if (targets.isEmpty()) {
            return null;
        }
        final Card cloudpost = targets.stream().filter(StageCopyAi::isCloudpost).findFirst().orElse(null);
        return cloudpost != null ? cloudpost : targets.getFirst();
    }

    public static boolean setTarget(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        final Card target = chooseTarget(ai, sa);
        if (target == null) {
            return false;
        }
        sa.getTargets().add(target);
        return true;
    }
}
