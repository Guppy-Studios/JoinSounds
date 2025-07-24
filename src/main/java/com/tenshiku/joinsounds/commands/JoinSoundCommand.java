package com.tenshiku.joinsounds.commands;

import com.tenshiku.joinsounds.JoinSounds;
import com.tenshiku.joinsounds.models.JoinSound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles the /joinsound command and all its subcommands
 */
public class JoinSoundCommand implements CommandExecutor, TabCompleter {

    private final JoinSounds plugin;
    private final List<String> subCommands = Arrays.asList("set", "remove", "disable", "off", "preview", "list", "info", "reload", "help");

    public JoinSoundCommand(JoinSounds plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check basic permission
        if (!player.hasPermission(plugin.getConfigManager().getUsePermission())) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        // Check if plugin is enabled
        if (!plugin.getConfigManager().isPluginEnabled()) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "§cPlugin is currently disabled.");
            return true;
        }

        // No arguments - show help or open GUI (future)
        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "§cUsage: /joinsound set <sound>");
                    return true;
                }
                setSound(player, args[1]);
                break;

            case "remove":
            case "disable":
            case "off":
                removeSound(player);
                break;

            case "preview":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "§cUsage: /joinsound preview <sound>");
                    return true;
                }
                previewSound(player, args[1]);
                break;

            case "list":
                listSounds(player);
                break;

            case "info":
                showInfo(player);
                break;

            case "reload":
                if (!player.hasPermission(plugin.getConfigManager().getAdminPermission())) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                reloadConfigs(player);
                break;

            case "help":
                showHelp(player);
                break;

            default:
                // Try to set the sound directly
                setSound(player, args[0]);
                break;
        }

        return true;
    }

    /**
     * Set a player's join sound
     */
    private void setSound(Player player, String soundId) {
        // Check cooldown
        if (plugin.getPlayerDataManager().isOnCooldown(player.getUniqueId(), "change")) {
            long remaining = plugin.getPlayerDataManager().getRemainingCooldown(player.getUniqueId(), "change");
            player.sendMessage(plugin.getConfigManager().getMessage("cooldown-active", "time", String.valueOf(remaining)));
            return;
        }

        // Check if sound exists
        JoinSound sound = plugin.getSoundManager().getSound(soundId);
        if (sound == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("sound-not-found", "sound", soundId));
            return;
        }

        // Check if sound is available for selection
        if (!sound.isAvailableForSelection()) {
            if (sound.isHidden()) {
                player.sendMessage(plugin.getConfigManager().getMessage("sound-not-found", "sound", soundId));
            } else if (!sound.isSeasonallyAvailable()) {
                player.sendMessage(plugin.getConfigManager().getPrefix() + "§cThat sound is not currently available.");
            } else {
                player.sendMessage(plugin.getConfigManager().getPrefix() + "§cThat sound is currently disabled.");
            }
            return;
        }

        // Check permission for specific sound
        if (!player.hasPermission(sound.getPermission())) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        // Set the sound
        plugin.getPlayerDataManager().setPlayerSound(player.getUniqueId(), soundId);
        player.sendMessage(plugin.getConfigManager().getMessage("sound-changed", "sound", sound.getDisplayName()));

        // Play preview if enabled
        if (plugin.getConfigManager().isPreviewEnabled()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getSoundManager().previewSound(player, soundId);
            }, 10L); // Small delay
        }
    }

    /**
     * Remove a player's join sound
     */
    private void removeSound(Player player) {
        if (!plugin.getPlayerDataManager().hasPlayerSound(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "§cYou don't have a join sound set.");
            return;
        }

        plugin.getPlayerDataManager().removePlayerSound(player.getUniqueId());
        player.sendMessage(plugin.getConfigManager().getMessage("sound-disabled"));
    }

    /**
     * Preview a sound
     */
    private void previewSound(Player player, String soundId) {
        JoinSound sound = plugin.getSoundManager().getSound(soundId);
        if (sound == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("sound-not-found", "sound", soundId));
            return;
        }

        // Check if sound is available
        if (!sound.isAvailableForSelection()) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "§cThat sound is not available for preview.");
            return;
        }

        if (!player.hasPermission(sound.getPermission())) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        plugin.getSoundManager().previewSound(player, soundId);
        player.sendMessage(plugin.getConfigManager().getPrefix() + "§aPreviewing sound: §6" + sound.getDisplayName());
    }

    /**
     * List available sounds for the player
     */
    private void listSounds(Player player) {
        Map<String, JoinSound> accessibleSounds = plugin.getSoundManager().getAccessibleSounds(player);

        if (accessibleSounds.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "§cNo sounds are available to you.");
            return;
        }

        player.sendMessage(plugin.getConfigManager().getPrefix() + "§aAvailable sounds:");

        for (Map.Entry<String, JoinSound> entry : accessibleSounds.entrySet()) {
            JoinSound sound = entry.getValue();
            String soundId = entry.getKey();

            // Format: "- bell (Bell Chime) - A gentle bell sound"
            StringBuilder line = new StringBuilder("§7- §6" + soundId);
            if (!sound.getDisplayName().equals(soundId)) {
                line.append(" §7(").append(sound.getDisplayName()).append("§7)");
            }

            if (!sound.getDescription().isEmpty()) {
                line.append(" §7- ").append(sound.getDescription().get(0));
            }

            player.sendMessage(line.toString());
        }

        player.sendMessage("§7Use §6/joinsound set <sound> §7to select a sound");
        player.sendMessage("§7Use §6/joinsound preview <sound> §7to test a sound");
    }

    /**
     * Show player's current sound info
     */
    private void showInfo(Player player) {
        String currentSound = plugin.getPlayerDataManager().getPlayerSound(player.getUniqueId());

        if (currentSound == null) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "§7You don't have a join sound set.");
            player.sendMessage("§7Use §6/joinsound list §7to see available sounds");
            return;
        }

        JoinSound sound = plugin.getSoundManager().getSound(currentSound);
        if (sound == null) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "§cYour current sound (§6" + currentSound + "§c) is invalid!");
            player.sendMessage("§7Use §6/joinsound remove §7to clear it");
            return;
        }

        player.sendMessage(plugin.getConfigManager().getPrefix() + "§aCurrent join sound:");
        player.sendMessage("§7Sound: §6" + sound.getDisplayName() + " §7(§6" + currentSound + "§7)");

        if (!sound.getDescription().isEmpty()) {
            player.sendMessage("§7Description: §f" + String.join(" ", sound.getDescription()));
        }

        player.sendMessage("§7Volume: §6" + Math.round(sound.getVolume() * 100) + "%");
        player.sendMessage("§7Radius: §6" + sound.getRadius() + " blocks");

        if (!sound.isSeasonallyAvailable()) {
            player.sendMessage("§c⚠ This sound is currently out of season");
        }

        player.sendMessage("§7Use §6/joinsound preview " + currentSound + " §7to test it");
    }

    /**
     * Reload plugin configurations
     */
    private void reloadConfigs(Player player) {
        try {
            plugin.reloadPlugin();
            player.sendMessage(plugin.getConfigManager().getMessage("config-reloaded"));
        } catch (Exception e) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "§cFailed to reload configuration: " + e.getMessage());
            plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
        }
    }

    /**
     * Show help message
     */
    private void showHelp(Player player) {
        player.sendMessage(plugin.getConfigManager().getPrefix() + "§6JoinSounds Commands:");
        player.sendMessage("§6/joinsound set <sound> §7- Set your join sound");
        player.sendMessage("§6/joinsound remove §7- Remove your join sound");
        player.sendMessage("§6/joinsound preview <sound> §7- Preview a sound");
        player.sendMessage("§6/joinsound list §7- List available sounds");
        player.sendMessage("§6/joinsound info §7- Show your current sound");

        if (player.hasPermission(plugin.getConfigManager().getAdminPermission())) {
            player.sendMessage("§c/joinsound reload §7- Reload configuration");
        }

        int accessibleCount = plugin.getSoundManager().getAccessibleSounds(player).size();
        player.sendMessage("§7You have access to §6" + accessibleCount + " §7sounds");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete subcommands
            StringUtil.copyPartialMatches(args[0], subCommands, completions);

            // Also include sound IDs for direct setting
            Map<String, JoinSound> accessibleSounds = plugin.getSoundManager().getAccessibleSounds(player);
            StringUtil.copyPartialMatches(args[0], accessibleSounds.keySet(), completions);

        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if ("set".equals(subCommand) || "preview".equals(subCommand)) {
                // Complete with accessible sound IDs
                Map<String, JoinSound> accessibleSounds = plugin.getSoundManager().getAccessibleSounds(player);
                StringUtil.copyPartialMatches(args[1], accessibleSounds.keySet(), completions);
            }
        }

        return completions.stream().sorted().collect(Collectors.toList());
    }
}