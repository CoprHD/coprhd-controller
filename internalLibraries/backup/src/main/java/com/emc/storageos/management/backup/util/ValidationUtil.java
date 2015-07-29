/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.management.backup.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import com.google.common.base.Preconditions;

/**
 * Common validation methods for backup service
 */
public class ValidationUtil {
    private static final Logger log = LoggerFactory.getLogger(ValidationUtil.class);

    /**
     * The enum of user requirements when file is not exist
     */
    public enum NotExistEnum {
        NOT_EXSIT_OK,
        NOT_EXSIT_ERROR,
        NOT_EXSIT_CREATE
    }

    /**
     * The enum of file type
     */
    public enum FileType {
        File,
        Dir,
        Any
    }

    /**
     * Validate file based on user's requirements
     */
    public static void validateFile(File file, FileType type, NotExistEnum notExist)
            throws IOException {
        Preconditions.checkNotNull(file, "Invalid parameter");
        if (file.exists()) {
            switch (type) {
                case File:
                    Preconditions.checkState(file.isFile(),
                            "%s is not a file", file.getAbsolutePath());
                    break;
                case Dir:
                    Preconditions.checkState(file.isDirectory(),
                            "%s is not a directory", file.getAbsolutePath());
                    break;
                case Any:
                    break;
                default:
                    log.error("not support file type: {}", type.toString());
                    throw new UnsupportedOperationException();
            }
        } else {
            switch (notExist) {
                case NOT_EXSIT_OK:
                    log.info("File is not exist: {}", file.getAbsoluteFile());
                    return;
                case NOT_EXSIT_ERROR:
                    log.error("File is not exist: {}", file.getAbsoluteFile());
                    throw new IllegalStateException("File is not exist");
                case NOT_EXSIT_CREATE:
                    if (type == FileType.File) {
                        file.createNewFile();
                    } else {
                        file.mkdirs();
                    }
                    break;
                default:
                    log.error("not support enum type: {}", notExist.toString());
                    throw new UnsupportedOperationException();
            }
        }
    }

}
