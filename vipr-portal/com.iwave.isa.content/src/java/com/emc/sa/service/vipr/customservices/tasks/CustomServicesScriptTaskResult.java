package com.emc.sa.service.vipr.customservices.tasks;

public class CustomServicesScriptTaskResult extends CustomServicesTaskResult {

    private final String scriptOut;

    public CustomServicesScriptTaskResult(final String scriptOut, final String out, final String err, final int retCode) {
        super(out, err, retCode, null);
        this.scriptOut = scriptOut;
    }

    public String getScriptOut() {
        return scriptOut;
    }
}
