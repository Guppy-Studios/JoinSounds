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

        loadAliases();

        plugin.getLogger().info("Loaded " + loadedCount + " sounds" +
                (skippedCount > 0 ? " (" + skippedCount + " skipped)" : "") +
                " and " + soundAliases.size() + " aliases");
    }

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


    public JoinSound getSound(String identifier) {
        if (identifier == null) {
            return null;
        }

        JoinSound sound = availableSounds.get(identifier);
        if (sound != null) {
            return sound;
        }

        String soundId = soundAliases.get(identifier.toLowerCase());
        if (soundId != null) {
            return availableSounds.get(soundId);
        }

        return null;
    }


    public Set<String> getAvailableSoundIds() {
        return availableSounds.keySet();
    }

    public Map<String, JoinSound> getAvailableSounds() {
        return availableSounds.entrySet().stream()
                .filter(entry -> entry.getValue().isAvailableForSelection())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String, JoinSound> getAllSounds() {
        return new HashMap<>(availableSounds);
    }

    public boolean hasSound(String identifier) {
        return getSound(identifier) != null;
    }

    public void playJoinSound(Player player, String soundId) {
        if (!plugin.getConfigManager().isPluginEnabled()) {
            return;
        }

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

        if (!sound.isEnabled()) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Sound " + soundId + " is disabled");
            }
            return;
        }

        if (!sound.isSeasonallyAvailable()) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Sound " + soundId + " is not seasonally available");
            }
            return;
        }

        if (!player.hasPermission(sound.getPermission())) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Player " + player.getName() + " lacks permission for sound: " + soundId);
            }
            return;
        }

        if (plugin.getPlayerDataManager().isOnCooldown(player.getUniqueId(), "rejoin")) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Player " + player.getName() + " is on rejoin cooldown");
            }
            return;
        }

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

    private void playSound(Player player, JoinSound sound) {
        Location location = player.getLocation();
        String nexoSoundId = sound.getNexoSoundId();
        float volume = (float) sound.getVolume();
        float pitch = (float) sound.getPitch();
        int radius = sound.getRadius();

        try {
            int playersInRange = 0;

            for (Player nearbyPlayer : player.getWorld().getPlayers()) {
                double distance = nearbyPlayer.getLocation().distance(location);
                if (distance <= radius) {
                    try {
                        nearbyPlayer.playSound(location, nexoSoundId, volume, pitch);
                        playersInRange++;
                    } catch (Exception e) {
                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().warning("Failed to play Nexo sound " + nexoSoundId +
                                    " to " + nearbyPlayer.getName() + ", trying fallback: " + e.getMessage());
                        }
                        try {
                            nearbyPlayer.playSound(location, Sound.BLOCK_NOTE_BLOCK_BELL, volume, pitch);
                            playersInRange++;
                        } catch (Exception e2) {
                            plugin.getLogger().warning("Failed to play fallback sound to " + nearbyPlayer.getName() + ": " + e2.getMessage());
                        }
                    }
                }
            }

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
            try {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL,
                        (float) sound.getVolume(), (float) sound.getPitch());
            } catch (Exception e2) {
                plugin.getLogger().warning("Failed to preview fallback sound: " + e2.getMessage());
            }
        }
    }

    private boolean isWorldEnabled(String worldName, Player player) {
        if (player.hasPermission(plugin.getConfigManager().getBypassWorldPermission())) {
            return true;
        }

        return plugin.getConfigManager().isWorldEnabled(worldName);
    }

    public Map<String, JoinSound> getAccessibleSounds(Player player) {
        return getAvailableSounds().entrySet().stream()
                .filter(entry -> player.hasPermission(entry.getValue().getPermission()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public int getSoundCount() {
        return availableSounds.size();
    }

    public int getEnabledSoundCount() {
        return (int) availableSounds.values().stream()
                .filter(JoinSound::isEnabled)
                .count();
    }
}