package com.tenshiku.joinsounds.models;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;


public class JoinSound {

    private final String id;
    private final String displayName;
    private final String nexoSoundId;
    private final String permission;
    private final List<String> description;
    private final Material guiMaterial;
    private final String itemModel;
    private final int customModelData;
    private final boolean enchanted;
    private final double volume;
    private final double pitch;
    private final int radius;
    private final boolean enabled;
    private final boolean hidden;
    private final String seasonalStart;
    private final String seasonalEnd;

    public JoinSound(String id, String displayName, String nexoSoundId, String permission,
                     List<String> description, Material guiMaterial, String itemModel,
                     int customModelData, boolean enchanted, double volume, double pitch,
                     int radius, boolean enabled, boolean hidden, String seasonalStart, String seasonalEnd) {
        this.id = id;
        this.displayName = displayName;
        this.nexoSoundId = nexoSoundId;
        this.permission = permission;
        this.description = description;
        this.guiMaterial = guiMaterial;
        this.itemModel = itemModel;
        this.customModelData = customModelData;
        this.enchanted = enchanted;
        this.volume = volume;
        this.pitch = pitch;
        this.radius = radius;
        this.enabled = enabled;
        this.hidden = hidden;
        this.seasonalStart = seasonalStart;
        this.seasonalEnd = seasonalEnd;
    }

    /**
     * Create a JoinSound from a configuration section
     * @param id The sound ID
     * @param section The configuration section
     * @return JoinSound instance or null if invalid
     */
    public static JoinSound fromConfig(String id, ConfigurationSection section) {
        try {
            // Required fields
            String displayName = section.getString("display-name", id);
            String nexoSoundId = section.getString("nexo-sound-id");
            String permission = section.getString("permission");

            if (nexoSoundId == null || permission == null) {
                return null; // Required fields missing
            }

            // Optional description
            List<String> description = section.getStringList("description");

            // GUI item settings
            Material material = Material.NOTE_BLOCK; // default
            String itemModel = null;
            int customModelData = 0;
            boolean enchanted = false;

            ConfigurationSection guiSection = section.getConfigurationSection("gui-item");
            if (guiSection != null) {
                String materialName = guiSection.getString("material", "NOTE_BLOCK");
                try {
                    material = Material.valueOf(materialName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Keep default if material is invalid
                    material = Material.NOTE_BLOCK;
                }

                itemModel = guiSection.getString("item-model");
                customModelData = guiSection.getInt("custom-model-data", 0);
                enchanted = guiSection.getBoolean("enchanted", false);
            }

            // Sound properties with defaults
            double volume = section.getDouble("volume", 0.8);
            double pitch = section.getDouble("pitch", 1.0);
            int radius = section.getInt("radius", 16);
            boolean enabled = section.getBoolean("enabled", true);
            boolean hidden = section.getBoolean("hidden", false);

            // Seasonal settings
            String seasonalStart = null;
            String seasonalEnd = null;
            ConfigurationSection seasonalSection = section.getConfigurationSection("seasonal");
            if (seasonalSection != null) {
                seasonalStart = seasonalSection.getString("start-date");
                seasonalEnd = seasonalSection.getString("end-date");
            }

            return new JoinSound(id, displayName, nexoSoundId, permission, description,
                    material, itemModel, customModelData, enchanted,
                    volume, pitch, radius, enabled, hidden, seasonalStart, seasonalEnd);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if this sound is currently available based on seasonal settings
     * @return true if available, false if out of season
     */
    public boolean isSeasonallyAvailable() {
        if (seasonalStart == null || seasonalEnd == null) {
            return true; // Not seasonal
        }

        try {
            LocalDate now = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

            LocalDate startDate = LocalDate.parse(now.getYear() + "-" + seasonalStart, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalDate endDate = LocalDate.parse(now.getYear() + "-" + seasonalEnd, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // Handle year wrap-around (e.g., Dec 28 to Jan 5)
            if (startDate.isAfter(endDate)) {
                // Season crosses year boundary
                return now.isAfter(startDate) || now.isBefore(endDate);
            } else {
                // Normal season within same year
                return (now.isEqual(startDate) || now.isAfter(startDate)) &&
                        (now.isEqual(endDate) || now.isBefore(endDate));
            }
        } catch (Exception e) {
            // If seasonal parsing fails, default to available
            return true;
        }
    }

    /**
     * Check if this sound should be shown to players
     * Takes into account enabled status, hidden status, and seasonal availability
     * @return true if should be shown
     */
    public boolean isAvailableForSelection() {
        return enabled && !hidden && isSeasonallyAvailable();
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNexoSoundId() {
        return nexoSoundId;
    }

    public String getPermission() {
        return permission;
    }

    public List<String> getDescription() {
        return description;
    }

    public Material getGuiMaterial() {
        return guiMaterial;
    }

    public String getItemModel() {
        return itemModel;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public boolean isEnchanted() {
        return enchanted;
    }

    public double getVolume() {
        return volume;
    }

    public double getPitch() {
        return pitch;
    }

    public int getRadius() {
        return radius;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isHidden() {
        return hidden;
    }

    public String getSeasonalStart() {
        return seasonalStart;
    }

    public String getSeasonalEnd() {
        return seasonalEnd;
    }

    @Override
    public String toString() {
        return "JoinSound{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", nexoSoundId='" + nexoSoundId + '\'' +
                ", permission='" + permission + '\'' +
                ", enabled=" + enabled +
                ", hidden=" + hidden +
                '}';
    }
}