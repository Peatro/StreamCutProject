package com.peatroxd.streamcutproject.workerexecution;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WorkerExecutionProperties.class)
public class WorkerExecutionConfiguration {
}
