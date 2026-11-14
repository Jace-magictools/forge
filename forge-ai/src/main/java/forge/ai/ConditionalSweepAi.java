package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools: rule 231 (6/11, Toxico, relevé « mana perdu » : White Sun's Twilight ×2 en main six tours, six terrains
 * dégagés, de 21 à 5 points de vie, l'adversaire sans créature). Une sous-capacité sous condition ne doit pas refuser
 * le sort à elle seule : « si X vaut 5 ou plus, détruisez toutes les autres créatures » était jugée comme un balayage
 * même à X = 4, et il n'y avait rien à balayer. Trois cas lèvent le refus : la condition n'est pas remplie, le balayage
 * ne détruirait rien de chez nous, ou X peut s'arrêter juste sous le seuil (Martial Coup, Blot Out the Sky).
 */
public final class ConditionalSweepAi {
    private ConditionalSweepAi() { }

    public static boolean excuses(final Player ai, final AbilitySub ab) {
        if (ab.usesTargeting() || ab.getConditions() == null) {
            return false;
        }
        if (!ab.metConditions()) {
            return true;
        }
        if (ab.getApi() != ApiType.DestroyAll || !ab.hasParam("ConditionCheckSVar")) {
            return false;
        }
        final boolean nothingOfOurs = CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield),
                ab.getParamOrDefault("ValidCards", ""), ai, ab.getHostCard(), ab).isEmpty();
        return nothingOfOurs || capX(ab);
    }

    /** X stops just under the threshold of the sweep: the first part of the spell keeps almost everything. */
    private static boolean capX(final AbilitySub ab) {
        final String compare = ab.getParamOrDefault("ConditionSVarCompare", "");
        if (!compare.startsWith("GE") || !"Count$xPaid".equals(ab.getSVar(ab.getParam("ConditionCheckSVar")))) {
            return false;
        }
        final SpellAbility root = ab.getRootAbility();
        final int threshold = AbilityUtils.calculateAmount(ab.getHostCard(), compare.substring(2), ab);
        final Integer x = root.getXManaCostPaid();
        if (x == null || threshold < 2 || x < threshold) {
            return false;
        }
        root.setXManaCostPaid(threshold - 1);
        return true;
    }
}
