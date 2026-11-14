package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 255 (D145, tester 08/11, a test game): Blade Historian ("attacking creatures you
 * control have double strike") was in play, Alpine Watchdog 3/3 vigilance facing a lone Archon of Sun's Grace 3/4, and
 * the AI kept everybody home: double strike only shows once the creature is attacking, so the combat prediction saw a
 * 3/3 bouncing off a 3/4 instead of 3 first-strike + 3 that kills the Archon. General rule: when the AI predicts a
 * creature as an attacker that is not attacking yet, a continuous static whose Affected names "attacking" (Blade
 * Historian, Rafiq-like anthems, Kwende...) and adds Double Strike / First Strike counts as if it were attacking.
 * Only for the attacker side (a blocker never gets "attacking" statics).
 */
public final class AttackStrike255 {
    private AttackStrike255() { }

    /** true if a static "attacking ... AddKeyword kw" will give {@code kw} to {@code attacker} once it attacks */
    static boolean grantedWhenAttacking(final Card attacker, final String kw) {
        if (attacker == null || !attacker.isCreature()) {
            return false;
        }
        final Game game = attacker.getGame();
        if (game.getCombat() != null && game.getCombat().isAttacking(attacker)) {
            return false;
        }
        final CardCollectionView cards = CardCollection.combine(game.getCardsIn(ZoneType.Battlefield),
                game.getCardsIn(ZoneType.Command));
        for (final Card card : cards) {
            for (final StaticAbility stAb : card.getStaticAbilities()) {
                if (!stAb.checkMode(StaticAbilityMode.Continuous) || !stAb.hasParam("Affected")
                        || !stAb.hasParam("AddKeyword")) {
                    continue;
                }
                final String affected = stAb.getParam("Affected");
                if (!affected.contains("attacking")) {
                    continue;
                }
                boolean adds = false;
                for (final String k : stAb.getParam("AddKeyword").split(" & ")) {
                    if (k.trim().equals(kw)) {
                        adds = true;
                    }
                }
                if (!adds || !stAb.checkConditions()) {
                    continue;
                }
                if (stAb.hasParam("AffectedDefined")) {
                    final String def = stAb.getParam("AffectedDefined");
                    if ("Self".equals(def)) {
                        if (!attacker.equals(card)) {
                            continue;
                        }
                    } else if ("Equipped".equals(def) || "Enchanted".equals(def)) {
                        if (!attacker.equals(card.getAttachedTo())) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                if (attacker.isValid(stripAttacking(affected).split(","), card.getController(), card, stAb)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** the Affected restriction without its "attacking" property (Creature.attacking+YouCtrl -> Creature.YouCtrl) */
    static String stripAttacking(final String affected) {
        return affected.replaceAll("\\+attacking( [A-Za-z]+)?", "")
                .replaceAll("\\.attacking( [A-Za-z]+)?\\+", ".")
                .replaceAll("\\.attacking( [A-Za-z]+)?", "");
    }

    /** double strike of {@code attacker} as an attacker, statics "attacking" included */
    public static boolean hasDoubleStrike(final Card attacker) {
        return attacker.hasDoubleStrike() || grantedWhenAttacking(attacker, "Double Strike");
    }

    /** first or double strike of {@code attacker} as an attacker, statics "attacking" included */
    public static boolean hasFirstStrike(final Card attacker) {
        return attacker.hasFirstStrike() || attacker.hasDoubleStrike()
                || grantedWhenAttacking(attacker, "Double Strike") || grantedWhenAttacking(attacker, "First Strike");
    }

    /**
     * Second half of rule 255: a vigilant attacker that kills any single blocker and is worth less than every blocker
     * that could kill it attacks: no defensive cost, and the worst case is an even or better trade.
     */
    public static boolean vigilantTrade(final Card attacker, final boolean canKillAll, final boolean worthLessThanKillers) {
        return attacker.hasKeyword(forge.game.keyword.Keyword.VIGILANCE) && canKillAll && worthLessThanKillers;
    }
}
