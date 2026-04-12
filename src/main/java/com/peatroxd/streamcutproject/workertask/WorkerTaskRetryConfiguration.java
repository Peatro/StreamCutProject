package com.peatroxd.streamcutproject.workertask;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WorkerTaskRetryProperties.class)
public class WorkerTaskRetryConfiguration {
}
