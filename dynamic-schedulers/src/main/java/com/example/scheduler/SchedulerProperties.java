package com.example.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "scheduling")
public class SchedulerProperties {
    private List<SchedulerTask> tasks = new ArrayList<>();

    public List<SchedulerTask> getTasks() { return tasks; }
    public void setTasks(List<SchedulerTask> tasks) { this.tasks = tasks; }
}
