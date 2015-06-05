/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.aix.command.ListMountPointsCommand;
import com.emc.aix.model.MountPoint;
import com.google.common.collect.Sets;

public class VerifyMountPoint extends AixExecutionTask<Void> {

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
            throw new IllegalStateException( getMessage("VerifyMountPoint.log.notAbsolutePath", mountPoint) );
        }
        checkAllowedPath();
        checkExistingMountPoints();
    }

    protected void checkAllowedPath() {
        if ((disallowedPaths != null) && disallowedPaths.contains(mountPoint)) {
            throw new IllegalStateException( getMessage("VerifyMountPoint.log.notAllowed", mountPoint) );
        }
    }

    protected void checkExistingMountPoints() {
        Map<String, MountPoint> mountPoints = executeCommand(new ListMountPointsCommand(), SHORT_TIMEOUT);
        for (MountPoint mp : mountPoints.values()) {
            if (StringUtils.equals(mp.getPath(), mountPoint)) {
                throw new IllegalStateException( getMessage("VerifyMountPoint.log.exists", mountPoint) );
            }
        }
    }
}