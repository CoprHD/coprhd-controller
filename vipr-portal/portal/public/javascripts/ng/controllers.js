angular.module("portalApp").controller({
    DefaultCtrl: function($scope) {},

    BlockVirtualPoolsCtrl: function($scope, translate, $http) {
        var MIN_SIZE = 'min';
        var DEFAULT_JOURNAL_SIZE = '0.25';
        var DEFAULT_JOURNAL_UNITS = 'x';
        var rpCopyDialog = $('#rpCopyDialog');

        $scope.state = location.pathname.match(/\/create\//) ? 'create' : 'edit';
        $scope.locked = $scope.vpool.locked;
        $scope.none = translate('common.none');

        $scope.$watch('vpool.virtualArrays', function(value, oldVal) {
            $http.post(routes.BlockVirtualPools_listVirtualArrayAttributesJson(), {vpool: $scope.vpool}).success(function(data) {
                angular.forEach(data, function(value, key) {
                    $scope[key + "AvailableOptions"] = $.grep($scope[key + "Options"], function(val) {
                        return value.indexOf(val.id) != -1;
                    });
                });
            });
        });

        var track = {
            autoTierPolicy: {
                path: routes.BlockVirtualPools_listAutoTierPoliciesJson(),
                depend: ['vpool.virtualArrays', 'vpool.provisioningType', 'vpool.systemType', 'vpool.uniqueAutoTierPolicyNames']
            },
            haVirtualArray: {
                path: routes.BlockVirtualPools_listHighAvailabilityVirtualArraysJson(),
                depend: ['vpool.virtualArrays', 'vpool.highAvailability']
            },
            haVirtualPool: {
                path: routes.BlockVirtualPools_listHighAvailabilityVirtualPoolsJson(),
                depend: ['vpool.virtualArrays', 'vpool.haVirtualArray']
            },
            continuousCopyVirtualPool: {
                path: routes.BlockVirtualPools_listContinuousCopyVirtualPoolsJson(),
                depend: ['vpool.virtualArrays']
            },
            rpVirtualArray: {
                path: routes.BlockVirtualPools_listRecoverPointVirtualArraysJson(),
                depend: ['vpool.virtualArrays', 'vpool.remoteProtection']
            },
            rpVirtualPool: {
                path: routes.BlockVirtualPools_listRecoverPointVirtualPoolsJson(),
                depend: ['rpCopy.virtualArray']
            },
            rpJournalVirtualArray: {
                path: routes.BlockVirtualPools_listRecoverPointVirtualArraysJson(),
                depend: ['vpool.virtualArrays', 'vpool.remoteProtection']
            },
            rpJournalVirtualPool: {
                path: routes.BlockVirtualPools_listRecoverPointJournalVPoolsJson(),
                depend: ['rpCopy.journalVArray']
            },
            vpoolSourceJournalVirtualArray: {
                path: routes.BlockVirtualPools_listRecoverPointVirtualArraysJson(),
                depend: ['vpool.virtualArrays', 'vpool.remoteProtection']
            },
            vpoolSourceJournalVirtualPool: {
                path: routes.BlockVirtualPools_listSourceRpJournalVPoolsJson(),
                depend: ['vpool.sourceJournalVArray']
            },
            vpoolHAJournalVirtualArray: {
                path: routes.BlockVirtualPools_listRecoverPointVirtualArraysJson(),
                depend: ['vpool.virtualArrays', 'vpool.remoteProtection']
            },
            vpoolHAJournalVirtualPool: {
                path: routes.BlockVirtualPools_listHaRpJournalVPoolsJson(),
                depend: ['vpool.haJournalVArray']
            }            
        };

        angular.forEach(track, function(options, key) {
            angular.forEach(options.depend.concat('vpool.' + key), function(expression) {
                $scope.$watch(expression, function(value, oldVal) {
                    if (value != oldVal) {
                        $http.post(options.path, {vpool: $scope.vpool, rpCopy: $scope.rpCopy}).success(function(data) {
                            $scope[key + 'Options'] = data;
                        });
                    }
                });
            });
        });

        $scope.addRecoverPointCopy = function(event) {
            $scope.$root.errors = {};
            $scope.rpCopy = {
                journalSize: DEFAULT_JOURNAL_SIZE,
                journalSizeUnit: DEFAULT_JOURNAL_UNITS
            };
            rpCopyDialog.modal('show');
            event.preventDefault();
        };

        $scope.saveRecoverPointCopy = function(rpCopy, event) {
            function find(options, id) {
                var name = $scope.none;
                angular.forEach(options, function(option) {
                    if (option.id == id) {
                        name = option.name;
                    }
                });
                return name;
            }
            $http.post(routes.BlockVirtualPools_validateRecoverPointCopy(), {rpCopy: $scope.rpCopy}).success(function(data) {
                if (data.success) {
                    rpCopy.virtualArrayName = find($scope.rpVirtualArrayOptions, rpCopy.virtualArray);
                    rpCopy.virtualPoolName = find($scope.rpVirtualPoolOptions, rpCopy.virtualPool);
                    rpCopy.journalVArrayName = find($scope.rpJournalVirtualArrayOptions, rpCopy.journalVArray);
                    rpCopy.journalVPoolName = find($scope.rpJournalVirtualPoolOptions, rpCopy.journalVPool);
                    rpCopy.formattedJournalSize = rpCopy.journalSize && rpCopy.journalSize.toLowerCase() != MIN_SIZE ?
                                                  rpCopy.journalSize + rpCopy.journalSizeUnit : rpCopy.journalSize;
                    if (rpCopy.index !== undefined) {
                        $scope.vpool.rpCopies[rpCopy.index] = rpCopy;
                    } else {
                        $scope.vpool.rpCopies.push(rpCopy);
                    }
                    rpCopyDialog.modal('hide');
                }
                else {
                    angular.forEach(data, function(error) {
                        var errors = $scope.$root.errors;
                        errors[error.key] = errors[error.key] || [];
                        errors[error.key].push(error.message);
                    });
                }
            })
        };

        $scope.deleteRecoverPointCopy = function(index, event) {
            $scope.vpool.rpCopies.splice(index, 1);
            event.preventDefault();
        };

        $scope.editRecoverPointCopy = function(rpCopy, index, event) {
            $scope.$root.errors = {};
            $scope.rpCopy = angular.copy(rpCopy);
            $scope.rpCopy.index = index;
            //empty-option in select-one is expecting undefined
            if ($scope.rpCopy.virtualPool === null) {
                $scope.rpCopy.virtualPool = undefined;
            }
            rpCopyDialog.modal('show');
            event.preventDefault();
        };
    },
    HealthCtrl: function($scope, $http, $interval) {
        $scope.init = function() {
            angular.extend($scope, $scope.healthDetails);
        };
        
        var promise = function(nodeId) {
            return $http({
               method: 'GET',
               url: routes.SystemHealth_renderNodeDetailsJson(),
               params: {nodeId: nodeId}
            });
        }
        if ($scope.healthDetails) {
            var getNodeStats = $interval(function() {
                promise($scope.nodeId).success( function(data) {
                    return angular.extend($scope, data);
                })
            }, 5000);

            $scope.$on('$destroy', function() {
               if (angular.isDefined(getNodeStats)) {
                   $interval.cancel(getNodeStats);
                   getNodeStats = undefined;
               } 
            }); 
        }
    },
    HealthDiagnosticCtrl: function ($scope, $http) {
        var promise = function(nodeId) {
            return $http({
                method:'GET',
                url: routes.SystemHealth_listDiagnosticsJson(),
                params: {nodeId: nodeId}
            })
        }
        var getDiagnostics = function() {
            promise($scope.nodeId).success(function(data) {
               return angular.extend($scope, data); 
            });
        }
        getDiagnostics();
    },
    ServiceFormCtrl: function($scope, $http, $timeout, translate) {
        angular.forEach($scope.serviceDescriptor.items, function(value) {
          if (value.type == "password") {
        	  var verify = ".verify";
        	  var verifyField = {
        			  "name": value.name + verify,
        			  "label": translate("password.verify.label", value.label),
        			  "type": value.type + verify,
        			  "description": translate("password.verify.description", value.label),
        			  "required": value.required,
                      "matchWith": value.name + ".value",
                      "matchError": translate('password.verify.noMatch', translate(value.label))
        	  };
        	  $scope[verifyField.name] = verifyField;
        	  
        	  var index = $scope.serviceDescriptor.items.indexOf(value) + 1;
        	  $scope.serviceDescriptor.items.splice(index , 0, verifyField);
          }
          $scope[value.name] = value;
        });
        
        $scope.disableSubmitButton = function() {
                // find all the required fields, and all the password verify fields
        	var passwordVerifyFields = [];
        	var requiredFields = [];
        	angular.forEach($scope.serviceDescriptor.items, function(field) {
        		if (field.required === true && !$scope.overriddenValues[field.name]) {
        			requiredFields.push(field);
        		}
        		if (field.type === "password.verify") {
        			passwordVerifyFields.push(field);
        		}
        	});
        	
        	// if a required field has no value, disable the order button
                var result = false;
                angular.forEach(requiredFields, function(field) {
                    if (field.value == null || field.value.length < 1) {
                        result = true;
                    }   
                });
                
                // if a password verify field has an error, disable the order button
                angular.forEach(passwordVerifyFields, function(field) {
                        var errors = $scope.errors[field.name];
                        if (errors && errors.length > 0) {
                                result = true;
                        }
                });
                
                // if we make it out, enable the order button
                return result;
        }
    },
    FileRessourceCtrl: function($scope, $http, $window, translate) {
    	$scope.edit = false;
    	$scope.rule = {};
    	$scope.add = {endpoint:'', permission:'ro'};
    	
    	$scope.secOpt = [{id:'sys', name:translate('resources.filesystem.export.security.sys')},
    	                 {id:'krb5', name:translate('resources.filesystem.export.security.krb5')},
    	                 {id:'krb5p', name:translate('resources.filesystem.export.security.krb5p')},
    	                 {id:'krb5i', name:translate('resources.filesystem.export.security.krb5i')}];

    	$scope.permOpt = [{id:'ro', name:translate('resources.filesystem.export.permission.ro')}, 
    	                  {id:'rw', name:translate('resources.filesystem.export.permission.rw')}, 
    	                  {id:'root', name:translate('resources.filesystem.export.permission.root')}];
    	
    	var setData = function(data) {
    		$scope.rule = data;
    	}
    	
    	var resetModal = function() {
    		$scope.rule = {};
    	}
    	
    	$scope.populateModal = function(edit, id, path, sec, anon) {
    		resetModal();
    		$scope.edit = edit;
    		$scope.rule.subDir = "";
    		if (edit) {
    			$scope.exportPath = path;
    			$scope.rule.security = sec;
        		$scope.rule.anon = anon;
        		var data = {params: { id: id, path: path, sec: sec} };
        		if (window.location.pathname.indexOf("resources.filesnapshots") > -1) {
        			$http.get(routes.FileSnapshots_fileSnapshotExportsJson(), data).success(setData);
        		} else {
        			$http.get(routes.FileSystems_fileSystemExportsJson(), data).success(setData);
        		}
    		} else {
    			$scope.rule.security = "sys";
        		$scope.rule.anon = "root";
        		$scope.rule.endpoints = [];
        		$scope.rule.endpoints.push(angular.copy($scope.add));
        		$scope.$apply();
    		}
    	}

    	$scope.deleteEndpoint = function(idx) { $scope.rule.endpoints.splice(idx, 1); }
    	$scope.addEndpoint = function() { $scope.rule.endpoints.push(angular.copy($scope.add)); }

    	$scope.$watch('rule', function(newVal) {
    		var ro = [], rw = [], root = [];
    		angular.forEach($scope.rule.endpoints, function(obj) {
    			if (obj.endpoint != '' && obj.permission == 'ro') {
    				ro.push(obj.endpoint);
    			} else if (obj.endpoint != '' && obj.permission == 'rw') {
    				rw.push(obj.endpoint);
    			} else if (obj.endpoint != '' && obj.permission == 'root') {
    				root.push(obj.endpoint);
    			}
    		});
    		$scope.rule.anon = newVal.anon;
    		$scope.rule.security = newVal.security;
    		$scope.rule.subDir = newVal.subDir;
    		$scope.ro = ro.toString();
    		$scope.rw = rw.toString();
    		$scope.root = root.toString();
    	}, true);
    },
    FileShareAclCtrl: function($scope, $http, $window, translate) {
    	
    	$scope.add = {type:'User', name:'', domain:'', permission:'Change'};
    	
    	$scope.typeOpt = [{id:'User', name:translate('resources.filesystem.acl.user')},
    	                 {id:'Group', name:translate('resources.filesystem.acl.group')}];

    	$scope.permOpt = [{id:'Read', name:translate('resources.filesystem.acl.read')}, 
    	                  {id:'Change', name:translate('resources.filesystem.acl.change')}, 
    	                  {id:'FullControl', name:translate('resources.filesystem.acl.fullcontrol')}];
    	
    	var setData = function(data) {
    		$scope.ack = data;
    	}
    	
    	var resetModal = function() {
    		$scope.acl = {};
    	}
    	
    	$scope.populateModal = function() {
    		    resetModal();
    			$scope.acl.accesscontrols = [];
        		$scope.acl.accesscontrols.push(angular.copy($scope.add));
        		$scope.$apply();

    	}

    	$scope.deleteACE = function(idx) { $scope.acl.accesscontrols.splice(idx, 1); }
    	$scope.addACE = function() { $scope.acl.accesscontrols.push(angular.copy($scope.add)); }

    	$scope.$watch('acl', function(newVal) {
    		var accessList = [];
    		angular.forEach($scope.acl.accesscontrols, function(obj) {
    			if (obj.name != '') {
    				accessList.push(obj.type + "~~~"+obj.name+ "~~~"+obj.domain+"~~~"+obj.permission);
    			}
    		});
    		
    		$scope.formAccessControlList = accessList.toString();
    	}, true);
    },
    SnapshotShareAclCtrl: function($scope, $http, $window, translate) {
    	
    	$scope.add = {type:'USER', name:'', domain:'', permission:'Read'};
    	
    	$scope.typeOpt = [{id:'USER', name:translate('resources.filesystem.acl.user')},
    	                 {id:'GROUP', name:translate('resources.filesystem.acl.group')}];

    	var setData = function(data) {
    		$scope.ack = data;
    	}
    	
    	var resetModal = function() {
    		$scope.acl = {};
    	}
    	
    	$scope.populateModal = function() {
    		    resetModal();
    			$scope.acl.accesscontrols = [];
        		$scope.acl.accesscontrols.push(angular.copy($scope.add));
        		$scope.$apply();

    	}

    	$scope.deleteACE = function(idx) { $scope.acl.accesscontrols.splice(idx, 1); }
    	$scope.addACE = function() { $scope.acl.accesscontrols.push(angular.copy($scope.add)); }

    	$scope.$watch('acl', function(newVal) {
    		var accessList = [];
    		angular.forEach($scope.acl.accesscontrols, function(obj) {
    			if (obj.name != '') {
    				accessList.push(obj.type + "~~~"+obj.name+ "~~~"+obj.domain+"~~~"+obj.permission);
    			}
    		});
    		
    		$scope.formAccessControlList = accessList.toString();
    	}, true);
    },
    FileShareSubDirCtrl: function($scope, $http, $window, translate) {
    	$scope.populateModal = function() {	
    	    $scope.$apply();
       }
    },
    AssociateProjectCtrl: function($scope, $http, $window, translate) {
    	
    	var resetModal = function() {
    		$scope.associateForm = {};
    		$scope.tenant = {};
    		$scope.project = {};
    	}
    	
    	$scope.populateModal = function(ids) {
    		
    		resetModal();
    		$scope.nasIds = ids;
    		$scope.projectOptions = [];
    		$scope.projectTenantOptions = [];
    		
            $http.get(routes.StorageSystems_getProjectsForNas()).success(function(data) {
            	$scope.projectTenantOptions = data;
            });
            $scope.getProjects = function(value){
            	
            	if (value) {
            		value = value.substring(1);
            		value = value.substring(0,value.length-1);
            	}
            	 var projects = value.split(",");
            	 var myNewOptions = [];
            	 for (var j = 0; j < projects.length; j++) {
                     var project = projects[j].split("~~~");
                    myNewOptions.push({ id: project[0], name: project[1] });
                 }
            	 $scope.projectOptions = myNewOptions;
            };
            
    	    $scope.$apply();
       }
    },
    NfsAclCtrl: function($scope, $http, $window, translate) {
    	
    	$scope.add = {type:'user', name:'', domain:'', permission:'read', permissionType:'allow'};
    	
    	$scope.typeOpt = [{id:'user', name:translate('resources.filesystem.acl.user')},
    	                 {id:'group', name:translate('resources.filesystem.acl.group')}];
    	
    	$scope.permissionTypeOpt =[{id:'allow', name:translate('resources.filesystem.nfsacl.allow')},
    	      	                 {id:'deny', name:translate('resources.filesystem.nfsacl.deny')}];

    	$scope.permOpt = [{id:'read', name:translate('resources.filesystem.nfsacl.read')}, 
    	                  {id:'write', name:translate('resources.filesystem.nfsacl.write')}, 
    	                  {id:'execute', name:translate('resources.filesystem.nfsacl.execute')}];
    	
    	var setData = function(data) {
    		$scope.acl = data;
    	}
    	
    	var resetModal = function() {
    		$scope.acl = {};
    	}
    	
    	$scope.populateModal = function() {
    		    resetModal();
    			$scope.acl.accesscontrols = [];
        		$scope.acl.accesscontrols.push(angular.copy($scope.add));
        		$scope.$apply();

    	}

    	$scope.deleteACE = function(idx) { $scope.acl.accesscontrols.splice(idx, 1); }
    	$scope.addACE = function() { $scope.acl.accesscontrols.push(angular.copy($scope.add)); }
    	
    	$scope.$watch('acl', function(newVal) {
    		var accessList = [];
    		angular.forEach($scope.acl.accesscontrols, function(obj) {
    			if (obj.name != '') {
    				var val = obj.type + "~~~"+obj.name+ "~~~"+obj.domain+"~~~"+obj.permission+"~~~"+obj.permissionType;
    				val =val.split(",").join("/")
    				accessList.push(val);
    			}
    		});
    		
    		$scope.formAccessControlList = accessList.toString();
    	}, true);
    },
    FileQuotaCtrl: function($scope, $http, $filter, translate) {
        $scope.securityOptions = [{id:"unix", name:translate('resources.filesystem.quota.security.unix')}, 
                                  {id:"ntfs", name:translate('resources.filesystem.quota.security.ntfs')},
                                  {id:"mixed", name:translate('resources.filesystem.quota.security.mixed')}];
        
        $scope.populateModal = function(id) {
            $scope.id = id;
            $scope.quota = {};
            var data = {params: { id: id}};
            $http.get(routes.FileSystems_fileSystemQuotaJson(), data).success(function(data) {
                $scope.quota.name = data.name;
                $scope.quota.securityStyle = data.securityStyle;
                $scope.quota.oplock = data.oplock
                $scope.quota.size = $filter('number')(data.quotaSize, 0);
            });
       }
    },
    ClusterCtrl: function ($scope, $http, $interval) {
        $scope.clusterUnstable = true;
        var LONG_POLLING=15000;
        var SHORT_POLLING=5000;
        
        var getClusterInfo = function() {
            $http.get(routes.Common_clusterInfoJson()).success(function(data) {
                if (data != null) {
                    $scope.clusterInfo = data;
                    $scope.clusterUnstable = !($scope.clusterInfo.currentState == "STABLE");
                }
            });
        }
        
        getClusterInfo();
        var clusterPoller = $interval(getClusterInfo,LONG_POLLING);
    }
});

angular.module("portalApp").directive('taskcard', function() {
    return {
        templateUrl: '/public/templates/taskcard.html'
    }
});

angular.module("portalApp").controller('taskController', function($rootScope, $scope, $timeout, $document, $http, $window) {
    $scope.tasks = {}
    $scope.tenantTasks = [];
    $scope.systemTasks = [];
    $scope.dataAvailable = false;

    $scope.dialogVisible = false;
    $scope.showTenantTasks = true;

    $scope.dataAvailable = false;
    $scope.numOfRunningTasks = -1;

    var SHORT_POLL_SECS = 5000;
    var LONG_POLL_SECS = 15000;

    var poll_timeout = LONG_POLL_SECS;

    var taskPoller;
    var countPoller;
    var taskPollerActive = false;

    var setCountPoller = function() {
        countPoller = $timeout(pollForCount, poll_timeout);
    }

    // Polls just for the count
    var pollForCount = function() {
        $http.get(routes.Tasks_activeTaskCount()).success(function(numberOfTasks) {
            $scope.numOfRunningTasks = numberOfTasks;
            setCountPoller();
        })
        .error(function(data, status) {
            console.log("Error fetching active task count " + status);
        });
    };

    var setTaskPoller = function() {
        if (taskPollerActive) {
            taskPoller = $timeout(pollForRecentTasks, poll_timeout);
        }
    }

    // Poll for the initial set of 5 most recent tasks
    var pollForRecentTasks = function() {
        $http.get(routes.Tasks_recentTasks()).
         success(function(tasks) {
            // Tasks come most recent first, so push onto list in reverse
            for (var i=tasks.length-1;i>=0;i--) {
                var task = tasks[i];

                var taskList = null;
                if (task.systemTask) {
                    taskList = $scope.systemTasks;
                }
                else {
                    taskList = $scope.tenantTasks;
                }

                var existingTask = findTaskInList(task, taskList);
                if (existingTask == null) {
                    taskList.unshift(task);
                    if (taskList.length > 5) {
                        taskList.splice(5, 1);
                    }
                }
                else {
                    existingTask.progress = task.progress;
                }
            }

            $scope.dataAvailable = true;
            setTaskPoller();
        })
        .error(function(data, status) {
            console.log("Error fetching recent tasks "+status);
        });
    };

    var stopTaskPoller = function() {
        $timeout.cancel(taskPoller);
        $timeout.cancel(countPoller);
        $scope.dataAvailable = false;
        poll_timeout = LONG_POLL_SECS;
        taskPollerActive = false;
        setCountPoller();
    }

    var startTaskPoller  = function() {
        $timeout.cancel(countPoller);
        taskPollerActive = true;
        poll_timeout = SHORT_POLL_SECS
        pollForRecentTasks();
        pollForCount();
    };

    // Poll for task counts
    (function() {
        pollForCount();
    })();

    var findTaskInList = function(task, list) {
        for (var i=0;i<list.length;i++) {
            if (list[i].id == task.id ) {
                return list[i]
            }
        }

        return null;
    }

    $scope.hideDialog = function() {
        $scope.dialogVisible = false;

        stopTaskPoller();
        taskDialogElement = undefined;
    }

    $scope.toggleTaskDialog = function(clickEvent) {
        if ($scope.dialogVisible) {
            $scope.hideDialog();
        }
        else {
            $scope.dialogVisible = true;
            taskDialogElement = $(clickEvent.target).parents("li").get(0)
            clickEvent.stopPropagation();
            startTaskPoller();
        }
    }

    $scope.showTaskDetails = function(taskId) {
        window.location =  routes.Tasks_taskDetails({taskId:taskId});
    }

    // Hides the dialog if page clicked outside of it
    var taskDialogElement;
    $document.on('click', function(event) {
        if ($scope.dialogVisible && taskDialogElement) {
            var isClickedElementChildOfTaskDialog = $(taskDialogElement).children().has(event.target).length > 0;

            if (isClickedElementChildOfTaskDialog) {
                return;
            }

            $scope.$apply(function() {
                $scope.hideDialog();
            });
        }
    }) ;

    $scope.resourceImage = function(resourceType) {
        switch(resourceType) {
            case "StorageSystem":
                return "StorageArray.png";
            case "StorageProvider":
                return "StorageArray.png";
            case "NetworkSystem":
                return "Switch.png";
            case "Volume":
                return "Volume.png";
            case "ProtectionSystem":
                return "Protection.png";
            case "Host":
                return "Host.png";
            case "Vcenter":
                return "VCenter.png";
            case "ExportGroup":
                return "Export.png";
            case "BlockSnapshot":
                return "Snapshot.png";
            case "ComputeSystem":
                return "Host.png";
            case "Cluster":
                return "Cluster.png";
            case "SysEvent":
                return "VDC.png";
            default:
                console.log("Unknown Resource Type "+resourceType);
                return "Folder.png";
        }
    }

    $scope.calculateTimeElapsed = function(start, end) {
        var elapsed;
        if (end == 0) {
            elapsed = moment(start).diff(moment(), "seconds");
        }
        else {
            elapsed = moment(start).diff(moment(end), "seconds");
        }

        return moment.duration(elapsed, "seconds").humanize();;
    }
});

angular.module("portalApp").controller('taskDetailsCtrl', function($scope, $timeout, $http, $window) {
    var getTaskDetails = function() {
        $http.get(routes.Tasks_taskDetailsJson({'taskId':$scope.task.id})).success(function (data) {
            $scope.task = data;

            if (!$scope.task.isComplete) {
                // Re-Check again in 15 seconds
                $timeout(getTaskDetails, 15000);
            }
        });
    }

    if (!$scope.task.isComplete) {
        $timeout(getTaskDetails, 15000);
    }

    $scope.viewOrder = function() {
        window.location = routes.Orders_receipt({'orderId':$scope.task.orderId});
    }
    $scope.getLocalDateTime = function(o,datestring){
    	return render.localDate(o,datestring);
    }
});
angular.module("portalApp").controller("summaryCountCtrl", function($scope, $http, $timeout, $window) {
    $scope.pending = 0;
    $scope.error = 0;
    $scope.ready = 0;
    $scope.dataReady = false;

    var poller = function() {
                $http.get(routes.Tasks_countSummary({tenantId:$scope.tenantId})).success(function(countSummary) {
                    console.log("Fetching Summary");
                    $scope.pending = countSummary.pending;
                    $scope.error = countSummary.error;
                    $scope.ready = countSummary.ready;
                    $scope.total = countSummary.pending + countSummary.error + countSummary.ready;
                    $scope.dataReady = true;

                    $timeout(poller, 5000);
                }).
                error(function(data, status) {
                    console.log("Error fetching countSummary "+status);
                    $timeout(poller, 5000);
                });
    };

    /**
     * Filters the DataTable by entering the filter value into the Datatable Filter Input box
     */
    $scope.filterTasks = function(state) {
        window.table.tasks.dataTable.getDataTable().fnFilter(state);
    }

    poller();

});

angular.module("portalApp").controller("vdcTaskCtrl", function($scope, $http, $timeout, $window, translate) {
    $scope.task = null;
    var poller = function() {
        $http.get(routes.Tasks_taskDetailsJson({taskId:$scope.taskId})).success(function(task) {
            $scope.task=task;
            $scope.inProgress = !task.isComplete;
            $scope.isError = task.isError;
            $scope.isSuccess = task.isComplete && !task.isError;
            
            var taskMessageKey;
            if (task.name) {
                taskMessageKey = 'vdc.task.' + task.name.replace(' ','') + '.message';
                $scope.taskStatusMessage = translate.raw(taskMessageKey);
            }
            
            if( !task.isComplete) {
                $timeout(poller, 5000);
            }
        }).
        error(function(data, status) {
            console.log("Error fetching task "+status);
            $timeout(poller, 5000);
        });
    };
    
    if ($scope.taskId) {
        poller();
    }

});

angular.module("portalApp").controller("storageProviderCtrl", function($scope) {
    $scope.editMode = ($scope.smisProvider.id) ? true : false;
    
    $scope.$watch("smisProvider.interfaceType", function(newVal, oldVal) {
        if (newVal !== oldVal) {
            $scope.updateInterfaceSettings(false);
        }
    });
    $scope.$watch("smisProvider.useSSL", function (newVal, oldVal) {
       if (newVal !== oldVal) {
           $scope.updateInterfaceSettings(true);
       } 
    });

    $scope.portMap = {};
    for (var i = 0; i < $scope.defaultStorageProviderPortMap.length; i++) {
        var defaultPort = $scope.defaultStorageProviderPortMap[i];
        $scope.portMap[defaultPort.id] = defaultPort.name;
    }

    /**
     * Set port number depending on useSSL selection.
     */
    $scope.updateInterfaceSettings = function(isSslCheckboxTarget) {
        var interfaceType = $scope.smisProvider.interfaceType;

        var useSSL = calculateUseSSL(interfaceType, isSslCheckboxTarget);
        if (useSSL) {
            interfaceType += "_useSSL";
        }

        if (!isSslCheckboxTarget || interfaceType === "ddmc_useSSL") {
            $scope.smisProvider.useSSL = useSSL;
        }

        $scope.smisProvider.portNumber = $scope.portMap[interfaceType];
    };

    function calculateUseSSL(interfaceType, isSslCheckboxTarget) {
        var isSslCheckboxChecked = isSslCheckboxTarget && $scope.smisProvider.useSSL;
        var isSslDefaultTypeSelected = !isSslCheckboxTarget && isSSLDefaultType(interfaceType);

        return interfaceType === "ddmc" || isSslCheckboxChecked || isSslDefaultTypeSelected;
    }

    function containsOption(value, options) {
        var found = false;
        angular.forEach(options, function(option) {
            if (option.id == value) {
                found = true;
            }
        });

        return found;
    }

    function isSSLDefaultType(storageProviderType) {
        return containsOption(storageProviderType, $scope.sslDefaultStorageProviderList);
    }

    $scope.isHideSSLCheckboxStorageSystem = function() {
        return containsOption($scope.smisProvider.interfaceType, $scope.nonSSLStorageSystemList);
    }

    $scope.isMDMDefaultType = function() {
        return containsOption($scope.smisProvider.interfaceType, $scope.mdmDefaultStorageProviderList);
    }
    
    $scope.isMDMOnlyType = function() {
    	return containsOption($scope.smisProvider.interfaceType, $scope.mdmonlyProviderList);
    }

    $scope.isElementManagerType = function() {
        return containsOption($scope.smisProvider.interfaceType, $scope.elementManagerStorageProviderList);
    }
});

angular.module("portalApp").controller("SystemLogsCtrl", function($scope, $http, $sce, $cookies, translate) {
    var LOGS_JSON = routes.SystemHealth_logsJson();
    var APPLY_FILTER = routes.SystemHealth_logs();
    var DOWNLOAD_LOGS = routes.SystemHealth_download();
    var SEVERITIES = {
        '4': 'ERROR',
        '5': 'WARN',
        '7': 'INFO',
        '8': 'DEBUG'
    };
    
    $scope.orderTypeOptions = [{id:'', name:translate("systemLogs.orderType.NONE")}];
    angular.forEach($scope.orderTypes, function(value) {
        this.push({id:value, name:translate("systemLogs.orderType."+value)});
    }, $scope.orderTypeOptions);
    
    $scope.nodeIdOptions = [{id:'', name:translate('system.logs.allnodes')}];
    angular.forEach($scope.controlNodes, function(value) {
        this.push({id:value.nodeId, name:value.nodeName + " (" + value.nodeId + ")"});
    }, $scope.nodeIdOptions);
    
    $scope.serviceOptions = [];
    angular.forEach($scope.allServices, function(value) {
        this.push({id:value, name:value});
    }, $scope.serviceOptions);
    
    $scope.severityOptions = [];
    angular.forEach(SEVERITIES, function(value, key) {
        this.push({id:key, name:value});
    }, $scope.severityOptions);

    $scope.descending = $cookies.sort === 'desc';
    $scope.toggleSort = function() {
        $scope.descending = !$scope.descending;
        $cookies.sort = ($scope.descending ? 'desc' : 'asc'); 
    };
    
    $scope.filter = {
        maxCount: 1000,
        startTime: $scope.startTime,
        severity: $scope.severity,
        nodeId: $scope.nodeId || '',
        service: $scope.service,
        searchMessage: $scope.searchMessage
    };
    $scope.$watchCollection('filter', function() {
        $scope.filterDialog = angular.extend({orderTypes: ''}, $scope.filter);
    });
    
    $scope.filterText = getFilterText();
    $scope.loading = false;
    $scope.error = null;

    $scope.moreLogs = function() {
        if ($scope.loading) {
            return;
        }
        var nextStartTime = getNextStartTime();
        if (nextStartTime) {
            var args = angular.extend(getFetchArgs(), { start: nextStartTime });
            fetchLogs(args);
        }
    };
    
    // Hooks for the filter/download dialog
    angular.element("#filter-dialog").on("show.bs.modal", function (event) {
        $scope.$apply(function() {
            var button = $(event.relatedTarget);
            var type = button.data('type');
            
            $scope.filterDialog.type = type;
            if (type == 'download') {                
                $scope.filterDialog.endTime = new Date().getTime();                
                $scope.filterDialog.endTime_date = getDate($scope.filterDialog.endTime);
                $scope.filterDialog.endTime_time = getTime($scope.filterDialog.endTime);
            }
            $scope.filterDialog.startTime_date = getDate($scope.filterDialog.startTime);
            $scope.filterDialog.startTime_time = getTime($scope.filterDialog.startTime);
           });
    });
    
    // Applies the filter from the dialog
    $scope.applyFilter = function() {
        angular.element('#filter-dialog').modal('hide');
        var args = {
            startTime: getDateTime($scope.filterDialog.startTime_date, $scope.filterDialog.startTime_time),
            severity: $scope.filterDialog.severity,
            nodeId: $scope.filterDialog.nodeId,
            service: $scope.filterDialog.service,
            searchMessage: $scope.filterDialog.searchMessage
        };
        var url = APPLY_FILTER + "?" + encodeArgs(args);
        window.location.href = url;
    };
    
    // Downloads the logs from the server
    $scope.downloadLogs = function() {
        angular.element('#filter-dialog').modal('hide');
        var args = {
            startTime: getDateTime($scope.filterDialog.startTime_date, $scope.filterDialog.startTime_time),
            endTime: getDateTime($scope.filterDialog.endTime_date, $scope.filterDialog.endTime_time),
            severity: $scope.filterDialog.severity,
            nodeId: $scope.filterDialog.nodeId,
            service: $scope.filterDialog.service,
            searchMessage: $scope.filterDialog.searchMessage,
            orderTypes: $scope.filterDialog.orderTypes
        };
        if ($scope.filterDialog.endTimeCurrentTime) {
            args.endTime = new Date().getTime();
        }
        var url = DOWNLOAD_LOGS + "?" + encodeArgs(args);
        window.open(url, "_blank");
    };
    
    $scope.getLocalDateTime = function(o,datestring){
    	return render.localDate(o,datestring);
    }
    
    // Fill the table with data
    fetchLogs(getFetchArgs());
    
    function getDate(millis) {
        return millis ? formatDate(millis, "YYYY-MM-DD") : "";
    }
    
    function getTime(millis) {
        return millis ? formatDate(millis, "HH:mm") : "";
    }
    
    function getDateTime(dateStr, timeStr) {
        if (dateStr && timeStr) {
            return moment(dateStr + " " + timeStr, "YYYY-MM-DD HH:mm").toDate().getTime();
        }
        return null;
    }
    
    // Constructs the filter text to display at the top of the page
    function getFilterText() {
        var services = $scope.filter.service.join(", ");
        var severity = SEVERITIES[$scope.filter.severity];
        var startTime = formatDate($scope.filter.startTime, 'YYYY-MM-DD HH:mm');
        return $sce.trustAsHtml(translate("systemLogs.filter.service", services, severity, startTime));
    }
    
    // Gets the next start time for fetching more logs
    function getNextStartTime() {
        if ($scope.logs && $scope.logs.length > 0) {
            return $scope.logs[$scope.logs.length - 1].time_ms + 1;
        }
        return undefined;
    }
    
    function encodeArgs(args) {
        var encoded = [];
        angular.forEach(args, function(value, key) {
            if (angular.isArray(value)) {
                angular.forEach(value, function(value) {
                    encoded.push(key+"="+encodeURIComponent(value));
                });
            }
            else if (value) {
                encoded.push(key+"="+encodeURIComponent(value));
            }
        });
        return encoded.join("&");
    }
    
    function getFetchArgs() {
        return {
            maxcount: $scope.filter.maxCount,
            start: $scope.filter.startTime,
            severity: $scope.filter.severity,
            node_id: $scope.filter.nodeId,
            log_name: $scope.filter.service,
            msg_regex: getSearchRegex($scope.filter.searchMessage)
        };
    }
    
    function getSearchRegex(message) {
        return message ? ("(?i).*" + message + ".*") : undefined;
    }
    
    function fetchLogs(args) {
        console.log("fetch args: "+JSON.stringify(args));
        $scope.loading = true;
        var params = { uri: "logs.json?" + encodeArgs(args) };
        return $http.get(LOGS_JSON, { params: params }).
            success(fetchSuccess).
            error(fetchError);
    }
    
    function fetchSuccess(data, status, headers, config) {
        $scope.loading = false;
        $scope.error = null;
        $scope.logs = ($scope.logs || []);
        
        angular.forEach(data, function(value) {
            // Ignore log messages with no time
            if (value.time_ms) {
                $scope.logs.push(value);
            }
        });
    }
    
    function fetchError(data, status, headers, config) {
        $scope.loading = false;
        $scope.error = data;
    }
});
