/**
 * Wraps the jQuery dataTables functionality into a more manageable object.
 * <p>
 * This will prepare to create a dataTable for the given selector. The dataTable
 * is not actually created until the create() method is called, allowing further
 * customization of the dataTable before creation.
 * <p>
 * The supported column options are:
 * <dl>
 * <dt>property</dt><dd>name of the row property for this column</dd>
 * <dt>cssClass</dt><dd>name of the cssClass to apply to the column</dd>
 * <dt>sortable</dt><dd>whether the column is sortable</dd>
 * <dt>visible</dt><dd>whether the column is visible</dd>
 * <dt>sortDataColumn</dt><dd>the data index of the column to actually sort on</dd>
 * <dt>renderFunction</dt><dd>function to use to render the column data</dd>
 * <dt>useRendered</dt><dd>whether the rendered value should be used for sorting, or the original<dd>
 * </dl>
 * 
 * @param selector
 *        the selector containing the table to create a datatable from.
 * @param columns
 *        [optional] the columns for this datatable. May be configured later using <tt>setColumns</tt>.
 */
function DataTable(selector, columns) {
    var my = {};
    var beforeCreateHandlers = [];
    var afterCreateHandlers = [];
    var lastUpdatedField = null;
    var pollInterval = null;
    var maxRows = -1;
    var fnFindRowToEject = null;
    var dataTable = null;
    var created = false;
    var settings = {
            "sDom": "ft<\"dataTables_footer\"plri<\"dataTables_selectedItems\"><\"dataTables_processingError\">>",
            "sPaginationType": "bootstrapFirstLast",
            "oLanguage": {
              "sInfo":              Messages.get("datatable.info"),
              "sInfoEmpty":         Messages.get("datatable.info.empty"),
              "sInfoFiltered":      Messages.get("datatable.info.filtered"),
              "sInfoPostFix":       "",
              "sInfoThousands":     Messages.get("datatable.info.thousands"),
              "sLengthMenu":        Messages.get("datatable.length.menu"),
              "sLoadingRecords":    "&nbsp;",
              "sProcessing":        Messages.get("datatable.processing"),
              "sSearch":            "",
              "sSearchPlaceHolder": Messages.get("datatable.placeholder.search"),
              "sZeroRecords":       Messages.get("datatable.zeroRecords"),
              "sEmptyTable":        Messages.get("datatable.empty"),
              "oPaginate": {
                  "sFirst":     Messages.get("datatable.first"),
                  "sLast":      Messages.get("datatable.last"),
                  "sNext":      Messages.get("datatable.next"),
                  "sPrevious":  Messages.get("datatable.previous")
              },
              "oAria": {
                  "sSortAscending":     Messages.get("datatable.sortAscending"),
                  "sSortDescending":    Messages.get("datatable.sortDescending")
              }
            },
            "bProcessing": true,
            "bAutoWidth": false,
            "bLengthChange": false,
            "fnHeaderCallback": function(row) {
                $('th', row).each(function() {
                    if ($('.th-wrapper', this).size() == 0) {
                        $(this).wrapInner('<div class="th-wrapper"></div>');
                    }
                });
            },
            "fnRowCallback": function( nRow, aData, iDisplayIndex, iDisplayIndexFull ) {
                //only create a new scope the first time the row is drawn
                var angularScope = angular.element(nRow).scope() || this.scope().$new();
                portalApp.render(nRow, {row: aData}, angularScope);
            }
    };
    
    $(selector).data('dataTable', this);
    
    if (columns) {
        setColumns(columns);
    }
    
    function addOptions(options) {
        $.extend(true, settings, options);
    }

    function createColumn(column) {
        var aoColumn = {
                "sDefaultContent": "",
                "mDataProp": column.property,
                "sClass": column.cssClass,
                "bSortable": column.sortable == true,
                "bVisible": column.visible != false,
                "bSearchable": column.searchable != false
        };
        if (column.sortDataColumn != null && column.sortDataColumn != -1) {
            aoColumn["aDataSort"] = [column.sortDataColumn];
        }
        if (column.renderFunction) {
            aoColumn["fnRender"] = column.renderFunction;
            aoColumn["bUseRendered"] = column.useRendered;
        }
        return aoColumn;
    }
    
    function setColumns(columns) {
        var aoColumns = [];
        for (var i = 0; i < columns.length; i++) {
            var aoColumn = createColumn(columns[i]);
            aoColumns.push(aoColumn);
        }
        
        addOptions({ "aoColumns": aoColumns });
    }
        
    // Displays an error in the processing error area
    function showError(error) {
        if (error) {        
            // Create a closable alert
            var button = '<button type="button" class="close" data-dismiss="alert">&times;</button>';
            var escaped = $("<div/>").text(error);
            
            var processingError = $(selector + ' .dataTables_processingError');
            processingError.html("<div class='alert alert-danger'>" + button + escaped.text() + "</div>");
            processingError.show();
        }
    }
    
    function hideError() {
        var processingError = $(selector + ' .dataTables_processingError');
        processingError.text('');
        processingError.hide();
    }
    
    function getErrorMessage(xhr, thrown) {
        var errorMessage = thrown;
        try {
            // Try to interpret the response text as JSON,
            // play will return a 'description' property
            var responseObj = $.parseJSON(xhr.responseText);
            if (responseObj.description) {
                 errorMessage += ": "+responseObj.description;
            }
            if (responseObj.details) {
                errorMessage += ": "+responseObj.details;
            }
        }
        catch (e) {
            console.error("Unable to parse error response");
            console.error(xhr.responseText);
            // Stick with the generic error message
        }

        return errorMessage;
    }
    
    function abort() {
        var xhr = $(selector).data('previousXHR');
        if (xhr) {
            console.log('  - Aborting XHR');
            $(selector).removeData('previousXHR');
            xhr.abort();
        }
    }
    
    // Called when performing server-side queries
    function serverData(sUrl, aoData, fnCallback, oSettings) {
        abort(); 
        
        var xhr = $.ajax( {
            "url":  sUrl,
            "data": aoData,
            "dataType": "json",
            "cache": false,
            "type": oSettings.sServerMethod,
            "success": function (data) {
                $(oSettings.oInstance).trigger('xhr', oSettings);
                fnCallback(data);
                if (data.error) {
                    showError(data.error);
                }
            },
            "error": function (xhr, error, thrown) {
                // Hide the processing element
                dataTable.oApi._fnProcessingDisplay(oSettings, false);
                
                if (error == "parsererror") {
                    oSettings.oApi._fnLog(oSettings, 0, "DataTables warning: JSON data from "+
                        "server could not be parsed. This is caused by a JSON formatting error.");
                    showError(Messages.get("datatable.parse.error"));
                }
                else if (thrown == "OK") {
                    console.error("Ingoring OK response in error handler");
                }
                else if (error != "abort") {
                    // Handle other errors (like 500 server error)
                    showError(getErrorMessage(xhr, thrown));
                }
            }
        } );

        var $target = $(selector);
        xhr.always(function() {
            if (xhr == $target.data('previousXHR')) {
                console.log("  - Removing XHR");
                $target.removeData('previousXHR');
            }
        });
        $target.data('previousXHR', xhr);

        oSettings.jqXHR = xhr;
    }

    my.addOptions = addOptions;
    my.setColumns = setColumns;

    /**
     * Sets the URL of the server side source.
     * 
     * @param source
     *        the server source URL.
     * @param paramsCallback 
     *        [optional] the callback for sending additional parameters to the server, taking an array
     *        of objects as the only argument. 
     */
    my.setSource = function(source, paramsCallback) {
        settings["sAjaxSource"] = source;
        settings["fnServerData"] = serverData;
        if (paramsCallback) {
            settings["fnServerParams"] = paramsCallback;
        }
    }
    

    /**
     * Sets the default sort column and direction (<tt>asc</tt> | <tt>desc</tt>).
     * 
     * @param columnIndex
     *        the index of the column to sort.
     * @param sortDirection
     *        <tt>asc</tt> for ascending,
     *        <tt>desc</desc> for descending.
     */
    my.setDefaultSorting = function(columnIndex, sortDirection) {
        settings["aaSorting"] = [[columnIndex, sortDirection]];
    }
    
    /**
     * Sets the row callback for the data table.
     */
    my.setRowCallback = function(rowCallback) {
        settings["fnRowCallback"] = rowCallback;
    }
    
    /**
     * Sets the draw callback for the data table.
     */
    my.setDrawCallback = function(drawCallback) {
        settings["fnDrawCallback"] = drawCallback;
    }
    
    /**
     * Adds a handler that can be used to customize the datatable just before creation.
     */
    my.beforeCreate = function(handler) {
        beforeCreateHandlers.push(handler);
    }
    
    /**
     * Adds a handler that can be used to customize the datatable after creation.
     */
    my.afterCreate = function(handler) {
        afterCreateHandlers.push(handler);
    }

    /**
     * Used in conjunction with the polling to sets the maximum number of rows the table should display.  If row is added
     * which causes the number of rows to go over max, fnFindRowToEject is invoked with all the data in the table to provide
     * the index of the row which should be ejected from the table.
     *
     * @param max
     *  maximum number of rows the table should show
     * @param rowToEjectFunction
     *  A function that takes an array of Objects and returns an index of the row to eject
     *
     */
    my.setMaxRows = function(max, rowToEjectFunction) {
        maxRows = max;
        fnFindRowToEject = rowToEjectFunction;
    }
    
    /**
     * Creates the data table based on the current settings.
     */
    my.create = function() {

    	if (!created) {
	        for (var i = 0; i < beforeCreateHandlers.length; i++) {
	            beforeCreateHandlers[i](my);
	        }
	        
	        dataTable = $(selector + " table").dataTable(settings);
	        if (settings.sAjaxSource) {
	            dataTable.fnSetFilteringDelay(400);
	            // When processing starts, clear and hide the processing error
	            dataTable.on('processing', hideError);
	        }
	        // Restyle the search box to bootstrap and add a placeholder string
	        $(selector + ' .dataTables_filter label input').addClass("form-control input-sm search-query").attr("placeholder", settings.oLanguage.sSearchPlaceHolder);
	        
	        // Hook up any button enablements for this datatable
	        var setEnabled = function(button, enabled) {
	            $(button).prop('disabled', !enabled);
	        }
	        var updateButtons = function() {
	            var selected = my.getSelectedItems();
	            var updateButton = function() {
	                // Whether this button only support single selection
	                var singleSelection = $(this).data('selection') == 'single';
	                
	                if (!selected || (selected.length == 0) || (singleSelection && selected.length != 1)) {
	                    setEnabled(this, false);
	                }
	                else {
	                    var property = $(this).data('property');
	                    var value = $(this).data('value');
	                    var notValue = $(this).data('not-value');
	                    if (property && (value != null || notValue != null)) {
	                        var enabled = true;
	                        for (key in selected) {
	                            if (value != null && selected[key][property] != value) {
	                                enabled = false;
	                                break;
	                            }
	                            if (notValue != null && selected[key][property] == notValue) {
	                                enabled = false;
	                                break;
	                            }
	                        }
	                        setEnabled(this, enabled);
	                    }
	                    else {
	                        setEnabled(this, true);
	                    }
	                }
	            }
	            // Target buttons within the outer selector
	            $(selector + ' button[data-enabled="selected"]').each(updateButton);
	            var id = $(selector).attr('id');
	            if (id) {
	                // Target buttons that reference the table by ID
	                $('button[data-table="'+id+'"][data-enabled="selected"]').each(updateButton);
	            }
	        }
	        $(selector + ' table').on('change', 'input:checkbox', function(e,selectAll) {
	            //set the table's value to the joined selected ids, so $('#dataTable').val() does something useful
	            if(!selectAll){
	                $(selector).val(my.getSelectedValues().join(","));
	                updateButtons();
	            }
	        });
	        
	        // Updates the buttons if the table is redrawn by a search filter.
	        $(selector).on('draw.dt', function() { updateButtons(); });
	        
	        updateButtons();
	        
	        for (var i = 0; i < afterCreateHandlers.length; i++) {
	            afterCreateHandlers[i](my);
	        }

            created = true;
    	}

        return dataTable;
    }
    
    /**
     * Causes the data table to be reloaded.
     */
    my.refresh = function() {
        dataTable.fnReloadAjax();
    }
    
    /**
     * Aborts the last server side call.
     */
    my.abort = function() {
        abort();
    }
    
    /**
     * Resets the page back to the first, clears the search box and deselects any selected values.
     */
    my.reset = function() {
        dataTable.fnPageChange('first');
        dataTable.fnFilter("");
        my.deselectAll();
    }
    
    /**
     * Gets the row containing the given element.
     */
    my.getRow = function(elem) {
        var tr = $(elem).closest('tr');
        if (tr.size() == 0) {
            return null;
        }
        return tr[0];
    }
    
    /**
     * Gets the row data given an element within the row.
     */
    my.getRowData = function(elem) {
        var tr = my.getRow(elem);
        return tr != null ? dataTable.fnGetData(tr) : null;
    }
    
    /**
     * Gets the data table.
     */
    my.getDataTable = function() {
        return dataTable;
    }
    
    /**
     * Gets the selector used when creating the data table.
     */
    my.getSelector = function() {
        return selector;
    }

    /**
     * Adds expandable support to the data table.  The config object can provide custom properties and callbacks to
     * affect how/if the rows are expanded.
     * <dl>
     * <dt>trigger</dt><dd>The event trigger to begin expansion, defaults to 'click'</dd>
     * <dt>target</dt><dd>The target selector for the event trigger, defaults to 'tr'</dd>
     * <dt>expandedClass</dt><dd>The css class to apply to the cell of the expanded row, defaults to 'expanded'</dd>
     * <dt>expandRow</dt><dd>Callback function to expand a row: <tt>function(result, data, row)</tt>. When complete,
     * call <tt>result.resolve(content)</tt> or <tt>result.reject(content)</tt>, the distinction is which completion
     * callback will be called (rowOpened or rowFailed).  When no content is provided the row is not opened.</dd>
     * <dt>rowOpening</dt><dd>Called when the row is opening: <tt>function(data, row)</tt></dd>
     * <dt>rowClosing</dt><dd>Called when the row is closing: <tt>function(data, row, openedRow)</tt></dd>
     * <dt>rowOpened</dt><dd>Called after the row is opened: <tt>function(data, row, openedRow)</tt></dd>
     * <dt>rowClosed</dt><dd>Called after the row is closed: <tt>function(data, row)</tt></dd>
     * <dt>rowFailed</dt><dd>Called after the row has failed: <tt>function(data, row, openedRow)</tt></dd>
     * <dt>canExpand</dt><dd>Called before starting to open the row: <tt>function(data, row)</tt>. A non-empty result
     * will allow the row to be opened.</dd>
     * </dl>
     */
    my.setExpandable = function(config) {
        var expander = $.extend({
            'trigger': 'click',
            'target':  'tr',
            'expandedClass': 'expandedRow',
            'expandRow':  function(result, data, row) { result.resolve() },
            'rowOpening': function(data, row) {},
            'rowOpened':  function(data, row, openedRow) {},
            'rowClosing': function(data, row, openedRow) {},
            'rowClosed':  function(data, row) {},
            'rowFailed':  function(data, row, openedRow) {},
            'canExpand':  function(data, row) { return true; }
        }, config);
        
        var working = false;
        var table = $(selector + ' table');
        table.off(expander.trigger, expander.target);
        table.on(expander.trigger, expander.target, function(e) {
            if ($(e.target).is('a')) {
                return;
            }
            var row = my.getRow(this);
            if (row == null) { 
                return;
            }
            var data = my.getRowData(this);
            
            if (!expander.canExpand(data, row)) {
                return;
            }
            
            if (working) {
                return;
            }
            working = true;
            if (dataTable.fnIsOpen(row)) {
                expander.rowClosing(data, row);
                dataTable.fnClose(row);
                expander.rowClosed(data, row);
                working = false;
            }
            else {
                var result = $.Deferred();
                result.done(function(content) {
                    var newRow = null;
                    if (content != null) {
                        newRow = dataTable.fnOpen(row, content, expander.expandedClass);
                    }
                    expander.rowOpened(data, row, newRow);
                });
                result.fail(function(content) {
                    var newRow = null
                    if (content != null) {
                        newRow = dataTable.fnOpen(row, content, expander.expandedClass);
                    }
                    expander.rowFailed(data, row, newRow);
                });
                result.always(function() {
                    working = false;
                });

                expander.rowOpening(data, row);
                // Call the expand function
                expander.expandRow(result, data, row);
            }
        });
    }
    
    my.setDefaultExpandable = function(config) {
        function expand(data, row, openedRow) {
            // Apply the same css class to the opened row
            var trClass = $(row).attr('class');
            $(openedRow).addClass($(row).attr('class')).addClass('openedRow');
            $(row).addClass('openRow');
            $('td.expandable', row).attr('class', 'expandable expanded');
        }
        function collapse(data, row) {
            $(row).removeClass('openRow');
            $('td.expandable', row).attr('class', 'expandable');
        }
        function preventExpand(data, row) {
            collapse(data, row);
            $('td.expandable', row).addClass('noexpand');
        }
        var expander = $.extend({
            'target': 'tr',
            'rowOpening': function(data, row) {
                $('td.expandable', row).attr('class', 'expandable loading');
            },
            'rowOpened':  function(data, row, openedRow) {
                if (openedRow) {
                    expand(data, row, openedRow);
                }
                else {
                    preventExpand(data, row);
                }
            },
            'rowFailed':  function(data, row, openedRow) {
                if (openedRow) {
                    expand(data, row, openedRow);
                }
                else {
                    preventExpand(data, row);
                }
            },
            'rowClosed': function(data, row) {
                collapse(data, row);
            },
            'canExpand': function(data, row) {
                var expandable = $('td.expandable', row);
                return (expandable.size() > 0) && !expandable.hasClass('noexpand');
            }
            
        }, config);
        my.setExpandable(expander);
    }

    my.setSimpleExpandable = function(template, property) {
        var rowCallback = function(row, data) {
            // If a property has been provided, trigger the expandable capability on whether the row has the property
            if (property && !data[property]) {
                $('td.expandable', row).addClass('noexpand');
            }
            else {
                $('td.expandable', row).removeClass('noexpand');
            }
        }
        addOptions({'fnRowCallback': rowCallback});
        
        var expandRow = function(result, data, row) {
            if (templates[template]) {
                result.resolve(templates[template].apply(data));
            }
            else {
                result.reject(Messages.get("datatable.unknown.template", template));
            }
        }
        my.setDefaultExpandable({'expandRow': expandRow});
    }

    my.getSelectedItems = function(name) {
        var items = [];
        var checked = name ? 'input[name="'+name+'"]:checked' : 'input:checked';
        dataTable.$(checked, {"filter": "applied"}).each(function() {
            var tr = $(this).closest('tr')[0];
            items.push(dataTable.fnGetData(tr));
        });
        return items;
    }
    
    my.getSelectedValues = function(name) {
        var values = [];
        var checked = name ? 'input[name="'+name+'"]:checked' : 'input:checked';
        dataTable.$(checked, {"filter": "applied"}).each(function() {
            values.push($(this).val());
        });
        return values;
    }
    
    my.addSelectAllHandler = function(name) {
        var selectAllName = name ? 'selectAll_'+name : 'selectAll';
        $(selector + ' thead input:checkbox[name="'+selectAllName+'"]').on('change', function() {
            if ($(this).is(':checked')) {
                my.selectAll(name);
            }
            else {
                my.deselectAll(name);
            }
        });
    }
    
    my.getFilteredRows = function() {
        var rows = [];
        $(dataTable.fnSettings().aiDisplay).each(function(index, item) {
            var row = dataTable.fnGetNodes(item);
            if (row) {
                rows.push(row);
            }
        });
        return rows;
    }
    
    my.selectAll = function(name) {
        var checkboxes = name ? 'input[name="'+name+'"]:checkbox' : 'input:checkbox';
        $(checkboxes+':not(:disabled)', my.getFilteredRows()).prop('checked', true).trigger('change',true);
        var selectAllName = name ? 'selectAll_'+name : 'selectAll';
        $(selector + ' thead input:checkbox[name="'+selectAllName+'"]').prop('checked', true);
        my.updateSelectedFooter();
    }
    
    my.deselectAll = function(name) {
        var checkboxes = name ? 'input[name="'+name+'"]:checkbox' : 'input:checkbox';
        $(checkboxes, my.getFilteredRows()).prop('checked', false).trigger('change',true);
        var selectAllName = name ? 'selectAll_'+name : 'selectAll';
        $(selector + ' thead input:checkbox[name="'+selectAllName+'"]').prop('checked', false);
        my.updateSelectedFooter();
    }
    
    my.setLastUpdatedField = function(field) {
      lastUpdatedField = field
      if (lastUpdatedField) {
        my.startPolling();
      }
    }
    
    my.startPolling = function() {
      pollInterval = window.setInterval(function() {my.poll()}, 5000);
    }
    
    my.stopPolling = function() {
      clearInterval(pollInterval);
    }
    
    my.poll = function () {
      
      var serverParams = []
      if (settings["fnServerParams"]) {
        settings["fnServerParams"](serverParams);
      }

      var lastUpdated = getLastUpdated();
      console.log("Polling for Tasks " +  lastUpdated);
      serverParams.push({ "name": "lastUpdated", "value": lastUpdated });
      
      $.ajax( {
        "url":  settings["sAjaxSource"],
        "data": serverParams,
        "cache": false,
        "success": function (data) {
          var existingRecords = 0;
          var newRecords = 0;
            for (var i = 0; i < data.aaData.length; i++) {
              var dataItem = data.aaData[i];
              var id = dataItem["id"];
              var pos = getPositionForColumn("id", id);
              if (pos != null && pos > -1) {
                existingRecords++;
                dataTable.fnUpdate(dataItem, pos, undefined, false, false);
              }
              else {
                newRecords++;
                dataTable.fnAddData(dataItem, false);
              }
            }

            if (maxRows != -1 && fnFindRowToEject != null) {
                var numRecordsToEject = dataTable.fnGetData().length - maxRows;

                for (var i = 0; i < numRecordsToEject; i++) {
                    var data = dataTable.fnGetData();

                    var indexToDelete = fnFindRowToEject(data);
                    if (indexToDelete >= 0) {
                        dataTable.fnDeleteRow(indexToDelete);
                    }
                }
            }

            if (existingRecords > 0 || newRecords > 0) {
              dataTable.fnStandingRedraw();
            }
            
            if (data.error) {
                showError(data.error);
            }
        },
        "error": function (xhr, error, thrown) {
            // Hide the processing element
            dataTable.oApi._fnProcessingDisplay(oSettings, false);
            
            if (error == "parsererror") {
                oSettings.oApi._fnLog(oSettings, 0, "DataTables warning: JSON data from "+
                    "server could not be parsed. This is caused by a JSON formatting error.");
                showError("JSON data from server could not be parsed");
            }
            else if (error != "abort") {
                // Handle other errors (like 500 server error)
                showError(getErrorMessage(xhr, thrown));
            }
        }
      });      
    }
    
    function getLastUpdated() {
      var lastUpdated = null;
      if (lastUpdatedField) {
        var data = dataTable.fnGetData();
        $.each( data, function(i, row){
          rowLastUpdated = data[i][lastUpdatedField]
          if (lastUpdated == null) {
            lastUpdated = rowLastUpdated 
          }
          else if (lastUpdated < rowLastUpdated) {
            lastUpdated = rowLastUpdated;
          }
        }) 
      }
      return lastUpdated;
    }    
    
    function getPositionForColumn(columnName, value) {
      var pos = -1;
      var data = dataTable.fnGetData();
      $.each( data, function(i, row){
        if (value == row[columnName]) {
          pos = i;
          return false;
        }
      });
      return pos;
    }

    my.updateSelectedFooter = function() {
        var numItems = my.getSelectedItems().length;
        $('.dataTables_selectedItems').text(Messages.get('dataTable.selectedItems', numItems));
    }

    my.needResourceLimitAlert = function (limit) {
        var numOfResources = dataTable.fnGetData().length;
        if (numOfResources * 100 / limit >= 90){
            return true;
        }
        return false;
    }
	
    return my;
}
