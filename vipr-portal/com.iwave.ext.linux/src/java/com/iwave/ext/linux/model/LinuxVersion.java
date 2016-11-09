/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.model;

public class LinuxVersion {

    private LinuxDistribution distribution;
    private String version;

    public LinuxVersion(LinuxDistribution distro, String version)
    {
        setDistribution(distro);
        setVersion(version);
    }

    @Override
    public String toString() {
        return String.format("%s %s", distribution, version);
    }

    public LinuxDistribution getDistribution() {
        return distribution;
    }

    public void setDistribution(LinuxDistribution distribution) {
        this.distribution = distribution;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public enum LinuxDistribution {
        SUSE("SuSE"),
        REDHAT("RHEL"),
        CENTOS("CentOS"),
        UNKNOWN("N/A");

        private String name;

        private LinuxDistribution(String name) {
            this.setName(name);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public static LinuxDistribution fromName(String name) {
            if (SUSE.getName().equals(name)) {
                return SUSE;
            }
            else if (REDHAT.getName().equals(name)) {
                return REDHAT;
            }
            else if (CENTOS.getName().equals(name)) {
                return CENTOS;
            }
            else {
                return UNKNOWN;
            }
        }
    }

}
