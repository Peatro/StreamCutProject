package com.peatroxd.streamcutproject.workerdispatch;

public interface WorkerDispatchPort {

    void dispatch(WorkerDispatchPayload payload);
}
