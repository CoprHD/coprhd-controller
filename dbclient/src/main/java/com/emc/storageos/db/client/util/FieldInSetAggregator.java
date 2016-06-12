/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import com.emc.storageos.db.client.DbAggregatorItf;
import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.Row;

import java.net.URISyntaxException;
import java.util.*;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FieldInSetAggregator implements DbAggregatorItf {
    private static final Logger log = LoggerFactory.getLogger(FieldInSetAggregator.class);
    private ColumnField _columnField;
    private String _field;
    private List<URI> _list;
    private Set<String> _set;
    private DataObjectType _doType;

    /**
     * @return the aggregated value.
     */
    public List<URI> getAggregate() {
        return _list;
    }

    public String[] getAggregatedFields() {
        return new String[] { _field };
    }

    public FieldInSetAggregator(Class<? extends DataObject> clazz, Set<String> allowedValues, String field) {
        _list = new ArrayList<URI>();
        _set = allowedValues;
        _doType = TypeMap.getDoType(clazz);
        _columnField = _doType.getColumnField(field);
        _field = field;
    }

    @Override
    public void aggregate(List<CompositeColumnName> columns) {

        if (columns == null || columns.size() == 0) {
            return;
        }
        
        CompositeColumnName column = columns.iterator().next();
        if (column.getOne().equals(_field)) {
            String value = ColumnValue.getPrimitiveColumnValue(column.getValue(),
                    _columnField.getPropertyDescriptor()).toString();
            if (_set.contains(value)) {
                try {
                    _list.add(new URI(column.getRowKey()));
                } catch (URISyntaxException ex) {
                    log.warn("URI syntax error:{}", ex);
                }
            }
        }
    }

}
