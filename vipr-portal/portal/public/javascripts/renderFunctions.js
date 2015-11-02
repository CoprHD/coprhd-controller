var render = render || {};

/*
 * Data Table Render Functions
 */

render.orderStatus = function(o, val) {
    if (!val) {
        return "";
    }
    var labels = {
        'PENDING':   'label-default',
        'EXECUTING': 'label-info',
        'SUCCESS':   'label-success',
        'ERROR':     'label-danger',
        'SCHEDULED': 'label-default',
        'CANCELLED': 'label-warning',
        'APPROVAL':  'label-default',
        'APPROVED':  'label-info',
        'REJECTED':  'label-danger'
    }
    var icons = {
        'PENDING':   'glyphicon glyphicon-time',
        'EXECUTING': 'glyphicon glyphicon-refresh rotate',
        'SUCCESS':   'glyphicon glyphicon-ok',
        'PARTIAL_SUCCESS': 'glyphicon glyphicon-info-sign',
        'ERROR':     'glyphicon glyphicon-remove',
        'SCHEDULED': 'glyphicon glyphicon-time',
        'CANCELLED': 'glyphicon glyphicon-remove',
        'APPROVAL':  'glyphicon glyphicon-flag',
        'APPROVED':  'glyphicon glyphicon-check',
        'REJECTED':  'glyphicon glyphicon-remove'
    };
    
    var label = defaultValue(labels[val], '');
    var icon = defaultValue(icons[val], 'glyphicon glyphicon-question-sign');
    
    return '<span class="' + icon + '"></span>';
}

render.taskState = function(o, val) {
  if (!val) {
      return "";
  }
  var labels = {
      'pending':   'label-default',
      'ready':   'label-success',
      'error':     'label-danger'
  }
  var icons = {
      'pending': 'glyphicon-refresh rotate',
      'ready':   'glyphicon-ok',
      'error':     'glyphicon-remove'
  };
  
  var label = defaultValue(labels[val], '');
  var icon = defaultValue(icons[val], 'icon-question-sign');
  
  return '<span class="glyphicon ' + icon + '"></span>';
}

render.link = function(o, val) {
    if (o.aData.link) {
        return "<a href='" + o.aData.link + "'>" + val + "</a>";
    }
    else {
        return val;
    }
}

render.date = function(o, val) {
    if (!val) {
        return "";
    }
    var date = new Date(("number" == typeof val) ? val : Date.parse(val));
    return formatDateTime(date);
}

render.localDate = function(o, val) {
    if (!val) {
        return "";
    }
    var date = new Date(("number" == typeof val) ? val : Date.parse(val));
    return formatLocalDateTime(date);
}

render.localDateISO = function(o, val) {
    if (!val) {
        return "";
    }
    var date = new Date(("number" == typeof val) ? val : Date.parse(val));
    return formatDate(date, 'YYYY-MM-DD HH:mm:ss');
}

render.relativeDate = function(o, val) {
    if (!val) {
        return "";
    }
    var date = new Date(("number" == typeof val) ? val : Date.parse(val));
    var millis = date.getTime();
    var text = formatRelativeDate(date);
    
    return '<span data-relative-time="'+millis+'">'+text+'</span>';
}

render.expiryStatus = function(o, val) {
	if (!val) {
		return "";
	}
	var expiryDate = val.substring(val.indexOf('/')-2,val.lastIndexOf('.'));
	var formattedDate = render.localDate(o, expiryDate);
	return val.substring(0, val.indexOf('/')-2).concat(formattedDate);
}

render.vdclastReached = function(o, val) {
    if (!val) {
        return "";
    }
    var shouldAlarm = o.aData.lastReachAlarm;
    var date = new Date(("number" == typeof val) ? val : Date.parse(val));
    var millis = date.getTime();
    var text = formatRelativeDate(date);

    if (shouldAlarm) {
        return '<span data-relative-time="'+millis+'">'+text+'</span>' + '&nbsp;&nbsp;<span class="text-danger"><span class="glyphicon glyphicon-exclamation-sign"></span></span>';
    } else {
        return '<span data-relative-time="'+millis+'">'+text+'</span>';
    }
}

render.serviceCatalogImage = function(o, val) {
    var url = "/public/img/serviceCatalog/" + val;
    return "<img src='" + url + "'>";
}

render.approvalStatus = function(o, val) {
    var icons = {
        'PENDING':  'glyphicon glyphicon-time',
        'APPROVED': 'glyphicon glyphicon-ok',
        'REJECTED': 'glyphicon glyphicon-remove'
    };
    var messages = {
        'PENDING':  Messages.get("renderFunctions.approval.status.pending"),
        'APPROVED': Messages.get("renderFunctions.approval.status.approved"),
        'REJECTED': Messages.get("renderFunctions.approval.status.rejected")
    };
    
    var icon = defaultValue(icons[val], 'glyphicon glyphicon-none');
    var message = defaultValue(messages[val], val);
    
    return "<span class='" + icon + "'></span> <span>" + message+"</span>";
}

render.operatingSystem = function(o, val) {
    var values = {
        'LINUX':   Messages.get("renderFunctions.operating.system.linux"),
        'WINDOWS': Messages.get("renderFunctions.operating.system.windows"),
        'No_OS': Messages.get("renderFunctions.operating.system.no.os"),
        'UNKNOWN': Messages.get("renderFunctions.operating.system.unkown")
    };
    return defaultValue(values[val], val);
}

render.protocols = function(o, val) {
    var values = {
        'FC':   Messages.get("renderFunctions.protocols.fiber"),
        'ISCSI': Messages.get("renderFunctions.protocols.iscsi")
    };
    
    if (val) {
        var protocols = val.split(",");
        val = "";
        for (var i = 0; i < protocols.length; i++) {
            var protocol = protocols[i].replace(" ", "");
            if (i > 0) {
                val += ", ";
            }
            val += defaultValue(values[protocol], protocol);
        }
    }
    return val;
}

render.approvalStatus = function(o, val) {
    var icons = {
        'PENDING':  'glyphicon glyphicon-time',
        'APPROVED': 'glyphicon glyphicon-ok',
        'REJECTED': 'glyphicon glyphicon-remove'
    };
    var messages = {
        'PENDING':  Messages.get("renderFunctions.approval.status.pending"),
        'APPROVED': Messages.get("renderFunctions.approval.status.approved"),
        'REJECTED': Messages.get("renderFunctions.approval.status.rejected")
    };
    
    var icon = defaultValue(icons[val], 'glyphicon glyphicon-none');
    var message = defaultValue(messages[val], val);
    
    return "<span class='" + icon + "'></span> <span>" + message+"</span>";
}

render.discoveryStatusIcon = function(o, val) {
    if (!o) {
        return "";
    }
    var labels = {
        'CREATED':     'label-default',
        'IN_PROGRESS': 'label-info',
        'SCHEDULED':   'label-info',
        'COMPLETE':    'label-success',
        'PARTIAL_SUCCESS': 'label-warning',
        'ERROR':       'label-danger'
    }
    var icons = {
        'CREATED':     'glyphicon glyphicon-time',
        'IN_PROGRESS': 'glyphicon glyphicon-refresh rotate',
        'SCHEDULED':   'glyphicon glyphicon-time',
        'COMPLETE':    'glyphicon glyphicon-ok',
        'PARTIAL_SUCCESS': 'glyphicon glyphicon-warning-sign',
        'ERROR':       'glyphicon glyphicon-remove'
    };

    var label = defaultValue(labels[o.aData.discoveryStatus], 'label-default');
    var icon = defaultValue(icons[o.aData.discoveryStatus], 'glyphicon glyphicon-question-sign');
    
    var compatibilityStatus = o.aData.compatibilityStatus;
    if (compatibilityStatus == 'Incompatible' || compatibilityStatus == 'INCOMPATIBLE') {
        label = 'label-warning';
        icon = 'glyphicon glyphicon-exclamation-sign';
    }
	if (label == 'label-warning') {
		return '<span class="label ' + label + '"><span class="'
				+ '<a href="#" data-toggle="tooltip" data-placement="left" title="Import failed to at least one Compute Image Server"><span class="glyphicon glyphicon-warning-sign"></a>'
				+ '</span></span>';
	} else {
		return '<span class="label ' + label + '"><span class="' + icon + '"></span></span>';		
	}
}

render.discoveryStatusMessage = function(o, val) {
    if (!o) {
        return "";
    }
    var messages = {
        'CREATED':     Messages.get("renderFunctions.discovery.status.pending"),
        'SCHEDULED':   Messages.get("renderFunctions.discovery.status.scheduled"),
        'IN_PROGRESS': Messages.get("renderFunctions.discovery.status.progress"),
        'COMPLETE':    Messages.get("renderFunctions.discovery.status.complete"),
        'PARTIAL_SUCCESS':    Messages.get("renderFunctions.discovery.status.importFailed"),
        'ERROR':       Messages.get("renderFunctions.discovery.status.failed")
    };

    var message = o.aData.errorSummary;
    if (!message) {
        message = defaultValue(messages[o.aData.discoveryStatus], Messages.get("renderFunction.discovery.status.unknown"));
    }
    
    return '<span>' + message + '</span>';
}

render.discoveryStatus = function(o, val) {
    return render.discoveryStatusIcon(o, val) + " " + render.discoveryStatusMessage(o, val);
}

render.registrationStatus = function(o, val) {
    if (!val) {
        return "";
    }
    var icons = {
        'REGISTERED':  'glyphicon glyphicon-ok',
        'Registered':  'glyphicon glyphicon-ok',
        'UNREGISTERED':  'glyphicon glyphicon-remove text-muted',
        'Unregistered':  'glyphicon glyphicon-remove text-muted'
    };
    var icon = icons[val];
    if (icon != null) {
        return "<span class='" + icon + "'></span>";
    }
    else {
        return "";
    }
}

render.allocationDisqualified = function (o,val) {
    return (val) ? '<span class="glyphicon glyphicon-thumbs-down"></span>' : '<span class="glyphicon glyphicon-thumbs-up"></span>';
}

render.vdcStatus = function(o, val) {
    if (!val) {
        return "";
    }

    
    var labels = {
        'ISOLATED': 'label-success',
        'CONNECTING': 'label-info',
        'CONNECTING_SYNCED': 'label-info',
        'CONNECTED': 'label-success',
        'CONNECT_PRECHECK_FAILED': 'label-danger',
        'CONNECT_FAILED': 'label-danger',
        'DISCONNECTING': 'label-info',
        'DISCONNECTED': 'label-danger',
        'DISCONNECT_PRECHECK_FAILED': 'label-danger',
        'DISCONNECT_FAILED': 'label-danger',
        'REMOVING': 'label-info',
        'REMOVE_SYNCED': 'label-success', 
        'REMOVE_PRECHECK_FAILED': 'label-danger',
        'REMOVE_FAILED': 'label-danger',
        'UPDATING': 'label-info',
        'UPDATE_PRECHECK_FAILED': 'label-danger',
        'UPDATE_FAILED': 'label-danger',
        'RECONNECTING': 'label-info',
        'RECONNECT_PRECHECK_FAILED': 'label-danger',
        'RECONNECT_FAILED': 'label-danger'
    }
    var icons = {
        'ISOLATED': 'glyphicon glyphicon-ok',
        'CONNECTING': 'glyphicon glyphicon-refresh rotate',
        'CONNECTING_SYNCED': 'glyphicon glyphicon-refresh rotate',
        'CONNECTED': 'glyphicon glyphicon-ok',
        'CONNECT_PRECHECK_FAILED': 'glyphicon glyphicon-remove',
        'CONNECT_FAILED': 'glyphicon glyphicon-remove',
        'DISCONNECTING': 'glyphicon glyphicon-refresh rotate',
        'DISCONNECTED': 'glyphicon glyphicon-remove',
        'DISCONNECT_PRECHECK_FAILED': 'glyphicon glyphicon-remove',
        'DISCONNECT_FAILED': 'glyphicon glyphicon-remove',
        'REMOVING': 'glyphicon glyphicon-refresh rotate',
        'REMOVE_SYNCED': 'glyphicon glyphicon-ok', 
        'REMOVE_PRECHECK_FAILED': 'glyphicon glyphicon-remove',
        'REMOVE_FAILED': 'glyphicon glyphicon-remove',
        'UPDATING': 'glyphicon glyphicon-refresh rotate',
        'UPDATE_PRECHECK_FAILED': 'glyphicon glyphicon-remove',
        'UPDATE_FAILED': 'glyphicon glyphicon-remove',
        'RECONNECTING': 'glyphicon glyphicon-refresh rotate',
        'RECONNECT_PRECHECK_FAILED': 'glyphicon glyphicon-remove',
        'RECONNECT_FAILED': 'glyphicon glyphicon-remove'
    };
    
    var messages = {
        'ISOLATED': Messages.get("renderFunctions.vdc.status.isolated"),
        'CONNECTING': Messages.get("renderFunctions.vdc.status.connecting"),
        'CONNECTING_SYNCED': Messages.get("renderFunctions.vdc.status.connectingSync"),
        'CONNECTED': Messages.get("renderFunctions.vdc.status.connected"),
        'CONNECT_PRECHECK_FAILED': Messages.get("renderFunctions.vdc.status.connectPrecheckFailed"),
        'CONNECT_FAILED': Messages.get("renderFunctions.vdc.status.connectFailed"),
        'DISCONNECTING': Messages.get("renderFunctions.vdc.status.disconnecting"),
        'DISCONNECTED': Messages.get("renderFunctions.vdc.status.disconnected"),
        'DISCONNECT_PRECHECK_FAILED': Messages.get("renderFunctions.vdc.status.disconnectPrecheckFailed"),
        'DISCONNECT_FAILED': Messages.get("renderFunctions.vdc.status.disconnectFailed"),
        'REMOVING': Messages.get("renderFunctions.vdc.status.removing"),
        'REMOVE_SYNCED': Messages.get("renderFunctions.vdc.status.removingSync"), 
        'REMOVE_PRECHECK_FAILED': Messages.get("renderFunctions.vdc.status.removePrecheckFailed"),
        'REMOVE_FAILED': Messages.get("renderFunctions.vdc.status.removeFailed"), 
        'UPDATING': Messages.get("renderFunctions.vdc.status.updating"),
        'UPDATE_PRECHECK_FAILED': Messages.get("renderFunctions.vdc.status.updatePrecheckFailed"),
        'UPDATE_FAILED': Messages.get("renderFunctions.vdc.status.updateFailed"),
        'RECONNECTING': Messages.get("renderFunctions.vdc.status.reconnecting"),
        'RECONNECT_PRECHECK_FAILED': Messages.get("renderFunctions.vdc.status.reconnectPrecheckFailed"),
        'RECONNECT_FAILED': Messages.get("renderFunctions.vdc.status.reconnectFailed")
    };
    
    var label = defaultValue(labels[o.aData.connectionStatus], 'label-warning');
    var icon = defaultValue(icons[o.aData.connectionStatus], 'glyphicon glyphicon-question-sign');
    var message = o.aData.errorSummary;
    if (!message) {
        message = defaultValue(messages[o.aData.connectionStatus], Messages.get("renderFunctions.vdc.status.unknown"));
    }
    
    return '<span class="label ' + label + '"><span class="' + icon + '"></span></span> <span>' + message+'</span>';
}

render.operationalStatus = function(o, val) {
    var states = {
        'OK':  {
            'icon': 'glyphicon glyphicon-ok',
            'classes': 'label-success'
        },
        'NOT_OK': {
            'icon': 'glyphicon glyphicon-remove',
            'classes': 'label-danger'
        },
        'UNKNOWN': {
            'icon': 'glyphicon glyphicon-question-sign',
            'classes': 'label-warning'
        }
    };
    var state = states[val];
    if (!state) {
        state = states['UNKNOWN'];
    }
    return "<span class='label "+state.classes+"'><span class='"+state.icon+"'></span></span>";
}

render.boolean = function(o, val) {
    return (val) ? '<span class="glyphicon glyphicon-ok"></span>' : '';
}

render.sizeInGb = function(o, val) {
    return (typeof(val) != 'undefined') ? Messages.get("renderFunctions.size.GB", val) : "";
}

render.bytes = function(o, val) {
    if ((typeof(val) == 'undefined') || (val == null) || (val == "")) {
        return "";
    }
    return util.parseBytes(val, 2);
}

render.networkIdentifier = function(o, val) {
  var id = o.aData.networkIdentifier;
  var iqn = o.aData.iqn;
  return iqn ? iqn : id;
}

render.status = function(o,val) {
    return "<status type='node' status='{{row.status}}'></status>";
}

/*
 * Other Render Functions
 */

render.clusterStatus = function(selector, val) {
    val = (val) ? val : "UNKNOWN";

    var states = {
        'UNKNOWN': {
            'icon': 'glyphicon glyphicon-question-sign',
            'classes': 'label-warning',
            'text': Messages.get("renderFunctions.cluster.status.unknown")
        },
        'STABLE': {
            'icon': 'glyphicon glyphicon-ok',
            'classes': 'label-success',
            'text': Messages.get("renderFunctions.cluster.status.stable")
        },
        'SYNCING': {
            'icon': 'glyphicon glyphicon-download',
            'classes': 'label-info',
            'text': Messages.get("renderFunctions.cluster.status.sync")
        },
        'UPGRADING': {
            'icon': 'glyphicon glyphicon-repeat',
            'classes': 'label-info',
            'text': Messages.get("renderFunctions.cluster.status.upgrading")
        },
        'UPGRADING_PREP_DB': {
            'icon': 'glyphicon glyphicon-time',
            'classes': 'label-info',
            'text': Messages.get("renderFunctions.cluster.status.prepDB")
        },
        'UPGRADING_CONVERT_DB': {
            'icon': 'glyphicon glyphicon-repeat',
            'classes': 'label-info',
            'text': Messages.get("renderFunctions.cluster.status.upgradeDB")
        },
        'UPGRADING_FAILED': {
            'icon': 'glyphicon glyphicon-remove',
            'classes': 'label-danger',
            'text': Messages.get("renderFunctions.cluster.status.upgradeFailed")
        },
        'DEGRADED': {
            'icon': 'glyphicon glyphicon-warning-sign',
            'classes': 'label-warning',
            'text': Messages.get("renderFunctions.cluster.status.degraded")
        },
        'UPDATING': {
            'icon': 'glyphicon glyphicon-wrench',
            'classes': 'label-info',
            'text': Messages.get("renderFunctions.cluster.status.updating")
        },
        'POWERINGOFF': {
            'icon': 'glyphicon glyphicon-off',
            'classes': 'label-danger',
            'text': Messages.get("renderFunctions.cluster.status.poweringOff")
        },
        'INITIALIZING': {
            'icon': 'glyphicon glyphicon-repeat',
            'classes': 'label-default',
            'text': Messages.get("renderFunctions.cluster.status.init")
        }
    }
    var state = states[val];
    if (state != null) {
        $(selector).html("<span class='label " + state.classes + "'><span class='" + state.icon + "'></span> " + state.text + "</span>");
    }
    else {
        $(selector).html(val);
    }
}

render.manageUrl = function(o, val) {
    if (o.aData.manageUrl) {
        return "<a href='" + o.aData.manageUrl + "' class='btn btn-xs btn-default' target='_blank'>" + Messages.get("renderFunctions.manage") + "</a>";
    }
    else {
        return val;
    }
}

render.certificate = function(data) {
    return "<pre><code>" + data.certificateInfo + "</code></pre>";
}

render.taskState = function(o, val) {
  var state = o.aData.state;
  var s = '';
  if (state) {
    s += '<div>';
    s += renderTaskStateIcon(state);
    if (state.toUpperCase() == "PENDING") {
      s += ' ' + Messages.get('resources.tasks.pending');
    }        
    else if (state.toUpperCase() == "READY") {
      s += ' ' + Messages.get('resources.tasks.ready');
    }
    else if (state.toUpperCase() == "ERROR") {
      s += ' ' + Messages.get('resources.tasks.error');
    }
    s += '</div>';
  }      
  return s;
}

render.taskProgress = function(o, val) {
  var s = "";
  s += renderTaskStatusBar(o.aData.progress, o.aData.state);
  return s;
}

render.taskStart = function(o, val) {
  var s = '<span data-relative-time="' + val + '">' + formatRelativeDate(val) + '</span>';
  return s;
}    

render.taskElapsed = function(o, val) {
  var s =  '<span data-elapsed-time="' + o.aData.start;
  if (o.aData.end) {
    s += ":" + o.aData.end;
  }
  s += '">' + formatElapsedTime(o.aData.start, o.aData.end) + '</span>';
  return s;
}    

render.taskResource = function(o, val) {
  var s = renderResourceImage(o.aData.resourceType)
  if (o.aData.resourceName != null) {
    var resourceLink = getResourceLink(o.aData.resourceType, o.aData.resourceId)
    if (resourceLink) {
      s += " <a href='" + resourceLink + "'>";
    }
    s += o.aData.resourceName;
    if (resourceLink) {
      s += "</a>";
    }
  }
  return s;
}

render.taskDetails = function(data) {
  var details = "<dl class='dl-horizontal'><dt>" + Messages.get('tasks.id') + "</dt><dd>" + data.id + "</dd>";
  
  if (data["start"]) {
    details += "<dt>" + Messages.get('tasks.start') + "</dt><dd>" + formatLocalDateTime(data.start) + "</dd>";
  }
  if (data["end"]) {
    details += "<dt>" + Messages.get('tasks.end') + "</dt><dd>" + formatLocalDateTime(data.end) + "</dd>";
  }
  if (data["description"]) {
    details += "<dt>" + Messages.get('tasks.description') + "</dt><dd>" + data.description + "</dd>";
  }            
  if (data["message"]) {
    details += "<dt>" + Messages.get('tasks.message') + "</dt><dd>" + data.message + "</dd>";
  }
  if (data["errorCode"]) {
    details += "<dt>" + Messages.get('tasks.errorCode') + "</dt><dd>" + data.errorCode;
    if (data["errorCodeDescription"]) {
      details += " " + data.errorCodeDescription;
    }
    details += "</dd>";
  }
  if (data["errorDetails"]) {
    details += "<dt>" + Messages.get('tasks.errorCodeDetails') + "</dt><dd>" + data.errorDetails + "</dd>";
  }         
  if (data["orderId"]) {
    details += "<dt>" + Messages.get('tasks.orderId') + "</dt><dd><a href='" + routes.Orders_receipt({"orderId": data.orderId}) + "'>" + (data.orderNumber ? data.orderNumber : Messages.get('tasks.viewOrder')) + "</a></dd>";
  }
  return details;
}

function getResourceLink(resourceType, resourceId) {
  var resourceLink;
  if (resourceType) {
    if (resourceType.toUpperCase() == "VOLUME") {
      resourceLink = routes.BlockVolumes_volume({"volumeId": resourceId});
    }
    else if (resourceType.toUpperCase() == "EXPORT_GROUP") {
      resourceLink = routes.BlockExportGroups_exportGroup({"exportGroupId": resourceId});
    }
    else if (resourceType.toUpperCase() == "FILE_SHARE") {
      resourceLink = routes.FileSystems_fileSystem({"fileSystemId": resourceId});
    }         
    else if (resourceType.toUpperCase() == "FILE_SNAPSHOT") {
      resourceLink = routes.FileSnapshots_snapshot({"snapshotId": resourceId});
    }         
    else if (resourceType.toUpperCase() == "BLOCK_SNAPSHOT") {
      resourceLink = routes.BlockSnapshots_snapshotDetails({"snapshotId": resourceId});
    }         
//     else if (resourceType.toUpperCase() == "BLOCK_CONTINOUS_COPY") {
//       resourceLink = routes.BlockVolumes_volume({"volumeId": resourceId});
//     }         
    else if (resourceType.toUpperCase() == "HOST") {
      resourceLink = routes.Hosts_edit({"id": resourceId});
    }         
    else if (resourceType.toUpperCase() == "CLUSTER") {
      resourceLink = routes.HostClusters_edit({"id": resourceId});
    }   
    else if (resourceType.toUpperCase() == "STORAGE_SYSTEM") {
      resourceLink = routes.StorageSystems_edit({"id": resourceId});
    }
    else if (resourceType.toUpperCase() == "NETWORK_SYSTEM") {
      resourceLink = routes.SanSwitches_edit({"id": resourceId});
    }
    else if (resourceType.toUpperCase() == "PROTECTION_SYSTEM") {
      resourceLink = routes.DataProtectionSystems_edit({"id": resourceId});
    }
    else if (resourceType.toUpperCase() == "STORAGE_PROVIDER") {
      resourceLink = routes.StorageProviders_edit({"id": resourceId});
    }
    else if (resourceType.toUpperCase() == "VCENTER") {
      resourceLink = routes.VCenter_edit({"id": resourceId});
    }
    else if (resourceType.toUpperCase() == "COMPUTE_SYSTEM") {
      resourceLink = routes.ComputeSystem_edit({"id": resourceId});
    }
  }
  return resourceLink;
}

function renderTaskStatusBar(progress, state) {
  var stateClass = "progress-bar-info";
  if (state) {
    if (state.toUpperCase() == "READY") {
      stateClass = "progress-bar-success";
    }
    else if (state.toUpperCase() == "ERROR") {
      stateClass = "progress-bar-danger";
    }
  }
  
  var s = "";
  s += '<div class="progress">';
  s += '<div class="progress-bar ' + stateClass + '" role="progressbar" aria-valuenow="60" aria-valuemin="0" aria-valuemax="100" style="width: ' + progress + '%;">';
  s += '<span>' + Messages.get('resources.tasks.percent', progress + "") + '</span>';
  s += '</div>';
  s += '</div>';
  return s;      
}

function renderTaskStateIcon(state) {
  var s = '';
  if (state) {
    if (state.toUpperCase() == "PENDING") {
      s += '<span class="glyphicon glyphicon-refresh rotate"></span>';
    }        
    else if (state.toUpperCase() == "READY") {
      s += '<span class="glyphicon glyphicon-ok"></span>';
    }
    else if (state.toUpperCase() == "ERROR") {
      s += '<span class="glyphicon glyphicon-remove"></span>';
    }
  }      
  return s;
}    

function renderResourceImage(resourceType) {
    var resourceImage = null;
    if (resourceType) {
      if (resourceType.toUpperCase() == "VOLUME") {
        resourceImage = Messages.get('affectedResources.volume.image');
      }
      else if (resourceType.toUpperCase() == "EXPORT_GROUP") {
        resourceImage = Messages.get('affectedResources.exportGroup.image');
      }
      else if (resourceType.toUpperCase() == "FILE_SHARE") {
        resourceImage = Messages.get('affectedResources.fileSystem.image');
      }         
      else if (resourceType.toUpperCase() == "FILE_SNAPSHOT") {
        resourceImage = Messages.get('affectedResources.fileSnapshot.image');
      }         
      else if (resourceType.toUpperCase() == "BLOCK_SNAPSHOT") {
        resourceImage = Messages.get('affectedResources.blockSnapshot.image');
      }         
      else if (resourceType.toUpperCase() == "BLOCK_CONTINOUS_COPY") {
        resourceImage = Messages.get('affectedResources.blockContinousCopy.image');
      }         
      else if (resourceType.toUpperCase() == "HOST") {
        resourceImage = Messages.get('affectedResources.host.image');
      }         
      else if (resourceType.toUpperCase() == "CLUSTER") {
        resourceImage = Messages.get('affectedResources.cluster.image');
      }   
      else if (resourceType.toUpperCase() == "STORAGE_SYSTEM") {
          resourceImage = Messages.get('affectedResources.storageSystem.image');
      }
      else if (resourceType.toUpperCase() == "NETWORK_SYSTEM") {
          resourceImage = Messages.get('affectedResources.networkSystem.image');
      }
      else if (resourceType.toUpperCase() == "PROTECTION_SYSTEM") {
        resourceImage = Messages.get('affectedResources.protectionSystem.image');
      }
      else if (resourceType.toUpperCase() == "STORAGE_PROVIDER") {
        resourceImage = Messages.get('affectedResources.storageSystem.image');
      }
      else if (resourceType.toUpperCase() == "VCENTER") {
        resourceImage = Messages.get('affectedResources.vcenter.image');
      }
      else if (resourceType.toUpperCase() == "COMPUTE_SYSTEM") {
          resourceImage = Messages.get('affectedResources.computeSystem.image');
      }
    }
  
    var s = '';
    if (resourceImage) {
      s += '<img src="/public/img/' + resourceImage + '" width="25px" height="20px">';
    }
    
  return s;
}

render.recoveryErrorStatus = function(selector, val) {
	val = (val) ? val : "";
	
	var states = {
			'REPAIR_FAILED' : {
				'text' : Messages.get("renderFunctions.recovery.error.repair")
			},
			'SYNC_FAILED' : {
				'text' : Messages.get("renderFunctions.recovery.error.sync")
			},
			'INTERNAL_ERROR' : { 
				'text' : Messages.get("renderFunctions.recovery.error.internal")
			}
	}
	
    var state = states[val];
    if (state != null) {
        var helpMsg = Messages.get("renderFunctions.recovery.error.helpMsg")
        $(selector).html("<span> " + state.text + ".  " + helpMsg + "</span>");
    }
    else {
        $(selector).html(val);
    }
}

render.recoveryStatus = function(selector, val) {
    val = (val) ? val : "NA";

    var states = {
        'INIT': {
        	'icon': 'glyphicon glyphicon-repeat',
            'classes': 'label-default',
            'text': Messages.get("renderFunctions.recovery.status.init")
        },
        'PREPARING': {
            'icon': 'glyphicon glyphicon-ok',
            'classes': 'label-success',
            'text': Messages.get("renderFunctions.recovery.status.preparing")
        },
        'SYNCING': {
            'icon': 'glyphicon glyphicon-download',
            'classes': 'label-info',
            'text': Messages.get("renderFunctions.recovery.status.sync")
        },
        'REPAIRING': {
            'icon': 'glyphicon glyphicon-warning-sign',
            'classes': 'label-warning',
            'text': Messages.get("renderFunctions.recovery.status.repair")
        },
        'FAILED': {
        	'icon': 'glyphicon glyphicon-remove',
            'classes': 'label-danger',
            'text': Messages.get("renderFunctions.recovery.status.failed")
        },
        'DONE': {
        	'icon': 'glyphicon glyphicon-ok',
            'classes': 'label-success',
            'text': Messages.get("renderFunctions.recovery.status.done")
        }
        
    }
    var state = states[val];
    if (state != null) {
        $(selector).html("<span class='label " + state.classes + "'><span class='" + state.icon + "'></span> " + state.text + "</span>");
    }
    else {
        $(selector).html(val);
    }
}

render.dbstatus = function(selector, val) {
    val = (val) ? val : "NA";

    var states = {
        'NOT_STARTED': {
        	'icon': 'glyphicon glyphicon-repeat',
            'classes': 'label-default',
            'text': Messages.get("renderFunctions.database.status.init")
        },
        'IN_PROGRESS': {
            'icon': 'glyphicon glyphicon-download',
            'classes': 'label-info',
            'text': Messages.get("renderFunctions.database.status.progress")
        },        
        'FAILED': {
        	'icon': 'glyphicon glyphicon-remove',
            'classes': 'label-danger',
            'text': Messages.get("renderFunctions.database.status.fail")
        },
        'SUCCESS': {
        	'icon': 'glyphicon glyphicon-ok',
            'classes': 'label-success',
            'text': Messages.get("renderFunctions.database.status.success")
        }
        
    }
    var state = states[val];
    if (state != null) {
        $(selector).html("<span class='label " + state.classes + "'><span class='" + state.icon + "'></span> " + state.text + "</span>");
    }
    else {
        $(selector).html(val);
    }
}



render.editableLink = function(o, val) {
    var data = o.aData;
    if (data.editable) {
        return "<a href='edit/" + data.id + "'>" + val + "</a>";
    } else {
        return val;
    }
}
