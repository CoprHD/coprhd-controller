/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.linux.command.ListMultiPathEntriesCommand;
import com.iwave.ext.linux.model.MultiPathEntry;
import com.iwave.ext.linux.model.PathInfo;

public class FindMultiPathEntryForDmName extends LinuxExecutionTask<MultiPathEntry> {

    private String dmName;

    public FindMultiPathEntryForDmName(String dmName) {
        this.dmName = dmName;
    }

    @Override
    public MultiPathEntry executeTask() throws Exception {
        List<MultiPathEntry> entries = executeCommand(new ListMultiPathEntriesCommand(), SHORT_TIMEOUT);
        MultiPathEntry entry = findMultiPathEntry(dmName, entries);
        if (entry == null) {
            throw stateException("FindMultiPathEntryForDmName.illegalState.couldNotFindEntry", dmName);
        }
        logInfo("find.multipath.dm.name", entry.toString());
        checkStatus(entry);
        return entry;
    }

    protected void checkStatus(MultiPathEntry entry) {
        for (PathInfo path : entry.getPaths()) {
            if (path.isFailed()) {
                logWarn("find.multipath.dm.name.failed", entry.getDmName(), path.getDevice());
            }
        }
    }

    protected MultiPathEntry findMultiPathEntry(String dmName, List<MultiPathEntry> multipathEntries) {
        for (MultiPathEntry entry : multipathEntries) {
            String entryDmName= entry.getDmName();
			logDebug("FindMultiPathEntryForDmName.log.checking", entry.getName(), entry.getDmName(), dmName);
            if (StringUtils.equalsIgnoreCase(entryDmName, dmName)) {
                return entry;
            }
        }
        return null;
    }
    
}
