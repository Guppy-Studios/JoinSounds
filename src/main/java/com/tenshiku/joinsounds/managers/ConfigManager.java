package com.tenshiku.joinsounds.managers;

import com.tenshiku.joinsounds.JoinSounds;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Manages all configuration files for the plugin
 */
public class ConfigManager {

    private final JoinSounds plugin;
    private FileConfiguration config;
    private FileConfiguration soundsConfig;
    private FileConfiguration messagesConfig;

    private File soundsFile;
    private File messagesFile;

    public ConfigManager(JoinSounds plugin) {
        this.plugin = plugin;
    }

    /**
     * Load all configuration files
     */
    public void loadConfigs() {
        plugin.getLogger().info("Loading configuration files...");

        // Load main config.yml
        loadMainConfig();

        // Load sounds.yml
        loadSoundsConfig();

        // Load messages.yml
        loadMessagesConfig();

        plugin.getLogger().info("Configuration files loaded successfully!");

        // Display some config info if debug is enabled
        if (isDebugMode()) {
            plugin.getLogger().info("Debug mode enabled");
            plugin.getLogger().info("Default radius: " + getDefaultRadius());
            plugin.getLogger().info("Play delay: " + getPlayDelay() + " ticks");
            plugin.getLogger().info("Storage type: " + getStorageType());
        }
    }

    /**
     * Load the main config.yml file
     */
    private void loadMainConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Validate important settings
        if (config.getInt("sounds.default-radius") < 1) {
            plugin.getLogger().warning("Invalid default-radius in config.yml, using default value of 16");
        }

        if (config.getDouble("sounds.volume") < 0.0 || config.getDouble("sounds.volume") > 1.0) {
            plugin.getLogger().warning("Invalid volume in config.yml, should be between 0.0 and 1.0");
        }
    }

    /**
     * Load the sounds.yml configuration file
     */
    private void loadSoundsConfig() {
        soundsFile = new File(plugin.getDataFolder(), "sounds.yml");
        if (!soundsFile.exists()) {
            plugin.saveResource("sounds.yml", false);
            plugin.getLogger().info("Created default sounds.yml");
        }
        soundsConfig = YamlConfiguration.loadConfiguration(soundsFile);

        // Validate sounds config
        if (soundsConfig.getConfigurationSection("sounds") == null) {
            plugin.getLogger().warning("No 'sounds' section found in sounds.yml!");
        }
    }

    /**
     * Load the messages.yml configuration file
     */
    private void loadMessagesConfig() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
            plugin.getLogger().info("Created default messages.yml");
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Validate messages config
        if (messagesConfig.getConfigurationSection("messages") == null) {
            plugin.getLogger().warning("No 'messages' section found in messages.yml!");
        }
    }

    /**
     * Reload all configuration files
     */
    public void reloadConfigs() {
        plugin.getLogger().info("Reloading configuration files...");

        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.soundsConfig = YamlConfiguration.loadConfiguration(soundsFile);
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        plugin.getLogger().info("Configuration files reloaded!");
    }

    /**
     * Save the sounds configuration file
     */
    public void saveSoundsConfig() {
        try {
            soundsConfig.save(soundsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save sounds.yml: " + e.getMessage());
        }
    }

    /**
     * Save the messages configuration file
     */
    public void saveMessagesConfig() {
        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save messages.yml: " + e.getMessage());
        }
    }

    // Configuration getters
    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getSoundsConfig() {
        return soundsConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    // General Settings
    public boolean isPluginEnabled() {
        return config.getBoolean("general.enabled", true);
    }

    public String getPrefix() {
        return config.getString("general.prefix", "&6[JoinSounds] &f").replace("&", "§");
    }

    public boolean isDebugMode() {
        return config.getBoolean("general.debug", false);
    }

    // Sound Settings
    public int getDefaultRadius() {
        return Math.max(1, config.getInt("sounds.default-radius", 16));
    }

    public int getMaxRadius() {
        return Math.max(getDefaultRadius(), config.getInt("sounds.max-radius", 32));
    }

    public int getMinRadius() {
        return Math.max(1, config.getInt("sounds.min-radius", 5));
    }

    public double getDefaultVolume() {
        return Math.max(0.0, Math.min(1.0, config.getDouble("sounds.volume", 0.8)));
    }

    public double getDefaultPitch() {
        return Math.max(0.5, Math.min(2.0, config.getDouble("sounds.pitch", 1.0)));
    }

    public int getPlayDelay() {
        return Math.max(0, config.getInt("sounds.play-delay", 20));
    }

    public boolean playToSelf() {
        return config.getBoolean("sounds.play-to-self", true);
    }

    // World Settings
    public List<String> getEnabledWorlds() {
        return config.getStringList("worlds.enabled-worlds");
    }

    public List<String> getDisabledWorlds() {
        return config.getStringList("worlds.disabled-worlds");
    }

    public boolean isWorldEnabled(String worldName) {
        List<String> enabledWorlds = getEnabledWorlds();
        List<String> disabledWorlds = getDisabledWorlds();

        // If enabled-worlds list is not empty, world must be in it
        if (!enabledWorlds.isEmpty()) {
            return enabledWorlds.contains(worldName);
        }

        // Otherwise, world is enabled unless in disabled-worlds
        return !disabledWorlds.contains(worldName);
    }

    // Permission Settings
    public String getUsePermission() {
        return config.getString("permissions.use-permission", "joinsounds.use");
    }

    public String getAdminPermission() {
        return config.getString("permissions.admin-permission", "joinsounds.admin");
    }

    public String getBypassWorldPermission() {
        return config.getString("permissions.bypass-world-permission", "joinsounds.bypass.world");
    }

    public String getCustomRadiusPermission() {
        return config.getString("permissions.custom-radius-permission", "joinsounds.radius.custom");
    }

    // GUI Settings
    public String getGuiTitle() {
        return config.getString("gui.title", "&6Choose Your Join Sound").replace("&", "§");
    }

    public int getGuiSize() {
        int size = config.getInt("gui.size", 27);
        // Ensure size is multiple of 9 and within valid range
        if (size % 9 != 0 || size < 9 || size > 54) {
            return 27;
        }
        return size;
    }

    public boolean isPreviewEnabled() {
        return config.getBoolean("gui.enable-preview", true);
    }

    // Storage Settings
    public String getStorageType() {
        String type = config.getString("storage.type", "YAML").toUpperCase();
        // Validate storage type
        if (!type.equals("YAML") && !type.equals("H2") && !type.equals("MYSQL") && !type.equals("MARIADB")) {
            plugin.getLogger().warning("Invalid storage type '" + type + "', defaulting to YAML");
            return "YAML";
        }
        return type;
    }

    public String getYamlFileName() {
        return config.getString("storage.yaml.file-name", "playerdata.yml");
    }

    // H2 Database Settings
    public String getH2FileName() {
        return config.getString("storage.h2.file-name", "joinsounds.db");
    }

    public String getH2Username() {
        return config.getString("storage.h2.username", "sa");
    }

    public String getH2Password() {
        return config.getString("storage.h2.password", "");
    }

    // MySQL Database Settings
    public String getMySQLHost() {
        return config.getString("storage.mysql.host", "localhost");
    }

    public int getMySQLPort() {
        return config.getInt("storage.mysql.port", 3306);
    }

    public String getMySQLDatabase() {
        return config.getString("storage.mysql.database", "minecraft");
    }

    public String getMySQLUsername() {
        return config.getString("storage.mysql.username", "root");
    }

    public String getMySQLPassword() {
        return config.getString("storage.mysql.password", "password");
    }

    public String getMySQLTablePrefix() {
        return config.getString("storage.mysql.table-prefix", "joinsounds_");
    }

    public boolean getMySQLUseSSL() {
        return config.getBoolean("storage.mysql.use-ssl", false);
    }

    public int getMySQLConnectionTimeout() {
        return config.getInt("storage.mysql.connection-timeout", 30000);
    }

    // MariaDB Database Settings (extends MySQL settings)
    public String getMariaDBHost() {
        return config.getString("storage.mariadb.host", getMySQLHost());
    }

    public int getMariaDBPort() {
        return config.getInt("storage.mariadb.port", getMySQLPort());
    }

    public String getMariaDBDatabase() {
        return config.getString("storage.mariadb.database", getMySQLDatabase());
    }

    public String getMariaDBUsername() {
        return config.getString("storage.mariadb.username", getMySQLUsername());
    }

    public String getMariaDBPassword() {
        return config.getString("storage.mariadb.password", getMySQLPassword());
    }

    public String getMariaDBTablePrefix() {
        return config.getString("storage.mariadb.table-prefix", getMySQLTablePrefix());
    }

    public boolean getMariaDBUseSSL() {
        return config.getBoolean("storage.mariadb.use-ssl", getMySQLUseSSL());
    }

    public int getMariaDBConnectionTimeout() {
        return config.getInt("storage.mariadb.connection-timeout", getMySQLConnectionTimeout());
    }

    // Generic database settings getter for current storage type
    public String getDatabaseHost() {
        String type = getStorageType();
        if (type.equals("MYSQL")) return getMySQLHost();
        if (type.equals("MARIADB")) return getMariaDBHost();
        return "localhost";
    }

    public int getDatabasePort() {
        String type = getStorageType();
        if (type.equals("MYSQL")) return getMySQLPort();
        if (type.equals("MARIADB")) return getMariaDBPort();
        return 3306;
    }

    public String getDatabaseName() {
        String type = getStorageType();
        if (type.equals("MYSQL")) return getMySQLDatabase();
        if (type.equals("MARIADB")) return getMariaDBDatabase();
        if (type.equals("H2")) return getH2FileName();
        return "minecraft";
    }

    public String getDatabaseUsername() {
        String type = getStorageType();
        if (type.equals("MYSQL")) return getMySQLUsername();
        if (type.equals("MARIADB")) return getMariaDBUsername();
        if (type.equals("H2")) return getH2Username();
        return "root";
    }

    public String getDatabasePassword() {
        String type = getStorageType();
        if (type.equals("MYSQL")) return getMySQLPassword();
        if (type.equals("MARIADB")) return getMariaDBPassword();
        if (type.equals("H2")) return getH2Password();
        return "";
    }

    public String getTablePrefix() {
        String type = getStorageType();
        if (type.equals("MYSQL")) return getMySQLTablePrefix();
        if (type.equals("MARIADB")) return getMariaDBTablePrefix();
        return "joinsounds_";
    }

    // Cooldown Settings
    public boolean areCooldownsEnabled() {
        return config.getBoolean("cooldowns.enabled", true);
    }

    public int getChangeSoundCooldown() {
        return Math.max(0, config.getInt("cooldowns.change-sound-cooldown", 30));
    }

    public int getRejoinCooldown() {
        return Math.max(0, config.getInt("cooldowns.rejoin-cooldown", 5));
    }

    // Advanced Settings
    public boolean shouldCheckUpdates() {
        return config.getBoolean("advanced.check-updates", true);
    }

    public boolean shouldSendMetrics() {
        return config.getBoolean("advanced.metrics", true);
    }

    public int getMaxSoundsPerPlayer() {
        return config.getInt("advanced.max-sounds-per-player", -1);
    }

    // Message Methods
    public String getMessage(String path) {
        String message = messagesConfig.getString("messages." + path, "Message not found: " + path);
        return getPrefix() + message.replace("&", "§");
    }

    public String getMessage(String path, String... replacements) {
        String message = getMessage(path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return message;
    }

    public String getMessageWithoutPrefix(String path) {
        String message = messagesConfig.getString("messages." + path, "Message not found: " + path);
        return message.replace("&", "§");
    }

    public String getMessageWithoutPrefix(String path, String... replacements) {
        String message = getMessageWithoutPrefix(path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return message;
    }
}