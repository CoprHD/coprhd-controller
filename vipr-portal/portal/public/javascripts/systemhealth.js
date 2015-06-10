// top-level namespace
var SystemHealth = SystemHealth || {};

/** The base proxy uri for making Ajax calls to Bourne */
SystemHealth.PROXY_URI = "/system/proxy/";

/** Handle Ajax error and display an error message based on HTTP status,
 *  with the provided error title. */
SystemHealth.handleAjaxError = function(jqXHR, exception, errorTitle) {
	// ignore the common 0 status error due to not ready, ajax call is cancelled, etc.
	if (jqXHR.status == 0) 
		return;
	
	var errorMessage = "";
	// Reload page after 1 second on Unauthorized, which will forward to login page
	if (jqXHR.status == 401) {
		errorMessage = Messages.get("systemHealth.message.401");
		setTimeout("window.location.reload();", 1000);
	} else if (jqXHR.status == 404) {
		errorMessage = Messages.get("systemHealth.message.404");
	} else if (jqXHR.status == 500) {
		errorMessage = Messages.get("systemHealth.message.500");
	} else if (jqXHR.status == 403) {
		errorMessage = Messages.get("systemHealth.message.403");
	} else if (jqXHR.status == 12029) {
		errorMessage = Messages.get("systemHealth.message.connectFailed");
	} else if (exception === 'parsererror') {
		errorMessage = Messages.get("systemHealth.message.parseError");
	} else if (exception === 'timeout') {
		errorMessage = Messages.get("systemHealth.message.timeout");
	} else if (exception === 'abort') {
		errorMessage = Messages.get("systemHealth.message.abort");
	} else {
		errorMessage = Messages.get("systemHealth.message.uncaught", jqXHR.status, jqXHR.responseText);
	}
	
	displayErrorMessage(errorMessage, errorTitle);
};

/** Handle server error message by displaying the error message with title. If a HTTP
 *  response code 401 (Unauthorized) is encountered in message, reload page so user 
 *  is forced to login again. 
 **/ 
SystemHealth.handleErrorMessage = function(errorMessage, errorTitle) {
	displayErrorMessage(errorMessage, errorTitle);
	
	// Reload page after 1 second on Unauthorized, which will forward to login page
	if(errorMessage && errorMessage.indexOf("response code: 401") > 0) {
		setTimeout("window.location.reload();", 1000);
	}
};

function displayErrorMessage(errorMessage, errorTitle) {
	//jAlert(errorMessage, errorTitle);
	var alertBox = $("#alerts_error");
	
	// add close button if there isn't one
	if(alertBox.html().length < 20) {
		alertBox.append("<button type='button' class='close' onclick='$(\".alert\").html(\"\").hide();'>&times;</button>");
	}
	alertBox.append("<b>" + errorTitle +"</b><br>" + errorMessage + "<br>").show();
};

/** Sort array of objects by string field. */
SystemHealth.sortObjectsByField = function(array, field) {
	array.sort(function(x, y) {
		return x[field].localeCompare(y[field]);
	});
};

SystemHealth.urlParams = {};
(function () {
    var match,
        pl = /\+/g,  // Regex for replacing addition symbol with a space
        search = /([^&=]+)=?([^&]*)/g,
        decode = function (s) { return decodeURIComponent(s.replace(pl, " ")); };
        
    if(window.location.search && window.location.search.length > 3) {
    	// remove leading question mark
        query = window.location.search.substring(1);
        while (match = search.exec(query)) {
        	SystemHealth.urlParams[decode(match[1])] = decode(match[2]);
        }
    }
})();
