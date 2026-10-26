package com.comphenix.protocol.scheduler;

import com.comphenix.protocol.injector.temporary.TemporaryPlayer;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.utility.MinecraftReflection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public class FoliaScheduler implements ProtocolScheduler {
    private final Object foliaRegionScheduler;
    private final MethodAccessor runAtFixedRate;
    private final MethodAccessor runDelayed;
    private final MethodAccessor execute;
    private final MethodAccessor cancel;

    private final Object foliaAsyncScheduler;
    private final MethodAccessor executeAsync;

    private final MethodAccessor getEntityScheduler;
    private final MethodAccessor executeEntity;

    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;

        MethodAccessor getScheduler = Accessors.getMethodAccessor(Bukkit.getServer().getClass(), "getGlobalRegionScheduler");
        this.foliaRegionScheduler = getScheduler.invoke(Bukkit.getServer());

        this.runAtFixedRate = Accessors.getMethodAccessor(foliaRegionScheduler.getClass(), "runAtFixedRate", Plugin.class,
                Consumer.class, long.class, long.class);
        this.execute = Accessors.getMethodAccessor(foliaRegionScheduler.getClass(), "run", Plugin.class, Consumer.class);
        this.runDelayed = Accessors.getMethodAccessor(foliaRegionScheduler.getClass(), "runDelayed", Plugin.class, Consumer.class, long.class);

        MethodAccessor getAsyncScheduler = Accessors.getMethodAccessor(Bukkit.getServer().getClass(), "getAsyncScheduler");
        foliaAsyncScheduler = getAsyncScheduler.invoke(Bukkit.getServer());

        this.executeAsync = Accessors.getMethodAccessor(foliaAsyncScheduler.getClass(), "runNow", Plugin.class, Consumer.class);

        Class<?> taskClass = MinecraftReflection.getLibraryClass("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
        this.cancel = Accessors.getMethodAccessor(taskClass, "cancel");

        Class<?> entitySchedulerClass = MinecraftReflection.getLibraryClass("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
        this.getEntityScheduler = Accessors.getMethodAccessor(Entity.class, "getScheduler");
        this.executeEntity = Accessors.getMethodAccessor(entitySchedulerClass, "run", Plugin.class, Consumer.class, Runnable.class);
    }

    @Override
    public Task scheduleSyncRepeatingTask(Runnable task, long delay, long period) {
        // Folia rejects delays and periods below one tick; Bukkit treats them as "next tick"
        Object taskHandle = runAtFixedRate.invoke(foliaRegionScheduler, plugin, (Consumer<Object>)(t -> task.run()),
                Math.max(1L, delay), Math.max(1L, period));
        return new FoliaTask(cancel, taskHandle);
    }

    @Override
    public Task runTask(Runnable task) {
        Object taskHandle = execute.invoke(foliaRegionScheduler, plugin, (Consumer<Object>)(t -> task.run()));
        return new FoliaTask(cancel, taskHandle);
    }

    @Override
    public Task runTask(Entity entity, Runnable task) {
        if (!canUseEntityScheduler(entity)) {
            return runTask(task);
        }

        Object entityScheduler = getEntityScheduler.invoke(entity);
        // If the entity is removed before the task runs, finish the work on the global region instead of dropping it
        Runnable retired = () -> runTask(task);
        Object taskHandle = executeEntity.invoke(entityScheduler, plugin, (Consumer<Object>)(t -> task.run()), retired);
        if (taskHandle == null) {
            // the entity was already removed
            return runTask(task);
        }
        return new FoliaTask(cancel, taskHandle);
    }

    @Override
    public Task scheduleSyncDelayedTask(Runnable task, long delay) {
        if (delay <= 0) {
            // Folia's runDelayed rejects a zero delay
            return runTask(task);
        }
        Object taskHandle = runDelayed.invoke(foliaRegionScheduler, plugin, (Consumer<Object>)(t -> task.run()), delay);
        return new FoliaTask(cancel, taskHandle);
    }

    @Override
    public Task runTaskAsync(Runnable task) {
    	Object taskHandle = executeAsync.invoke(foliaAsyncScheduler, plugin, (Consumer<Object>)(t -> task.run()));
    	return new FoliaTask(cancel, taskHandle);
    }

    private static boolean canUseEntityScheduler(Entity entity) {
        if (entity == null || entity instanceof TemporaryPlayer) {
            return false;
        }
        // Players in the login or configuration phase are not in a world yet, so their entity scheduler would not run
        return entity instanceof Player player ? player.isOnline() : entity.isValid();
    }
}
