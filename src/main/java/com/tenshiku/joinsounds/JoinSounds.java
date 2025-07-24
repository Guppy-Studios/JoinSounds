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

        if (getServer().getPluginManager().getPlugin("Nexo") == null) {
            getLogger().severe("Nexo plugin not found! This plugin requires Nexo to function properly.");
            getLogger().severe("Disabling JoinSounds...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            this.configManager = new ConfigManager(this);
            getLogger().info("ConfigManager initialized!");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize ConfigManager: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            configManager.loadConfigs();
            getLogger().info("Configurations loaded!");
        } catch (Exception e) {
            getLogger().severe("Failed to load configurations: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            this.soundManager = new SoundManager(this);
            this.playerDataManager = new PlayerDataManager(this);

            getLogger().info("All managers initialized successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize managers: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        JoinSoundCommand commandExecutor = new JoinSoundCommand(this);

        getServer().getScheduler().runTask(this, () -> {
            try {
                java.lang.reflect.Method getCommandMapMethod = getServer().getClass().getMethod("getCommandMap");
                org.bukkit.command.CommandMap commandMap = (org.bukkit.command.CommandMap) getCommandMapMethod.invoke(getServer());

                org.bukkit.command.Command joinSoundCommand = new org.bukkit.command.Command("joinsound") {
                    @Override
                    public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
                        return commandExecutor.onCommand(sender, this, commandLabel, args);
                    }

                    @Override
                    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
                        return commandExecutor.onTabComplete(sender, this, alias, args);
                    }
                };

                joinSoundCommand.setDescription("Manage your join sound");
                joinSoundCommand.setUsage("/joinsound [set|remove|preview|list|info|reload] [sound]");
                joinSoundCommand.setAliases(java.util.Arrays.asList("js", "joinmusic"));
                joinSoundCommand.setPermission("joinsounds.use");

                commandMap.register("joinsounds", joinSoundCommand);

                getLogger().info("Commands registered successfully!");
            } catch (Exception e) {
                getLogger().severe("Failed to register commands: " + e.getMessage());
                e.printStackTrace();
            }
        });

        try {
            getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
            getLogger().info("Event listeners registered!");
        } catch (Exception e) {
            getLogger().severe("Failed to register event listeners: " + e.getMessage());
            e.printStackTrace();
        }

        getLogger().info("JoinSounds plugin has been enabled successfully!");
        getLogger().info("Nexo integration active!");

        if (configManager.isDebugMode()) {
            getLogger().info("Debug mode is enabled");
            getLogger().info("Loaded " + soundManager.getAvailableSoundIds().size() + " sounds");
        }
    }

    public void onDisable() {
        getLogger().info("Disabling JoinSounds plugin...");

        if (playerDataManager != null) {
            try {
                playerDataManager.saveAll();
                getLogger().info("Player data saved successfully!");
            } catch (Exception e) {
                getLogger().severe("Failed to save player data: " + e.getMessage());
                e.printStackTrace();
            }
        }

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

            configManager.reloadConfigs();
            soundManager.loadSounds();
            playerDataManager.loadPlayerData();

            getLogger().info("Plugin reloaded successfully!");

        } catch (Exception e) {
            getLogger().severe("Failed to reload plugin: " + e.getMessage());
            e.printStackTrace();
        }
    }
}