package logic;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import listeners.DeathAction;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import survivalgames.main.SurvivalMain;

import java.util.*;

public class Arena {

    private final SurvivalMain survivalMain;
    List<Player> players;
    private final Region region;
    private final String name;
    private final Map<Location, Boolean> spawnPoints;
    private final World world;

    // for grace period
    private int timer;


    //for world border
    private int time;

    private int taskID;
    private int otherID;
    private boolean freezePeriod = true;
    private boolean gracePeriod = true;
    private final List<Player> playersInGulag;
    private final boolean gulagInProgress = false;
    private final boolean gulagFreezePeriod = true;
    private final List<Player> playersInGulagMatch;
    private final List<Player> pastGulag;
    private final Map<Block, BlockData> explodedBlocks;
    private final List<Location> fallenBlocks;
    private Location redeployLocation;
    private Location center = null;
    private double borderSize;

    public Arena(Region region, String name, double borderSize) {
        this.players = new ArrayList<>();
        this.region = region;
        this.name = name;
        this.spawnPoints = new HashMap<>();
        this.world = Bukkit.getWorld(Objects.requireNonNull(region.getWorld()).getName()); // TODO: find a better way to do this
        this.survivalMain = SurvivalMain.survivalMain;
        this.playersInGulag = new ArrayList<>();
        this.playersInGulagMatch = new ArrayList<>();
        this.pastGulag = new ArrayList<>();
        this.borderSize = borderSize;
        this.explodedBlocks = new HashMap<>();
        this.fallenBlocks = new ArrayList<>();
    }

    public void addPlayer(Player player) {
        this.players.add(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFoodLevel(20);
        player.setHealth(20);
        player.setFireTicks(0);
        player.getInventory().clear();
        player.setTotalExperience(0);
        player.setLevel(0);
        this.freezePeriod = true;
    }

    public void setCenter(Location location) {
        this.center = location;
    }

    public Location getCenter() {
        return center;
    }

    public boolean isFreezePeriod() {
        return freezePeriod;
    }

    public void addExplodedBlock(Block block, BlockData data) {
        this.explodedBlocks.put(block, data);
    }

    public Map<Block, BlockData> getExplodedBlocks() {
        return explodedBlocks;
    }

    public void addFallenBlock(Location location) {
        this.fallenBlocks.add(location);
    }

    public List<Location> getFallenBlocks() {
        return fallenBlocks;
    }

    public void addPlayerToGulag(Player player) {
        this.playersInGulag.add(player);
        player.getActivePotionEffects().clear();
        player.setFireTicks(0);
        player.setHealth(20);

        if (this.playersInGulag.size() >= 2 && this.playersInGulagMatch.isEmpty()) {
            Location side1 = new Location(player.getWorld(), 147, 43, -569);
            Location side2 = new Location(player.getWorld(), 147, 43, -598);

            this.playersInGulag.get(0).teleport(side1);
            this.playersInGulag.get(1).teleport(side2);

            this.playersInGulag.get(0).getInventory().clear();
            this.playersInGulag.get(1).getInventory().clear();

            this.playersInGulagMatch.add(this.playersInGulag.get(0));
            this.playersInGulagMatch.add(this.playersInGulag.get(1));

            this.playersInGulag.remove(0);
            this.playersInGulag.remove(0);



            for (Player p : this.playersInGulagMatch) {
                p.getInventory().clear();
                p.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
                p.getInventory().setItem(1, new ItemStack(Material.BOW));
                p.getInventory().setItem(2, new ItemStack(Material.ARROW, 10));
                p.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                p.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
                p.getInventory().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
                p.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
                p.setHealth(20);
                p.setFoodLevel(20);
            }


        } else {
            player.sendTitle(ChatColor.GOLD + "You're in the gulag.", "Fight to earn redeployment", 10, 80, 10);
            player.teleport(new Location(player.getWorld(), 160, 49, -584));
            player.getInventory().clear();
        }
    }

    public void addPlayerToPastGulag(Player p) {
        this.pastGulag.add(p);
    }

    public List<Player> getPastGulag() {
        return pastGulag;
    }

    public List<Player> getPlayersInGulag() {
        return playersInGulag;
    }

    public List<Player> getPlayersInGulagMatch() {
        return playersInGulagMatch;
    }

    public void removePlayer(Player player) {
        this.playersInGulag.remove(player);
        this.playersInGulagMatch.clear();
        this.players.remove(player);
        if (this.players.size() <= 1) {
            end();
        }
    }

    public void end() {
        if (this.players.size() == 1) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + this.getPlayers().get(0).getName() + " won the game! Type " +
                    "/sg join " + this.getName() + " to play again!");
        } else {
            Bukkit.broadcastMessage(ChatColor.GOLD + "Nobody won the game! Type " +
                    "/sg join " + this.getName() + " to play again!");
        }

        this.stopTimer();
        this.getPlayers().clear();
        this.getPlayersInGulagMatch().clear();
        this.getPlayersInGulag().clear();
        this.getPastGulag().clear();
        this.removeWorldBorder(Objects.requireNonNull(this.getRedeployLocation().getWorld()).getWorldBorder());
        this.setFreezePeriod(true);
        this.removeDropsOnGround(Objects.requireNonNull(this.getCenter().getWorld()));
        this.repairMap();
    }

    public void removeDropsOnGround(World world) {
        for (Entity current : world.getEntities()) {
            if (current instanceof Item) {
                current.remove();
            }
        }
    }

    public void endFreezePeriod() {
        this.freezePeriod = false;
        setTimer(15);
        startTimer();
    }

    public void setFreezePeriod(boolean b) {
        this.freezePeriod = b;
    }

    public void endGracePeriod() {
        this.gracePeriod = false;
        for (Player p : this.getPlayers()) {
            if (p.getInventory().getChestplate() != null && p.getInventory().getChestplate().getType().equals(Material.ELYTRA)) {
                p.getInventory().setChestplate(new ItemStack(Material.AIR));
            }

            if (p.getInventory().contains(Material.ELYTRA)) {
                p.getInventory().remove(Material.ELYTRA);
            }

        }

        World world = this.getCenter().getWorld();
        assert world != null;
        world.getWorldBorder().setCenter(this.getCenter());

        world.getWorldBorder().setSize(this.borderSize);
        startWorldBorderTimer(240, world.getWorldBorder());
    }

    public void removeWorldBorder(WorldBorder worldBorder) {
        worldBorder.setSize(30000000);
        Bukkit.getScheduler().cancelTask(otherID);
    }

    public void stopWorldBorderTimer() {
        Bukkit.getScheduler().cancelTask(otherID);
    }

    public void startWorldBorderTimer(int interval, WorldBorder worldBorder) {

        this.time = interval;

        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        otherID = scheduler.scheduleSyncRepeatingTask(survivalMain, () -> {


            if (time == 0) {
                // grace period over
                if (worldBorder.getSize() <= 76) {
                    Bukkit.getScheduler().cancelTask(otherID);
                    return;
                }
                Bukkit.broadcastMessage(ChatColor.GOLD + "Border shrinking!");
                worldBorder.setSize(worldBorder.getSize() / 2, 30);
                this.time = interval;
            }

            if (time == 60 || time == 30 || time <= 10) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "Border closing in " + time + " seconds!");
            } else if (time % 60 == 0 && time != 240) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "Border closing in " + (time / 60) + " minutes!");
            }

            this.time--;

        }, 0L, 20L);
    }

    public boolean isGracePeriod() {
        return gracePeriod;
    }

    public List<Player> getPlayers() {
        return this.players;
    }

    public Region getRegion() {
        return region;
    }

    public String getName() {
        return name;
    }

    public void addSpawnPoint(Location location) {
        if (this.spawnPoints.isEmpty()) {
            this.redeployLocation = location;
        }
        this.spawnPoints.put(location, false);
    }

    public Location getRedeployLocation() {
        return redeployLocation;
    }

    public Map<Location, Boolean> getSpawnPoints() {
        return spawnPoints;
    }

    public void setTimer(int timer) {
        this.timer = timer;
    }

    public void stopTimer() {
        Bukkit.getScheduler().cancelTask(taskID);
    }

    public void startTimer() {
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        taskID = scheduler.scheduleSyncRepeatingTask(survivalMain, () -> {
            if (timer == 0) {
                // grace period over
                endGracePeriod();
                stopTimer();
                return;
            }

            if (timer == 30 || timer == 15 || timer == 10 || timer <= 5) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "Grace period for " + timer + " more seconds!");
            }

            timer--;

        }, 0L, 20L);

    }

    public void prepareMap() {
        // make sure to repair the map first in case a chest was destroyed.
        repairMap();
        fillChests();
    }

    private void fillChests() {


        // 40% chance of food
        Material[] food = {Material.COOKED_BEEF, Material.COOKED_CHICKEN, Material.COOKED_PORKCHOP};

        // 20% chance of armor
        Material[] armor = {Material.LEATHER_CHESTPLATE, Material.LEATHER_BOOTS, Material.LEATHER_HELMET,
                Material.LEATHER_LEGGINGS, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS,
                Material.IRON_BOOTS, Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
                Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_HELMET, Material.GOLDEN_HELMET,
                Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS};

        // 20% chance of weapon
        Material[] weapon = {Material.ARROW, Material.WOODEN_SWORD, Material.STONE_AXE, Material.BOW,
                Material.FISHING_ROD, Material.IRON_SWORD, Material.STONE_SWORD};

        // 15% chance of materials(lapis, diamonds, sticks, xp bottles)
        Material[] materials = {Material.IRON_INGOT, Material.DIAMOND, Material.STICK, Material.EXPERIENCE_BOTTLE, Material.LAPIS_LAZULI};

        // 5% chance of really good stuff(golden apples, etc.)
        Material[] op = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, Material.GOLDEN_APPLE, Material.TNT};

        Random random = new Random();

        for (Chunk chunk : this.world.getLoadedChunks()) {
            for (BlockState entity : chunk.getTileEntities()) {
                if (entity instanceof Chest && Utils.isInside(entity.getLocation(), this.getRegion())) {
                    Chest chest = (Chest) entity;
                    Inventory inventory = chest.getBlockInventory();
                    inventory.clear();
                    for (int i = 0; i < inventory.getSize() / 5; i++) {

                        double num = random.nextDouble();

                        if (num <= 0.4) {
                            inventory.setItem(random.nextInt(inventory.getSize()), new ItemStack(food[random.nextInt(food.length)]));
                        } else if (num <= 0.6) {
                            inventory.setItem(random.nextInt(inventory.getSize()), new ItemStack(armor[random.nextInt(armor.length)]));
                        } else if (num <= 0.8) {
                            int ran = random.nextInt(weapon.length);
                            ItemStack item;
                            if (ran == 0) {
                                item = new ItemStack(Material.ARROW, 3);
                            } else {
                                item = new ItemStack(weapon[random.nextInt(weapon.length)]);
                            }
                            inventory.setItem(random.nextInt(inventory.getSize()), item);
                        } else if (num <= 0.95) {
                            inventory.setItem(random.nextInt(inventory.getSize()), new ItemStack(materials[random.nextInt(materials.length)]));
                        } else {
                            inventory.setItem(random.nextInt(inventory.getSize()), new ItemStack(op[random.nextInt(op.length)]));
                        }

                    }
                }
            }
        }

    }

    private void repairMap() {
        // this must be done first otherwise we might overwrite an original block
        for (Location location : fallenBlocks) {
            Block block = location.getBlock();
            block.setType(Material.AIR);
        }
        for (Block block : explodedBlocks.keySet()) {
            block.setBlockData(explodedBlocks.get(block));
        }
    }

}
