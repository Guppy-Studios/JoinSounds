package com.tenshiku.joinsounds.managers;

import com.tenshiku.joinsounds.JoinSounds;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class PlayerDataManager {

    private final JoinSounds plugin;
    private final Map<UUID, String> playerSounds;
    private final Map<UUID, Long> lastSoundChange;
    private final Map<UUID, Long> lastJoinSound;

    // File storage
    private File playerDataFile;
    private FileConfiguration playerDataConfig;

    // Database storage
    private String jdbcUrl;
    private String username;
    private String password;
    private String tablePrefix;
    private boolean useDatabase;

    public PlayerDataManager(JoinSounds plugin) {
        this.plugin = plugin;
        this.playerSounds = new HashMap<>();
        this.lastSoundChange = new HashMap<>();
        this.lastJoinSound = new HashMap<>();

        initializeStorage();
        loadPlayerData();
    }

    /**
     * Initialize storage system based on configuration
     */
    private void initializeStorage() {
        String storageType = plugin.getConfigManager().getStorageType();

        switch (storageType) {
            case "H2":
                setupH2Database();
                break;
            case "MYSQL":
                setupMySQLDatabase();
                break;
            case "MARIADB":
                setupMariaDBDatabase();
                break;
            case "YAML":
            default:
                setupYamlStorage();
                break;
        }

        if (useDatabase) {
            createTables();
        }

        plugin.getLogger().info("Using " + storageType + " storage for player data");
    }

    /**
     * Setup YAML file storage
     */
    private void setupYamlStorage() {
        useDatabase = false;
        String fileName = plugin.getConfigManager().getYamlFileName();
        playerDataFile = new File(plugin.getDataFolder(), fileName);

        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
                plugin.getLogger().info("Created " + fileName);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create " + fileName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Setup H2 database storage
     */
    private void setupH2Database() {
        useDatabase = true;
        String fileName = plugin.getConfigManager().getH2FileName();
        File dbFile = new File(plugin.getDataFolder(), fileName);

        jdbcUrl = "jdbc:h2:" + dbFile.getAbsolutePath().replace(".db", "") + ";MODE=MySQL";
        username = plugin.getConfigManager().getH2Username();
        password = plugin.getConfigManager().getH2Password();
        tablePrefix = plugin.getConfigManager().getTablePrefix();

        // Load H2 driver
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("H2 driver not found! Please add H2 to your dependencies.");
            useDatabase = false;
            setupYamlStorage();
        }
    }

    /**
     * Setup MySQL database storage
     */
    private void setupMySQLDatabase() {
        useDatabase = true;
        String host = plugin.getConfigManager().getMySQLHost();
        int port = plugin.getConfigManager().getMySQLPort();
        String database = plugin.getConfigManager().getMySQLDatabase();
        boolean useSSL = plugin.getConfigManager().getMySQLUseSSL();
        int timeout = plugin.getConfigManager().getMySQLConnectionTimeout();

        jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=" + useSSL + "&connectTimeout=" + timeout + "&autoReconnect=true";
        username = plugin.getConfigManager().getMySQLUsername();
        password = plugin.getConfigManager().getMySQLPassword();
        tablePrefix = plugin.getConfigManager().getMySQLTablePrefix();

        // Load MySQL driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("MySQL driver not found! Please add MySQL connector to your dependencies.");
            useDatabase = false;
            setupYamlStorage();
        }
    }

    /**
     * Setup MariaDB database storage
     */
    private void setupMariaDBDatabase() {
        useDatabase = true;
        String host = plugin.getConfigManager().getMariaDBHost();
        int port = plugin.getConfigManager().getMariaDBPort();
        String database = plugin.getConfigManager().getMariaDBDatabase();
        boolean useSSL = plugin.getConfigManager().getMariaDBUseSSL();
        int timeout = plugin.getConfigManager().getMariaDBConnectionTimeout();

        jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + database +
                "?useSSL=" + useSSL + "&connectTimeout=" + timeout + "&autoReconnect=true";
        username = plugin.getConfigManager().getMariaDBUsername();
        password = plugin.getConfigManager().getMariaDBPassword();
        tablePrefix = plugin.getConfigManager().getMariaDBTablePrefix();

        // Load MariaDB driver
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("MariaDB driver not found, trying MySQL driver...");
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e2) {
                plugin.getLogger().severe("Neither MariaDB nor MySQL driver found! Please add database connector to your dependencies.");
                useDatabase = false;
                setupYamlStorage();
            }
        }
    }

    /**
     * Create database tables if they don't exist
     */
    private void createTables() {
        if (!useDatabase) return;

        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "sound VARCHAR(255), " +
                "last_change BIGINT, " +
                "last_join BIGINT" +
                ")";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTableSQL);
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Database tables created/verified");
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
            e.printStackTrace();
            useDatabase = false;
            setupYamlStorage();
        }
    }

    /**
     * Get database connection
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * Load player data from storage
     */
    public void loadPlayerData() {
        if (useDatabase) {
            loadFromDatabase();
        } else {
            loadFromYaml();
        }
    }

    /**
     * Load player data from YAML file
     */
    private void loadFromYaml() {
        if (playerDataFile == null || !playerDataFile.exists()) {
            return;
        }

        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);

        for (String uuidString : playerDataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);

                String sound = playerDataConfig.getString(uuidString + ".sound");
                if (sound != null) {
                    playerSounds.put(uuid, sound);
                }

                long lastChange = playerDataConfig.getLong(uuidString + ".last-change", 0);
                if (lastChange > 0) {
                    lastSoundChange.put(uuid, lastChange);
                }

                long lastJoin = playerDataConfig.getLong(uuidString + ".last-join", 0);
                if (lastJoin > 0) {
                    lastJoinSound.put(uuid, lastJoin);
                }

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in playerdata.yml: " + uuidString);
            }
        }

        plugin.getLogger().info("Loaded data for " + playerSounds.size() + " players from YAML");
    }

    /**
     * Load player data from database
     */
    private void loadFromDatabase() {
        String selectSQL = "SELECT uuid, sound, last_change, last_join FROM " + tablePrefix + "players";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            int count = 0;
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String sound = rs.getString("sound");
                    long lastChange = rs.getLong("last_change");
                    long lastJoin = rs.getLong("last_join");

                    if (sound != null && !sound.isEmpty()) {
                        playerSounds.put(uuid, sound);
                    }
                    if (lastChange > 0) {
                        lastSoundChange.put(uuid, lastChange);
                    }
                    if (lastJoin > 0) {
                        lastJoinSound.put(uuid, lastJoin);
                    }

                    count++;
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in database: " + rs.getString("uuid"));
                }
            }

            plugin.getLogger().info("Loaded data for " + count + " players from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player data from database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save all player data
     */
    public void saveAll() {
        if (useDatabase) {
            saveToDatabaseAsync();
        } else {
            saveToYaml();
        }
    }

    /**
     * Save player data to YAML file
     */
    private void saveToYaml() {
        if (playerDataConfig == null || playerDataFile == null) {
            return;
        }

        // Clear existing data
        for (String key : playerDataConfig.getKeys(false)) {
            playerDataConfig.set(key, null);
        }

        // Save current data
        for (Map.Entry<UUID, String> entry : playerSounds.entrySet()) {
            String uuidString = entry.getKey().toString();
            playerDataConfig.set(uuidString + ".sound", entry.getValue());

            if (lastSoundChange.containsKey(entry.getKey())) {
                playerDataConfig.set(uuidString + ".last-change", lastSoundChange.get(entry.getKey()));
            }

            if (lastJoinSound.containsKey(entry.getKey())) {
                playerDataConfig.set(uuidString + ".last-join", lastJoinSound.get(entry.getKey()));
            }
        }

        try {
            playerDataConfig.save(playerDataFile);
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Saved player data to YAML");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save playerdata.yml: " + e.getMessage());
        }
    }

    /**
     * Save player data to database asynchronously
     */
    private void saveToDatabaseAsync() {
        CompletableFuture.runAsync(() -> saveToDatabase());
    }

    /**
     * Save player data to database
     */
    private void saveToDatabase() {
        String upsertSQL;
        String storageType = plugin.getConfigManager().getStorageType();

        if ("H2".equals(storageType)) {
            upsertSQL = "MERGE INTO " + tablePrefix + "players (uuid, sound, last_change, last_join) VALUES (?, ?, ?, ?)";
        } else {
            upsertSQL = "INSERT INTO " + tablePrefix + "players (uuid, sound, last_change, last_join) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE sound = VALUES(sound), last_change = VALUES(last_change), last_join = VALUES(last_join)";
        }

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(upsertSQL)) {

            for (Map.Entry<UUID, String> entry : playerSounds.entrySet()) {
                UUID uuid = entry.getKey();

                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, entry.getValue());
                pstmt.setLong(3, lastSoundChange.getOrDefault(uuid, 0L));
                pstmt.setLong(4, lastJoinSound.getOrDefault(uuid, 0L));
                pstmt.addBatch();
            }

            pstmt.executeBatch();

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Saved player data to database");
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save player data to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Player data methods
    public String getPlayerSound(UUID uuid) {
        return playerSounds.get(uuid);
    }

    public void setPlayerSound(UUID uuid, String soundId) {
        if (soundId == null) {
            playerSounds.remove(uuid);
            removePlayerFromStorage(uuid);
        } else {
            playerSounds.put(uuid, soundId);
            lastSoundChange.put(uuid, System.currentTimeMillis());
        }

        // Auto-save
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveAll);
    }

    public boolean hasPlayerSound(UUID uuid) {
        return playerSounds.containsKey(uuid);
    }

    public void removePlayerSound(UUID uuid) {
        playerSounds.remove(uuid);
        removePlayerFromStorage(uuid);

        // Auto-save
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveAll);
    }

    /**
     * Remove player from storage completely
     */
    private void removePlayerFromStorage(UUID uuid) {
        if (useDatabase) {
            String deleteSQL = "DELETE FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {

                pstmt.setString(1, uuid.toString());
                pstmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to remove player from database: " + e.getMessage());
            }
        }
        // For YAML, removal happens during save

        // Remove from memory
        lastSoundChange.remove(uuid);
        lastJoinSound.remove(uuid);
    }

    // Cooldown methods
    public boolean isOnCooldown(UUID uuid, String cooldownType) {
        if (!plugin.getConfigManager().areCooldownsEnabled()) {
            return false;
        }

        Map<UUID, Long> cooldownMap;
        int cooldownTime;

        switch (cooldownType) {
            case "change":
                cooldownMap = lastSoundChange;
                cooldownTime = plugin.getConfigManager().getChangeSoundCooldown();
                break;
            case "rejoin":
                cooldownMap = lastJoinSound;
                cooldownTime = plugin.getConfigManager().getRejoinCooldown();
                break;
            default:
                return false;
        }

        Long lastTime = cooldownMap.get(uuid);
        if (lastTime == null) {
            return false;
        }

        long timeDiff = (System.currentTimeMillis() - lastTime) / 1000;
        return timeDiff < cooldownTime;
    }

    public long getRemainingCooldown(UUID uuid, String cooldownType) {
        if (!plugin.getConfigManager().areCooldownsEnabled()) {
            return 0;
        }

        Map<UUID, Long> cooldownMap;
        int cooldownTime;

        switch (cooldownType) {
            case "change":
                cooldownMap = lastSoundChange;
                cooldownTime = plugin.getConfigManager().getChangeSoundCooldown();
                break;
            case "rejoin":
                cooldownMap = lastJoinSound;
                cooldownTime = plugin.getConfigManager().getRejoinCooldown();
                break;
            default:
                return 0;
        }

        Long lastTime = cooldownMap.get(uuid);
        if (lastTime == null) {
            return 0;
        }

        long timeDiff = (System.currentTimeMillis() - lastTime) / 1000;
        return Math.max(0, cooldownTime - timeDiff);
    }

    public void setLastJoinSound(UUID uuid) {
        lastJoinSound.put(uuid, System.currentTimeMillis());
    }

    // Statistics
    public int getPlayerCount() {
        return playerSounds.size();
    }

    public String getStorageInfo() {
        if (useDatabase) {
            return plugin.getConfigManager().getStorageType() + " database";
        } else {
            return "YAML file";
        }
    }
}