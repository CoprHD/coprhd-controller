package com.emc.sa.service.vipr.oe.tasks;

public final class OrchestrationTaskResult {

    private final String out;
    private final String err;
    private final int returnCode;

    public OrchestrationTaskResult(final String out, final String err, final int returnCode) {
        this.out = out;
        this.err = err;
        this.returnCode = returnCode;
    }
    public String getOut() {
        return out;
    }

    public String getErr() {
        return err;
    }

    public int getReturnCode() {
        return returnCode;
    }
}
