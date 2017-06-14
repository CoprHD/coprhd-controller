/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import org.apache.commons.lang3.StringUtils;

/**
 * Gets the list of currently mounted filesystems on the host using the command "df"
 *
 */
public class GetMountedFilesystem extends LinuxExecutionTask<String> {

    private static int DF_DEVICE_INDEX = 0;
    private static int DF_PATH_INDEX = 5;

    private String path;

    public GetMountedFilesystem(String path) {
        this.path = path;
    }

    @Override
    public String executeTask() throws Exception {
        ListMountedFileSystemsCommand command = new ListMountedFileSystemsCommand();
        String output = executeCommand(command, SHORT_TIMEOUT);

        if (output != null) {
            String[] lines = output.split(System.getProperty("line.separator"));
            if (lines != null) {
                for (String line : lines) {
                    String[] values = line.split("\\s+");
                    if (values != null && values.length > DF_PATH_INDEX && StringUtils.equals(path, values[DF_PATH_INDEX])) {
                        return values[DF_DEVICE_INDEX];
                    }
                }
            }
        }
        return null;
    }
}
