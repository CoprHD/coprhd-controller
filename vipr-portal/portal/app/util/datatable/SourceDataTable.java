/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.datatable;

import java.util.ArrayList;
import java.util.List;

import util.datatable.DataTablesSupport.Source;

/**
 * Base class for datatables that provide their own data.
 * 
 * @author jonnymiller
 *
 * @param <T>
 */
public abstract class SourceDataTable<T> extends DataTable {
    /**
     * Fetches the rows for the datatable based on the given parameters.
     * 
     * @param params
     *        the datatable parameters.
     * @return the data.
     */
    public List fetchRows(DataTableParams params) {
        List rows = new ArrayList();
        List<T> data = fetchData(params);
        for (T item : data) {
            rows.add(convert(item));
        }
        return rows;
    }

    /**
     * Fetches the data from the database based on the given parameters.
     * 
     * @param params
     *        the datatable parameters.
     * @return the data.
     */
    protected abstract List<T> fetchData(DataTableParams params);

    /**
     * Converts an item into the row representation. By default, this just returns the item.
     * 
     * @param item
     *        the item to convert.
     * @return the row representation.
     */
    protected Object convert(T item) {
        return item;
    }

    /**
     * Populates a datatable source with data.
     * 
     * @param source the datatable source.
     * @param params the datatable parameters.
     */
    public void populateSource(Source source, DataTableParams params) {
        List data = fetchRows(params);
        source.aaData = data;
        source.iTotalRecords = (long) data.size();
        source.iTotalDisplayRecords = (long) data.size();
    }
}
