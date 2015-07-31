/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.iwave.ext.windows.model.wmi.IScsiSession;

public class FindIScsiSessions extends WindowsExecutionTask<List<IScsiSession>> {
    private final Set<String> targetIqns;

    public FindIScsiSessions(Set<String> targetIqns) {
        this.targetIqns = targetIqns;
    }

    @Override
    public List<IScsiSession> executeTask() throws Exception {
        List<IScsiSession> results = Lists.newArrayList();
        for (IScsiSession session : getTargetSystem().listIScsiSessions()) {
            if (targetIqns.contains(session.getTargetName())) {
                results.add(session);
            }
        }
        if (results.isEmpty()) {
            throw stateException("illegalState.FindiScsiSessions.noSessionForIQN", targetIqns);
        }
        return results;
    }
}
