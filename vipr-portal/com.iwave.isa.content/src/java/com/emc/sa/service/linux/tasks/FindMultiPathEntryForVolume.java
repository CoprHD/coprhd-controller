/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.List;

import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.linux.command.ListMultiPathEntriesCommand;
import com.iwave.ext.linux.model.MultiPathEntry;
import com.iwave.ext.linux.model.PathInfo;
import com.iwave.ext.linux.util.VolumeWWNUtils;

public class FindMultiPathEntryForVolume extends LinuxExecutionTask<MultiPathEntry> {
    /** Linux multipath entries prefix WWNs with a number 3 */
    public static final String LINUX_WWN_PREFIX = "3";

    private BlockObjectRestRep volume;

    public FindMultiPathEntryForVolume(BlockObjectRestRep volume) {
        this.volume = volume;
    }

    @Override
    public MultiPathEntry executeTask() throws Exception {
        List<MultiPathEntry> entries = executeCommand(new ListMultiPathEntriesCommand(), SHORT_TIMEOUT);
        MultiPathEntry entry = findMultiPathEntry(volume, entries);
        if (entry == null) {
            throw stateException("FindMultiPathEntryForVolume.illegalState.couldNotFindEntry", volume.getWwn().toLowerCase());
        }
        logInfo("find.multipath.wwn", entry.toString());
        checkStatus(entry);
        return entry;
    }

    protected void checkStatus(MultiPathEntry entry) {
        for (PathInfo path : entry.getPaths()) {
            if (path.isFailed()) {
                logWarn("find.multipath.wwn.failed", entry.getWwid(), path.getDevice());
            }
        }
    }

    protected MultiPathEntry findMultiPathEntry(BlockObjectRestRep blockVolume, List<MultiPathEntry> multipathEntries) {
        for (MultiPathEntry entry : multipathEntries) {
            String entryWwn = stripWwnPrefix(entry.getWwid());
            logDebug("FindMultiPathEntryForVolume.checking", entry.getName(), entryWwn, blockVolume.getWwn());
            if (VolumeWWNUtils.wwnMatches(entryWwn, volume)) {
                return entry;
            }
        }
        logDebug("FindMultiPathEntryForVolume.noEntries", blockVolume.getWwn());
        return null;
    }

    private static String stripWwnPrefix(String wwn) {
        return wwn.startsWith(LINUX_WWN_PREFIX) ? wwn.substring(1) : wwn;
    }
}
