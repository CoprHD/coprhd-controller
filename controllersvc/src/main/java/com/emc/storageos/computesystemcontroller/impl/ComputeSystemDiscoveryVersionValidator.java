/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import com.emc.aix.model.AixVersion;
import com.emc.hpux.model.HpuxVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.util.VersionChecker;
import com.iwave.ext.linux.model.LinuxVersion;
import com.iwave.ext.vmware.EsxVersion;
import com.iwave.ext.vmware.VcenterVersion;
import com.iwave.ext.windows.model.wmi.WindowsVersion;

@Component
public class ComputeSystemDiscoveryVersionValidator {
    private static final String WINDOWS_MIN_PROP = "compute_windows_version";
    private static final String REDHAT_MIN_PROP = "compute_redhat_linux_version";
    private static final String SUSE_MIN_PROP = "compute_suse_linux_version";
    private static final String VCENTER_MIN_PROP = "compute_vmware_vcenter_version";
    private static final String AIX_MIN_PROP = "compute_aix_version";
    private static final String AIXVIO_MIN_PROP = "compute_aixvio_version";
    private static final String VMWARE_ESX_MIN_PROP = "compute_vmware_esx_version";
    private static final String HPUX_MIN_PROP = "compute_hpux_version";

    private CoordinatorClient coordinatorClient;

    private WindowsVersion windowsVersion;
    private LinuxVersion redhatVersion;
    private LinuxVersion suseVersion;
    private VcenterVersion vcenterVersion;
    private AixVersion aixVersion;
    private AixVersion aixVioVersion;
    private EsxVersion esxVersion;
    private HpuxVersion hpuxVersion;

    public boolean isValidVersionNumber(String versionNumber) {
        boolean result = false;

        if (!StringUtils.isEmpty(versionNumber)) {
            String testVersionNumber = versionNumber.trim();
            if (testVersionNumber.endsWith(".")) {
                return false;
            }

            String[] parts = testVersionNumber.split("\\.");
            for (String part : parts) {
                if (StringUtils.isEmpty(part) || !StringUtils.isNumeric(part)) {
                    return false;
                }
            }
            return true;
        }
        return result;
    }

    private String getSysProperty(String property) {

        String result = null;
        Map<String, String> properties = coordinatorClient.getPropertyInfo().getAllProperties();
        for (String key : properties.keySet()) {
            if (key != null && key.equals(property)) {
                return properties.get(key);
            }
        }
        return result;
    }

    public WindowsVersion getWindowsMinimumVersion(boolean forceLookup) {
        if (forceLookup || windowsVersion == null) {
            String versionProp = this.getSysProperty(WINDOWS_MIN_PROP);
            if (isValidVersionNumber(versionProp)) {
                windowsVersion = new WindowsVersion(versionProp, "");
            }
            else {
                windowsVersion = null;
                throw new IllegalStateException(String.format("System property for Windows Version Number(%s) is invalid - value is '%s'",
                        WINDOWS_MIN_PROP, versionProp));
            }

        }
        return windowsVersion;
    }

    public HpuxVersion getHpuxMinimumVersion(boolean forceLookup) {
        if (forceLookup || hpuxVersion == null) {
            String versionProp = this.getSysProperty(HPUX_MIN_PROP);
            if (isValidVersionNumber(versionProp)) {
                hpuxVersion = new HpuxVersion(versionProp);
            }
            else {
                hpuxVersion = null;
                throw new IllegalStateException(String.format("System property for HPUX Version Number(%s) is invalid - value is '%s'",
                        HPUX_MIN_PROP, versionProp));
            }
        }
        return hpuxVersion;
    }

    public AixVersion getAixMinimumVersion(boolean forceLookup) {
        if (forceLookup || aixVersion == null) {
            String versionProp = this.getSysProperty(AIX_MIN_PROP);
            if (isValidVersionNumber(versionProp)) {
                aixVersion = new AixVersion(versionProp);
            }
            else {
                aixVersion = null;
                throw new IllegalStateException(String.format("System property for AIX Version Number(%s) is invalid - value is '%s'",
                        AIX_MIN_PROP, versionProp));
            }
        }
        return aixVersion;
    }

    public AixVersion getAixVioMinimumVersion(boolean forceLookup) {
        if (forceLookup || aixVioVersion == null) {
            String versionProp = this.getSysProperty(AIXVIO_MIN_PROP);
            if (isValidVersionNumber(versionProp)) {
                aixVioVersion = new AixVersion(versionProp);
            }
            else {
                aixVioVersion = null;
                throw new IllegalStateException(String.format("System property for AIX VIO Version Number(%s) is invalid - value is '%s'",
                        AIXVIO_MIN_PROP, versionProp));
            }
        }
        return aixVioVersion;
    }

    public LinuxVersion getRedhatLinuxMinimumVersion(boolean forceLookup) {
        if (forceLookup || redhatVersion == null) {
            String versionProp = this.getSysProperty(REDHAT_MIN_PROP);
            if (isValidVersionNumber(versionProp)) {
                redhatVersion = new LinuxVersion(LinuxVersion.LinuxDistribution.REDHAT, versionProp);
            }
            else {
                redhatVersion = null;
                throw new IllegalStateException(String.format(
                        "System property for Redhat Linux Version Number(%s) is invalid - value is '%s'", REDHAT_MIN_PROP, versionProp));
            }

        }
        return redhatVersion;
    }

    public LinuxVersion getSuSELinuxMinimumVersion(boolean forceLookup) {
        if (forceLookup || suseVersion == null) {
            String versionProp = this.getSysProperty(SUSE_MIN_PROP);
            if (isValidVersionNumber(versionProp)) {
                suseVersion = new LinuxVersion(LinuxVersion.LinuxDistribution.SUSE, versionProp);
            }
            else {
                suseVersion = null;
                throw new IllegalStateException(String.format(
                        "System property for SuSE Enterprise Linux Version Number(%s) is invalid - value is '%s'", SUSE_MIN_PROP,
                        versionProp));
            }

        }
        return suseVersion;
    }

    public VcenterVersion getVcenterMinimumVersion(boolean forceLookup) {
        if (forceLookup || vcenterVersion == null) {
            String versionProp = this.getSysProperty(VCENTER_MIN_PROP);
            if (isValidVersionNumber(versionProp)) {
                vcenterVersion = new VcenterVersion(versionProp);
            }
            else {
                vcenterVersion = null;
                throw new IllegalStateException(String.format(
                        "System property for VMware vCenter Version Number(%s) is invalid - value is '%s'", VCENTER_MIN_PROP, versionProp));
            }

        }
        return vcenterVersion;
    }

    public EsxVersion getEsxMinimumVersion(boolean forceLookup) {
        if (forceLookup || esxVersion == null) {
            String versionProp = this.getSysProperty(VMWARE_ESX_MIN_PROP);
            if (isValidVersionNumber(versionProp)) {
                esxVersion = new EsxVersion(versionProp);
            }
            else {
                esxVersion = null;
                throw new IllegalStateException(String.format(
                        "System property for VMware ESX Version Number(%s) is invalid - value is '%s'", VMWARE_ESX_MIN_PROP, versionProp));
            }
        }
        return esxVersion;
    }

    public ComputeSystemDiscoveryVersionValidator() {
        super();
    }

    public boolean isValidHpuxVersion(HpuxVersion version) {
        return (VersionChecker.verifyVersionDetails(
                getHpuxMinimumVersion(true).getVersion(), version.getVersion()) >= 0) ? true : false;
    }

    public boolean isValidAixVersion(AixVersion version) {
        return (VersionChecker.verifyVersionDetails(
                getAixMinimumVersion(true).getVersion(), version.getVersion()) >= 0) ? true : false;
    }

    public boolean isValidAixVioVersion(AixVersion version) {
        return (VersionChecker.verifyVersionDetails(
                getAixVioMinimumVersion(true).getVersion(), version.getVersion()) >= 0) ? true : false;
    }

    public boolean isValidVcenterVersion(VcenterVersion version) {
        return (VersionChecker.verifyVersionDetails(
                getVcenterMinimumVersion(true).getVersion(), version.getVersion()) >= 0) ? true : false;
    }

    public boolean isValidWindowsVersion(WindowsVersion version) {
        return (VersionChecker.verifyVersionDetails(
                getWindowsMinimumVersion(true).getVersion(), version.getVersion()) >= 0) ? true : false;
    }

    public boolean isValidLinuxVersion(LinuxVersion version) {
        if (LinuxVersion.LinuxDistribution.REDHAT.equals(version.getDistribution())) {
            return (VersionChecker.verifyVersionDetails(
                    getRedhatLinuxMinimumVersion(true).getVersion(), version.getVersion()) >= 0) ? true : false;
        }
        else if (LinuxVersion.LinuxDistribution.SUSE.equals(version.getDistribution())) {
            return (VersionChecker.verifyVersionDetails(
                    getSuSELinuxMinimumVersion(true).getVersion(), version.getVersion()) >= 0) ? true : false;
        }
        else {
            return false;
        }
    }

    public void setCoordinatorClient(CoordinatorClient client) {
        this.coordinatorClient = client;
    }

    public boolean isValidEsxVersion(EsxVersion version) {
        return (VersionChecker.verifyVersionDetails(
                getEsxMinimumVersion(true).getVersion(), version.getVersion()) >= 0) ? true : false;
    }

}
