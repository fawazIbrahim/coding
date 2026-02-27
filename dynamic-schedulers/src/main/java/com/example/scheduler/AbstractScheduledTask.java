package com.example.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractScheduledTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(AbstractScheduledTask.class);

    @Override
    public final void run() {
        log.info("Task starting: {}", getTaskName());
        try {
            execute();
            log.info("Task completed: {}", getTaskName());
        } catch (Exception e) {
            log.error("Task failed: {}", getTaskName(), e.getMessage(), e);
            onError(e);
        }
    }

    protected abstract String getTaskName();

    protected abstract void execute() throws Exception;

    /**
     * Override to customize error handling (e.g. alerting, metrics, retry logic).
     * Default behaviour is to swallow the exception after logging.
     */
    protected void onError(Exception e) {}
}
