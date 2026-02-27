package com.example.scheduler;

public class SchedulerTask {
    private String name;
    private Long fixedRate;
    private Long fixedDelay;
    private Long initialDelay;
    private String cron;
    private String bean;
    private String method;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getFixedRate() { return fixedRate; }
    public void setFixedRate(Long fixedRate) { this.fixedRate = fixedRate; }

    public Long getFixedDelay() { return fixedDelay; }
    public void setFixedDelay(Long fixedDelay) { this.fixedDelay = fixedDelay; }

    public Long getInitialDelay() { return initialDelay; }
    public void setInitialDelay(Long initialDelay) { this.initialDelay = initialDelay; }

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }

    public String getBean() { return bean; }
    public void setBean(String bean) { this.bean = bean; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
}
