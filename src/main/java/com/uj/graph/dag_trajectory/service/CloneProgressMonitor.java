package com.uj.graph.dag_trajectory.service;

import org.eclipse.jgit.lib.ProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloneProgressMonitor implements ProgressMonitor {
    private static final Logger log = LoggerFactory.getLogger(CloneProgressMonitor.class);
    
    private int totalTasks;
    private int currentTask;
    private String currentTaskName;
    private int totalWork;
    private int currentWork;
    private long lastUpdateTime;

    @Override
    public void start(int totalTasks) {
        this.totalTasks = totalTasks;
        this.currentTask = 0;
        this.lastUpdateTime = System.currentTimeMillis();
        log.info("Clone operation started: announcedTasks={}", totalTasks);
    }

    @Override
    public void beginTask(String name, int totalWork) {
        this.currentTask++;
        this.currentTaskName = name;
        this.totalWork = totalWork;
        this.currentWork = 0;
        this.lastUpdateTime = System.currentTimeMillis();
        log.info("Clone task started: sequence={}, announcedTasks={}, task={}, totalWork={}",
                currentTask, totalTasks, name, totalWork);
    }

    @Override
    public void update(int completed) {
        currentWork += completed;
        long now = System.currentTimeMillis();
        long elapsed = now - lastUpdateTime;
        
        if (elapsed > 1000 || currentWork >= totalWork) {
            int percent = totalWork > 0 ? (currentWork * 100) / totalWork : 0;
            log.debug("Task progress: {} - {}/{} bytes ({}%)", currentTaskName, currentWork, totalWork, percent);
            lastUpdateTime = now;
        }
    }

    @Override
    public void endTask() {
        log.info("Clone task completed: sequence={}, task={}, transferred={}",
                currentTask, currentTaskName, currentWork);
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void showDuration(boolean enabled) {
    }
}
