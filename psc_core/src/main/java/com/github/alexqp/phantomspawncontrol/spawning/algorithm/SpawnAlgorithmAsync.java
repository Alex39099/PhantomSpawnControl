package com.github.alexqp.phantomspawncontrol.spawning.algorithm;

import com.github.alexqp.commons.config.ConfigChecker;
import com.github.alexqp.commons.config.ConsoleErrorType;
import com.github.alexqp.commons.messages.ConsoleMessage;
import com.github.alexqp.phantomspawncontrol.data.phantom.PhantomStatsContainer;
import com.github.alexqp.phantomspawncontrol.data.player.PlayerStatsContainer;
import com.github.alexqp.phantomspawncontrol.spawning.algorithm.spawnRegulator.playerRegulator.PlayerConditionsBuilder;
import com.github.alexqp.phantomspawncontrol.spawning.algorithm.spawnRegulator.PlayerSpawnRegulator;
import com.github.alexqp.phantomspawncontrol.spawning.algorithm.spawnRegulator.SpawnRegulatorHandler;
import com.github.alexqp.phantomspawncontrol.spawning.algorithm.spawnRegulator.locationRegulator.GeneralLocationConditions;
import com.github.alexqp.phantomspawncontrol.spawning.algorithm.spawnRegulator.locationRegulator.WorldGuardConditions;
import com.github.alexqp.phantomspawncontrol.utility.SpawnCancelMsg;
import com.google.common.collect.Range;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.Objective;
import org.bukkit.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnAlgorithmAsync extends SpawnRegulatorHandler {

    @Nullable
    public static SpawnAlgorithmAsync build(@NotNull JavaPlugin plugin, @NotNull ConfigurationSection rootSection,
                                            @NotNull Objective scoreObjective, @NotNull PhantomStatsContainer phantomStatsContainer,
                                            @NotNull PlayerStatsContainer playerStatsContainer) {

        ConfigChecker configChecker = new ConfigChecker(plugin);

        int maxGroupSize = configChecker.checkInt(rootSection, "max_group_size", ConsoleErrorType.WARN, 4, Range.atLeast(1));
        double spawnChanceMultiplier = configChecker.checkDouble(rootSection, "spawn_chance_multiplier", ConsoleErrorType.WARN, 1, Range.greaterThan(0.0));

        ConfigurationSection section = configChecker.checkConfigSection(rootSection, "spawn_attempts", ConsoleErrorType.ERROR);
        if (section != null) {

            int minDelay = configChecker.checkInt(section, "min_delay", ConsoleErrorType.WARN, 1200, Range.atLeast(0));
            int[] spawnAttemptDelay = {minDelay, configChecker.checkInt(section, "max_delay", ConsoleErrorType.WARN, minDelay + 1200, Range.atLeast(minDelay))};

            SpawnAlgorithmAsync algorithm = new SpawnAlgorithmAsync(scoreObjective, maxGroupSize,
                    spawnChanceMultiplier, spawnAttemptDelay, phantomStatsContainer);

            // location regulator....
            algorithm.addSpawnRegulator(plugin, GeneralLocationConditions.build(plugin, rootSection))
                    .addSpawnRegulator(plugin, WorldGuardConditions.build(plugin, rootSection));

            // player regulator...
            algorithm.addSpawnRegulator(plugin, playerStatsContainer);
            for (PlayerSpawnRegulator regulator : PlayerConditionsBuilder.build(plugin, rootSection, scoreObjective, algorithm)) {
                algorithm.addSpawnRegulator(plugin, regulator);
            }

            return algorithm;
        }

        return null;
    }

    private Objective scoreObjective;

    private int maxGroupSize;
    private double spawnChanceMultiplier;
    private int[] spawnAttemptDelay;

    private PhantomStatsContainer phantomStatsContainer;
    private int randomChanceScore = 72000;

    private SpawnAlgorithmAsync(@NotNull Objective scoreObjective, int maxGroupSize,
                                double spawnChanceMultiplier, int[] spawnAttemptDelay,
                                @Nullable PhantomStatsContainer phantomStatsContainer) {
        this.scoreObjective = scoreObjective;

        this.maxGroupSize = maxGroupSize;

        this.spawnChanceMultiplier = spawnChanceMultiplier;
        this.spawnAttemptDelay = spawnAttemptDelay;

        this.phantomStatsContainer = phantomStatsContainer;
    }

    public Consumer<Phantom> getPhantomStatsConsumerAsync(int score) {
        if (phantomStatsContainer != null)
            return phantomStatsContainer.getPhantomStatsConsumerAsync(score - randomChanceScore, new Random());
        return phantom -> {}; // lambda consumer
    }

    private Set<Location> getBoxSpawnLocations(final Location pLoc) {
        HashSet<Location> locations = new HashSet<>();

        int y = pLoc.getBlockY() + ThreadLocalRandom.current().nextInt(20, 35);
        int halfDiameter = 10;

        for (int i = 0; i < maxGroupSize; i++) {
            int x = ThreadLocalRandom.current().nextInt(pLoc.getBlockX() - halfDiameter, pLoc.getBlockX() + halfDiameter + 1);
            int z = ThreadLocalRandom.current().nextInt(pLoc.getBlockZ() - halfDiameter, pLoc.getBlockZ() + halfDiameter + 1);
            locations.add(new Location(pLoc.getWorld(), x, y, z));
        }
        return locations;
    }

    public void setRandomChanceScore(int score) {
        randomChanceScore = score;
    }

    private double getRandomSpawnChance(double score) {
        return spawnChanceMultiplier * (score - (double) randomChanceScore) / score; // multiplier * default MC formula
    }

    public boolean shouldSpawnAsync(@NotNull final Player p, @NotNull final JavaPlugin plugin) {
        return this.computeSpawnRegulatorAsync(p, plugin);
    }

    @NotNull
    public Set<Location> getSpawnLocationsAsync(@NotNull final Player p, @NotNull final JavaPlugin plugin) {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        try {
            Set<Location> spawnLocations = this.computeSpawnRegulatorAsync(scheduler.callSyncMethod(plugin, () -> this.getBoxSpawnLocations(p.getLocation())).get(), plugin);
            if (!spawnLocations.isEmpty()) {
                double chance = scheduler.callSyncMethod(plugin, () -> this.getRandomSpawnChance(scoreObjective.getScore(p.getName()).getScore())).get();
                for (Location loc : new HashSet<>(spawnLocations)) {
                    if (Math.random() < chance) {
                        ConsoleMessage.debug(this.getClass(), plugin, SpawnCancelMsg.build(p, "random chance"));
                        spawnLocations.remove(loc);
                    }
                }
            }
            return spawnLocations;
        }
        catch (InterruptedException | ExecutionException e) {
            SpawnCancelMsg.printFutureGetError(plugin, this, "SpawnLocations", e);
        }
        return new HashSet<>();
    }

    public int getSpawnAttemptDelayAsync() {
        int delay = ThreadLocalRandom.current().nextInt(spawnAttemptDelay[0], spawnAttemptDelay[1] + 1);
        return Math.max(delay, 0);
    }
}
