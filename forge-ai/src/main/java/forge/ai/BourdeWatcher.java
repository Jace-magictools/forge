package forge.ai;

import com.google.common.eventbus.Subscribe;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.ability.ApiType;
import forge.game.event.GameEventBlockersDeclared;
import forge.game.event.GameEventGameOutcome;
import forge.game.event.GameEventTurnPhase;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordInterface;
import forge.game.staticability.StaticAbilityMustBlock;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.GameLossReason;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * magic-tools : chasse aux bourdes (consigne de tester du 27/10). Écoute les phases d'une partie simulée
 * et écrit une ligne « BOURDE|type|tour|joueur|détail » sur la sortie quand l'IA rate quelque chose d'évident :
 * <ul>
 * <li>mana : à son étape de fin, le joueur pouvait payer un permanent de sa main (sans cible, sans X, sans coût autre que le mana, rituel de
 * rythme rapide exclu) ; l'IA garde parfois une carte exprès, la ligne dit lesquelles pour juger ensuite ; les gardes voulues
 * (script, effet d'arrivée sans bonne cible, contresort, règle 93 : voir gardeVoulue) vont sur une ligne GARDE, hors des bourdes ;</li>
 * <li>letal : avant les bloqueurs, ses créatures capables d'attaquer suffisaient à tuer un adversaire qui n'a
 * aucun bloqueur possible, et il a attaqué avec moins ;</li>
 * <li>removal : le perdant meurt avec en main un sort de removal ciblé (détruire, exiler ou renvoyer un permanent, blesser, faire sacrifier ; contresorts exclus)
 * qu'il avait les terrains pour payer, avec une cible adverse légale ; la ligne dit depuis quel tour il l'avait en main. Seuls comptent les sorts
 * qu'il aurait pu lancer à au moins une étape de fin (mana encore libre, cible adverse, vitesse rituel seulement à son tour) ;
 * pioché trop tard ou toujours sans mana : ligne GARDE « removal sans mana », hors des bourdes ;</li>
 * <li>meule : le perdant a perdu en piochant dans une bibliothèque vide ; la ligne dit l'adversaire et ce qui restait en jeu, pour départager l'auto-meule de la meule adverse.</li>
 * <li>blocage : un bloqueur meurt au combat sans tuer aucune des créatures qu'il bloquait, alors que le défenseur aurait survécu
 * sans bloquer du tout (vie au-dessus des dégâts de toutes les attaquantes) ; la ligne dit la vie, les dégâts et qui bloquait quoi
 * (blocages pour protéger un planeswalker ou contre une créature infectieuse exclus). Un blocage qui, à la déclaration,
 * ne devait pas coûter le bloqueur ou devait tuer l'attaquante (tour de combat adverse ensuite) n'est pas une bourde :
 * ligne BLOCAGE-SURPRISE, hors des bourdes. Exclus aussi : blocage imposé (« doit être bloquée si possible », leurre, provocation,
 * bloque à chaque combat), attaquante toxique ou poisonneuse (poison), bloqueur qui gagne à mourir (SacMe, persistance, ténacité).</li>
 * </ul>
 * Branché par SimulateMatch quand -Dforge.bourdes=true ; ne change rien au jeu.
 */
public class BourdeWatcher {
    private final Game game;

    private PartieRecorder recorder;

    public BourdeWatcher(Game game) {
        this.game = game;
    }

    /** Range aussi chaque bourde dans la partie enregistrée (visionneur du site). */
    public BourdeWatcher avec(PartieRecorder r) {
        this.recorder = r;
        return this;
    }

    @Subscribe
    public void recieve(GameEventTurnPhase ev) {
        try {
            Player p = game.getPhaseHandler().getPlayerTurn();
            if (p == null || game.isGameOver()) {
                return;
            }
            if (ev.phase() == PhaseType.COMBAT_DECLARE_BLOCKERS) {
                letal(p);
            } else if (ev.phase() == PhaseType.COMBAT_END) {
                blocages();
            } else if (ev.phase() == PhaseType.END_OF_TURN) {
                mana(p);
                for (Player q : game.getRegisteredPlayers()) {
                    removalOuverts(q, q == p);
                }
            }
        } catch (RuntimeException e) {
            // un relevé ne doit jamais casser la partie
        }
    }

    /** Blocages relevés à la déclaration : bloqueur, créatures bloquées, vie du défenseur, dégâts de toutes ses attaquantes. */
    private record Blocage(Card bloqueur, List<Card> bloquees, List<Card> attaquesJoueur, Player defenseur, int vie, int degats, boolean prevu, boolean excuse) {}
    private final List<Blocage> blocages = new ArrayList<>();

    @Subscribe
    public void recieve(GameEventBlockersDeclared ev) {
        try {
            blocages.clear();
            Combat combat = game.getPhaseHandler().getCombat();
            if (combat == null) {
                return;
            }
            for (Player d : game.getRegisteredPlayers()) {
                List<Card> attaquesJoueur = new ArrayList<>(combat.getAttackersOf(d));
                int degats = degatsSansBloquer(d, combat);
                for (Card b : combat.getAllBlockers()) {
                    if (b.getController() != d) {
                        continue;
                    }
                    // à la déclaration, d'après le plateau seul : le bloqueur devait-il mourir sans rien tuer ?
                    boolean prevu = ComputerUtilCombat.blockerWouldBeDestroyed(d, b, combat);
                    boolean excuse = veutMourir(b);
                    for (Card a : combat.getAttackersBlockedBy(b)) {
                        if (ComputerUtilCombat.attackerWouldBeDestroyed(d, a, combat)) {
                            prevu = false;
                        }
                        excuse |= impose(a, b);
                    }
                    blocages.add(new Blocage(b, new ArrayList<>(combat.getAttackersBlockedBy(b)), attaquesJoueur, d, d.getLife(), degats, prevu, excuse));
                }
            }
        } catch (RuntimeException e) {
            // un relevé ne doit jamais casser la partie
        }
    }

    /** Dégâts des attaquants de d s'il ne bloquait rien : double initiative (Rafiq, Winota…) comprise, sinon un blocage qui sauve la vie passe pour une bourde. */
    static int degatsSansBloquer(Player d, Combat combat) {
        int degats = 0;
        for (Card a : combat.getAttackersOf(d)) {
            degats += Math.max(0, ComputerUtilCombat.damageIfUnblocked(a, d, combat, true));
        }
        return degats;
    }

    /** Le bloqueur gagne à mourir : SacMe (œuf de dragon…), persistance ou ténacité encore prêtes. */
    private static boolean veutMourir(Card b) {
        // part de toute façon à la fin du tour (jetons de Force of Rage, AtEOT : relevé v322, Precipitron) : bloquer ne coûte rien
        return b.hasSVar("SacMe") || b.hasSVar("EndOfTurnLeavePlay")
                || (b.hasKeyword(Keyword.PERSIST) && b.getCounters(CounterEnumType.M1M1) == 0)
                || (b.hasKeyword(Keyword.UNDYING) && b.getCounters(CounterEnumType.P1P1) == 0);
    }

    /** Blocage imposé par les règles, ou contre le poison : pas un choix de l'IA à juger sur la vie. */
    private static boolean impose(Card a, Card b) {
        if (a.hasKeyword(Keyword.TOXIC) || a.hasKeyword(Keyword.POISONOUS) || a.hasKeyword(Keyword.INFECT)) {
            return true;
        }
        if (a.hasStartOfKeyword("CARDNAME must be blocked") || a.hasKeyword("All creatures able to block CARDNAME do so.")
                || b.getMustBlockCards().contains(a) || StaticAbilityMustBlock.blocksEachCombatIfAble(b)) {
            return true;
        }
        for (KeywordInterface inst : a.getKeywords()) {
            if (inst.getOriginal().startsWith("MustBeBlockedBy")) {
                return true;
            }
        }
        return false;
    }

    /** À la fin du combat : bloqueurs morts sans rien tuer alors que le défenseur ne risquait pas de mourir ce tour-ci. */
    private void blocages() {
        for (Blocage b : blocages) {
            if (b.vie() <= b.degats() || b.bloquees().isEmpty() || b.excuse()) {
                continue;
            }
            if (enJeu(b.bloqueur())) {
                continue;
            }
            boolean tue = false;
            List<String> noms = new ArrayList<>();
            for (Card a : b.bloquees()) {
                // bloquer pour protéger un planeswalker, ou contre l'infection (poison, pas la vie), peut se défendre
                if (a.hasKeyword(forge.game.keyword.Keyword.INFECT) || !b.attaquesJoueur().contains(a)) {
                    tue = true;
                }
                noms.add(a.getName());
                if (!enJeu(a)) {
                    tue = true;
                }
            }
            if (!tue && !b.prevu()) {
                System.out.println("BLOCAGE-SURPRISE|" + game.getPhaseHandler().getTurn() + "|" + b.defenseur().getName() + "|vie " + b.vie()
                        + ", degats " + b.degats() + ", " + b.bloqueur().getName() + " bloque " + String.join(" ; ", noms));
            } else if (!tue) {
                ecrit("blocage", b.defenseur(), "vie " + b.vie() + ", degats " + b.degats() + ", " + b.bloqueur().getName()
                        + " bloque " + String.join(" ; ", noms));
            }
        }
        blocages.clear();
    }

    /** Toujours le même objet en jeu (une carte qui change de zone devient un autre objet, un jeton disparaît). */
    private boolean enJeu(Card c) {
        Card s = game.getCardState(c, null);
        return s != null && s.isInZone(ZoneType.Battlefield);
    }

    @Subscribe
    public void recieve(GameEventGameOutcome ev) {
        try {
            for (Player p : game.getRegisteredPlayers()) {
                if (p.getOutcome() == null || p.getOutcome().lossState == null) {
                    continue;
                }
                GameLossReason why = p.getOutcome().lossState;
                List<String> gardes = new ArrayList<>();
                List<String> sansMana = new ArrayList<>();
                for (Card c : removalGardes(p)) {
                    int n = ouverts.getOrDefault(c.getId(), 0);
                    String d = c.getName() + " [" + removalSa(p, c, false).getApi() + ", " + cmc(removalSa(p, c, false)) + "/" + p.getLandsInPlay().size()
                            + ", en main depuis le tour " + c.getTurnInZone() + (n > 0 ? ", lançable à " + n + " fin(s) de tour" : "") + "]";
                    (n > 0 ? gardes : sansMana).add(d);
                }
                if (!gardes.isEmpty()) {
                    ecrit("removal", p, why + " ; " + String.join(" ; ", gardes));
                }
                if (!sansMana.isEmpty()) {
                    System.out.println("GARDE|" + game.getPhaseHandler().getTurn() + "|" + p.getName() + "|removal sans mana ; " + String.join(" ; ", sansMana));
                }
                if (why == GameLossReason.Milled) {
                    List<String> enJeu = new ArrayList<>();
                    for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
                        if (!c.isLand()) {
                            enJeu.add(c.getName());
                        }
                    }
                    List<String> adv = new ArrayList<>();
                    for (Player o : game.getRegisteredPlayers()) {
                        if (o != p) {
                            adv.add(o.getName());
                        }
                    }
                    ecrit("meule", p, "contre " + String.join(" ; ", adv) + ", en jeu " + String.join(" ; ", enJeu));
                }
            }
        } catch (RuntimeException e) {
            // un relevé ne doit jamais casser la partie
        }
    }

    private static final java.util.EnumSet<ApiType> REMOVAL = java.util.EnumSet.of(ApiType.Destroy, ApiType.DealDamage,
            ApiType.ChangeZone, ApiType.Sacrifice);

    /** Étapes de fin où chaque sort de removal en main (id de carte) était lançable : mana libre et cible adverse. */
    private final Map<Integer, Integer> ouverts = new HashMap<>();

    /** À chaque étape de fin, pour chaque joueur : ses sorts de removal qu'il pouvait lancer là (rituel seulement à son tour). */
    void removalOuverts(Player q, boolean sonTour) {
        for (Card c : q.getCardsIn(ZoneType.Hand)) {
            if (c.isLand()) {
                continue;
            }
            SpellAbility sa = removalSa(q, c, true);
            if (sa == null || (!sonTour && !c.isInstant() && !c.hasKeyword(Keyword.FLASH))) {
                continue;
            }
            // sort à X (Fireball, Crackle with Power) : lançable seulement si X ≥ 2 est payable, X = 0 ou 1 n'est pas un removal
            int x = sa.getPayCosts().getTotalMana().countX();
            if (ComputerUtilMana.canPayManaCost(sa, q, 2 * x, false)) {
                ouverts.merge(c.getId(), 1, Integer::sum);
            }
        }
    }

    int ouverts(Card c) {
        return ouverts.getOrDefault(c.getId(), 0);
    }

    private static int cmc(SpellAbility sa) {
        return sa.getPayCosts().getTotalMana().getCMC();
    }

    /**
     * Le sort de removal ciblé de la carte (détruire, blesser, exiler ou renvoyer un permanent, faire sacrifier) qui a une cible adverse
     * légale, et que les terrains du joueur suffisent à payer ; null sinon. viaMana : on vérifie le mana ailleurs (canPayManaCost).
     */
    static SpellAbility removalSa(Player p, Card c, boolean viaMana) {
        int terrains = p.getLandsInPlay().size();
        for (SpellAbility sa : c.getSpells()) {
            if (!sa.isSpell() || sa.getApi() == null || !REMOVAL.contains(sa.getApi())) {
                continue;
            }
            if (sa.getApi() == ApiType.ChangeZone && !"Battlefield".equals(sa.getParam("Origin"))) {
                continue;
            }
            if (!sa.usesTargeting()) {
                continue;
            }
            if (!viaMana && cmc(sa) > terrains) {
                continue;
            }
            // une cible adverse légale (Doom Blade face à des créatures noires n'en a pas)
            sa.setActivatingPlayer(p);
            // sort interdit depuis la main (Experimental Frenzy : 4 des 10 Lightning Bolt « gardés » au v15)
            if (interditDepuisLaMain(sa, c, p)) {
                continue;
            }
            // coût additionnel payable : Goblin Grenade sans gobelin en jeu n'est pas lançable (65 « morts avec » au v14)
            if (!forge.game.cost.CostPayment.canPayAdditionalCosts(sa.getPayCosts(), sa, false)) {
                continue;
            }
            for (forge.game.GameEntity e : sa.getTargetRestrictions().getAllCandidates(sa, false)) {
                if ((e instanceof Player o ? p.isOpponentOf(o) : e instanceof Card t && p.isOpponentOf(t.getController())) && utile(sa, e)) {
                    return sa;
                }
            }
        }
        return null;
    }

    /** CantBeCast (Origin$ Hand) ne se lit que pendant un lancement (getCastFrom) : on le simule, sans rien laisser sur la carte. */
    private static boolean interditDepuisLaMain(SpellAbility sa, Card c, Player p) {
        forge.game.zone.Zone from = c.getCastFrom();
        SpellAbility castSa = c.getCastSA();
        try {
            c.setCastFrom(c.getZone());
            return forge.game.staticability.StaticAbilityCantBeCast.cantBeCastAbility(sa, c, p);
        } finally {
            c.setCastFrom(from);
            c.setCastSA(castSa);
        }
    }

    /**
     * Une cible qui vaut le sort de dégâts : créature ou planeswalker adverse qu'il tue, ou adversaire à portée (vie ≤ 3 × dégâts).
     * Tournoi v15 (31/10) : Galvanic Blast sans métal (2 dégâts) gardé 15 tours face à Wall of Glare 0/5, Grand Arbiter 2/3 et
     * Geist (hexproof), adversaire à 22 : rien à tuer, compté « lançable ». Sorts à X et autres effets : toute cible adverse.
     */
    static boolean utile(SpellAbility sa, forge.game.GameEntity e) {
        if (sa.getApi() != ApiType.DealDamage || sa.getPayCosts().getTotalMana().countX() > 0 || !sa.hasParam("NumDmg")) {
            return true;
        }
        int dmg = forge.game.ability.AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("NumDmg"), sa);
        if (dmg <= 0) {
            return false;
        }
        if (e instanceof Player o) {
            return o.getLife() <= 3 * dmg;
        }
        Card t = (Card) e;
        if (t.isPlaneswalker() && t.getCurrentLoyalty() <= dmg) {
            return true;
        }
        return t.isCreature() && ComputerUtilCombat.getEnoughDamageToKill(t, dmg, sa.getHostCard(), false) <= dmg;
    }

    /** Sorts de removal ciblés restés en main, que les terrains du joueur suffisaient à payer, avec une cible adverse. */
    static List<Card> removalGardes(Player p) {
        List<Card> gardes = new ArrayList<>();
        for (Card c : p.getCardsIn(ZoneType.Hand)) {
            if (!c.isLand() && removalSa(p, c, false) != null) {
                gardes.add(c);
            }
        }
        return gardes;
    }

    private void ecrit(String type, Player p, String detail) {
        System.out.println("BOURDE|" + type + "|" + game.getPhaseHandler().getTurn() + "|" + p.getName() + "|" + detail);
        if (recorder != null) {
            recorder.bourde(type, p, detail);
        }
    }

    private void letal(Player p) {
        Combat combat = game.getPhaseHandler().getCombat();
        for (Player opp : p.getOpponents()) {
            if (!opp.canLoseLife() || opp.getLife() <= 0) {
                continue;
            }
            boolean bloqueur = false;
            for (Card c : opp.getCreaturesInPlay()) {
                if (CombatUtil.canBlock(c)) {
                    bloqueur = true;
                    break;
                }
            }
            if (bloqueur) {
                continue;
            }
            int possible = 0;
            int attaque = 0;
            List<String> restees = new ArrayList<>();
            for (Card c : p.getCreaturesInPlay()) {
                boolean attaquant = combat != null && combat.getAttackersOf(opp).contains(c);
                if (attaquant) {
                    attaque += Math.max(0, c.getNetCombatDamage());
                    possible += Math.max(0, c.getNetCombatDamage());
                } else if (combat == null || !combat.isAttacking(c)) {
                    if (c.isUntapped() && CombatUtil.canAttack(c, opp)) {
                        possible += Math.max(0, c.getNetCombatDamage());
                        restees.add(c.getName());
                    }
                }
            }
            if (possible >= opp.getLife() && attaque < opp.getLife()) {
                ecrit("letal", p, "vie " + opp.getLife() + ", possible " + possible + ", attaque " + attaque
                        + ", restees " + String.join(" ; ", restees));
            }
        }
    }

    void mana(Player p) {
        List<String> jouables = new ArrayList<>();
        List<String> voulues = new ArrayList<>();
        for (Card c : p.getCardsIn(ZoneType.Hand)) {
            if (c.isLand() || c.isInstant() || c.hasKeyword(forge.game.keyword.Keyword.FLASH)) {
                continue;
            }
            if (c.getType().isLegendary() && !p.getCardsIn(ZoneType.Battlefield, c.getName()).isEmpty()) {
                continue;
            }
            for (SpellAbility sa : variantesCout(c)) {
                if (!sa.isSpell() || sa.usesTargeting() || sa.costHasX() || !sa.getPayCosts().isOnlyManaCost() || sa.getPayCosts().getTotalMana().getCMC() == 0) {
                    continue;
                }
                if (!c.getType().isPermanent()) {
                    continue;
                }
                sa.setActivatingPlayer(p);
                // interdit de lancement (Serra Avenger les trois premiers tours, Rule of Law…, et depuis la main sous
                // Experimental Frenzy : 1er faux positif « mana » du relevé v23, Frénésie) : pas une bourde
                if (interditDepuisLaMain(sa, c, p)) {
                    continue;
                }
                if (ComputerUtilMana.canPayManaCost(sa, p, 0, false)) {
                    String r = raison(p, c, sa);
                    (gardeVoulue(r) ? voulues : jouables).add(c.getName() + " [" + r + "]");
                    break;
                }
            }
        }
        if (!jouables.isEmpty()) {
            ecrit("mana", p, String.join(" ; ", jouables));
        }
        if (!voulues.isEmpty()) {
            System.out.println("GARDE|" + game.getPhaseHandler().getTurn() + "|" + p.getName() + "|" + String.join(" ; ", voulues));
        }
    }

    /**
     * Les sorts de la carte tels qu'ils se lancent vraiment : un coût additionnel au choix (AlternateAdditionalCost :
     * Bayou Groff « sacrifie une créature ou paie {3} », Dusk Rose Reliquary « sacrifie un artefact ou une créature »)
     * remplace le sort de base par une variante par coût ; on ne garde que les variantes payables en mana seul
     * (Bayou Groff à {3}{1}{G}), un sacrifice n'est pas jugé ici.
     */
    static List<SpellAbility> variantesCout(Card c) {
        String kw = null;
        for (KeywordInterface inst : c.getKeywords()) {
            if (inst.getOriginal().startsWith("AlternateAdditionalCost")) {
                kw = inst.getOriginal();
                break;
            }
        }
        if (kw == null) {
            return new ArrayList<>(c.getSpells());
        }
        List<SpellAbility> out = new ArrayList<>();
        for (SpellAbility sa : c.getSpells()) {
            if (!sa.isSpell()) {
                continue;
            }
            for (String s : kw.split(":", 2)[1].split(":")) {
                forge.game.cost.Cost cout = new forge.game.cost.Cost(s, false);
                if (!cout.isOnlyManaCost()) {
                    continue;
                }
                SpellAbility v = sa.copy();
                v.getPayCosts().add(cout);
                out.add(v);
            }
        }
        return out;
    }

    /**
     * Carte gardée exprès, pas une bourde : condition du script non remplie (NeedsToPlay, cartes requises), effet d'arrivée sans
     * bonne cible (Journey to Nowhere sans créature adverse, Accursed Marauder contre un plateau vide), créature qui mourrait
     * en arrivant, copie inutile (NonStacking), mana gardé pour un contresort (règle 22), créature retenue contre un balayage (règle 93).
     */
    static boolean gardeVoulue(String raison) {
        return raison.startsWith("garde ") || raison.equals("retenue-93") || raison.equals("NeedsToPlayCriteriaNotMet")
                || raison.equals("MissingNeededCards") || raison.equals("BadEtbEffects")
                || raison.equals("WouldBecomeZeroToughnessCreature") || raison.equals("NonStacking");
    }

    /**
     * Ce que dit l'IA de la carte, rejouée à froid à l'étape de fin : la décision de son API (checkApiLogic, restrictions de moment sautées) (CantPlayAi, WouldDestroyLegend…),
     * « NonStacking » si une copie est déjà en jeu, la décision de saSideEffects (« garde X » : règle 22, mana gardé pour un contresort ; BadEtbEffects, NeedsToPlay…), « ETB » si l'API accepte mais qu'un déclencheur d'arrivée ciblé la retient,
     * « RemoveDeck » d'abord si la carte est marquée AI:RemoveDeck:All (jamais jouée, sauf un permanent lancé de la main : règle 46), « api-ok » sinon (la carte est refusée ailleurs : priorité, réserve de mana, autre sort préféré).
     */
    private String raison(Player p, Card c, SpellAbility sa) {
        try {
            // marquée AI:RemoveDeck:All : retirée d'office des choix de l'IA (AiController.getSpellAbilityToPlay)
            if (ComputerUtilCard.isCardRemAIDeck(c) && !AiController.isRemovedPermanentSpell(sa) && !AiController.isRemovedPermanentAbility(sa)
                    && !AiController.isRemovedSimpleSpell(sa)) {
                return "RemoveDeck";
            }
            if (sa.getApi() == null) {
                return "sans-api";
            }
            // retenues de nos propres règles (CantPlayAi sinon) : sous notre Culling Scales (77), rien à empreindre (96),
            // Culling Scales qui ne toucherait que nous
            if (CullingScalesAi.wouldBeCulled(p, c)) {
                return "garde règle 77";
            }
            if (!ImprintFirstAi.worthCasting(p, c)) {
                return "garde règle 96";
            }
            if (CullingScalesAi.applies(c) && !CullingScalesAi.worthCasting(p, c)) {
                return "garde Culling Scales";
            }
            if (ArrivalPayoffAi.holdBack(p, sa, game.getPhaseHandler())) {
                return "garde règle 103";
            }
            if ((HarmfulArrival247.refuse(p, sa) || HarmfulArrival247.holdForVictim(p, sa))) {
                return "garde règle 247";
            }
            // règle 225 : Necromancy sans créature qui puisse la porter (Akroma, protection contre le noir, seule au cimetière)
            if (c.isEnchantment() && !ReanimatorAi.hasCreatureToWearIt(p, c)) {
                return "garde règle 225";
            }
            // règle 195 : célérité qui repart en fin de tour, lancée seulement si elle attaque (l'étape de fin ne le dit plus)
            if (LeavesAtEndStepAi.applies(c)) {
                return "garde règle 195";
            }
            if (PingerVictim254.refuse(p, sa)) {
                return "règle 254";
            }
            // l'étape de fin interdit la vitesse rituel : on saute les restrictions de moment et on interroge la logique de l'API
            SpellAbilityAi api = SpellApiToAi.Converter.get(sa);
            if (sa.hasParam("AILogic") && !api.checkAiLogic(p, sa, sa.getParam("AILogic"))) {
                return "AILogic " + sa.getParam("AILogic");
            }
            AiAbilityDecision d = api.checkApiLogic(p, sa);
            if (!d.willingToPlay()) {
                // préférence écrite dans le script (Cemetery Gatekeeper : NeverCastIfLifeBelow$ 8)
                if (d.decision() == AiPlayDecision.CantPlayAi && c.hasSVar("AICastPreference")) {
                    return "garde AICastPreference";
                }
                return String.valueOf(d.decision());
            }
            // règle 93 : créature simple retenue quand le plateau est déjà gagné en largeur (balayage possible)
            if (HoldBackAi.holdBack(p, sa, game.getPhaseHandler())) {
                return "retenue-93";
            }
            if ("True".equals(c.getSVar("NonStackingEffect")) && ComputerUtilCard.isNonDisabledCardInPlay(p, c.getName())) {
                return "NonStacking";
            }
            if (p.getController() instanceof PlayerControllerAi pc) {
                AiPlayDecision side = pc.getAi().saSideEffects(c, sa);
                if (side != AiPlayDecision.WillPlay) {
                    Card garde = CounterReserveAi.counterKept(p, sa);
                    if (garde != null) {
                        return "garde " + garde.getName();
                    }
                    return side == AiPlayDecision.CurseEffects ? "CurseEffects " + causeCurse(p, sa) : String.valueOf(side);
                }
            }
            for (forge.game.trigger.Trigger t : c.getTriggers()) {
                if (t.getMode() == forge.game.trigger.TriggerType.ChangesZone && "Battlefield".equals(t.getParam("Destination"))
                        && t.ensureAbility() != null && t.ensureAbility().usesTargeting()) {
                    return "ETB";
                }
            }
            return "api-ok";
        } catch (RuntimeException e) {
            return "erreur";
        }
    }

    /** Laquelle des vérifications de saSideEffects rend CurseEffects : pioche forcée qui viderait la bibliothèque, morph, dégâts mortels, ou la carte en jeu à AICurseEffect. */
    private static String causeCurse(Player p, SpellAbility sa) {
        if (DeckOutAi.wouldDeckSelf(p, sa)) {
            return "deck-out (bibliothèque " + p.getCardsIn(ZoneType.Library).size() + ")";
        }
        if (!MorphAi.worthCastingFaceDown(sa)) {
            return "morph";
        }
        if (!sa.isLandAbility() && p.canLoseLife() && ComputerUtil.getDamageForPlaying(p, sa) >= p.getLife()) {
            return "dégâts mortels";
        }
        for (Card c : p.getGame().getCardsIn(ZoneType.Battlefield)) {
            if (c.hasSVar("AICurseEffect")) {
                return c.getName() + " (" + c.getSVar("AICurseEffect") + ")";
            }
        }
        return "?";
    }
}
