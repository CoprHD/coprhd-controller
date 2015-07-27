/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util.datatable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class DataTableParams {
    private String searchString;
    private String sortColumn;
    private boolean sortDescending;
    private Integer start;
    private Integer pageSize;

    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public List<String> getSearchValues() {
        if (StringUtils.isNotBlank(searchString)) {
            String[] values = searchString.split("\\s+");
            return Arrays.asList(values);
        }
        else {
            return Collections.emptyList();
        }
    }
    
    public String getSortColumn() {
        return sortColumn;
    }

    public void setSortColumn(String sortColumn) {
        this.sortColumn = sortColumn;
    }

    public boolean isSortDescending() {
        return sortDescending;
    }

    public void setSortDescending(boolean sortDescending) {
        this.sortDescending = sortDescending;
    }

    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
