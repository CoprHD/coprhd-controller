/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class DDPaging {

    @SerializedName("current_page")
    @JsonProperty(value = "current_page")
    private int currentPage;

    @SerializedName("page_entries")
    @JsonProperty(value = "page_entries")
    private int pageEntries;

    @SerializedName("total_entries")
    @JsonProperty(value = "total_entries")
    private int totalEntries;

    @SerializedName("page_size")
    @JsonProperty(value = "page_size")
    private int pageSize;

    @SerializedName("page_links")
    @JsonProperty(value = "page_links")
    private List<DDRestLinkRep> pageLinks;

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getPageEntries() {
        return pageEntries;
    }

    public void setPageEntries(int pageEntries) {
        this.pageEntries = pageEntries;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    public void setTotalEntries(int totalEntries) {
        this.totalEntries = totalEntries;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<DDRestLinkRep> getPageLinks() {
        return pageLinks;
    }

    public void setPageLinks(List<DDRestLinkRep> pageLinks) {
        this.pageLinks = pageLinks;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
