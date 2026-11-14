package forge.ai;

import forge.game.card.Card;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;

/**
 * magic-tools: rule 234. An aura whose script has no AttachAILogic is never cast: AttachAi finds no logic and gives
 * up, whatever the board (Dog Umbra, 0 casts and 2320 refusals in 60 games of ComAura against Simic; Deviant Glee;
 * Petrify; about twenty cards in all). The script already says what the aura does, so the logic is read from it:
 * a bonus or a keyword on the enchanted creature is a Pump, a restriction or a malus on it is a Curse.
 * A script with its own logic is left alone, and an effect that cannot be read here (triggers, Set P/T, X) stays
 * without logic rather than being guessed.
 */
public final class UnscriptedAuraAi {
    private UnscriptedAuraAi() { }

    public static String infer(final Card aura) {
        if (aura == null || !aura.isAura()) {
            return null;
        }
        boolean bonus = false;
        boolean malus = false;
        for (final StaticAbility st : aura.getStaticAbilities()) {
            if (st.checkMode(StaticAbilityMode.CantAttack) || st.checkMode(StaticAbilityMode.CantBlock)) {
                final String valid = st.getParam("ValidCard");
                malus |= valid != null && valid.contains("EnchantedBy");
            }
            if (!st.checkMode(StaticAbilityMode.Continuous) || !"Enchanted".equals(st.getParam("AffectedDefined"))) {
                continue;
            }
            final int total = amount(st.getParam("AddPower")) + amount(st.getParam("AddToughness"));
            bonus |= total > 0 || st.hasParam("AddKeyword");
            malus |= total < 0;
        }
        // a bonus wins: Mishra's Domination gets +2/+2 on your creature and only restricts the opponent's
        return bonus ? "Pump" : malus ? "Curse" : null;
    }

    private static int amount(final String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            return 0;
        }
    }
}
