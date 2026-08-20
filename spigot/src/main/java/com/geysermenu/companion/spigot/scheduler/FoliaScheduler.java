package com.geysermenu.companion.spigot.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Thread-dispatch facade for Folia / Paper / legacy Spigot.
 *
 * <p>On Folia there is no single "main thread": the global game state runs on the global region
 * thread, every loaded region ticks on its own thread, and any entity may migrate between region
 * threads at any tick. {@code Bukkit.getScheduler()} throws {@link UnsupportedOperationException}
 * there, so every hop from an IO thread back into the Bukkit API has to name the thread it wants.
 *
 * <p>The Folia scheduler API ({@code Bukkit.getGlobalRegionScheduler()},
 * {@code Bukkit.getAsyncScheduler()}, {@code Entity#getScheduler()}) also exists on Paper 1.20.1+
 * where it maps onto the single main thread, so this class prefers it whenever it is present and
 * only falls back to {@code Bukkit.getScheduler()} on plain Spigot, which has neither.
 *
 * <p>All entry points are safe to call from any thread, including Netty IO threads.
 */
public final class FoliaScheduler {

    /** True when the running server exposes the region/entity/async scheduler API (Folia or Paper 1.20.1+). */
    private static final boolean REGIONISED_API = detectRegionisedApi();

    /** True when the running server is actually Folia (regions really are separate threads). */
    private static final boolean FOLIA = detectFolia();

    private FoliaScheduler() {
    }

    private static boolean detectRegionisedApi() {
        try {
            Bukkit.class.getMethod("getGlobalRegionScheduler");
            Bukkit.class.getMethod("getAsyncScheduler");
            Entity.class.getMethod("getScheduler");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static boolean hasRegionisedApi() {
        return REGIONISED_API;
    }

    /**
     * Run on the global region thread (Folia) / main thread (Paper, Spigot).
     *
     * <p>Use this for server-wide state that belongs to no particular region: player lookups,
     * the online player list, console command dispatch, plugin manager queries.
     */
    public static void runGlobal(Plugin plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (REGIONISED_API) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run owning the given entity, on whichever region thread currently ticks it.
     *
     * <p>On Folia the entity scheduler follows the entity across region transfers and simply drops
     * the task (invoking {@code retired}) if the entity is removed before it can run, which is the
     * only correct way to touch an entity from an off-thread callback.
     */
    public static void runForEntity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (REGIONISED_API) {
            entity.getScheduler().execute(plugin, task, retired, 1L);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run owning the region that contains {@code location}.
     */
    public static void runForRegion(Plugin plugin, Location location, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (REGIONISED_API) {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Resolve {@code playerUuid} and hand the {@link Player} to {@code task} on that player's own
     * region/entity thread.
     *
     * <p>Callable from a Netty IO thread. The player lookup itself is deliberately performed on the
     * global region thread rather than on the caller's thread, so no assumption is made about the
     * thread-safety of {@code Bukkit.getPlayer}. From there the task hops once more onto the
     * player's entity scheduler; if the player logs out in between, the task is silently dropped.
     */
    public static void runForPlayer(Plugin plugin, UUID playerUuid, Consumer<Player> task) {
        runForPlayer(plugin, playerUuid, task, null);
    }

    /**
     * As {@link #runForPlayer(Plugin, UUID, Consumer)}, but {@code offline} is invoked on the global
     * region thread when no such player is online.
     */
    public static void runForPlayer(Plugin plugin, UUID playerUuid, Consumer<Player> task, Runnable offline) {
        if (playerUuid == null) {
            if (offline != null) {
                runGlobal(plugin, offline);
            }
            return;
        }
        runGlobal(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                if (offline != null) {
                    offline.run();
                }
                return;
            }
            runForEntity(plugin, player, () -> {
                Player current = Bukkit.getPlayer(playerUuid);
                if (current != null && current.isOnline()) {
                    task.accept(current);
                }
            }, offline == null ? () -> {
            } : offline);
        });
    }

    /**
     * Run off any game thread. Never touches the Bukkit API for you - the task must stay off it.
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (REGIONISED_API) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Run off any game thread after a delay.
     */
    public static void runAsyncDelayed(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (REGIONISED_API) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay, unit);
        } else {
            long ticks = Math.max(1L, unit.toMillis(delay) / 50L);
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, ticks);
        }
    }

    /**
     * Cancel every task this plugin still has queued. Safe on all three platforms.
     */
    public static void cancelTasks(Plugin plugin) {
        try {
            if (REGIONISED_API) {
                Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
                Bukkit.getAsyncScheduler().cancelTasks(plugin);
            } else {
                Bukkit.getScheduler().cancelTasks(plugin);
            }
        } catch (Throwable ignored) {
        }
    }
}
