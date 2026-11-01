package com.universalplatform;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables database-backed polling workers only on deployments that can keep
 * a worker process alive. Serverless request containers must opt out.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "platform.background-workers", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class BackgroundWorkerConfiguration {
}
