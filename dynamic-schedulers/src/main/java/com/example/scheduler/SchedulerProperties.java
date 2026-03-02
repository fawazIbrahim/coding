package com.example.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "scheduling")
public class SchedulerProperties {
    private boolean virtualThreads = false;
    private int threadPoolSize = 5;
    private List<SchedulerTask> tasks = new ArrayList<>();

    public boolean isVirtualThreads() { return virtualThreads; }
    public void setVirtualThreads(boolean virtualThreads) { this.virtualThreads = virtualThreads; }

    public int getThreadPoolSize() { return threadPoolSize; }
    public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }

    public List<SchedulerTask> getTasks() { return tasks; }
    public void setTasks(List<SchedulerTask> tasks) { this.tasks = tasks; }
}
