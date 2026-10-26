package com.comphenix.protocol.scheduler;

import org.bukkit.entity.Entity;

public interface ProtocolScheduler {
    Task scheduleSyncRepeatingTask(Runnable task, long delay, long period);

    Task runTask(Runnable task);

    /**
     * Runs a task on the thread that owns the given entity.
     * <p>
     * On Folia this is the entity's region thread, so the task may safely touch the entity and the world around it.
     * Everywhere else it is the same as {@link #runTask(Runnable)}.
     *
     * @param entity the entity the task works on, or null to use the default sync thread.
     * @param task   the task to run.
     * @return the scheduled task.
     */
    default Task runTask(Entity entity, Runnable task) {
        return runTask(task);
    }

    Task scheduleSyncDelayedTask(Runnable task, long delay);

    Task runTaskAsync(Runnable task);
}
