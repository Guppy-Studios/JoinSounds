package com.tenshiku.joinsounds;

import com.tenshiku.joinsounds.commands.JoinSoundCommand;
import com.tenshiku.joinsounds.listeners.PlayerJoinListener;
import com.tenshiku.joinsounds.managers.ConfigManager;
import com.tenshiku.joinsounds.managers.PlayerDataManager;
import com.tenshiku.joinsounds.managers.SoundManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class JoinSounds extends JavaPlugin {

    private static JoinSounds instance;

    private ConfigManager configManager;
    private SoundManager soundManager;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Starting JoinSounds plugin...");

        // Check if Nexo is loaded
        if (getServer().getPluginManager().getPlugin("Nexo") == null) {
            getLogger().severe("Nexo plugin not found! This plugin requires Nexo to function properly.");
            getLogger().severe("Disabling JoinSounds...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers in order
        try {
            this.configManager = new ConfigManager(this);
            this.soundManager = new SoundManager(this);
            this.playerDataManager = new PlayerDataManager(this);

            getLogger().info("Managers initialized successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize managers: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Load configurations
        try {
            configManager.loadConfigs();
            getLogger().info("Configurations loaded!");
        } catch (Exception e) {
            getLogger().severe("Failed to load configurations: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register commands
        try {
            JoinSoundCommand commandExecutor = new JoinSoundCommand(this);
            org.bukkit.command.PluginCommand command = getCommand("joinsound");
            if (command != null) {
                command.setExecutor(commandExecutor);
                command.setTabCompleter(commandExecutor);
                getLogger().info("Commands registered!");
            } else {
                getLogger().severe("Failed to register joinsound command - not found in plugin.yml");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
            e.printStackTrace();
        }

        // Register listeners
        try {
            getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
            getLogger().info("Event listeners registered!");
        } catch (Exception e) {
            getLogger().severe("Failed to register event listeners: " + e.getMessage());
            e.printStackTrace();
        }

        // Plugin startup complete
        getLogger().info("JoinSounds plugin has been enabled successfully!");
        getLogger().info("Nexo integration active!");

        // Display some stats
        if (configManager.isDebugMode()) {
            getLogger().info("Debug mode is enabled");
            getLogger().info("Loaded " + soundManager.getAvailableSoundIds().size() + " sounds");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling JoinSounds plugin...");

        // Save any pending player data
        if (playerDataManager != null) {
            try {
                playerDataManager.saveAll();
                getLogger().info("Player data saved successfully!");
            } catch (Exception e) {
                getLogger().severe("Failed to save player data: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Clear static instance
        instance = null;

        getLogger().info("JoinSounds plugin has been disabled!");
    }

    /**
     * Get the main plugin instance
     * @return JoinSounds instance
     */
    public static JoinSounds getInstance() {
        return instance;
    }

    /**
     * Get the configuration manager
     * @return ConfigManager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Get the sound manager
     * @return SoundManager instance
     */
    public SoundManager getSoundManager() {
        return soundManager;
    }

    /**
     * Get the player data manager
     * @return PlayerDataManager instance
     */
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    /**
     * Reload all plugin configurations and managers
     * Used by admin reload command
     */
    public void reloadPlugin() {
        try {
            getLogger().info("Reloading JoinSounds plugin...");

            // Reload configurations
            configManager.reloadConfigs();

            // Reload sounds
            soundManager.loadSounds();

            // Reload player data
            playerDataManager.loadPlayerData();

            getLogger().info("Plugin reloaded successfully!");

        } catch (Exception e) {
            getLogger().severe("Failed to reload plugin: " + e.getMessage());
            e.printStackTrace();
        }
    }
}