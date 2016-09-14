/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
import com.iwave.ext.linux.command.iscsi.ListIScsiSessionsCommand;
import com.iwave.ext.linux.model.IScsiSession;

public class FindIScsiSessions extends LinuxExecutionTask<List<IScsiSession>> {
    private Set<String> iqns;

    public FindIScsiSessions(Collection<String> iqns) {
        this.iqns = Sets.newHashSet(iqns);
    }

    @Override
    public List<IScsiSession> executeTask() throws Exception {
        List<IScsiSession> sessions = executeCommand(new ListIScsiSessionsCommand(), SHORT_TIMEOUT);
        Iterator<IScsiSession> iter = sessions.iterator();
        while (iter.hasNext()) {
            IScsiSession session = iter.next();
            if (!isMatch(session)) {
                iter.remove();
            }
        }
        return sessions;
    }

    private boolean isMatch(IScsiSession session) {
        if (iqns.contains(session.getIfaceInitiatorName())) {
            return true;
        }
        return false;
    }
}
