package forge.ai;

import java.util.Set;

import forge.LobbyPlayer;
import forge.game.Game;
import forge.game.player.IGameEntitiesFactory;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import org.tinylog.Logger;

public class LobbyPlayerAi extends LobbyPlayer implements IGameEntitiesFactory {

    private String aiProfile = "";
    private boolean rotateProfileEachGame;
    private AIOption option;

    public LobbyPlayerAi(String name, Set<AIOption> options) {
        super(name);
        if (options != null && !options.isEmpty()) {
            option = options.iterator().next();
        }
    }

    public void setAiProfile(String profileName) {
        Logger.debug("[AI Preferences] " + name + " using profile " + profileName);
        aiProfile = profileName;
    }
    public String getAiProfile() {
        return aiProfile;
    }

    public void setRotateProfileEachGame(boolean rotateProfileEachGame) {
        this.rotateProfileEachGame = rotateProfileEachGame;
    }

    /** magic-tools (09/11, essai B de tester) : FORGE_AI_SIM=full|hybrid passe l'IA en mode simulation de Forge
     *  (elle essaie ses coups sur une copie de la partie avant de choisir) ; FORGE_AI_SIM_DECK=texte le limite
     *  aux joueurs dont le nom contient ce texte (le nom du deck en mode sim). Sans variable : inchangé. */
    static AIOption simulationMode(AIOption option, String name) {
        String sim = System.getenv("FORGE_AI_SIM");
        if (sim == null || sim.isEmpty()) {
            return option;
        }
        String only = System.getenv("FORGE_AI_SIM_DECK");
        if (only != null && !only.isEmpty() && (name == null || !name.contains(only))) {
            return option;
        }
        return "hybrid".equalsIgnoreCase(sim) ? AIOption.USE_HYBRID_SIMULATION : AIOption.USE_FULL_SIMULATION;
    }

    private PlayerControllerAi createControllerFor(Player ai) {
        PlayerControllerAi result = new PlayerControllerAi(ai.getGame(), ai, this);
        result.getAi().setUseSimulation(simulationMode(option, getName()));
        return result;
    }

    @Override
    public PlayerController createMindSlaveController(Player master, Player slave) {
        return createControllerFor(slave);
    }

    @Override
    public Player createIngamePlayer(Game game, final int id) {
        Player ai = new Player(getName(), game, id);
        ai.setFirstController(createControllerFor(ai));

        if (rotateProfileEachGame) {
            setAiProfile(AiProfileUtil.getRandomProfile());
        }
        return ai;
    }

    @Override
    public void hear(LobbyPlayer player, String message) { /* Local AI is deaf. */ }
}