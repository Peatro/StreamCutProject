package com.peatroxd.streamcutproject.workerdispatch;

import org.springframework.stereotype.Service;

@Service
public class NoOpWorkerDispatchPort implements WorkerDispatchPort {

    @Override
    public void dispatch(WorkerDispatchPayload payload) {
        // No broker is wired in this MVP stage.
    }
}
