/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import java.util.concurrent.atomic.AtomicReference;

public class ScaleIOContants {
    static final String REGEX_CAPACITY = "\\s+\\d+(?:\\.\\d+)?\\s+\\w{2}\\s+\\((.*?)\\)";
    static final String REGEX_CAPACITY_NO_SPACE_IN_FRONT = "\\d+(?:\\.\\d+)?\\s+\\w{2}\\s+\\((.*?)\\)";
    static final String REGEX_BYTES_CAPACITY = "\\s+(\\d+)\\s+Bytes\\s+";

    enum PoolCapacityMultiplier {

        BYTES("Bytes", 1),
        KB_BYTES("KB", 1024),
        MB_BYTES("MB", 1048576),
        GB_BYTES("GB", 1073741824),
        TB_BYTES("TB", 1099511627776L),
        PB_BYTES("PB", 1125899906842624L);

        private String postFix;
        private long multiplier;

        PoolCapacityMultiplier(String name, long multipler) {
            this.postFix = name;
            this.multiplier = multipler;
        }

        static AtomicReference<PoolCapacityMultiplier[]> cachedValues =
                new AtomicReference<PoolCapacityMultiplier[]>();

        static PoolCapacityMultiplier matches(String name) {
            if (cachedValues.get() == null) {
                cachedValues.compareAndSet(null, values());
            }
            for (PoolCapacityMultiplier thisMultiplier : cachedValues.get()) {
                if (name.endsWith(thisMultiplier.postFix)) {
                    return thisMultiplier;
                }
            }
            return null;
        }

        String getPostFix() {
            return postFix;
        }

        public long getMultiplier() {
            return multiplier;
        }
    }
}
