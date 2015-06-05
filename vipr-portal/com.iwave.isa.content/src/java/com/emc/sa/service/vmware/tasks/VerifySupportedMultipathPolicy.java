/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import javax.inject.Inject;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.HostPathSelectionPolicyOption;
import com.vmware.vim25.mo.HostSystem;

public class VerifySupportedMultipathPolicy extends ExecutionTask<Void> {
    @Inject
    private VCenterAPI vcenter;
    private String multipathPolicy;
    private HostStorageAPI hostStorageAPI;

    public VerifySupportedMultipathPolicy(HostSystem host, String multipathPolicy) {
        this.multipathPolicy = multipathPolicy;
        this.hostStorageAPI = new HostStorageAPI(host);
        provideDetailArgs(multipathPolicy);
    }

    @Override
    public void execute() throws Exception {
        HostPathSelectionPolicyOption[] policies = hostStorageAPI.getPathSelectionPolicyOptions();
        if (!isPathPolicySupported(policies, multipathPolicy)) {
            throw stateException("VerifySupportedMultipathPolicy.illegalState.policy.not.supported", multipathPolicy);
        }
    }
    
    private boolean isPathPolicySupported(HostPathSelectionPolicyOption[] supportedPolicies, String multipathPolicy) {
        if (supportedPolicies != null) {
            for (HostPathSelectionPolicyOption policy : supportedPolicies) {
                if (policy.getPolicy().getKey().toString().equalsIgnoreCase(multipathPolicy.toString())) {
                    return true;
                }
            }
        }
        return false;
    }
}
