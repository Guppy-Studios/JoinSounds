package com.tenshiku.joinsounds.managers;

import com.tenshiku.joinsounds.JoinSounds;
import com.tenshiku.joinsounds.models.JoinSound;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SoundManager {

    private final JoinSounds plugin;
    private final Map<String, JoinSound> availableSounds;
    private final Map<String, String> soundAliases;

    public SoundManager(JoinSounds plugin) {
        this.plugin = plugin;
        this.availableSounds = new HashMap<>();
        this.soundAliases = new HashMap<>();
    }

    /**
     * Load all sounds from the sounds.yml configuration
     */
    public void loadSounds() {
        plugin.getLogger().info("Loading sounds from configuration...");

        availableSounds.clear();
        soundAliases.clear();

        ConfigurationSection soundsSection = plugin.getConfigManager().getSoundsConfig().getConfigurationSection("sounds");
        if (soundsSection == null) {
            plugin.getLogger().warning("No 'sounds' section found in sounds.yml!");
            return;
        }

        int loadedCount = 0;
        int skippedCount = 0;

        // Load individual sounds
        for (String soundId : soundsSection.getKeys(false)) {
            ConfigurationSection soundSection = soundsSection.getConfigurationSection(soundId);
            if (soundSection == null) {
                plugin.getLogger().warning("Invalid sound configuration for: " + soundId);
                skippedCount++;
                continue;
            }

            JoinSound sound = JoinSound.fromConfig(soundId, soundSection);
            if (sound != null) {
                availableSounds.put(soundId, sound);
                loadedCount++;

                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("Loaded sound: " + soundId +
                            " (" + sound.getDisplayName() +
                            ", enabled=" + sound.isEnabled() +
                            ", hidden=" + sound.isHidden() + ")");
                }
            } else {
                plugin.getLogger().warning("Failed to load sound: " + soundId + " (missing required fields)");
                skippedCount++;
            }
        }

        // Load aliases
        loadAliases();

        plugin.getLogger().info("Loaded " + loadedCount + " sounds" +
                (skippedCount > 0 ? " (" + skippedCount + " skipped)" : "") +
                " and " + soundAliases.size() + " aliases");
    }

    /**
     * Load sound aliases from configuration
     */
    private void loadAliases() {
        ConfigurationSection aliasSection = plugin.getConfigManager().getSoundsConfig().getConfigurationSection("aliases");
        if (aliasSection == null) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("No aliases section found in sounds.yml");
            }
            return;
        }

        for (String alias : aliasSection.getKeys(false)) {
            String soundId = aliasSection.getString(alias);
            if (soundId != null && availableSounds.containsKey(soundId)) {
                soundAliases.put(alias.toLowerCase(), soundId);
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("Loaded alias: " + alias + " -> " + soundId);
                }
            } else {
                plugin.getLogger().warning("Invalid alias '" + alias + "': sound '" + soundId + "' not found");
            }
        }
    }

    /**
     * Get a sound by ID or alias
     * @param identifier Sound ID or alias
     * @return JoinSound or null if not found
     */
    public JoinSound getSound(String identifier) {
        if (identifier == null) {
            return null;
        }

        // Try direct sound ID first
        JoinSound sound = availableSounds.get(identifier);
        if (sound != null) {
            return sound;
        }

        // Try alias (case-insensitive)
        String soundId = soundAliases.get(identifier.toLowerCase());
        if (soundId != null) {
            return availableSounds.get(soundId);
        }

        return null;
    }

    /**
     * Get all available sound IDs
     * @return Set of sound IDs
     */
    public Set<String> getAvailableSoundIds() {
        return availableSounds.keySet();
    }

    /**
     * Get all sounds that are available for selection (enabled, not hidden, seasonal check)
     * @return Map of available sounds
     */
    public Map<String, JoinSound> getAvailableSounds() {
        return availableSounds.entrySet().stream()
                .filter(entry -> entry.getValue().isAvailableForSelection())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Get all sounds regardless of availability
     * @return Map of all sounds
     */
    public Map<String, JoinSound> getAllSounds() {
        return new HashMap<>(availableSounds);
    }

    /**
     * Check if a sound exists by ID or alias
     * @param identifier Sound ID or alias
     * @return true if sound exists
     */
    public boolean hasSound(String identifier) {
        return getSound(identifier) != null;
    }

    /**
     * Play a join sound for a player
     * @param player The player who joined
     * @param soundId The sound ID to play
     */
    public void playJoinSound(Player player, String soundId) {
        if (!plugin.getConfigManager().isPluginEnabled()) {
            return;
        }

        // Check if sounds are enabled in this world
        if (!isWorldEnabled(player.getWorld().getName(), player)) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Sounds disabled in world: " + player.getWorld().getName());
            }
            return;
        }

        JoinSound sound = getSound(soundId);
        if (sound == null) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("Attempted to play unknown sound: " + soundId);
            }
            return;
        }

        // Check if sound is enabled and available
        if (!sound.isEnabled()) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Sound " + soundId + " is disabled");
            }
            return;
        }

        // Check seasonal availability
        if (!sound.isSeasonallyAvailable()) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Sound " + soundId + " is not seasonally available");
            }
            return;
        }

        // Check if player has permission
        if (!player.hasPermission(sound.getPermission())) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Player " + player.getName() + " lacks permission for sound: " + soundId);
            }
            return;
        }

        // Check rejoin cooldown
        if (plugin.getPlayerDataManager().isOnCooldown(player.getUniqueId(), "rejoin")) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Player " + player.getName() + " is on rejoin cooldown");
            }
            return;
        }

        // Play the sound with delay
        int delay = plugin.getConfigManager().getPlayDelay();
        if (delay > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                playSound(player, sound);
                plugin.getPlayerDataManager().setLastJoinSound(player.getUniqueId());
            }, delay);
        } else {
            playSound(player, sound);
            plugin.getPlayerDataManager().setLastJoinSound(player.getUniqueId());
        }
    }

    /**
     * Actually play the sound to nearby players
     * @param player The player who joined
     * @param sound The sound to play
     */
    private void playSound(Player player, JoinSound sound) {
        Location location = player.getLocation();
        String nexoSoundId = sound.getNexoSoundId();
        float volume = (float) sound.getVolume();
        float pitch = (float) sound.getPitch();
        int radius = sound.getRadius();

        try {
            int playersInRange = 0;

            // Play to nearby players
            for (Player nearbyPlayer : player.getWorld().getPlayers()) {
                double distance = nearbyPlayer.getLocation().distance(location);
                if (distance <= radius) {
                    try {
                        // Try to play Nexo sound
                        nearbyPlayer.playSound(location, nexoSoundId, volume, pitch);
                        playersInRange++;
                    } catch (Exception e) {
                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().warning("Failed to play Nexo sound " + nexoSoundId +
                                    " to " + nearbyPlayer.getName() + ", trying fallback: " + e.getMessage());
                        }
                        // Fallback to vanilla sound
                        try {
                            nearbyPlayer.playSound(location, Sound.BLOCK_NOTE_BLOCK_BELL, volume, pitch);
                            playersInRange++;
                        } catch (Exception e2) {
                            plugin.getLogger().warning("Failed to play fallback sound to " + nearbyPlayer.getName() + ": " + e2.getMessage());
                        }
                    }
                }
            }

            // Play to the joining player if enabled
            if (plugin.getConfigManager().playToSelf()) {
                try {
                    player.playSound(location, nexoSoundId, volume, pitch);
                } catch (Exception e) {
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().warning("Failed to play Nexo sound " + nexoSoundId +
                                " to joining player, trying fallback: " + e.getMessage());
                    }
                    try {
                        player.playSound(location, Sound.BLOCK_NOTE_BLOCK_BELL, volume, pitch);
                    } catch (Exception e2) {
                        plugin.getLogger().warning("Failed to play fallback sound to joining player: " + e2.getMessage());
                    }
                }
            }

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Played sound " + sound.getId() + " for player " + player.getName() +
                        " to " + playersInRange + " players in range");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error playing sound " + sound.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Preview a sound for a player (only plays to that player)
     * @param player The player to preview the sound for
     * @param soundId The sound ID to preview
     */
    public void previewSound(Player player, String soundId) {
        JoinSound sound = getSound(soundId);
        if (sound == null) {
            return;
        }

        try {
            player.playSound(player.getLocation(), sound.getNexoSoundId(),
                    (float) sound.getVolume(), (float) sound.getPitch());
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("Failed to preview Nexo sound, using fallback: " + e.getMessage());
            }
            // Fallback to vanilla sound
            try {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL,
                        (float) sound.getVolume(), (float) sound.getPitch());
            } catch (Exception e2) {
                plugin.getLogger().warning("Failed to preview fallback sound: " + e2.getMessage());
            }
        }
    }

    /**
     * Check if sounds are enabled in a specific world for a player
     * @param worldName The world name
     * @param player The player (for bypass permission check)
     * @return true if enabled
     */
    private boolean isWorldEnabled(String worldName, Player player) {
        // Check bypass permission
        if (player.hasPermission(plugin.getConfigManager().getBypassWorldPermission())) {
            return true;
        }

        return plugin.getConfigManager().isWorldEnabled(worldName);
    }

    /**
     * Get sounds that a player has permission to use
     * @param player The player
     * @return Map of accessible sounds
     */
    public Map<String, JoinSound> getAccessibleSounds(Player player) {
        return getAvailableSounds().entrySet().stream()
                .filter(entry -> player.hasPermission(entry.getValue().getPermission()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Get the total count of loaded sounds
     * @return Number of sounds
     */
    public int getSoundCount() {
        return availableSounds.size();
    }

    /**
     * Get the count of enabled sounds
     * @return Number of enabled sounds
     */
    public int getEnabledSoundCount() {
        return (int) availableSounds.values().stream()
                .filter(JoinSound::isEnabled)
                .count();
    }
}