// dummy console for browsers that do not have one
if (!window.console) { window.console = { log: function(){} }; } 

// Creates a holder for all templates
var templates = templates || {};

// Create a holder for all messages
var messages = messages || {};

// Utility functions
var util = util || {};

// Setting global moment locale with play locale
moment.locale(angular.injector(['ng', 'config']).get('locale'));

/**
 * Trims the inputs on a form before submission.
 */
util.trimForm = function(form) {
    $(form).find('input[type="text"]').each(function() {
        var value = $.trim($(this).val());
        $(this).val(value);
    });
}

/**
 * Replaces the button icon with a loading icon and disables all buttons in the same button bar. This should
 * only be called as the last step before a form submit or location change occurs.
 */
util.onButtonSubmitted = function(button) {
    var icon = $('span.glyphicon', button);
    if (icon.size() > 0) {
        var loading = templates.loadingIcon.apply();
        icon.replaceWith(loading);
    }
    // Disable all other buttons in the same button bar
    $(button).closest('.button-bar').find('button').prop('disabled', true);
    $(button).closest('.modal-footer').find('button').prop('disabled', true);
}

/**
 * Finds the button that targeted this form and changes its icon to a loading icon and disables all other buttons
 * in the same button bar.
 */
util.onFormSubmitted = function(form) {
    var id = $(form).attr('id');
    util.trimForm(form);
    util.onButtonSubmitted($('button[data-form="'+id+'"]'));
}

/**
 * Marks the button as submitted and submits the form.
 */
util.submitForm = function(button, form) {
    if (form.attr("confirm") && !confirm(form.attr("confirm"))) {
        return false;
    }
    util.trimForm(form);
    util.onButtonSubmitted(button);
    util.onButtonSubmitted($('button[type="submit"]', form));
    $(form).submit();
}

/**
 * Utility method for getting a nicer error message for an ajax error.
 */
util.getAjaxError = function(xhr, textStatus, errorThrown) {
    var errorMessage = errorThrown;
    try {
        // Try to interpret the response text as JSON,
        // play will return a 'description' property
        var responseObj = $.parseJSON(xhr.responseText);
        if (responseObj.description) {
            errorMessage += ": " + responseObj.description;
        }
    }
    catch (e) {
        // Stick with the generic error message
      if (xhr.responseText) {
        errorMessage += ": " + xhr.responseText;
      }
    }
    return errorMessage;
}

/**
 * Utility method for getting the value of a control.
 */
util.getControlValue = function(control) {
    var $control = $(control);
    if ($control.is(':checkbox') && !$control.is(':checked')) {
        var hiddenFalse = $('#'+$control.attr('id')+'False');
        return hiddenFalse.val();
    }
    else {
        return $control.val();
    }
}

/**
 * Utility method for setting the value of a control.
 */
util.setControlValue = function(control, value) {
    var $control = $(control);
    if ($control.is('select')) {
        $('option', $control).each(function() {
            var optionValue = $(this).val();
            if (value == optionValue) {
                $(this).attr('selected', 'selected');
            }
            else {
                $(this).removeAttr('selected');
            }
        });
        $control.trigger('chosen:updated');
        $control.trigger('change');
    }
    else {
        $control.val(value);
        if ($control.is('input[type="hidden"]')) {
            $control.change();
        }
    }
}

/**
 * Utility method for setting the control visibility.
 */
util.setVisible = function(control, visible) {
    var $control = $(control);
    if (visible) {
        $control.show();
    }
    else {
        $control.hide();
    }
    if ($control.is('select')) {
        $control.trigger('chosen:updated')
    }
}

/**
 * Utility method for setting a control's enabled state.
 */
util.setEnabled = function(control, enabled) {
    var $control = $(control);
    if (enabled) {
        $control.prop('disabled', false);
    }
    else {
        $control.prop('disabled', true);
    }
    if ($control.is('select')) {
        $control.trigger('chosen:updated')
    }
}

util.loadOptionsHandler = function(selector) {
    return function(data, textStatus, jqXHR) {
        var $select = $(selector);
        if ($select.is('select')) {
            // Preserve the empty option text if there is one
            var emptyOption = $select.children('option[value=""]');
            var emptyText = null;
            if (emptyOption.size() > 0) {
                emptyText = emptyOption.text();
            }
            var selectedValue = $select.val();
            $select.empty();
            if (emptyText != null) {
                $select.append("<option value=''>"+emptyText+"</option>");
            }
            
            // Add the new options
            for (var i = 0; i < data.length; i++) {
                var option = '<option value="'+data[i].id+'"';
                if (data[i].id == selectedValue) {
                    option += "selected";
                }
                option += ">"+data[i].name+"</option>";
                $select.append(option);
            }
            $select.trigger('chosen:updated');
            $select.trigger('options');
        }
        else if (select.is('.selectManyContent')) {
            // TODO maybe
        }
    };
}

/**
 * Loads options for a given control by submitting the form to the provided URL.
 * The response is expected to be a collection of objects with id and name properties.
 */
util.loadOptions = function(url, id, form) {
    var data = $(form ? form : 'form').serialize();
    var handler = util.loadOptionsHandler(id);
    $.post(url, data, handler, 'json');
}

/**
 * Creates a delayed handler, every time a trigger happens the timer is reset.
 */
util.delayedHandler = function(handler, delay) {
    var timeout = null;
    var trigger = function() {
        timeout = null;
        handler();
    }
    return function() {
        if (timeout) {
            window.clearTimeout(timeout);
        }
        timeout = window.setTimeout(trigger, delay);
    };
}

/**
 * Creates a throttled handler, it will trigger no more often than the delay.
 */
util.throttledHandler = function(handler, delay) {
    var timeout = null;
    var retrigger = false;
    var trigger = function() {
        timeout = null;
        handler();
        
        if (retrigger) {
            timeout = window.setTimeout(trigger, delay);
            retrigger = false;
        }
    }
    return function() {
        if (timeout) {
            retrigger = true;
        }
        else {
            timeout= window.setTimeout(trigger, delay);
        }
    };
}

/**
 * Enables collapsible support on any .collapse elements starting at the selector.
 */
util.collapsible = function(selector) {
    var $collapse = $('.collapse', $(selector));
    $collapse.on('hide.bs.collapse', function() {
        var $panel = $(this).parentsUntil('.panel-group', '.panel');
        $('.panel-heading', $panel).removeClass('in');
    });
    $collapse.on('show.bs.collapse', function() {
        var $panel = $(this).parentsUntil('.panel-group', '.panel');
        $('.panel-heading', $panel).addClass('in');
    });
    // Open any collapse element that has an error
    $collapse.each(function() {
        if ($('.has-error', this).size() > 0) {
            $(this).addClass('in');
        }
    });
}

/**
 * Clear form validation errors
 * @param [dialog] an optional dialog
 */
util.clearValidationErrors = function(dialog) {
    $('.form-group', dialog).each(function() {
        $(this).removeClass('has-error');
        $('.help-inline', this).text('');
    });
}

util.parseBytes = function(bytes, sig) {
    var unit = 1024;
    if (bytes < unit) {
        return bytes+"B";
    }
    var exp = Math.floor(Math.log(bytes)/Math.log(unit));
    var pre = "KMGTPE".charAt(exp-1);
    sig = sig ? sig : 0;
    return (bytes/Math.pow(unit, exp)).toFixed(sig)+pre+"B";
}

function commonInitialize() {
  $('.initialFocus').focus();
  
  initDynamicHelp();
  
  updateLocalDateTime();
  $(document).ajaxSuccess(updateLocalDateTime);
  $(document).ajaxError(function(e, xhr) {
      //reload the page on a 401 (which should bump us to the login)
      if (xhr.status == 401) {
        window.location.reload(true);
      }
  });
  
  // Updated relative times on the page every minute
  updateTimes();
  window.setInterval(updateTimes, 60000);
  
  // Extract all templates from the document
  $('.jsTemplate[id]').each(function() {
      var id = $(this).attr('id');
      var content = $(this).html();
      templates[id] = templates[id] || {}
      templates[id].apply = function(data) {
          return applyStringTemplate(content, data);
      }
  });
  $('.jsTemplate[id]').remove();
  
  // Extract all messages from the document
  $('.jsMessage[data-key]').each(function() {
      var key = $(this).data('key');
      var content = $(this).text();
      
      messages[key] = messages[key] || {}
      messages[key].message = content;
      messages[key].toString = function() {
          return content;
      }
      messages[key].apply = function(data) {
          return applyStringTemplate(content, data);
      }
  });
  $('.jsMessage[data-key]').remove();
  
  // File upload
  $(document).on('change', '.btn-file :file', function() {
      var input = $(this);
      var numFiles = input.get(0).files ? input.get(0).files.length : 1;
      var label = input.val().replace(/\\/g, '/').replace(/.*\//, '');
      input.trigger('fileselect', [numFiles, label]);
  });
  $(document).on('fileselect', '.btn-file :file', function(event, numFiles, label) {
      var inputGroup = $(this).parent('.btn-file').parent('.input-group-btn').parent('.input-group');
      var text = $('input[type="text"]', inputGroup);
      text.val(label);
  });
  
  // Form submit
  $(document).on('form', 'submit', function() {
      util.onButtonSubmitted($('button[type="submit"]', this));
  });
}

var processMultiFileUploadControls = function(element) {
    //Loop through all files in the control, remove those that are empty,
    //set the name attribute and insert an empty control at the end of the list

    var container = $(element).closest(".multi-file-upload-container"),
        controls  = container.find(".multi-file-upload"),
        clone     = controls.last().clone();

    clone.find("input").val(null);
    controls.find(".remove").removeClass("disabled");

    //remove empty controls. For those we keep, add name property so the file is uploaded
    controls.each(function() {
        var $file = $(this).find(":file");
        $file.val() ? $file.prop("name", $file.data("name")) : $(this).remove();
    });
    container.append(clone);
    //only show the first label and the last help-block
    container.find(".control-label").css('visibility', 'hidden')
             .filter(":first").css('visibility', 'visible');
    container.find(".help-block").hide().filter(":last").show();
    container.change();
};

$(document).on('fileselect', '.multi-file-upload .btn-file :file', function() {
    processMultiFileUploadControls(this);

    //Optional ajax validation. Its impossible to preserve the contents of a file
    //control through a page refresh, so ajax validation makes for a better user experience
    //Doesn't work in older browsers. Do some basic feature detection.
    if ($(this).data("validator") && FormData && this.files && this.files.length) {
        var $this   = $(this),
            group   = $this.closest(".form-group"),
            loading = group.find(".loading").removeClass('hide'),
            data    = new FormData();

        group.trigger("validationBegin");
        data.append("file", this.files[0]);
        $.ajax({url: $this.data("validator"), type: 'POST', data: data, processData: false, contentType: false,
            error: function(error) {
                group.addClass("has-error").find(".help-inline").text(error.responseText);
            },
            complete: function() {
                loading.addClass("hide");
                group.trigger("validationComplete");
            }
        });
    }
});

$(document).on("click", ".multi-file-upload .remove:not(.disabled)", function() {
    //calling .val(null) won't reset a a file input in IE. Instead, wrap it in a form, then reset it.
    var input = $(this).closest(".multi-file-upload").find(":file");
    input.wrap("<form>").closest('form').get(0).reset();
    input.unwrap();
    processMultiFileUploadControls(this);
});

//on submit handlers
$(document).on("submit", "form", function(e) {
    //gather selected datatable elements to this element
    $(this).find("input[data-gather-selected-ids-for]").each(function() {
        var ids = table[$(this).data("gather-selected-ids-for")].dataTable.getSelectedValues();
        $(this).val(ids.join(","));
    });
});

// Dynamically replace millis with local date time
function updateLocalDateTime(context) {
    context = context || document;
    $('.localDateTime:hidden', context).each(function() {
        var node = $(this);
        var format = $(node).data('format');
        if (format == null) {
            format = 'MMM Do YYYY, h:mm:ss A';
        }
        var value = $(node).text();
        if ($.isNumeric(value)) {
            var millis = Number(value);
            var dateTimeString = formatDate(millis, format);
            node.text(dateTimeString);
        }

        node.show();
    });
}

/**
 * Updates all elements with data-relative-time attribute (which must be milliseconds) and reformats the content as a 
 * relative time.
 */
function updateRelativeTime(context) {
    context = context || document;
    $('[data-relative-time]', context).each(function () {
        var value = $(this).data("relative-time");
        if ($.isNumeric(value)) {
            var millis = Number(value);
            $(this).text(formatRelativeDate(millis));
        }
    });
}

function updateElapsedTime(context) {
  context = context || document;
  $('[data-elapsed-time]', context).each(function () {
      var value = $(this).data("elapsed-time");
      if (typeof value === 'string' || value instanceof String) {
        var values = value.split(":");
        if (values) {
          var start = values[0];
          var end = null;
          if (values.length > 1) {
            end = values[1];
          }
          $(this).text(formatElapsedTime(start, end));
        }
      }
  });
}

function updateTimes(context) {
  updateRelativeTime(context);
  updateElapsedTime(context);
}

function initDynamicHelp() {
    $('[data-help-trigger]').each(function() {
        var container = $(this);
        var trigger = $(this).data('help-trigger');
        $(trigger).on('change', function() {
            updateDynamicHelp(container);
        });
        updateDynamicHelp(container);
    });
    
}

function updateDynamicHelp(container) {
    var trigger = container.data('help-trigger');
    var target = container.data('help-target');
    if (!target) {
        target = '.help-block';
    }
    var defaultHelpText = container.data('help-text');
    
    if (trigger && target) {
        var currentValue = $(trigger).val();
        if (currentValue) {
            var helpText = container.data('help-text-' + currentValue.toLowerCase());
            $(target, container).text(helpText ? helpText : defaultHelpText);
        }
        else {
            $(target,  container).text(defaultHelpText);
        }
    }
}

/**
 * Gets the URL for a catalog image, either a static image or a custom catalog image.
 * 
 * @param image the image value
 * @returns the URL.
 */
function catalogImageURL(image) {
    if (image && image.indexOf('urn:storageos') == 0) {
        return routes.CatalogImages_view({'image': image});
    }
    return '/public/img/serviceCatalog/'+image;
}

/**
 * Gets the client TimeZone offset.
 * 
 * @return the client TimeZone offset
 */
function getTimeZoneOffset() {
    return new Date().getTimezoneOffset();
}

/**
 * Formats a full date time string (including timezone offset).
 * 
 * @param date the date to format.
 * @return the formatted date time.
 */
function formatDateTime(date) {
    return formatDate(date, 'MMM Do YYYY, h:mm:ss a Z');
}

/**
 * Formats a local date time string.
 * 
 * @param date the date to format.
 * @returns the formatted date time.
 */
function formatLocalDateTime(date) {
    return formatDate(date, 'MMM Do YYYY, h:mm:ss A');
}

/**
 * Formats a date using the given format string.
 * 
 * @param date the date.
 * @param formatString the format string.
 * @return the formatted date time.
 */
function formatDate(date, formatString) {
    if (!isNaN(date)) {
        return new moment(date).format(formatString);
    }
    else {
        return date;
    }
}

/**
 * Formats a date relative to now.
 * 
 * @param date the date to format.
 * @return the relative formatted date.
 */
function formatRelativeDate(date) {
    return new moment(date).fromNow();
}

function formatElapsedTime(start, end) {
  var elapsed = calculateElapsed(start, end);
  if (elapsed) {
    return moment.duration(elapsed).humanize();
  }
  return "";  
}

function calculateElapsed(start, end) {
  var elapsed;
  if (start) {
    if (end) {
      elapsed = moment(Math.round(end)).diff(moment(Math.round(start)));
    }
    else {
      elapsed = moment().diff(moment(Math.round(start)));
    }
  }    
  return elapsed;
}

/**
 * Defaults a value if null.
 * 
 * @param value the value.
 * @param defaultValue the default value.
 * @return value if non-null, the default otherwise.
 */
function defaultValue(value, defaultValue) {
    return value ? value : defaultValue;
}

/**
 * Checks if a string value is blank.
 * 
 * @param str the string.
 * @returns true if the value is blank.
 */
function isBlank(str) {
    return !str || $.trim(str).length == 0;
}

/**
 * Checks if a string value is not blank.
 * 
 * @param str the string.
 * @returns true if the value is not blank.
 */
function isNotBlank(str) {
    return !isBlank(str);
}

/**
 * Creates a row click handler that goes to the URL in the rowLink field.
 * 
 * @param row the row.
 * @param data the row data.
 */
function createRowLink(row, data) {
    if (data.rowLink) {
        var clickHandler = function() {
            window.location.href = data.rowLink;
        };
        $('td', row).each(function() {
            var td = $(this);
            var hasInput = td.find('input').size() > 0;
            var hasButton = td.find('button').size() > 0;
            
            // Only add click handler to cells that don't contain input fields or buttons
            if (!hasInput && !hasButton) {
                td.on('click', clickHandler);
            }
        });
    }
}

/**
 * Applies an object to a template node.  The inner html of the node is used as template.
 * 
 * @param selector the jQuery selector.
 * @param data the object to template.
 */
function applyTemplate(selector, data) {
    return applyStringTemplate($(selector).html(), data);
}

/**
 * Applies an object to a template string.  The template can contain property references of the form 
 * <tt>{<i>property</i>}</tt> which will be replaced by the value of <tt>data.property</tt>.
 * 
 * @param template the template string.
 * @param data the object containing the replacements.
 * @returns the applied template.
 */
function applyStringTemplate(template, data) {
    function getProperty(key) {
        var parts = key.split(".");
        var value = data;
        for (var i = 0; i < parts.length; i++) {
            if (!value) {
                break;
            }
            value = value[parts[i]];
        }
        return value ? value : ""
    }
    return template.replace(/\{([\w\.]*)\}/g, function(match, key) { return getProperty(key); });
}

/**
 * Function for initializing a static master-detail.  Clicking on the header expands the content.
 * 
 * @param selector the selector for the master detail.
 */
function initStaticMasterDetail(selector) {
    var header = $(selector + ' .header');
    var content = $(selector + ' .content');
    var indicator = $(selector + ' .indicator');
    var working = false;
    
    header.on('click', function() {
        if (working) {
            return;
        }
        working = true;
        if (content.css('display') == 'none') {
            header.addClass('expanded');
            content.slideDown('fast', function() {
                working = false;
                content.css('display', 'block');
            });
        }
        else {
            header.removeClass('expanded');
            content.slideUp('fast', function() {
                working = false;
                content.css('display', 'none');
            });
        }
    });
}

function ExpandPanel(selector, url) {
  var self = this;
  
  this.selector = selector;
  this.url = url;
  
  var header = $(selector + ' .header');
  var content = $(selector + ' .content');
  var working = false;

  this.init = function() {
    self.collapse();
    
    header.on('click', function() {
      if (working) {
          return;
      }
      working = true;
      if (content.css('display') == 'none') {
        self.load();
      }
      else {
        self.collapse();
      }
    });      
    
  }

  this.collapse = function() {
    $('.collapsed', header).show();
    $('.expanded', header).hide();
    $('.loading', header).hide();    
    content.slideUp('fast', function() {
      working = false;
      content.css('display', 'none');
    });    
  };
  
  this.expand = function() {
    $('.collapsed', header).hide();
    $('.expanded', header).show();
    $('.loading', header).hide();        
    content.slideDown('fast', function() {
      working = false;
      content.css('display', 'block');
    });       
  }
  
  this.load = function() {
    $('.collapsed', header).hide();
    $('.expanded', header).hide();
    $('.loading', header).show();
    
    $.get(url, function(data, textStatus, xhr) {
      content.html(data);
      self.expand();
    }).fail(function(xhr, textStatus, errorThrown) {
      var errorMessage = util.getAjaxError(xhr, textStatus, errorThrown);
      content.html('<span class="errorMessage">' + errorMessage + "</span>");
      self.expand();
    });        
  }

}

function initDynamicMasterDetail(selector, url) {
  var header = $(selector + ' .header');
  var content = $(selector + ' .content');
  var working = false;
  
  $('.collapsed', header).show();
  $('.expanded', header).hide();
  $('.loading', header).hide();
  
  header.on('click', function() {
      if (working) {
          return;
      }
      working = true;
      if (content.css('display') == 'none') {
        $('.collapsed', header).hide();
        $('.expanded', header).hide();
        $('.loading', header).show();

        $.get(url, function(data, textStatus, xhr) {
          content.html(data);
          
          $('.collapsed', header).hide();
          $('.expanded', header).show();
          $('.loading', header).hide();        
          
          content.slideDown('fast', function() {
            working = false;
            content.css('display', 'block');
          });             
          
        }).fail(function(xhr, textStatus, errorThrown) {
          var errorMessage = util.getAjaxError(xhr, textStatus, errorThrown);
          content.html(errorMessage);
          
          $('.collapsed', header).hide();
          $('.expanded', header).show();
          $('.loading', header).hide();        
          
          content.slideDown('fast', function() {
            working = false;
            content.css('display', 'block');
          });             
          
        });        

      }
      else {
        $('.collapsed', header).show();
        $('.expanded', header).hide();
        $('.loading', header).hide();        
          content.slideUp('fast', function() {
              working = false;
              content.css('display', 'none');
          });
      }
  });  
}

function DateTimePicker(selector) {
  
  this.container = selector + "Container";
  this.dateContainer = selector + "DateContainer";
  this.timeContainer = selector + "TimeContainer";
  
  var self = this;
  this.currentTime = false;
  
  this.setTime = function(momentDate) {
    if (momentDate) {
      
      $(this.dateContainer).data('date', momentDate.format('YYYY-MM-DD'));
      var year = momentDate.year();
      var month = momentDate.month();
      var day = momentDate.date();
      var $dateContainer = $(this.dateContainer);
      var $datePicker = $dateContainer.data('bfhdatepicker');
      if ($datePicker) {
        $dateContainer.data('month', month);
        $dateContainer.data('year', year);
        $dateContainer.find('input[type=text]').val($datePicker.formatDate(month, year, day)).trigger('change')
      }

      $(this.timeContainer).data('time', momentDate.format('HH:mm'));    
      $(this.timeContainer).data('hour', momentDate.format('HH'));
      $(this.timeContainer).data('minute', momentDate.format('mm'));  
      this.updatePopover();
      
    }
  };
  
  this.updatePopover = function() {
    var $timePicker = $(this.timeContainer).data('bfhtimepicker');
    if ($timePicker) {
      $timePicker.updatePopover();
    }
  }

  this.formatDate = function(maintainCursor) {
      var dateField = $(selector + "Date");
      var d = $.trim(dateField.val());
      var cursor = dateField[0].selectionStart;
      
      var stripped = d.replace(/-/g, "");
      
      if (stripped.length > 4) {
        var year, month, day;
      
        year = stripped.substring(0,4);
          
        if ( stripped.length > 6) {
          month = stripped.substring(4,6);
          day = stripped.substring(6);
        }
        
        if (stripped.length < 7) {
          month = stripped.substring(4,5);
          day = stripped.substring(5,6);
        }
          
        d = year;
        if (month) d = d + "-" + month;
        if (day) d = d + "-" + day;
      }
      
      // truncate (yyyy-mm-dd)
      if ( d.length > 10)  {
          d = d.substring(0, 10);
      }
      
      dateField.val(d);
      if (maintainCursor && dateField.is(":focus")) {
        dateField[0].selectionStart = cursor;
        dateField[0].selectionEnd = cursor;
      }
  }
  
  this.formatTime = function(maintainCursor) {
    var timeField = $(selector + "Time");
    if (timeField!= null && timeField != undefined && timeField[0] != undefined) { 
	    var t = timeField.val();
	    var cursor = timeField[0].selectionStart;
	   
	    // enforce the separator
	    if (t.length > 2) {
	      var sep = t.charAt(2);
	      var hours = t.substring(0,2);
	      var minutes = t.substring(3);
	      if (sep != ":") {
	        minutes = t.substring(2);    
	      }
	      t = hours + ":" + minutes;
	    }
	      
	    //truncate to 5 chars 
	    if (t.length > 5) {
	      t = t.substring(0, 5);
	    }
	      
	    timeField.val(t);
	    if (maintainCursor && timeField.is(":focus")) {
	      timeField[0].selectionStart = cursor;
	      timeField[0].selectionEnd = cursor;
	    }
    }
  }
  
  this.onKeyUp = function(e) {
    var KEEP_KEYS = new Array(37,39);
    var maintainCursor = $.inArray(e.keyCode, KEEP_KEYS) > -1;
    this.formatDate(maintainCursor);
    this.formatTime(maintainCursor);
    if (this.validate() == true) {
      this.update();
    }
  }
  
  this.update = function () {
    var $element = $(selector + "TimeContainer");
    var timeInput = $element.find('.bfh-timepicker-toggle > input[type=text]').val();
    var timeParts = new String(timeInput).split(":");
    var hour = timeParts[0];
    var minute = timeParts[1];    
    $element.data('hour', hour);
    $element.data('minute', minute);    

    hour = new String(hour);
    if (hour.length == 1) {
      hour = "0" + hour;
    }
    
    minute = new String(minute);
    if (minute.length == 1) {
      minute = "0" + minute;
    }

    $element.find('.hour > input[type=text]').val(hour);
    $element.find('.minute > input[type=text]').val(minute);    
    
  }
   
  this.validate = function() {
    var result = true;
    
    if (this.currentTime != true) {
      var $date = $(selector + "Date");
      var thedate = moment($date.val(), "YYYY-MM-DD");
      if (!thedate || !thedate.isValid()) {
        result = false;
        $(selector + "DateContainer").addClass("error");
      }
      else {
        $(selector + "DateContainer").removeClass("error");
      }
      
      var $time = $(selector + "Time");
      var thetime = moment($time.val(), "HH:mm");
      if (!thetime || !thetime.isValid()) {
        result = false;
        $(selector + "TimeContainer").addClass("error");
      }    
      else {
        $(selector + "TimeContainer").removeClass("error");
      }
    }
    
    return result;
  }
  
  this.getTime = function() {
    if (this.currentTime) {
      return moment();
    }
    else {
      var $date = $(selector + "Date");
      var $time = $(selector + "Time");
  
      if ($date) {
        date = $date.val();
        if ($time) {
          time = $time.val();
          return moment(date + " " + time, "YYYY-MM-DD HH:mm");;
        }       
      }
    }
    return null;    
  };
  
  this.useCurrentTime = function(value) {
    
    this.currentTime = value ? true : value === 'true';
    
    if (this.currentTime == true) {
      $(selector + "Container").hide();
      $(selector + "CurrentTime").show();
      
      $(selector + "Container").trigger("useCurrentTime");
    }
    else {
      $(selector + "Container").show();
      $(selector + "CurrentTime").hide();
      
      $(selector + "Container").trigger("useSpecificTime");
    }
    
  };

}

function SelectMany(selector) {
  this.selector = selector;

  var self = this;
  // Updates the values from the controls
  this.clear = function() {
      $(selector).html("");
  };
  
  this.filter = function(name) {
    $(selector + ' input:checkbox').each(function() { 
      var val = $(this).val();
      var text = $.trim($(this).parent().text());
      var reg = new RegExp(name, "i");
//      if(reg.test(val) || reg.test(text)) {
      if(reg.test(text)) {
        $(this).parent().show();
        $(this).prop("disabled", false);
      }
      else {
        $(this).prop("disabled", true);
        $(this).parent().hide();
      }
    });
    $(selector).trigger('filtered');
  }
  
  this.addOption = function(name, value, checked, label) {
      var o = '<div class="checkbox"><label>';
      o += '<input name="' + name + '" type="checkbox" '; 
      if (checked) {
        o += 'checked="checked"';
      }
      o += 'value="' + value + '"/>' + label + "</label></div>";
      $(selector).append(o);
  };

  this.checkAll = function() {
    $(selector + ' input:checkbox:not(:checked)').prop("checked", true);
    $(selector).change();
  }
  this.uncheckAll = function() {
    $(selector + ' input:checkbox:checked').prop("checked", false);
    $(selector).change();
  }
  
  this.disable = function() {
    $(selector + "ControlGroup input").prop('disabled', true);
    $(selector + "ControlGroup button").prop('disabled', true);
  }
  this.enable = function() {
    $(selector + 'ControlGroup input').prop('disabled', false);
    $(selector + 'ControlGroup button').prop('disabled', false);
  }
  
};

/**
 * Simple change tracking.
 *
 * $("#rootElement").trackChanges("input,#dataTable", function(e) {
 *   if (e.changes.length > 0) {
 *     //show save button
 *   } else {
 *     //hide save button
 *   }
 * });
 * @param selector
 * @param callback
 */
$.fn.trackChanges = function(selector, callback) {
    this.find(selector).each(function() {
        $(this).data("orig-value", $(this).val());
    });

    this.on("change", function(e) {
        var changes = [];
        $(e.delegateTarget).find(selector).each(function() {
            if (($(this).data("orig-value") || "") != $(this).val()) {
                changes.push(this);
            }
        });
        e.changes = $(changes);
        callback(e)
    });
    (this).find(selector).change();
};


function renderBoolean(o, val) {
	  var s = "";
	  if (val) {
	    s += '<span class="glyphicon glyphicon-ok"></span> ';
	  }
	  return s;
}

function createCookie(name, value, days) {
  if (days) {
      var date = new Date();
      date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
      var expires = "; expires=" + date.toGMTString();
  } 
  else { 
    var expires = "";
  }
  document.cookie = escape(name) + "=" + escape(value) + expires + "; path=/";
}

function readCookie(name) {
  var nameEQ = escape(name) + "=";
  var ca = document.cookie.split(';');
  for (var i = 0; i < ca.length; i++) {
      var c = ca[i];
      while (c.charAt(0) == ' ') {
        c = c.substring(1, c.length);
      }
      if (c.indexOf(nameEQ) == 0) { 
        return unescape(c.substring(nameEQ.length, c.length));
      }
  }
  return null;
}

function updateFilter(keyCode)
{
	if(keyCode==13){
	document.getElementById("filterButton").click();
	}
}


function eraseCookie(name) {
  createCookie(name, "", -1);
}

$(document).ready(function(){
  commonInitialize();
});
