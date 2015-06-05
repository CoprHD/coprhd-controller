/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command.version;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.command.CommandException;
import com.iwave.ext.linux.model.LinuxVersion;

public class GetRedhatVersionCommandTwo extends LinuxVersionCommand {
    private static final String PACKAGE_NOT_INSTALLED = "package redhat-release is not installed";

    public GetRedhatVersionCommandTwo() {
        setCommand("rpm -q --queryformat '%{RELEASE}' redhat-release");
    }

    @Override
    public void parseOutput() {
        String stdOut = getOutput().getStdout();
        results = new LinuxVersion(LinuxVersion.LinuxDistribution.REDHAT, stdOut);
    }

    @Override
    protected void processError() throws CommandException {
        String stdout = StringUtils.trimToEmpty(getOutput().getStdout());
        String stderr = StringUtils.trimToEmpty(getOutput().getStderr());
        if (!StringUtils.equals(stdout, PACKAGE_NOT_INSTALLED) && !StringUtils.equals(stderr, PACKAGE_NOT_INSTALLED)) {
            super.processError();
        }
    }
}
