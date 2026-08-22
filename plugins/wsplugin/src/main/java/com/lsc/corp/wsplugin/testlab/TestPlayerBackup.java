package com.lsc.corp.wsplugin.testlab;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class TestPlayerBackup {
    public int schemaVersion = 1;
    public String playerUuid;
    public String world;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public String gameMode;
    public double health;
    public int foodLevel;
    public float saturation;
    public int level;
    public float exp;
    public double maxHealthBase;
    public double movementSpeedBase;
    public String storageItems;
    public String armorItems;
    public String offhandItem;
    public List<Effect> effects = new ArrayList<>();
    public long createdAtEpochMs;

    public static TestPlayerBackup capture(Player player) {
        TestPlayerBackup backup = new TestPlayerBackup();
        backup.playerUuid = player.getUniqueId().toString();
        Location location = player.getLocation();
        backup.world = location.getWorld().getName();
        backup.x = location.getX();
        backup.y = location.getY();
        backup.z = location.getZ();
        backup.yaw = location.getYaw();
        backup.pitch = location.getPitch();
        backup.gameMode = player.getGameMode().name();
        backup.health = player.getHealth();
        backup.foodLevel = player.getFoodLevel();
        backup.saturation = player.getSaturation();
        backup.level = player.getLevel();
        backup.exp = player.getExp();
        backup.maxHealthBase = player.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        backup.movementSpeedBase = player.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
        backup.storageItems = encode(player.getInventory().getStorageContents());
        backup.armorItems = encode(player.getInventory().getArmorContents());
        backup.offhandItem = encode(new ItemStack[]{player.getInventory().getItemInOffHand()});
        for (PotionEffect effect : player.getActivePotionEffects()) {
            Effect saved = new Effect();
            saved.type = effect.getType().getKey().toString();
            saved.duration = effect.getDuration();
            saved.amplifier = effect.getAmplifier();
            saved.ambient = effect.isAmbient();
            saved.particles = effect.hasParticles();
            saved.icon = effect.hasIcon();
            backup.effects.add(saved);
        }
        backup.createdAtEpochMs = System.currentTimeMillis();
        return backup;
    }

    public void restore(Player player) {
        if (!player.getUniqueId().toString().equals(playerUuid)) {
            throw new IllegalArgumentException("Backup belongs to another player");
        }
        player.closeInventory();
        player.getInventory().setStorageContents(decode(storageItems));
        player.getInventory().setArmorContents(decode(armorItems));
        ItemStack[] offhand = decode(offhandItem);
        player.getInventory().setItemInOffHand(offhand.length == 0 || offhand[0] == null
                ? new ItemStack(org.bukkit.Material.AIR) : offhand[0]);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        for (Effect saved : effects) {
            PotionEffectType type = PotionEffectType.getByKey(org.bukkit.NamespacedKey.fromString(saved.type));
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, saved.duration, saved.amplifier,
                        saved.ambient, saved.particles, saved.icon));
            }
        }
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealthBase);
        player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(movementSpeedBase);
        player.setGameMode(GameMode.valueOf(gameMode));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(exp);
        player.setHealth(Math.max(0.1, Math.min(health, player.getAttribute(Attribute.MAX_HEALTH).getValue())));
        World targetWorld = Bukkit.getWorld(world);
        if (targetWorld != null) {
            player.teleport(new Location(targetWorld, x, y, z, yaw, pitch));
        }
        player.updateInventory();
    }

    private static String encode(ItemStack[] items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(items.length);
            for (ItemStack item : items) {
                output.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot serialize Test Lab inventory backup", exception);
        }
    }

    private static ItemStack[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new ItemStack[0];
        }
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream input = new BukkitObjectInputStream(bytes)) {
            int length = input.readInt();
            ItemStack[] result = new ItemStack[length];
            for (int index = 0; index < length; index++) {
                result[index] = (ItemStack) input.readObject();
            }
            return result;
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot deserialize Test Lab inventory backup", exception);
        }
    }

    public static final class Effect {
        public String type;
        public int duration;
        public int amplifier;
        public boolean ambient;
        public boolean particles;
        public boolean icon;
    }
}
