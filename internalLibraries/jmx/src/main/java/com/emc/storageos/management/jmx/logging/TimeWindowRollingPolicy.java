/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.management.jmx.logging;

import java.io.File;
import java.util.Date;
import org.apache.log4j.pattern.PatternConverter;
import org.apache.log4j.rolling.RollingPolicyBase;
import org.apache.log4j.rolling.RolloverDescription;
import org.apache.log4j.rolling.RolloverDescriptionImpl;
import org.apache.log4j.rolling.helper.Action;
import org.apache.log4j.rolling.helper.FileRenameAction;
import org.apache.log4j.rolling.helper.GZCompressAction;
import org.apache.log4j.rolling.helper.ZipCompressAction;

/**
 * A rolling policy that simply adds timestamps to the archived logs.
 */
public final class TimeWindowRollingPolicy extends RollingPolicyBase {

    /**
     * Constructs a new instance.
     */
    public TimeWindowRollingPolicy() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void activateOptions() {
        super.activateOptions();

        PatternConverter itc = getDatePatternConverter();

        if (itc == null) {
            throw new IllegalStateException(
                    "FileNamePattern [" + getFileNamePattern()
                            + "] does not contain a valid date format specifier");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RolloverDescription initialize(
            final String file, final boolean append) {
        String newActiveFile = file;
        boolean explicitActiveFile = false;

        if (activeFileName != null) {
            explicitActiveFile = true;
            newActiveFile = activeFileName;
        }

        if (file != null) {
            explicitActiveFile = true;
            newActiveFile = file;
        }

        if (!explicitActiveFile) {
            StringBuffer buf = new StringBuffer();
            formatFileName(new Date(), buf);
            newActiveFile = buf.toString();
        }

        return new RolloverDescriptionImpl(newActiveFile, append, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RolloverDescription rollover(final String currentFileName) {
        StringBuffer buf = new StringBuffer();
        formatFileName(new Date(), buf);

        String renameTo = buf.toString();
        String compressedName = renameTo;
        Action compressAction = null;

        if (renameTo.endsWith(".gz")) {
            renameTo = renameTo.substring(0, renameTo.length() - 3);
            compressAction =
                    new GZCompressAction(
                            new File(renameTo), new File(compressedName), true);
        } else if (renameTo.endsWith(".zip")) {
            renameTo = renameTo.substring(0, renameTo.length() - 4);
            compressAction =
                    new ZipCompressAction(
                            new File(renameTo), new File(compressedName), true);
        }

        FileRenameAction renameAction =
                new FileRenameAction(
                        new File(currentFileName), new File(renameTo), false);

        return new RolloverDescriptionImpl(
                currentFileName, false, renameAction, compressAction);
    }
}
