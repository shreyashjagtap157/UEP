package com.universalplatform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class BackgroundWorkerConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BackgroundWorkerConfiguration.class);

    @Test
    void scheduledWorkerInfrastructureIsEnabledByDefault() {
        runner.run(context -> assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).hasSize(1));
    }

    @Test
    void scheduledWorkerInfrastructureCanBeDisabledForServerlessApiInstances() {
        runner.withPropertyValues("platform.background-workers.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty());
    }
}
