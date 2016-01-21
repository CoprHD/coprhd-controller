/*
 * Some common functions for working with datatables.
 */

function getDataTableWrapperId(selector) {
    return $(selector).parents(".dataTables_wrapper").attr("id");
}

function getDataTableWrapperSelector(selector) {
    var id = getDataTableWrapperId(selector);
    if (id) {
        return "#" + id;
    }
    return "";
}

/**
 * Watches a particular field from the datatable's row data.  Each row's field is checked for one of the trigger values
 * and when a match is found that row will be updated. The fieldsToUpdate determine which fields are actually updated
 * from the replacement data.
 * 
 * @param datatable the datatable.
 * @param itemsJson the items JSON function for retrieving items by ID {ids:'id1,id2,...'}
 * @param fieldName the name of the field to watch.
 * @param triggerValues the values of the field to trigger updates.
 * @param fieldsToUpdate the field values to actually update.
 * @param frequency the update frequency, if undefined defaults to 2000.
 */
function watchDatatableField(datatable, itemsJson, fieldName, triggerValues, fieldsToUpdate, frequency) {
    if (!frequency) {
        frequency = 2000;
    }
    var update = function() {
        var ids = new Array();
        var data = datatable.fnGetData();
        for (var i = 0; i < data.length; i++) {
            var row = data[i];
            var fieldValue = row[fieldName];
            // If the field value is one of the trigger values, update this row
            if ($.inArray(fieldValue, triggerValues) > -1) {
                ids.push(row.id);
            }
        }
        
        if (ids.length > 0) {
            var url = itemsJson({ids: ids.join(",")});
            var request = $.get(url, function(results) {
                updateDatatableRows(datatable, results, fieldsToUpdate);
            }).always(function () {
                window.setTimeout(update, frequency);
            });
        }
    };
    window.setTimeout(update, frequency);
}

/**
 * Updates the datatable rows with the provided items, updating only the fields specified for the matching row.
 * 
 * @param datatable the datatable.
 * @param items the items to update.
 * @param fields the fields to update.
 */
function updateDatatableRows(datatable, items, fields) {
    // Convert items to associative array (by ID)
    var data = new Array();
    for (var i = 0; i < items.length; i++) {
        data[items[i].id] = items[i];
    }
    
    var updates = false;
    var rows = datatable.fnGetData();
    for (var i = 0; i < rows.length; i++) {
        var row = rows[i];
        
        var newRow = data[row.id];
        if (newRow) {
            var changed = false;
            for (var j = 0; j < fields.length; j++) {
                var fieldName = fields[j];
                if (newRow[fieldName] != row[fieldName]) {
                    changed = true;
                    row[fieldName] = newRow[fieldName];
                }
            }
            
            if (changed) {
                updates = true;
                datatable.fnUpdate(row, i, undefined, false, false);
            }
        }
    }
    
    if (updates) {
        datatable.fnStandingRedraw();
    }
}

/**
 * Updates rows in a datatable based on discovery status.
 * 
 * @param datatable the datatable.
 * @param itemJson the callback function for retrieving the JSON for a list of items ( {ids: 'id1,id2,...'} as the format ).
 * @param extraFields (optional) an array of fields to update in addition to the standard discovery fields.
 */
function watchDiscoveryStatus(datatable, itemsJson, extraFields) {
    var fields = ['discoveryStatus', 'lastDiscoveredDate', 'errorSummary', 'errorDetails', 'statusMessage', 'expandDetails'];
    if (extraFields != null) {
        fields = fields.concat(extraFields);
    }
    var fieldToWatch = 'discoveryStatus';
    var triggerValues = ['PROCESSING', 'PENDING', 'IN_PROGRESS', 'CREATED', 'SCHEDULED'];
    watchDatatableField(datatable, itemsJson, fieldToWatch, triggerValues, fields);
}

/**
 * Updates rows in a datatable based on vdc status.
 * 
 * @param datatable the datatable.
 * @param itemJson the callback function for retrieving the JSON for a list of items ( {ids: 'id1,id2,...'} as the format ).
 * @param extraFields (optional) an array of fields to update in addition to the standard vdc fields.
 */
function watchVDCStatus(datatable, itemsJson, extraFields) {
    var fields = ['connectionStatus', 'canDelete', 'canDisconnect', 'canReconnect'];
    if (extraFields != null) {
        fields = fields.concat(extraFields);
    }
    var fieldToWatch = 'connectionStatus';
    var fastChangingValues = ['RECONNECTING','CONNECTING', 'CONNECTING_SYNCED', 'DISCONNECTING', 'REMOVE_SYNCED', 'REMOVING', 'UPDATING'];
    var slowChangingValues = ['ISOLATED', 'CONNECTED', 'CONNECT_PRECHECK_FAILED', 'DISCONNECTED', 
                              'REMOVE_PRECHECK_FAILED', 'REMOVE_FAILED', 'UPDATE_PRECHECK_FAILED', 'UPDATE_FAILED'];
    watchDatatableField(datatable, itemsJson, fieldToWatch, fastChangingValues, fields);
    watchDatatableField(datatable, itemsJson, fieldToWatch, slowChangingValues, fields, 30000);
}

/**
 * Updates rows in a datatable based on task status.
 * 
 * @param datatable the datatable.
 * @param itemJson the callback function for retrieving the JSON for a list of items ( {ids: 'id1,id2,...'} as the format ).
 * @param extraFields (optional) an array of fields to update in addition to the standard task fields.
 */
function watchTaskState(datatable, itemsJson, extraFields) {
    var fields = ['state', 'progress', 'displayState', 'message', 'description', 'end'];
    if (extraFields != null) {
        fields = fields.concat(extraFields);
    }
    var fieldToWatch = 'state';
    var triggerValues = ['pending'];
    watchDatatableField(datatable, itemsJson, fieldToWatch, triggerValues, fields);
}

/**
 * Updates rows in a datatable based on order status.
 * 
 * @param datatable the datatable.
 * @param itemJson the callback function for retrieving the JSON for a list of items ( {ids: 'id1,id2,...'} as the format ).
 * @param extraFields (optional) an array of fields to update in addition to the standard discovery fields.
 */
function watchOrderStatus(datatable, itemsJson, extraFields) {
    var fields = ['status'];
    if (extraFields != null) {
        fields = fields.concat(extraFields);
    }
    
    var fieldToWatch = 'status';
    var triggerValues = ['APPROVAL', 'APPROVED', 'EXECUTING', 'PENDING', 'SCHEDULED'];
    watchDatatableField(datatable, itemsJson, fieldToWatch, triggerValues, fields, 5000);
}

function watchUploadState(datatable, itemsJson, extraFields) {
    var fields = ['status'];
    if (extraFields != null) {
        fields = fields.concat(extraFields);
    }
    var fieldToWatch = 'status';
    var triggerValues = ['NOT_STARTED', 'IN_PROGRESS', 'FAILED', 'DONE', 'CANCELLED'];
    watchDatatableField(datatable, itemsJson, fieldToWatch, triggerValues, fields);
}

/**
 * Updates rows in a datatable based on data store status.
 * 
 * @param datatable the datatable.
 * @param itemJson the callback function for retrieving the JSON for a list of items ( {ids: 'id1,id2,...'} as the format ).
 * @param extraFields (optional) an array of fields to update in addition to the standard discovery fields.
 */
function watchDataStoreState(datatable, itemsJson, extraFields) {
    var fields = ['state', 'freeCapacity', 'totalCapacity', 'hostName', 'mountPoint'];
    if (extraFields != null) {
        fields = fields.concat(extraFields);
    }
    var fieldToWatch = 'state';
    var triggerValues = ['initializing', 'initialized', 'intenttodelete', 'deleting'];
    watchDatatableField(datatable, itemsJson, fieldToWatch, triggerValues, fields, 10000);
}
