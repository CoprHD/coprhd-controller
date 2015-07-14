/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import static com.emc.sa.util.ArrayUtil.safeArray;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.HostStorageAPI;
import com.vmware.vim25.HostHostBusAdapter;
import com.vmware.vim25.HostInternetScsiHba;
import com.vmware.vim25.mo.HostSystem;

public class AddInternetScsiTargets extends ExecutionTask<Void> {
    private HostSystem host;
    private Map<String, HostHostBusAdapter> hbas;
    private String[] addresses;

    public AddInternetScsiTargets(HostSystem host, Map<String, HostHostBusAdapter> hbas, String[] addresses) {
        this.host = host;
        this.hbas = hbas;
        this.addresses = safeArray(addresses);
        provideDetailArgs(StringUtils.join(addresses, ", "), host.getName());
    }

    @Override
    public void execute() throws Exception {
        HostStorageAPI hostStorageAPI = new HostStorageAPI(host);
        for (String iqn : hbas.keySet()) {
            HostHostBusAdapter hba = hbas.get(iqn);
            if (hba instanceof HostInternetScsiHba) {
                debug("Adding iSCSI send targets %s for HBA %s [%s]", StringUtils.join(addresses, ", "),
                        hba.getDevice(), iqn);
                hostStorageAPI.addInternetScsiSendTargets((HostInternetScsiHba) hba, addresses);
            }
        }
        hostStorageAPI.rescanHBAs();
    }
}
