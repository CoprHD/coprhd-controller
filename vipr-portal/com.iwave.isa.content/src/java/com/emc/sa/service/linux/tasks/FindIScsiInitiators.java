/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;
import com.iwave.ext.linux.command.iscsi.ListIScsiHostsCommand;
import com.iwave.ext.linux.model.IScsiHost;
import com.iwave.ext.linux.model.IScsiSession;

public class FindIScsiInitiators extends LinuxExecutionTask<List<IScsiHost>> {
    private Set<String> iqns;

    public FindIScsiInitiators(Collection<String> iqns) {
        this.iqns = Sets.newHashSet(iqns);
    }

    @Override
    public List<IScsiHost> executeTask() throws Exception {
        List<IScsiHost> hosts = executeCommand(new ListIScsiHostsCommand(), SHORT_TIMEOUT);
        Iterator<IScsiHost> iter = hosts.iterator();
        while (iter.hasNext()) {
            IScsiHost host = iter.next();
            if (!isMatch(host)) {
                iter.remove();
            }
        }
        if (hosts.isEmpty()) {
            String sourceIqns = StringUtils.join(iqns, ", ");
            throw stateException("illegalState.noActiveIscsiConnections", sourceIqns);
        }
        return hosts;
    }

    private boolean isMatch(IScsiHost host) {
        for (IScsiSession session : host.getSessions()) {
            if (iqns.contains(session.getIfaceInitiatorName())) {
                return true;
            }
        }
        return false;
    }
}
