/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.version;

import com.iwave.ext.linux.model.LinuxVersion;

public class GetLinuxVersionLSBReleaseCommand extends LinuxVersionCommand {
    private static final String DISTRIBUTOR_ID = "Distributor ID:";
    private static final String RELEASE = "Release:";

    private static final String RHEL = "RedHatEnterpriseServer";
    private static final String SUSE = "SUSE LINUX";
    private static final String CENTOS = "CentOS";

    public GetLinuxVersionLSBReleaseCommand() {
        setCommand("lsb_release -a");
    }

    @Override
    public void parseOutput() {
        String stdOut = getOutput().getStdout();
        String lines[] = stdOut.split("\n");

        LinuxVersion.LinuxDistribution distribution = LinuxVersion.LinuxDistribution.UNKNOWN;
        String release = "";

        for (String line : lines) {
            String lineElements[] = line.split("\t");
            String key = lineElements[0];
            String value = lineElements[1];
            if (key.equalsIgnoreCase(DISTRIBUTOR_ID)) {
                if (RHEL.equals(value)) {
                    distribution = LinuxVersion.LinuxDistribution.REDHAT;
                }
                else if (SUSE.equals(value)) {
                    distribution = LinuxVersion.LinuxDistribution.SUSE;
                }
                else if (CENTOS.equals(value)) {
                    distribution = LinuxVersion.LinuxDistribution.CENTOS;
                }
            }
            else if (key.equalsIgnoreCase(RELEASE)) {
                release = value;
            }
        }

        results = new LinuxVersion(distribution, release);
    }
}
