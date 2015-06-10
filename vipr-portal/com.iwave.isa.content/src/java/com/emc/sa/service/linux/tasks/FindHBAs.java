/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.emc.sa.service.linux.LinuxUtils;
import com.iwave.ext.linux.command.ListHBAInfoCommand;
import com.iwave.ext.linux.model.HBAInfo;

public class FindHBAs extends LinuxExecutionTask<List<HBAInfo>> {

    private Collection<String> pwwns;

    public FindHBAs(Collection<String> pwwns) {
        this.pwwns = pwwns;
    }

    @Override
    public List<HBAInfo> executeTask() throws Exception {
        List<HBAInfo> results = executeCommand(new ListHBAInfoCommand(), SHORT_TIMEOUT);
        debug("FindHBAs.lookingForWWNs", pwwns);
        Iterator<HBAInfo> iter = results.iterator();
        while (iter.hasNext()) {
            HBAInfo hba = iter.next();
            String wwn = LinuxUtils.normalizeWWN(hba.getWwpn());
            if (!pwwns.contains(wwn)) {
                logDebug("FindHBAs.ignoringHBA", hba);
                iter.remove();
            }
        }
        if (results.isEmpty()) {
            throw stateException("FindHBAs.illegalState.couldNotFindHBAs", pwwns);
        }
        return results;
    }
}
