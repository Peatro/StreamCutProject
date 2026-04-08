package com.peatroxd.streamcutproject.workerexecution;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.worker-execution")
@Getter
@Setter
public class WorkerExecutionProperties {

    private Duration staleTimeout = Duration.ofMinutes(2);
    private Duration reconcileInterval = Duration.ofSeconds(30);

}
