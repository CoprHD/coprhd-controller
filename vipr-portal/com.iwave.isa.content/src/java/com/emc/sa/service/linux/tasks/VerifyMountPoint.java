/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;
import com.iwave.ext.linux.command.ListMountPointsCommand;
import com.iwave.ext.linux.model.MountPoint;

public class VerifyMountPoint extends LinuxExecutionTask<Void> {

    private String mountPoint;
    private Set<String> disallowedPaths;

    public VerifyMountPoint(String mountPoint) {
        this(mountPoint, null);
    }

    public VerifyMountPoint(String mountPoint, Collection<String> disallowedPaths) {
        provideDetailArgs(mountPoint);
        this.mountPoint = mountPoint;
        this.disallowedPaths = Sets.newHashSet();
        if (disallowedPaths != null) {
            this.disallowedPaths.addAll(disallowedPaths);
        }
    }

    @Override
    public void execute() throws Exception {
        if (!StringUtils.startsWith(mountPoint, "/")) {
            throw stateException("VerifyMountPoint.illegalState.notAbsolute" + mountPoint);
        }
        checkAllowedPath();
        checkExistingMountPoints();
    }

    protected void checkAllowedPath() {
        if ((disallowedPaths != null) && disallowedPaths.contains(mountPoint)) {
            throw stateException("VerifyMountPoint.illegalState.notAllowed", mountPoint);
        }
    }

    protected void checkExistingMountPoints() {
        Map<String, MountPoint> mountPoints = executeCommand(new ListMountPointsCommand(), SHORT_TIMEOUT);
        for (MountPoint mp : mountPoints.values()) {
            if (StringUtils.equals(mp.getPath(), mountPoint)) {
                throw stateException("VerifyMountPOint.illegalState.alreadyExists", mountPoint);
            }
        }
    }
}
