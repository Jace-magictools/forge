package forge.ai;

import org.apache.commons.lang3.StringUtils;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostSacrifice;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * magic-tools, règle 16 (règle générale) : un sort ou une capacité qui sacrifie un permanent pour aller chercher
 * en bibliothèque, sur le champ de bataille, une carte de valeur de mana « celle du sacrifié + N » est un Pod,
 * même sans AILogic dans son script (Neoform, grenouille, n'était jamais lancé : rien ne disait à l'IA quoi
 * sacrifier). Il passe par la logique SacAndUpgrade de Forge, avec la préférence de sacrifice tirée du coût.
 */
public final class SacUpgradeAi {
    private SacUpgradeAi() { }

    /** The sacrificed type ("Creature"...) when sa is an unmarked Pod effect, else null. */
    public static String unmarkedPodSacType(final SpellAbility sa) {
        if (sa == null || sa.hasParam("AILogic") || !"Library".equals(sa.getParam("Origin"))
                || !"Battlefield".equals(sa.getParam("Destination"))) {
            return null;
        }
        final Card source = sa.getHostCard();
        final String goal = sa.getParam("ChangeType");
        if (source == null || goal == null || !goal.contains(".cmc") || !goal.contains("X")
                || !StringUtils.startsWith(source.getSVar("X"), "Sacrificed$CardManaCost/Plus")) {
            return null;
        }
        final Cost cost = sa.getPayCosts();
        if (cost == null) {
            return null;
        }
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostSacrifice sac && !sac.payCostFromSource() && sac.getAmount() != null
                    && "1".equals(sac.getAmount())) {
                return sac.getType().split("/")[0];
            }
        }
        return null;
    }

    /**
     * magic-tools, règle 204 : notre seul sacrificateur gratuit, avec une créature à persistance ou à ténacité en main
     * ou en jeu, est une pièce de boucle posée : il ne paie pas une montée de Pod (retour de tester, Eldritch
     * Evolution sur Viscera Seer, Murderous Redcap en main). Le chemin qui assemble la boucle (PodComboAi.plan) passe avant.
     */
    public static boolean keepsOutlet(final Player ai, final Card c) {
        if (!PodComboAi.isOutlet(c)) {
            return false;
        }
        for (final Card other : ai.getCardsIn(ZoneType.Battlefield)) {
            if (other != c && PodComboAi.isOutlet(other)) {
                return false;
            }
        }
        for (final ZoneType zone : new ZoneType[] {ZoneType.Hand, ZoneType.Battlefield}) {
            for (final Card other : ai.getCardsIn(zone)) {
                if (other != c && other.isCreature() && (other.hasKeyword(Keyword.PERSIST) || other.hasKeyword(Keyword.UNDYING))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * magic-tools, règle 296 : la même chose pour le bouclier de persistance (Melira, Vizier of Remedies). Le seul en
     * jeu, avec une autre créature à persistance ou à ténacité en main ou en jeu, ne paie pas une montée de Pod
     * (Pod contre Drow, tour 8 : Birthing Pod sacrifiait Melira pour Safehold Elite, Seer en jeu, Redcap à venir ;
     * la boucle n'a plus jamais été assemblée). Un sacrifié qui ne tient aucun rôle de boucle part avant.
     */
    public static boolean keepsShield(final Player ai, final Card c) {
        if (!NewAi.on(ai, 296) || !PodComboAi.shieldsPersistLoose(c)) {
            return false;
        }
        for (final Card other : ai.getCardsIn(ZoneType.Battlefield)) {
            if (other != c && PodComboAi.shieldsPersistLoose(other)) {
                return false;
            }
        }
        for (final ZoneType zone : new ZoneType[] {ZoneType.Hand, ZoneType.Battlefield}) {
            for (final Card other : ai.getCardsIn(zone)) {
                if (other != c && other.isCreature() && (other.hasKeyword(Keyword.PERSIST) || other.hasKeyword(Keyword.UNDYING))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Règles 204 et 296 : le seul sacrificateur gratuit ou le seul bouclier de persistance reste en jeu. */
    public static boolean keepsLoopPiece(final Player ai, final Card c) {
        return keepsOutlet(ai, c) || keepsShield(ai, c);
    }

    /** The sacrifice choices of a Pod effect, without the outlet to keep (règle 204) nor the shield (règle 296). */
    public static CardCollection withoutKeptOutlet(final Player ai, final CardCollection choices, final SpellAbility sa) {
        if (sa == null || !"Library".equals(sa.getParam("Origin"))
                || !(StringUtils.startsWith(sa.getParam("AILogic"), "SacAndUpgrade") || unmarkedPodSacType(sa) != null)) {
            return choices;
        }
        final CardCollection kept = CardLists.filter(choices, c -> !keepsLoopPiece(ai, c));
        return kept.isEmpty() ? choices : kept;
    }

    /** Gives the card the sacrifice preference Forge's Pod logic reads; true when sa is an unmarked Pod effect. */
    public static boolean prepare(final SpellAbility sa) {
        final String sacType = unmarkedPodSacType(sa);
        if (sacType == null) {
            return false;
        }
        final Card source = sa.getHostCard();
        if (!source.hasSVar("AIPreference")) {
            source.setSVar("AIPreference", "SacCost$" + sacType);
        }
        return true;
    }
}
