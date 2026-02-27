package com.example.scheduler;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ScheduledTaskRunnable extends AbstractScheduledTask implements ApplicationContextAware {

    private final SchedulerTask task;
    private ApplicationContext context;

    public ScheduledTaskRunnable(SchedulerTask task) {
        this.task = task;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.context = context;
    }

    @Override
    protected String getTaskName() {
        return task.getName();
    }

    @Override
    protected void execute() throws Exception {
        Object bean = context.getBean(task.getBean());
        Method method = bean.getClass().getMethod(task.getMethod());
        method.invoke(bean);
    }
}
