/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.Collections;
import java.util.List;

public class GetDirectoryContentsNoFail extends GetDirectoryContents {

    private String directory;

    public GetDirectoryContentsNoFail(String directory) {
        super(directory);
        this.directory = directory;
    }

    @Override
    public List<String> executeTask() throws Exception {
        try {
            return super.executeTask();
        } catch (Exception ex) {
            logInfo("linux.support.directory.does.not.exist", directory);
        }
        return Collections.emptyList();
    }
}
