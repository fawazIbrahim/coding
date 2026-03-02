package com.example.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SchedulerProperties.class)
public class DynamicSchedulerConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchedulerConfig.class);

    private final SchedulerProperties properties;
    private final ApplicationContext context;

    public DynamicSchedulerConfig(SchedulerProperties properties, ApplicationContext context) {
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(buildScheduler());

        for (SchedulerTask task : properties.getTasks()) {
            Runnable runnable = context.getBean(ScheduledTaskRunnable.class, task);
            long initialDelay = task.getInitialDelay() != null ? task.getInitialDelay() : 0;

            if (task.getCron() != null) {
                registrar.addCronTask(new CronTask(runnable, task.getCron()));
                log.info("Registered cron task '{}' with expression '{}'", task.getName(), task.getCron());

            } else if (task.getFixedRate() != null) {
                registrar.addFixedRateTask(new FixedRateTask(
                        runnable,
                        Duration.ofMillis(task.getFixedRate()),
                        Duration.ofMillis(initialDelay)));
                log.info("Registered fixed-rate task '{}' every {}ms", task.getName(), task.getFixedRate());

            } else if (task.getFixedDelay() != null) {
                registrar.addFixedDelayTask(new FixedDelayTask(
                        runnable,
                        Duration.ofMillis(task.getFixedDelay()),
                        Duration.ofMillis(initialDelay)));
                log.info("Registered fixed-delay task '{}' with delay {}ms", task.getName(), task.getFixedDelay());

            } else {
                log.warn("Task '{}' has no schedule defined (cron, fixedRate, or fixedDelay), skipping.", task.getName());
            }
        }
    }

    private TaskScheduler buildScheduler() {
        if (properties.isVirtualThreads()) {
            SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
            scheduler.setVirtualThreads(true);
            scheduler.setThreadNamePrefix("dynamic-scheduler-");
            return scheduler;
        }

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getThreadPoolSize());
        scheduler.setThreadNamePrefix("dynamic-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
