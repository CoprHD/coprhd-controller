/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.datatable;

import java.util.Collection;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.mvc.Scope;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DataTablesSupport {

    public static String createJSON(SourceDataTable dataTable, Scope.Params requestParams) {
        Source source = createSource(dataTable, requestParams);
        return toJson(source);
    }

    public static String createJSON(Collection data, Scope.Params requestParams) {
        Source source = createSource(data, requestParams);
        //just for performance calculating, should remove when test done.
        String result = toJson(source);
        return result;
    }

    public static String createJSON(Collection data, Scope.Params requestParams, String message) {
        Source source = createSource(data, requestParams, message);
        return toJson(source);
    }

    public static Source createSource(SourceDataTable dataTable, Scope.Params requestParams) {
        DataTableParams params = createParams(requestParams);
        Source source = new Source();
        dataTable.populateSource(source, params);
        source.sEcho = requestParams.get("sEcho", Integer.class);
        return source;
    }

    public static Source createSource(Collection data, Scope.Params requestParams) {
        Source source = new Source();
        source.aaData = data;
        source.iTotalRecords = (long) data.size();
        source.iTotalDisplayRecords = (long) data.size();
        source.sEcho = requestParams.get("sEcho", Integer.class);
        return source;
    }

    public static Source createSource(Collection data, Scope.Params requestParams, String message) {
        Source source = createSource(data, requestParams);
        source.message = StringUtils.defaultIfEmpty(message, null);
        return source;
    }

    public static DataTableParams createParams(Scope.Params requestParams) {
        DataTableParams params = new DataTableParams();
        params.setSearchString(requestParams.get("sSearch"));
        Integer sortIndex = requestParams.get("iSortCol_0", Integer.class);
        if (sortIndex != null) {
            params.setSortColumn(requestParams.get("mDataProp_" + sortIndex));
        }
        params.setSortDescending("desc".equals(requestParams.get("sSortDir_0")));
        params.setStart(requestParams.get("iDisplayStart", Integer.class));
        params.setPageSize(requestParams.get("iDisplayLength", Integer.class));
        return params;
    }

    public static String toJson(Object source) {
        Gson gson = new GsonBuilder().serializeNulls().create();
        // Note that GSON will escape HTML characters to their unicode versions.
        // The browser will still interpret tags. Escape < 'u003c' to &lt; so it will be ignored by browsers
        return gson.toJson(source).replaceAll("\\\\u003c", "&lt;");
    }

    public static class Source {
        public Long iTotalRecords;
        public Long iTotalDisplayRecords;
        public Integer sEcho;
        public Collection aaData;
        public String message;
    }
}
