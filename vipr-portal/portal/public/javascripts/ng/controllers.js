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
    			if (obj.name != '' && (obj.name.indexOf("\\") == -1)) {
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
    DissociateProjectCtrl: function($scope, $http, $window, translate) {
    	
    	var resetModal = function() {
    		$scope.dissociateForm = {};
    		$scope.projectsToDissociate = {};
    	}
    	
    	$scope.populateModal = function(ids, nasIdString) {
    		
    		resetModal();
    		$scope.projectsToDissociateOptions = [];
    		
    		var myNewOptions = [];
    		var projects = ids.split(",");
    		
    		for(var i = 0; i < projects.length; i++) {
    			var projectInfo = projects[i].split("+");
    			myNewOptions.push({ id: projectInfo[1], name: projectInfo[0] });
    		}
            	
            $scope.projectsToDissociateOptions = myNewOptions;
            $scope.nasIds = nasIdString;
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
    	                  {id:'execute', name:translate('resources.filesystem.nfsacl.execute')},
    	                  {id:'fullControl', name:translate('resources.filesystem.nfsacl.fullControl')},];
    	
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
    BucketAclCtrl: function($scope, $http, $window, translate) {
    	
    	$scope.add = {type:'user', name:'', domain:'', permission:'read'};
    	
    	$scope.typeOpt = [{id:'user', name:translate('bucket.acl.user')},
    	                 {id:'group', name:translate('bucket.acl.group')},
    	                 {id:'customgroup', name:translate('bucket.acl.customgroup')}];
    	
    	
    	$scope.permOpt = [{id:'read', name:translate('resources.bucket.acl.read')}, 
    	                  {id:'write', name:translate('resources.bucket.acl.write')}, 
    	                  {id:'execute', name:translate('resources.bucket.acl.execute')},
    	                  {id:'full_control', name:translate('resources.bucket.acl.full_control')},
						  {id:'delete', name:translate('resources.bucket.acl.delete')},
						  {id:'none', name:translate('resources.bucket.acl.none')},
						  {id:'privileged_write', name:translate('resources.bucket.acl.privileged_write')},
						  {id:'read_acl', name:translate('resources.bucket.acl.read_acl')},
						  {id:'write_acl', name:translate('resources.bucket.acl.write_acl')}];
    	
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
    				var val = obj.type + "~~~"+obj.name+ "~~~"+obj.domain+"~~~"+obj.permission;
    				val =val.split(",").join("|")
    				accessList.push(val);
    			}
    		});
    		
    		$scope.formAccessControlList = accessList.toString();
    	}, true);
    },
    AssignPolicyCtrl: function($scope, $http, $window, translate) {
    	
    	var resetModal = function() {
    		$scope.policyOptions = [];
    	}
    	
    	$scope.populateModal = function() {
    		
    		resetModal();
    		
    		$http.get(routes.FileSystems_getScheculePolicies()).success(function(data) {
            	$scope.policyOptions = data;
            });
            
    	    $scope.$apply();
       }
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

    $scope.isSecretKeyProviderList = function() {
    	return containsOption($scope.smisProvider.interfaceType, $scope.secretKeyProviderList);
    }

    $scope.isElementManagerType = function() {
        return containsOption($scope.smisProvider.interfaceType, $scope.elementManagerStorageProviderList);
    }
});

angular.module("portalApp").controller("SystemLogsCtrl", function($scope, $http, $sce, $cookies, translate) {
    var LOGS_JSON = routes.SystemHealth_logsJson();
    var APPLY_FILTER = routes.SystemHealth_logs();
    var DOWNLOAD_LOGS = routes.SystemHealth_download();
    var DEFAULT_DOWNLOAD_SEVERITY = '8';
    var DEFAULT_DOWNLOAD_ORDER_TYPES = 'ALL';
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
                $scope.filterDialog.severity = DEFAULT_DOWNLOAD_SEVERITY;
                $scope.filterDialog.orderTypes = DEFAULT_DOWNLOAD_ORDER_TYPES;
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

angular.module("portalApp").controller("AuditLogCtrl", function($scope, $http, $sce, $cookies, translate) {
    var APPLY_FILTER = routes.AuditLog_list();
    var DOWNLOAD_LOGS = routes.AuditLog_download();
    var RESULT_STATUS = {
        '' : 'ALL STATUS',
        'S': 'SUCCESS',
        'F': 'FAILURE'
    };

    $scope.resultStatusOptions = [];
    angular.forEach(RESULT_STATUS, function(value, key) {
        this.push({id:key, name:value});
    }, $scope.resultStatusOptions);

    $scope.descending = $cookies.sort === 'desc';
    $scope.toggleSort = function() {
        $scope.descending = !$scope.descending;
        $cookies.sort = ($scope.descending ? 'desc' : 'asc');
    };

    $scope.filter = {
        startTime: $scope.startTime,
        resultStatus: $scope.resultStatus,
        serviceType: $scope.serviceType,
        user: $scope.user,
        keyword: $scope.keyword
    };
    $scope.$watchCollection('filter', function() {
        $scope.filterDialog = angular.extend({orderTypes: ''}, $scope.filter);
    });

    $scope.filterText = getFilterText();
    $scope.loading = false;
    $scope.error = null;

    // Hooks for the filter/download dialog
    angular.element("#filter-dialog").on("show.bs.modal", function (event) {
        $scope.$apply(function() {
            var button = $(event.relatedTarget);
            var type = button.data('type');

            $scope.filterDialog.type = type;
            if (type === 'download') {
                $scope.filterDialog.endTime = new Date().getTime();
                $scope.filterDialog.endTime_date = getDate($scope.filterDialog.endTime);
                $scope.filterDialog.endTime_time = getHour($scope.filterDialog.endTime);
            }
            $scope.filterDialog.startTime_date = getDate($scope.filterDialog.startTime);
            $scope.filterDialog.startTime_time = getHour($scope.filterDialog.startTime);
        });
    });

    // Applies the filter from the dialog
    $scope.applyFilter = function() {
        angular.element('#filter-dialog').modal('hide');
        var args = {
            startTime: getDateTime($scope.filterDialog.startTime_date, $scope.filterDialog.startTime_time),
            resultStatus: $scope.filterDialog.resultStatus,
            serviceType: $scope.filterDialog.serviceType,
            user: $scope.filterDialog.user,
            keyword: $scope.filterDialog.keyword
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
            resultStatus: $scope.filterDialog.resultStatus,
            serviceType: $scope.filterDialog.serviceType,
            user: $scope.filterDialog.user,
            keyword: $scope.filterDialog.keyword
        };
        if ($scope.filterDialog.endTimeCurrentTime) {
            args.endTime = new Date().getTime();
        }
        var url = DOWNLOAD_LOGS + "?" + encodeArgs(args);
        window.open(url, "_blank");
    };

    $scope.getLocalDateTime = function(o,datestring){
        return render.localDate(o,datestring);
    };

    function getDate(millis) {
        return millis ? formatDate(millis, "YYYY-MM-DD") : "";
    }

    function getHour(millis) {
        return millis ? formatDate(millis, "HH") : "";
    }

    function getDateTime(dateStr, timeStr) {
        if (dateStr && timeStr) {
            return moment(dateStr + " " + timeStr, "YYYY-MM-DD HH:mm").toDate().getTime();
        }
        return null;
    }

    // Constructs the filter text to display at the top of the page
    function getFilterText() {
        var status = RESULT_STATUS[$scope.filter.resultStatus];
        var startTime = formatDate($scope.filter.startTime, 'YYYY-MM-DD HH:00');
        return $sce.trustAsHtml(translate("auditLog.filter.filterText", status, startTime));
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

});

angular.module("portalApp").controller("ConfigBackupCtrl", function($scope) {
    var hint = 'AM and PM';
    var twicePerDay = '12hour';

    angular.element("#backup-time").ready(function () {
        $scope.$apply(function () {
            $scope.backup_startTime = getLocalTimeFromOffset($schedulerTimeOffset);
            $scope.backup_format = $backup_interval.val();
        });
    });

    angular.element("#backup-interval").change(function () {
        var $interval = $backup_interval.val();
        $scope.backup_format = $interval;
        withHint($interval);
        $scope.$apply();
    });

    $scope.$watch('backup_startTime', function (newVal, oldVal) {
        if (newVal === undefined || newVal.indexOf(hint) > -1) return;
        setOffsetFromLocalTime($scope.backup_startTime);
        if (typeof $backup_interval != 'undefined') {
            withHint($backup_interval.val());
        }
    });

    function getLocalTimeFromOffset(offset) {
        var chosenHour = parseInt(offset/100);
        var chosenMin = offset%100;
        var utcMoment = moment.utc({hour:chosenHour, minute: chosenMin});
        var localTime = utcMoment.local().format("HH:mm");
        return localTime;
    }

    function setOffsetFromLocalTime(localTime) {
        if ($scope.backup_startTime !== undefined &&
            $scope.backup_startTime.indexOf(hint) === -1) {
            var localMoment = moment(localTime, "HH:mm");
            var utcOffset = parseInt(moment.utc(localMoment.toDate()).format("HHmm"));
            var $backup_time = $("#backup_scheduler_time");
            $backup_time.val(utcOffset);
            checkForm();
        }
    }

    function withHint($interval) {
        if ($scope.backup_startTime !== undefined) {
            var time = $scope.backup_startTime;
            if (time.indexOf(hint) === -1 && $interval === twicePerDay) {
                var hour = getHour(time);
                if (hour >= 12) {
                    var newHour = (hour - 12 < 10 ? "0" : "") + (hour - 12);
                    $scope.backup_startTime = time.replace(hour, newHour) + '\t' + hint;
                }
                else {
                    $scope.backup_startTime = time + '\t' + hint;
                }
            }
            else if (time.indexOf(hint) > -1 && $interval !== twicePerDay) {
                $scope.backup_startTime = time.replace(hint, '').trim();

            }
        }
    }

    function getHour(time) {
        if (time) {
            var index = time.indexOf(":");
            var value = time.substring(0, index);
            return !isNaN(value) ? Number(value) : 0;
        }
        return 0;
    }
});

angular.module("portalApp").factory('GuideCookies', function($rootScope, $http, $state, $translate, DateTimeUtil) {
    //angular ngCookie, and cookieStore pre 1.4 do not allow setting expiration or retrieving persistent cookies.
    //This is mozilla cookie getter/setter https://developer.mozilla.org/en-US/docs/Web/API/Document/cookie
    return  {
        getItem: function (sKey) {
            if (!sKey) { return null; }
            return decodeURIComponent(document.cookie.replace(new RegExp("(?:(?:^|.*;)\\s*" + encodeURIComponent(sKey).replace(/[\-\.\+\*]/g, "\\$&") + "\\s*\\=\\s*([^;]*).*$)|^.*$"), "$1")) || null;
        },
        setItem: function (sKey, sValue, vEnd, sPath, sDomain, bSecure) {
            if (!sKey || /^(?:expires|max\-age|path|domain|secure)$/i.test(sKey)) { return false; }
            var sExpires = "";
            if (vEnd) {
                switch (vEnd.constructor) {
                    case Number:
                        sExpires = vEnd === Infinity ? "; expires=Fri, 31 Dec 9999 23:59:59 GMT" : "; max-age=" + vEnd;
                        break;
                    case String:
                        sExpires = "; expires=" + vEnd;
                        break;
                    case Date:
                        sExpires = "; expires=" + vEnd.toUTCString();
                        break;
                }
            }
            document.cookie = encodeURIComponent(sKey) + "=" + encodeURIComponent(sValue) + sExpires + (sDomain ? "; domain=" + sDomain : "") + (sPath ? "; path=" + sPath : "") + (bSecure ? "; secure" : "");
            return true;
        },
        removeItem: function (sKey, sPath, sDomain) {
            if (!this.hasItem(sKey)) { return false; }
            document.cookie = encodeURIComponent(sKey) + "=; expires=Thu, 01 Jan 1970 00:00:00 GMT" + (sDomain ? "; domain=" + sDomain : "") + (sPath ? "; path=" + sPath : "");
            return true;
        },
        hasItem: function (sKey) {
            if (!sKey) { return false; }
            return (new RegExp("(?:^|;\\s*)" + encodeURIComponent(sKey).replace(/[\-\.\+\*()]/g, "\\$&") + "\\s*\\=")).test(document.cookie);
        },
        keys: function () {
            var aKeys = document.cookie.replace(/((?:^|\s*;)[^\=]+)(?=;|$)|^\s*|\s*(?:\=[^;]*)?(?:\1|$)/g, "").split(/\s*(?:\=[^;]*)?;\s*/);
            for (var nLen = aKeys.length, nIdx = 0; nIdx < nLen; nIdx++) { aKeys[nIdx] = decodeURIComponent(aKeys[nIdx]); }
            return aKeys;
        }
    };
});

angular.module("portalApp").controller('wizardController', function($rootScope, $scope, $timeout, $document, $http, $q, $window) {


    cookieObject = {};
    cookieKey = "VIPR_START_GUIDE";
    requiredSteps = 2;
    landingStep = 3;
    maxSteps = 9;
    currentStep = 0;
    completedSteps = 0;
    guideVisible = false;
    guideDataAvailable = false;
    optionalStepComplete = false;

    $scope.checkGuide = function() {
        cookieObject = angular.fromJson(readCookie(cookieKey));
        if (cookieObject) {
            $scope.$parent.completedSteps = cookieObject.completedSteps;
            $scope.$parent.guideMode = cookieObject.guideMode;
            $scope.$parent.currentStep = cookieObject.currentStep;
            $scope.$parent.guideDataAvailable = true;
            $scope.$parent.guideVisible = cookieObject.guideVisible;
            $scope.$parent.optionalStepComplete=cookieObject.optionalStepComplete;
            $scope.$parent.maxSteps = maxSteps;

        }
        $scope.$parent.isMenuPinned = readCookie("isMenuPinned");
    }

    $scope.toggleGuide = function() {

        if ($scope.$parent.guideVisible) {
		    $scope.closeGuide();
        }
        else {
            if (window.location.pathname != '/security/logout') {
                $scope.$parent.guideVisible = true;
                $scope.$parent.guideMode='full';
                if ($scope.$parent.completedSteps <= requiredSteps || !completedSteps){
                    if ($window.location.pathname == '/setup/license') {
                        if ($scope.$parent.currentStep == 1) {return;};
                    }
                    if ($window.location.pathname == '/setup/index') {
                        if ($scope.$parent.currentStep == 2) {return;};
                    }
                }
                $scope.initializeSteps();
		    }
        }
    }

    $scope.closeGuide = function() {
        $scope.$parent.guideVisible = false;
        $scope.$parent.guideDataAvailable = false;
        saveGuideCookies();
    }

    $scope.initializeSteps = function() {

        $scope.$parent.currentStep = 1;
        $scope.$parent.completedSteps = 0;
        $scope.$parent.maxSteps = maxSteps;
        $scope.$parent.guideDataAvailable = false;
        $scope.$parent.optionalStepComplete = false;

        checkStep(1);

    }

    $scope.completeStep = function(step) {

        finishChecking = function(){
            $scope.$parent.guideMode='full';
            saveGuideCookies();
        }

		if (!step) {
            step = $scope.$parent.currentStep;
        }

        switch (step) {
            case 1:
                updateGuideCookies3(1, 2,'full');
                return;
                break;
            case 2:

                updateGuideCookies3(2, 3,'full');
                return;
                break;
            case landingStep:
                goToNextStep(true);
                finishChecking();
                break;
            case 4:
                $http.get(routes.StorageSystems_list()).then(function (data) {
                    finished=false;
                    if (data.data.aaData.length != 0) {
                        testId = function (ss) {
                            var promises = ss.map( function (s) {
                                if (!finished && s.discoveryStatus == "COMPLETE"){
                                    if(checkCookie("guide_storageArray")){
                                        finished=true;
                                        goToNextStep(true);
                                        finishChecking();
                                    } else {finishChecking();}
                                }
                            });
                            $q.all(promises).then(function () {
                                if(!finished) {
                                    finishChecking();
                                }
                            });
                        };
                        return testId(data.data.aaData);
                    } else {
                        finishChecking();
                    }
                });
                break;
            case 5:
                $http.get(routes.SanSwitches_list()).then(function (data) {
                    finished=false;
                    if (data.data.aaData.length != 0) {
                        testId = function (sanswitches) {
                            var promises = sanswitches.map( function (s) {
                                if (!finished && s.discoveryStatus == "COMPLETE"){
                                    finished=true;
                                    $scope.$parent.optionalStepComplete = true;
                                    goToNextStep(true);
                                    finishChecking();
                                }
                            });
                            $q.all(promises).then(function () {
                                if(!finished) {
                                    //$scope.$parent.optionalStepComplete = false;
                                    //goToNextStep(true);
                                    finishChecking();
                                }
                            });
                        };
                        return testId(data.data.aaData);
                    } else {
                        //$scope.$parent.optionalStepComplete = false;
                        //goToNextStep(true);
                        finishChecking();
                    }
                });
                break;
            case 6:
                $http.get(routes.VirtualArrays_list()).then(function (data) {
                    finished=false;
                    if (data.data.aaData.length != 0) {
                        testId = function (vArrays) {
                            var promises = vArrays.map( function(vArray) {
                            return $http.get(routes.VirtualArrays_pools({'id':vArray.id})).then(function (data,$q) {
                                    if (!finished && data.data.aaData.length != 0){
                                        if(checkCookie("guide_varray")){
                                            finished=true;
                                            goToNextStep(true);
                                            finishChecking();
                                        } else {finishChecking();}
                                    }
                                });
                            });
                            $q.all(promises).then(function () {
                                if(!finished) {
                                    finishChecking();
                                }
                            });
                        };
                        return testId(data.data.aaData);
                    } else {
                        finishChecking();
                    }
                });
                break;
            case 7:
                $http.get(routes.BlockVirtualPools_list()).then(function (data) {
                    finished=false;
                    if (data.data.aaData.length != 0) {
                        testId = function (vPools) {
                            var promises = vPools.map( function(vPool) {
                            return $http.get(routes.BlockVirtualPools_pools({'id':vPool.id})).then(function (data,$q) {

                                    if (!finished && data.data.aaData.length != 0){
                                        finished=true;
                                        goToNextStep(true);
                                        finishChecking();
                                    }
                                });
                            });
                            $q.all(promises).then(function () {
                                if(!finished) {
                                    finishChecking();
                                }
                            });
                        };
                        return testId(data.data.aaData);
                    } else {
                        finishChecking();
                    }
                });
                break;
            case 8:
                $http.get(routes.Projects_list()).then(function (data) {
                    if (data.data.aaData.length != 0) {

                        goToNextStep(true);
                        finishChecking();
                    } else {
                        finishChecking();
                    }
                });
                break;
            default:
                goToNextStep(true);
                finishChecking();
        }

    }

    goToNextStep = function(complete) {
        if(complete) {
            if ( $scope.$parent.currentStep>$scope.$parent.completedSteps){
                $scope.$parent.completedSteps=$scope.$parent.currentStep;
            }
            if ( $scope.$parent.currentStep<maxSteps){
                $scope.$parent.currentStep=$scope.$parent.currentStep+1;
            }
            else {
                $scope.$parent.currentStep=landingStep;
            }
        }
        $scope.$parent.guideMode='full';
        saveGuideCookies();
    }

    $scope.goToNextSteps = function(complete) {
       if(complete) {

           if ( $scope.$parent.currentStep>completedSteps){
                $scope.$parent.completedSteps=$scope.$parent.currentStep;
           }
           if ( $scope.$parent.currentStep<maxSteps){
               $scope.$parent.currentStep=$scope.$parent.currentStep+1;
           }
           else {
               $scope.$parent.currentStep=landingStep;
           }
       }
       $scope.$parent.guideMode='full';
       saveGuideCookies();
   }

    $scope.runStep = function(step) {


        if (!step) {
            step = $scope.$parent.currentStep;
        }

        switch (step) {
            case 1:
                $scope.$parent.currentStep = 1;
                $scope.$parent.guideMode = 'side';
                saveGuideCookies();
                if ($window.location.pathname != '/setup/license') {
                    $window.location.href = '/setup/license';
                }
                else {
                    $scope.$parent.currentStep=1;
                    $scope.$parent.guideMode='side';
                }
                break;
            case 2:
                $scope.$parent.currentStep = 2;
                $scope.$parent.guideMode = 'side';
                saveGuideCookies();
                if ($window.location.pathname != '/setup/index') {
                    $window.location.href = '/setup/index';
                }
                else {
                    $scope.$parent.currentStep=2;
                    $scope.$parent.guideMode='side';
                }
                break;
            case landingStep:
                break;
            case 4:
                updateGuideCookies(4,'side');
                if ($window.location.pathname != '/storagesystems/createAllFlash') {
                    $window.location.href = '/storagesystems/createAllFlash';
                }
                else {
                    $scope.$parent.currentStep=4;
                    $scope.$parent.guideMode='side';
                }
                break;
            case 5:
                updateGuideCookies(5,'side');
                if ($window.location.pathname != '/sanswitches/list') {
                    $window.location.href = '/sanswitches/list';
                }
                else {
                    $scope.$parent.currentStep=5;
                    $scope.$parent.guideMode='side';
                }
                break;
            case 6:
                updateGuideCookies(6,'side');
                if ($window.location.pathname != '/virtualarrays/defaultvarray') {
                    $window.location.href = '/virtualarrays/defaultvarray';
                }
                else {
                    $scope.$parent.currentStep=6;
                    $scope.$parent.guideMode='side';
                }
                break;
            case 7:
                updateGuideCookies(7,'side');
                if ($window.location.pathname != '/blockvirtualpools/createAllFlash') {
                    $window.location.href = '/blockvirtualpools/createAllFlash';
                }
                else {
                    $scope.$parent.currentStep=7;
                    $scope.$parent.guideMode='side';
                }
                break;
            case 8:
                updateGuideCookies(8,'side');
                if ($window.location.pathname != '/projects/list') {
                    $window.location.href = '/projects/list';
                }
                else {
                    $scope.$parent.currentStep=8;
                    $scope.$parent.guideMode='side';
                }
                break;
            case 9:
                updateGuideCookies4(9,9,'side',false);
                $window.location.href = '/Catalog#ServiceCatalog/AllFlashservices';
                break;
            default:
                updateGuideCookies(step,'side');
                $scope.$parent.currentStep=step;
                $scope.$parent.guideMode='side';
            }
    }

    $scope.showStep = function(step) {

            if (!step) {
                step = $scope.$parent.currentStep;
            }

            $scope.$parent.currentStep = step;
            saveGuideCookies();

        }

    $scope.nextStep = function(step) {

        if (!step) {
            step = $scope.$parent.currentStep;
        }

        switch (step) {
            case requiredSteps:
                $scope.$parent.currentStep = 4;
                saveGuideCookies();
                break;
            case maxSteps:
                $scope.$parent.currentStep = landingStep;
                saveGuideCookies();
                break;
            default:
                $scope.$parent.currentStep = step+1;
                saveGuideCookies();
                break;
            }
    }

        $scope.previousStep = function(step) {
            if (!step) {
                step = $scope.$parent.currentStep;
            }

            switch (step) {
                case requiredSteps+2:
                    $scope.$parent.currentStep = requiredSteps;
                    saveGuideCookies();
                    break;
                case 1:
                    $scope.$parent.currentStep = 3;
                    saveGuideCookies();
                    break;
                default:
                    $scope.$parent.currentStep = step-1;
                    saveGuideCookies();
                    break;
                }
        }

    $scope.toggleMode = function(mode) {
        $scope.$parent.guideMode = mode;
    }

    updateGuideCookies = function(currentStep,guideMode) {
        cookieObject = {};
        cookieObject.currentStep=currentStep;
        cookieObject.completedSteps=$scope.$parent.completedSteps;
        cookieObject.guideMode=guideMode;
        cookieObject.guideVisible=$scope.$parent.guideVisible;
        cookieObject.optionalStepComplete=$scope.$parent.optionalStepComplete;
        createCookie(cookieKey,angular.toJson(cookieObject),'session');
    }

    updateGuideCookies3 = function(completedSteps,currentStep,guideMode) {
        cookieObject = {};
        cookieObject.currentStep=currentStep;
        cookieObject.completedSteps=completedSteps;
        cookieObject.guideMode=guideMode;
        cookieObject.guideVisible=$scope.$parent.guideVisible;
        cookieObject.optionalStepComplete=$scope.$parent.optionalStepComplete;
        createCookie(cookieKey,angular.toJson(cookieObject),'session');
    }

    updateGuideCookies4 = function(completedSteps,currentStep,guideMode,guideVisible) {
        cookieObject = {};
        cookieObject.currentStep=currentStep;
        cookieObject.completedSteps=completedSteps;
        cookieObject.guideMode=guideMode;
        cookieObject.guideVisible=guideVisible;
        cookieObject.optionalStepComplete=$scope.$parent.optionalStepComplete;
        createCookie(cookieKey,angular.toJson(cookieObject),'session');
    }

    $scope.restartGuide = function () {
        $scope.$parent.guideDataAvailable = false;
        removeGuideCookies();
        $scope.initializeSteps();
    }

    removeGuideCookies = function() {
        eraseCookie(cookieKey);
        eraseCookie("guide_storageArray");
        eraseCookie("guide_fabric");
        eraseCookie("guide_vpool");
        eraseCookie("guide_varray");
        eraseCookie("guide_project");
    }

    saveGuideCookies = function() {
        cookieObject = {};
        cookieObject.currentStep=$scope.$parent.currentStep;
        cookieObject.completedSteps=$scope.$parent.completedSteps;
        cookieObject.guideMode=$scope.$parent.guideMode;
        cookieObject.guideVisible=$scope.$parent.guideVisible;
        cookieObject.optionalStepComplete=$scope.$parent.optionalStepComplete;
        createCookie(cookieKey,angular.toJson(cookieObject),'session');
    }

    testFunc = function() {
    }

    $scope.checkStep = function() {
        checkStep($scope.$parent.currentStep);
    }

    checkStep = function(step) {

        finishChecking = function(){
            $scope.$parent.guideDataAvailable = true;
            saveGuideCookies();
        }

        switch (step) {
            case 1:
                $http.get(routes.Setup_license()).then(function (data) {
                    isLicensed = data.data;
                    if (isLicensed == 'true') {
                        $scope.$parent.completedSteps = 1;
                        $scope.$parent.currentStep = 2;
                        return checkStep(2);
                    }  else {
                        finishChecking();
                    }
                });
                break;
            case 2:
                $http.get(routes.Setup_initialSetup()).then(function (data) {
                    isSetup = data.data;
                    if (isSetup == 'true') {
                        $scope.$parent.completedSteps = 2;
                        $scope.$parent.currentStep = 3;
                        return checkStep(4);
                    } else {
                        finishChecking();
                    }
                });
                break;
            case landingStep:
                return true;
                break;
            case 4:
                $http.get(routes.StorageSystems_list()).then(function (data) {
                    finished=false;
                    if (data.data.aaData.length != 0) {
                        testId = function (ss) {
                            var promises = ss.map( function (s) {
                                if (!finished && s.discoveryStatus == "COMPLETE"){
                                    if(checkCookie("guide_storageArray")){
                                        finished=true;
                                        $scope.$parent.completedSteps = 4;
                                        return checkStep(5);
                                    } else {finishChecking();}
                                }
                            });
                            $q.all(promises).then(function () {
                                if(!finished) {
                                    finishChecking();
                                }
                            });
                        };
                        return testId(data.data.aaData);
                    } else {
                        finishChecking();
                    }
                });
                break;
            case 5:
                $http.get(routes.SanSwitches_list()).then(function (data) {
                    finished=false;
                    if (data.data.aaData.length != 0) {
                        testId = function (sanswitches) {
                            var promises = sanswitches.map( function (s) {
                                if (!finished && s.discoveryStatus == "COMPLETE"){
                                    finished=true;
                                    $scope.$parent.completedSteps = 5;
                                    $scope.$parent.optionalStepComplete = true;
                                    return checkStep(6);
                                }
                            });
                            $q.all(promises).then(function () {
                                if(!finished) {
                                    //$scope.$parent.optionalStepComplete = false;
                                    //return checkStep(6);
                                    finishChecking();
                                }
                            });
                        };
                        return testId(data.data.aaData);
                    } else {
                        //$scope.$parent.optionalStepComplete = false;
                        //return checkStep(6);
                        finishChecking();
                    }
                });
                break;
            case 6:
                $http.get(routes.VirtualArrays_list()).then(function (data) {
                    finished=false;
                    if (data.data.aaData.length != 0) {
                        testId = function (vArrays) {
                            var promises = vArrays.map( function(vArray) {
                            return $http.get(routes.VirtualArrays_pools({'id':vArray.id})).then(function (data,$q) {
                                    if (!finished && data.data.aaData.length != 0){
                                        if(checkCookie("guide_varray")){
                                            finished=true;
                                            $scope.$parent.completedSteps = 6;
                                            return checkStep(7);
                                        } else {finishChecking();}
                                    }
                                });
                            });
                            $q.all(promises).then(function () {
                                if(!finished) {
                                    finishChecking();
                                }
                            });
                        };
                        return testId(data.data.aaData);
                    } else {
                        finishChecking();
                    }
                });
                break;
            case 7:
                $http.get(routes.BlockVirtualPools_list()).then(function (data) {
                    finished=false;
                    if (data.data.aaData.length != 0) {
                        testId = function (vPools) {
                            var promises = vPools.map( function(vPool) {
                            return $http.get(routes.BlockVirtualPools_pools({'id':vPool.id})).then(function (data,$q) {

                                    if (!finished && data.data.aaData.length != 0){
                                        finished=true;
                                        $scope.$parent.completedSteps = 7;
                                        return checkStep(8);
                                    }
                                });
                            });
                            $q.all(promises).then(function () {
                                if(!finished) {
                                    finishChecking();
                                }
                            });
                        };
                        return testId(data.data.aaData);
                    } else {
                        finishChecking();
                    }
                });
                break;
            case 8:
                $http.get(routes.Projects_list()).then(function (data) {
                    if (data.data.aaData.length != 0) {
                        $scope.$parent.completedSteps = 8;
                        return checkStep(9);
                    } else {
                        finishChecking();
                    }
                });
                break;
            case maxSteps:
                $scope.$parent.completedSteps = maxSteps;
                finishChecking();
                break;
        }

    }
    $scope.getSummary = function() {

        $scope.guide_storageArray = "Not Complete";
        $scope.guide_varray = "Not Complete";
        $scope.guide_vpool = "Not Complete";
        $scope.guide_fabric = "Not Complete";
        $scope.guide_project = "Not Complete";

        arrayCookie = readCookie("guide_storageArray");
        if (arrayCookie) {
            $scope.guide_storageArray = arrayCookie.replace(/\"/g,'');
        }
        else if ($scope.completedSteps > 3){
            $scope.guide_storageArray = "Skipped";
        }
        varrayCookie = readCookie("guide_varray");
        if (varrayCookie) {
            $scope.guide_varray = varrayCookie;
        }
        else if ($scope.completedSteps > 5){
            $scope.guide_varray = "Skipped";
        }
        vpoolCookie = readCookie("guide_vpool");
        if (vpoolCookie) {
            $scope.guide_vpool = vpoolCookie;
        }
        else if ($scope.completedSteps > 6){
            $scope.guide_varray = "Skipped";
        }
        fabricCookie = readCookie("guide_fabric");
        if (fabricCookie) {
            $scope.guide_fabric = fabricCookie.replace(/\"/g,'');;
        }
        else if ($scope.completedSteps > 4){
            $scope.guide_fabric = "Skipped";
        }
        projectCookie = readCookie("guide_project");
        if (projectCookie) {
            $scope.guide_project = projectCookie;
        }
        else if ($scope.completedSteps > 7){
            $scope.guide_project = "Skipped";
        }
    }

    checkCookie = function(cookie) {
        cookieObject = readCookie(cookie);

        if (cookieObject) {
            return true;
        }
        return false;
    }
});