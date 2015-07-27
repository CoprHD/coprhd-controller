/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class DDDataWritten {
	
	@SerializedName("pre_comp_written")
    @JsonProperty(value="pre_comp_written")
    private long preCompWritten;
	
    @SerializedName("post_comp_written")
    @JsonProperty(value="post_comp_written")
    private long postCompWritten;
	
    @SerializedName("compression_factor")
    @JsonProperty(value="compression_factor")
    private float compressionFactor;

    public long getPreCompWritten() {
        return preCompWritten;
    }

    public void setPreCompWritten(long preCompWritten) {
        this.preCompWritten = preCompWritten;
    }

    public long getPostCompWritten() {
        return postCompWritten;
    }

    public void setPostCompWritten(long postCompWritten) {
        this.postCompWritten = postCompWritten;
    }

    public float getCompressionFactor() {
        return compressionFactor;
    }

    public void setCompressionFactor(float compressionFactor) {
        this.compressionFactor = compressionFactor;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
