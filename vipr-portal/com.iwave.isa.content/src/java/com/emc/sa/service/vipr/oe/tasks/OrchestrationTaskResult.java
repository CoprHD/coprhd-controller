package com.emc.sa.service.vipr.oe.tasks;

public class OrchestrationTaskResult {

    private String out;
    private String err;
    private int returnCode;

    public OrchestrationTaskResult(final String out, final String err, final int returnCode) {
        this.out = out;
        this.err = err;
        this.returnCode = returnCode;
    }
    public String getOut() {
        return out;
    }

    public void setOut(String out) {
        this.out = out;
    }

    public String getErr() {
        return err;
    }

    public void setErr(String err) {
        this.err = err;
    }

    public int getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(int returnCode) {
        this.returnCode = returnCode;
    }
}
