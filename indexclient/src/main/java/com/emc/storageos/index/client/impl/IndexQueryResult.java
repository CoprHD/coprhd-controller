/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.index.client.impl;

import java.net.URI;
import java.util.List;

public class IndexQueryResult {
    private long totalNum;
    private List<URI> uris;

    public IndexQueryResult(long totalNum, List<URI> uris) {
        this.totalNum = totalNum;
        this.uris = uris;
    }

    public long getTotalNum() {
        return totalNum;
    }

    public List<URI> getUris() {
        return uris;
    }
}
