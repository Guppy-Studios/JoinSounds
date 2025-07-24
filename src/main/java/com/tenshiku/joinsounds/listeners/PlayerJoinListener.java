package com.tenshiku.joinsounds.listeners;

import com.tenshiku.joinsounds.JoinSounds;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;


public class PlayerJoinListener implements Listener {

    private final JoinSounds plugin;

    public PlayerJoinListener(JoinSounds plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if plugin is enabled
        if (!plugin.getConfigManager().isPluginEnabled()) {
            return;
        }

        // Debug logging
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("Player " + player.getName() + " joined, checking for join sound...");
        }

        // Check if player has a selected sound
        String soundId = plugin.getPlayerDataManager().getPlayerSound(player.getUniqueId());
        if (soundId == null || soundId.isEmpty()) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Player " + player.getName() + " has no join sound set");
            }
            return;
        }

        // Verify the sound still exists
        if (!plugin.getSoundManager().hasSound(soundId)) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("Player " + player.getName() + " has invalid sound: " + soundId);
            }
            // Remove invalid sound
            plugin.getPlayerDataManager().removePlayerSound(player.getUniqueId());
            return;
        }

        // Check basic permission
        String usePermission = plugin.getConfigManager().getUsePermission();
        if (!player.hasPermission(usePermission)) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Player " + player.getName() + " lacks basic use permission: " + usePermission);
            }
            return;
        }

        // Check if sounds are enabled in this world (SoundManager handles bypass permission)
        if (!plugin.getConfigManager().isWorldEnabled(player.getWorld().getName())
                && !player.hasPermission(plugin.getConfigManager().getBypassWorldPermission())) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Join sounds disabled in world: " + player.getWorld().getName());
            }
            return;
        }

        // Play the sound (SoundManager will handle all other checks)
        try {
            plugin.getSoundManager().playJoinSound(player, soundId);

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Triggered join sound " + soundId + " for player " + player.getName());
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error playing join sound for " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfigManager().isDebugMode()) {
                e.printStackTrace();
            }
        }
    }
}