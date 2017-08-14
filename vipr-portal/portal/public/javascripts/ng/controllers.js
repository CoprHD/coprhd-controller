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

        $scope.disablePlacementPolicy = function(value) {
        	/*
        	 * When value does not belong to nonequalList or belongs to equalList, 
        	 * ie. value is not XTREMIO, VMAX, Unity, VnxBlock or value is "vplex_local","vplex_distributed","rp", "srdf"
        	 */
        	var nonequalList = ["vmax","vnxblock","xtremio","NONE","unity"];
        	var equalList = ["vplex_local","vplex_distributed","rp", "srdf"];
        	if((nonequalList.indexOf(value)==-1)||(equalList.indexOf(value)!=-1)) {
        		$('#vpool_placementPolicy').val('0').prop('selected',true);
        		$('#vpool_placementPolicy').change();
        	}
        	
        }
        
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
        
        // Set default values for scheduler
        $scope.scheduler = []
        $scope.scheduler.cycleFrequency = 1;
        $scope.scheduler.cycleType = "DAILY";
        $scope.scheduler.rangeOfRecurrence = 10;
        $scope.scheduler.recurrence = 1;
        $scope.scheduler.dayOfWeek = 1
        $scope.scheduler.dayOfMonth = 1
        current = new Date().getTime();                
        $scope.scheduler.startDate = formatDate(current, "YYYY-MM-DD");
        $scope.scheduler.startTime = formatDate(current, "HH:mm");;
        $scope.scheduler.maxNumOfCopies = 5;
        $scope.scheduler.currentTimezoneOffsetInMins = new Date().getTimezoneOffset();
        
        $scope.isSchedulerEnabled = function() {
           return $scope.schedulerEnabled;
        };
        
        $scope.isModalEnabled = function() {
            return $scope.serviceDescriptor.useModal;
         };
        
        $scope.isRecurring = function() {
           return $scope.isRecurringAllowed() && $scope.scheduler.recurrence != 1;
        };
        
        $scope.isRecurringAllowed = function() {
           return $scope.service.recurringAllowed;
        };
        
        $scope.isAutomaticExpirationAllowed = function() {
           var isSnapshotService = ['CreateBlockSnapshot', 'CreateFileSnapshot', 'CreateFullCopy', 
                 'CreateSnapshotOfApplication', 'CreateCloneOfApplication'].indexOf($scope.service.baseService) > -1;
           return $scope.scheduler.recurrence != 1 && isSnapshotService;	
        }
        
        $scope.enableScheduler = function() {
           // intialize data time picker if necessary
           setTimeout(function() {
              $('div.bfh-datepicker').each(function () {
                 var $datepicker = $(this)
                 $datepicker.bfhdatepicker($datepicker.data())
               })}, 0);
        };
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

            // if any field in serviceForm is invalid like max number of copies
            result = ! $scope.serviceForm.$valid;

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
        };
        $scope.dismissAssetError = function() {
            $scope.$root.assetError = undefined;
        }
        $scope.showModalDialog = function() {
            $('#serviceModal').modal({backdrop: 'static', keyboard: false});
            $scope.enableModalFields();
        }
        $scope.hideModalDialog = function() {
            $('#serviceModal').modal('hide');
            $scope.dismissAssetError();
            $scope.disableModalFields();
        }
        $scope.disableModalButton = function() {
            if ($scope.$root.errorCount > 0) {
                return true
            }
            return false;
        };
        $scope.enableModalFields = function() {
            $scope.updateModalFields = true;
        };
        $scope.disableModalFields = function() {
            $scope.updateModalFields = false;
        };
    },
    
    FileRessourceCtrl: function($scope, $http, $window, translate) {
       $scope.edit = false;
       $scope.isFsOnIsilon = false;
       $scope.rule = {};
       $scope.add = {endpoint:'', permission:'ro'};
       
       $scope.secOpt = [{id:'sys', name:translate('resources.filesystem.export.security.sys')},
                        {id:'krb5', name:translate('resources.filesystem.export.security.krb5')},
                        {id:'krb5p', name:translate('resources.filesystem.export.security.krb5p')},
                        {id:'krb5i', name:translate('resources.filesystem.export.security.krb5i')}];

       $scope.permOpt = [{id:'ro', name:translate('resources.filesystem.export.permission.ro')}, 
                         {id:'rw', name:translate('resources.filesystem.export.permission.rw')}, 
                         {id:'root', name:translate('resources.filesystem.export.permission.root')}];
       
       $scope.$watch('fsId', function () {
    	   $http.get(routes.FileSystems_getStorageSystemJson({id:$scope.fsId})).success(function(data) {             	            	 
               if ( data.systemType == "isilon" ) {
            	   $scope.isFsOnIsilon = true; 
               }
           });
       });
       
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
                     // Set the title as Modify rule
                     $scope.ruleTitle=translate('resources.filesystem.export.modify');
                     var data = {params: { id: id, path: path, sec: sec} };
                     if (window.location.pathname.indexOf("resources.filesnapshots") > -1) {
                           $http.get(routes.FileSnapshots_fileSnapshotExportsJson(), data).success(setData);
                     } else {
                           $http.get(routes.FileSystems_fileSystemExportsJson(), data).success(setData);
                     }
              } else {
                     // Set the title as Add rule
                     $scope.ruleTitle=translate('resources.filesystem.export.addExportRule');
                     $scope.rule.anon = "root";
                     $scope.rule.endpoints = [];
                     $scope.rule.endpoints.push(angular.copy($scope.add));
                     $scope.$apply();
              }
       }

       function isMatch(value, values) {
            if (!values) {
                return false;
            }
            if (!$.isArray(values)) {
                values = new String(values).split(",");
            }
            return $.inArray(value, values) > -1;
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
              
              $scope.rule.security = newVal.security;
              $scope.rule.anon = newVal.anon;
              $('#rule_security').find(':checkbox').each(function() {
                     $(this).prop("checked", isMatch($(this).val(),$scope.rule.security));
              });
              $scope.rule.subDir = newVal.subDir;
              $scope.ro = ro.toString();
              $scope.rw = rw.toString();
              $scope.root = root.toString();
       }, true);
    },
    filePolicyUnassignCtrl: function($scope, $http, $window, translate) {
        $scope.topologies = []       
        $http.get(routes.VirtualArrays_list()).success(function(data) {
        	$scope.virtualArrayOptions = data.aaData;
        });  
        $scope.$watch('policyId', function () {
            $http.get(routes.FileProtectionPolicy_details({id:$scope.policyId})).success(function(data) {             	            	 
                if ( (typeof data.replicationSettings != 'undefined') &&  (typeof data.replicationSettings.replicationTopologies != 'undefined') ) {
                    var protectionPolicyJson = data.replicationSettings.replicationTopologies;
                    angular.forEach(protectionPolicyJson, function(topology) {
                        var source =topology.sourceVArray.id.toString();
                        //for now api support only one target for each source.
                        var target=  topology.targetVArrays[0].id.toString();       	           
                        var topo = {sourceVArray:source, targetVArray:target};
                        $scope.topologies.push(angular.copy(topo));   
                    });
                }
            });
        });
     },
    
    filePolicyCtrl: function($scope, $http, $window, translate) {
        $scope.add = {sourceVArray:'', targetVArray:''};
        $scope.topologies = []
        $scope.deleteTopology = function(idx) { $scope.topologies.splice(idx, 1); }
        $scope.addTopology = function() { $scope.topologies.push(angular.copy($scope.add)); }
        
        $scope.populateVarray = function(selected) { 
            $http.get(routes.FileProtectionPolicy_getVarraysAssociatedWithPools({id:selected.value})).success(function(data) {     		 
            $scope.virtualArrayOptions = data;
        		
        });
       }
        $scope.$watch('policyId', function () {
        	
           $http.get(routes.FileProtectionPolicy_getVpoolForProtectionPolicy({id:$scope.policyId})).success(function(data) { 
              	$scope.vPoolOptions = data;
             });
        	
        	
            $http.get(routes.FileProtectionPolicy_details({id:$scope.policyId})).success(function(data) {             	            	 
                if ( (typeof data.replicationSettings != 'undefined') &&  (typeof data.replicationSettings.replicationTopologies != 'undefined') ) {
                    var protectionPolicyJson = data.replicationSettings.replicationTopologies;
                    angular.forEach(protectionPolicyJson, function(topology) {
                        var source =topology.sourceVArray.id.toString();
                        //for now api support only one target for each source.
                        var target=  topology.targetVArrays[0].id.toString();       	           
                        var topo = {sourceVArray:source, targetVArray:target};
                        $scope.topologies.push(angular.copy(topo));   
                    });
                }
            });
                                                                                            
        });   
        $scope.$watch('topologies', function(newVal) {
        	$scope.topologiesString = angular.toJson($scope.topologies, false);
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
    		$scope.targetVarrayOptions = [];
    	}
    	
    	$scope.populateModal = function() {
    		
    		resetModal();
    		
    		$http.get(routes.FileSystems_getScheculePolicies()).success(function(data) {
            	$scope.policyOptions = data;
            });
            
            $http.get(routes.FileSystems_getTargetVArrys()).success(function(varrays) {
            	$scope.targetVarrayOptions = varrays;
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
    },
    PolicyAsignVPol: function($scope,$http, $interval){
    	$scope.addVArray= function(){
    		var item = schome.vArray.length+1;
    		$scope.vArray.push({'id':'vArray'+item});
    	};
    	
    	$scope.removeVArray= function(){
    		var lastItem= $scope.vArray.length-1;
    		$scope.choices.splice(lastItem);
    	};
    	
    	console.log("Registring policy controller"+$scope.val());
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

angular.module("portalApp").controller('eventController', function($rootScope, $scope, $timeout, $document, $http, $window) {
    $scope.numOfPendingAndFailedEvents = -1;

    var SHORT_POLL_SECS = 5000;
    var LONG_POLL_SECS = 15000;

    var poll_timeout = LONG_POLL_SECS;

    var countPoller;

    var setCountPoller = function() {
        countPoller = $timeout(pollForCount, poll_timeout);
    }

    // Polls just for the count
    var pollForCount = function() {
        $http.get(routes.Events_pendingAndFailedEventCount()).success(function(numberOfEvents) {
            $scope.numOfPendingAndFailedEvents = numberOfEvents;
            setCountPoller();
        })
        .error(function(data, status) {
            console.log("Error fetching pending and failed event count " + status);
        });
    };

    // Poll for event counts
    (function() {
        pollForCount();
    })();
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

angular.module("portalApp").controller('eventDetailsCtrl', function($scope, $timeout, $http, $window) {
    var getEventDetails = function() {
        $http.get(routes.Events_eventDetailsJson({'eventId':$scope.task.id})).success(function (data) {
            $scope.event = data;
        });
    }
    $scope.getLocalDateTime = function(o,datestring){
        return render.localDate(o,datestring);
    };
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

angular.module("portalApp").controller("summaryEventCountCtrl", function($scope, $http, $timeout, $window) {
    $scope.pending = 0;
    $scope.approved = 0;
    $scope.declined = 0;
    $scope.failed = 0;
    $scope.dataReady = false;

    var poller = function() {
                $http.get(routes.Events_countSummary({tenantId:$scope.tenantId})).success(function(countSummary) {
                    console.log("Fetching Summary");
                    $scope.pending = countSummary.pending;
                    $scope.approved = countSummary.approved;
                    $scope.declined = countSummary.declined;
                    $scope.failed = countSummary.failed;
                    $scope.total = countSummary.pending + countSummary.approved + countSummary.declined + countSummary.failed;
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
    $scope.filterEvents = function(state) {
        window.table.events.dataTable.getDataTable().fnFilter(state);
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
    
    $scope.isProviderXIV = function() {
    	var interfaceType = $scope.smisProvider.interfaceType;
    	if (interfaceType == "ibmxiv") {
    		$('#smisProvider_useSSLControlGroup').find('input').attr('disabled', true);
    		$('input[name="smisProvider.useSSL"]').removeAttr('disabled');
    	}
    }
});

angular.module("portalApp").controller("SystemLogsCtrl", function($scope, $http, $sce, $cookies, translate, $interval) {
    var LOGS_JSON = routes.SystemHealth_logsJson();
    var APPLY_FILTER = routes.SystemHealth_logs();
    var DOWNLOAD_LOGS = routes.SystemHealth_download();
    var COLLECT_DIAGUTIL = routes.SystemHealth_collectDiagutilData();
    var GET_DIAGUTIL_STATUS = routes.SystemHealth_getDiagutilsStatus();
    var CANCEL_DIAGUTIL_JOB = routes.SystemHealth_cancelDiagutilJob();
    var DOWNLOAD_DIAGUTIL = routes.SystemHealth_downloadDiagutilData();
    var DEFAULT_DOWNLOAD_SEVERITY = '8';
    var DEFAULT_DOWNLOAD_ORDER_TYPES = 'ALL';
    var DEFAULT_DOWNLOAD_FTPS = 'ftp';
    var isMsgPopedUp = false;
    var diagutilStatus = '';
    var SEVERITIES = {
        '4': 'ERROR',
        '5': 'WARN',
        '7': 'INFO',
        '8': 'DEBUG'
    };
    var FTPS = {
    	'download': 'None',
        'ftp': 'FTP',
        'sftp': 'SFTP'
    }
    
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
    
    $scope.diagnosticOptions = [];
    angular.forEach($scope.allDiagnosticOptions, function(value, key) {
        this.push({id:value, name:key});
    }, $scope.diagnosticOptions);
    
    $scope.ftpOptions = [];
    angular.forEach(FTPS, function(value, key) {
        this.push({id:key, name:value});
    }, $scope.ftpOptions);

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
                $scope.diagnostic.type = 1;
                $scope.diagnostic.ftp = DEFAULT_DOWNLOAD_FTPS;
            }
            $scope.filterDialog.startTime_date = getDate($scope.filterDialog.startTime);
            $scope.filterDialog.startTime_time = getTime($scope.filterDialog.startTime);
           });
    });
    
    // Applies the filter from the dialog
    $scope.applyFilter = function() {
        angular.element('#filter-dialog').modal('hide');
        isMsgPopedUp = false;
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
    	if (confirm(translate('diagnostic.msg.collect.done'))) {
            console.info("yes");
        } else{
        	console.info("no");
        	return;
        }
        angular.element('#filter-dialog').modal('hide');
        isMsgPopedUp = false;
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

    //collect diagutil Data
   $scope.uploadDiagutilData = function() {
        isMsgPopedUp = false;
        var args = {
            options: $scope.diagnostic.options,
            nodeId: $scope.filterDialog.nodeId,
            services: $scope.filterDialog.service,
            severity: $scope.filterDialog.severity,
            searchMessage: $scope.filterDialog.searchMessage,
            startTime: getDateTime($scope.filterDialog.startTime_date, $scope.filterDialog.startTime_time),
            endTime: getDateTime($scope.filterDialog.endTime_date, $scope.filterDialog.endTime_time),
            orderType: $scope.filterDialog.orderTypes,
            ftpType: $scope.diagnostic.ftp,
            ftpAddr: $scope.diagnostic.url,
            userName: $scope.diagnostic.user,
            password: $scope.diagnostic.pw
            };
        if ($scope.filterDialog.endTimeCurrentTime) {
            args.endTime = new Date().getTime();
        }
        var url = COLLECT_DIAGUTIL + "?" + encodeArgs(args);
        $http.get(url).success(function (result) {
            //disable button here maybe?
        });

        //window.location.href = url;
    };
     var updateDiagutilStatus = function() {
        $http.get(GET_DIAGUTIL_STATUS).success( function (diagutilInfo) {
        console.log("diagutilsInfo status " + diagutilInfo.status + " desc is: " + diagutilInfo.desc);
        $scope.placeholder = diagutilInfo.desc;
        diagutilStatus = diagutilInfo.status;
        if(diagutilInfo.status == 'COLLECTING_SUCCESS' || diagutilInfo.status == 'DOWNLOAD_ERROR') {
            if (diagutilInfo.node != "" && diagutilInfo.location != "" && !isMsgPopedUp) {
            triggerDownload(diagutilInfo.status, diagutilInfo.nodeId, diagutilInfo.location);
            }
        }
        //$scope.diagnostic.status = diagutilInfo.status;
        //angular.element("#diagutilStatus").text = diagutilInfo.desc;
        });
        };
     $interval(updateDiagutilStatus, 3000);
    //
    //setInterval(updateDiagutilStatus, 3000);

    $scope.cancelDiagutilJob = function() {
        $http.get(CANCEL_DIAGUTIL_JOB);
    };

    
    $scope.getLocalDateTime = function(o,datestring){
    	return render.localDate(o,datestring);
    }
    
    // Fill the table with data
    fetchLogs(getFetchArgs());

    $scope.isDiagutilJobRunning = function() {
        if (diagutilStatus == "PRECHECK_ERROR" || diagutilStatus == "COLLECTING_ERROR"
        || diagutilStatus == "UPLOADING_ERROR" || diagutilStatus == "DOWNLOAD_ERROR"
        || diagutilStatus == "UNEXPECTED_ERROR" || diagutilStatus == "COMPLETE") {
        return false;
        }
        return true;
    };

    function triggerDownload(status, nodeId, fileName) {
        console.log("About to trigger download");
        if (status == 'DOWNLOAD_ERROR' ) { //another download session could pick up already collected data
            isMsgPopedUp = true;
            if (confirm(translate('diagnostic.msg.collect.done'))) {
                console.info("yes");
            } else{
                console.info("no");
                return;
            }
        }
        var args = {
            nodeId: nodeId,
            fileName: fileName
        };
        var url = DOWNLOAD_DIAGUTIL + "?" + encodeArgs(args);
        window.open(url, "_blank");
/*        $http.get(DOWNLOAD_DIAGUTIL).success( function(result) {
            //mark download complete status
        })
        .error(function() {
            //make download_error status in zk
        });  */


    }
    
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
        return message ? ("(?i).*" + replaceSpace(message) + ".*") : undefined;
    }

    function replaceSpace(searchMessage) {
        return searchMessage.split(" ").join("+");
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
        // For log collecting error, show warning instead of error
        if ($scope.error.code === 30070) {
            $("#log_info_box").removeClass("alert-danger").addClass("alert-warning");
        }
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
        if (newVal === undefined) return;
        setOffsetFromLocalTime($scope.backup_startTime, $backup_interval.val());
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

    function setOffsetFromLocalTime(localTime, $interval) {
        if ($scope.backup_startTime !== undefined) {
            var localMoment = moment(localTime, "HH:mm");
            var utcOffset = parseInt(moment.utc(localMoment.toDate()).format("HHmm"));
            if ($interval === twicePerDay) {
                utcOffset = (utcOffset >= 1200) ? (utcOffset - 1200) : utcOffset;
            }
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

angular.module("portalApp").controller("MyOrdersCtrl", function ($scope) {
    var ORDER_MY_LIST = routes.Order_list();
    console.info($scope);
    var dateFormat = "YYYY-MM-DD";

    var dateDaysAgo = $scope.dateDaysAgo;
    var startDate = $scope.startDate;
    var endDate = $scope.endDate;
    var current = new Date().getTime();

    angular.element("#orderSelector").ready(function () {
        $scope.$apply(function () {
            $scope.rangeStartDate = startDate != null ? startDate : formatDate(dateDaysAgo, dateFormat);
            $scope.rangeEndDate = endDate != null ? endDate : formatDate(current, dateFormat);
        });
    });

    angular.element("#endDatePicker").on("change", "input[type=text]", function (e) {
        var newEndVal = angular.element("#endDatePicker").find("input[type=text]").val();
        console.info("vals on change: " + $scope.rangeStartDate + "\t|" +
        $scope.rangeEndDate + "\t" + newEndVal);
        if (newEndVal < $scope.rangeStartDate) {
            alert("The End Date must be not earlier than the Start Date, please re-select.");
            return;
        } else {
            var url = ORDER_MY_LIST + "?startDate=" + encodeURIComponent($scope.rangeStartDate) +
                "&endDate=" + encodeURIComponent(newEndVal) +
                "&offsetInMinutes=" + getTimeZoneOffset();
            $('.bfh-datepicker-toggle input').attr("readonly", true);
            $('date-picker').click(false);

            console.info(url);
            window.location.href = url;
        }
    });
});

angular.module("portalApp").controller("AllOrdersCtrl", function ($scope) {
    var ORDER_ALL_ORDERS = routes.Order_allOrders();
    console.info($scope);
    var dateFormat = "YYYY-MM-DD";

    var dateDaysAgo = $scope.dateDaysAgo;
    var startDate = $scope.startDate;
    var endDate = $scope.endDate;
    var current = new Date().getTime();

    angular.element("#orderSelector").ready(function () {
        $scope.$apply(function () {
            $scope.rangeStartDate = startDate != null ? startDate : formatDate(dateDaysAgo, dateFormat);
            $scope.rangeEndDate = endDate != null ? endDate : formatDate(current, dateFormat);
        });
    });

    angular.element("#endDatePicker").on("change", "input[type=text]", function (e) {
        var newEndVal = angular.element("#endDatePicker").find("input[type=text]").val();
        console.info("vals on change: " + $scope.rangeStartDate + "\t|" +
        $scope.rangeEndDate + "\t" + newEndVal);
        if (newEndVal < $scope.rangeStartDate) {
            alert("The End Date must be not earlier than the Start Date, please re-select.");
            return;
        } else {
            var url = ORDER_ALL_ORDERS + "?startDate=" + encodeURIComponent($scope.rangeStartDate) +
                "&endDate=" + encodeURIComponent(newEndVal) +
                "&offsetInMinutes=" + getTimeZoneOffset();
            $('.bfh-datepicker-toggle input').attr("readonly", true);
            $('date-picker').click(false);

            console.info(url);
            window.location.href = url;
        }
    });
});

angular.module("portalApp").controller("schedulerEditCtrl", function($scope) {
    $scope.pad = function(number) {
       return (number < 10 ? '0' : '') + number
    }
    
    $scope.scheduler.currentTimezoneOffsetInMins = new Date().getTimezoneOffset();
    dateStr = $scope.scheduler.startDate + "T" + $scope.scheduler.startTime + ":00Z"
    startDateTime = new Date(dateStr);
    $scope.scheduler.startDate = startDateTime.getFullYear() + "-" + $scope.pad(startDateTime.getMonth() + 1) + "-" + $scope.pad(startDateTime.getDate());
    $scope.scheduler.startTime = $scope.pad(startDateTime.getHours()) + ":" + $scope.pad(startDateTime.getMinutes());
    
    $scope.isSchedulerEnabled = function() {
       return true;
    };
    
    $scope.isRecurring = function() {
       return $scope.isRecurringAllowed() && $scope.scheduler.recurrence != 1;
    };
    
    $scope.isRecurringAllowed = function() {
       return $scope.scheduler.recurringAllowed;
    };
    
    $scope.isAutomaticExpirationAllowed = function() {
        return $scope.isRecurring() && $scope.scheduler.maxNumOfCopies > 0;	
    }
});

angular.module("portalApp").controller('navBarController', function($rootScope, $scope) {
        $scope.toggleGuide = function(nonav) {
            $rootScope.$emit("toggleGuideMethod", nonav);
        }
});

angular.module("portalApp").controller('wizardController', function($rootScope, $scope, $timeout, $document, $http, $q, $window, translate) {

    $rootScope.$on("toggleGuideMethod", function(event, args){
       $scope.toggleGuide(args);
    });

    cookieObject = {};
    cookieKey = "VIPR_START_GUIDE";
    dataCookieKey = "GUIDE_DATA";
    requiredSteps = 2;
    landingStep = 3;
    maxSteps = 9;
    $scope.staleData = false;
    initialNav = $(".navMenu .active").text();
    initialNavParent = $(".rootNav.active").text();

    $scope.checkGuide = function() {
        cookieObject = angular.fromJson(readCookie(cookieKey));
        if (cookieObject) {
            $scope.completedSteps = cookieObject.completedSteps;
            $scope.guideMode = cookieObject.guideMode;
            $scope.currentStep = cookieObject.currentStep;
            $scope.guideDataAvailable = true;
            $scope.guideVisible = cookieObject.guideVisible;
            $scope.maxSteps = maxSteps;
			$scope.failedType = cookieObject.failedType;
        }
        $scope.isMenuPinned = readCookie("isMenuPinned");
    }

    $scope.toggleGuide = function(nonav) {

        //we need erase guide data on the license and initial setup nonav pages
        if (nonav) {
            eraseCookie(dataCookieKey);
        }

        if ($scope.guideVisible) {
		    $scope.closeGuide();
        }
        else {
            $scope.guideVisible = true;
            $scope.guideMode='full';
            if ($scope.completedSteps <= requiredSteps || !$scope.completedSteps){
                if ($window.location.pathname == '/setup/license') {
                    if ($scope.currentStep == 1) {return;};
                }
                if ($window.location.pathname == '/setup/index') {
                    if ($scope.currentStep == 2) {return;};
                }
            }
            $scope.initializeSteps();
        }
    }

    $scope.closeGuide = function() {
        $scope.guideVisible = false;
        $scope.guideDataAvailable = false;
        saveGuideCookies();
        setActiveMenu(initialNav,initialNavParent);
    }

    $scope.initializeSteps = function() {

        $scope.currentStep = 1;
        $scope.completedSteps = 0;
        $scope.maxSteps = maxSteps;
        $scope.guideDataAvailable = false;

        checkSteps();

    }

    $scope.completeStep = function(step) {
        function finishChecking(){
            $scope.guideMode='full';
            saveGuideCookies();
        }
		if (!step) {
            step = $scope.currentStep;
        }
        switch (step) {
            case 1:
                updateGuideCookies4(1, 2,'full',true);
                return;
                break;
            case 2:
                updateGuideCookies4(2, 3,'full',true);
                return;
                break;
            default:
                checkStep(step,function(){goToNextStep(true)},function(){finishChecking()});
        }
    }

    goToNextStep = function(complete) {
        if(complete) {
            completedSteps=$scope.completedSteps;
            currentStep=$scope.currentStep;
            if ( currentStep>completedSteps){
                completedSteps=currentStep;
            }
            if ( $scope.currentStep<maxSteps){
                currentStep=currentStep+1;
            }
            else {
                currentStep=landingStep;
            }
        }
        updateGuideCookies3(completedSteps,currentStep,'full');
        goToPage(currentStep);
    }

    $scope.runStep = function(error) {
        openMenu();
        step = $scope.currentStep;
        switch (step) {
            case 1:
                $scope.currentStep = 1;
                $scope.guideMode = 'side';
                saveGuideCookies();
                if ($window.location.pathname != '/setup/license') {
                    goToPage(1);
                }
                break;
            case 2:
                $scope.currentStep = 2;
                $scope.guideMode = 'side';
                saveGuideCookies();
                if ($window.location.pathname != '/setup/index') {
                    goToPage(2);
                }
                break;
            case 9:
                updateGuideCookies4(9,9,'side',false);
                goToPage(9);
                break;
            default:
                updateGuideCookies(step,'side');
                goToPage(step,error);
        }
    }

    goToPage = function(step,error) {

        switch (step) {
            case 1:
                if ($scope.completedSteps>1) {
                     loadPage('/system/licensing');
                } else {
                    loadPage('/setup/license');
                }
                break;
            case 2:
                if ($scope.completedSteps>2) {
                    loadPage('/config');
                } else {
                    loadPage('/setup/index');
                }
                break;
            case landingStep:
                break;
            case 4:
                if (!error) {
                    loadPage('/storagesystems/createAllFlash');
                } else {
                    if (error.indexOf("Provider") == -1){
                        loadPage('/storagesystems/list');
                    } else {
                        loadPage('/storageproviders/list');
                    }
                }
                break;
            case 5:
                loadPage('/sanswitches/list');
                break;
            case 6:
                loadPage('/virtualarrays/defaultvarray');
                break;
            case 7:
                loadPage('/blockvirtualpools/createAllFlash');
                break;
            case 8:
                loadPage('/projects/list');
                break;
            case 9:
                loadPage('/Catalog#ServiceCatalog/AllFlashServices');
                if ($window.location.pathname == '/Catalog') {
                    $window.location.reload(true);
                }
                break;
            default:
                console.log("Incorrect step, no page to go to");
            }
    }

    loadPage = function(link) {
        if ($window.location.pathname != link) {
            $window.location.href = link;
        } else {
            //already on page, reload from cookies
            $scope.checkGuide();
        }
    }

    $scope.showStep = function(step) {

            if (!step) {
                step = $scope.currentStep;
            }
            $scope.currentStep = step;
            //updateGuideCookies(step,'full');
            //goToPage(step);

        }

    $scope.toggleMode = function(mode) {
        $scope.guideMode = mode;
    }

    updateGuideCookies = function(currentStep,guideMode) {
        cookieObject = {};
        cookieObject.currentStep=currentStep;
        cookieObject.completedSteps=$scope.completedSteps;
        cookieObject.guideMode=guideMode;
        cookieObject.guideVisible=$scope.guideVisible;
        cookieObject.failedType=$scope.failedType;
        createCookie(cookieKey,angular.toJson(cookieObject),'session');
    }

    updateGuideCookies3 = function(completedSteps,currentStep,guideMode) {
        cookieObject = {};
        cookieObject.currentStep=currentStep;
        cookieObject.completedSteps=completedSteps;
        cookieObject.guideMode=guideMode;
        cookieObject.guideVisible=$scope.guideVisible;
        createCookie(cookieKey,angular.toJson(cookieObject),'session');
    }

    updateGuideCookies4 = function(completedSteps,currentStep,guideMode,guideVisible) {
        cookieObject = {};
        cookieObject.currentStep=currentStep;
        cookieObject.completedSteps=completedSteps;
        cookieObject.guideMode=guideMode;
        cookieObject.guideVisible=guideVisible;
        createCookie(cookieKey,angular.toJson(cookieObject),'session');
    }

    $scope.startAddMoreStorage = function () {
        removeGuideCookies();
        $scope.currentStep = 4;
        $scope.completedSteps = 3;
        $scope.maxSteps = maxSteps;
        saveGuideCookies();
    }

    removeGuideCookies = function() {
        eraseCookie(cookieKey);
        eraseCookie(dataCookieKey);
    }

    saveGuideCookies = function() {
        cookieObject = {};
        cookieObject.currentStep=$scope.currentStep;
        cookieObject.completedSteps=$scope.completedSteps;
        cookieObject.guideMode=$scope.guideMode;
        cookieObject.guideVisible=$scope.guideVisible;
        cookieObject.failedType=$scope.failedType;
        createCookie(cookieKey,angular.toJson(cookieObject),'session');
    }

    checkSteps = function(){

        function finishChecking(){
            $scope.guideDataAvailable = true;
            saveGuideCookies();
        }

        (function generateSteps(step){
            checkStep(step,function(){
                if(step<10){
                    generateSteps(step+1);
                }
            },function(){finishChecking()});
        })(1);
    }

    checkStep = function(step,callback,finishCheckingCallback) {

        finishChecking = function(){
            finishCheckingCallback();
        }

        switch (step) {
            case 1:
                $http.get(routes.Setup_license()).then(function (data) {
                    isLicensed = data.data;
                    if (isLicensed == 'true') {
                        $scope.completedSteps = 1;
                        $scope.currentStep = 2;
                        callback();
                    }  else {
                        finishChecking();
                    }
                });
                break;
            case 2:
                $http.get(routes.Setup_initialSetup()).then(function (data) {
                    isSetup = data.data;
                    if (isSetup == 'true') {
                        $scope.completedSteps = 2;
                        $scope.currentStep = 3;
                        callback();
                    } else {
                        finishChecking();
                    }
                });
                break;
            case landingStep:
            	guide_data=angular.fromJson(readCookie(dataCookieKey));
                if(guide_data){
                    $scope.completedSteps = 3;
                }
                callback();
                return true;
                break;
            case 4:
                if(checkCookie(dataCookieKey)){
                    providerid = "";
                    ssid= "";
                    guide_data=angular.fromJson(readCookie(dataCookieKey));

                    if(guide_data){
                        arrayCookie = guide_data.storage_systems;
                        if (arrayCookie) {
                            jQuery.each(arrayCookie, function() {
                                if (this.id.indexOf("StorageProvider") > -1){
                                    if (providerid){providerid += ","}
                                    providerid += this.id;
                                } else {
                                    if (ssid){ssid += ","}
                                    ssid += this.id;
                                }
                            });
                        }
                    }
                    var promises = [];
                    failedArray= [];
                    var failedType;
                    promises.push($http.get(routes.StorageProviders_discoveryCheckJson({'ids':providerid})).then(function (data) {
                        if (data.data.length != 0) {
                            if(!failedType){
                                failedType="PROVIDER";
                                $scope.failedType = failedType;
                                failedArray=failedArray.concat(data.data);
                            }
                        }
                    }));
                    promises.push($http.get(routes.StorageSystems_discoveryCheckJson({'ids':ssid})).then(function (data) {
                        if (data.data.length != 0) {
                            if(!failedType){
                                failedType="SYSTEM";
                                $scope.failedType = failedType;
                                failedArray=failedArray.concat(data.data);
                            }
                        }
                    }));
                    $q.all(promises).then(function () {
                        if (failedArray.length > 0) {
                            $scope.guideErrorObject = failedArray;
                            if(failedType=="PROVIDER"){
                                $scope.guideError = "The following Storage Providers have not been discovered yet:";
                                $scope.guideErrorSolution = "They may have failed or are pending discovery. Please check discovery status and fix any errors before continuing to the next step.";
                                finishChecking();
                            } else {
                                $scope.guideError = "The following Storage Systems have not been discovered yet:";
                                $scope.guideErrorSolution = "They may have failed or are pending discovery. Please check discovery status and fix any errors before continuing to the next step.";
                                finishChecking();
                            }
                        } else {
                            $http.get(routes.StorageProviders_getAllFlashStorageSystemsList({'ids':providerid.concat(",").concat(ssid)})).then(function (data) {
                                arrayCookie = guide_data.storage_systems;
                                storage_systems=[];
                                if (data.data.length > 0) {
                                    guide_data.storage_systems=data.data;
                                    createCookie(dataCookieKey,angular.toJson(guide_data),'session');
                                    $scope.completedSteps = 4;
                                    callback();
                                } else {
                                    if (guide_data.storage_systems){
                                        $scope.guideError = "The Guide supports only VMAX All-Flash, Unity All-Flash, and XtremIO storage systems. No All-Flash array detect during the last discovery. For other storage systems, please configure outside of the guide.";
                                    }
                                    finishChecking();
                                }
                            });
                        }
                    });
                } else {
                    finishChecking();
                }
                break;
            case 5:
                ssid= "";
                guide_data=angular.fromJson(readCookie(dataCookieKey));
                if(guide_data){
                    arrayCookie = guide_data.storage_systems;
                    if (arrayCookie) {
                        jQuery.each(arrayCookie, function() {
                            if (ssid){ssid += ","}
                            ssid += this.id;
                        });
                    }
                }
                $http.get(routes.Networks_getDisconnectedStorage({'ids':ssid})).then(function (data) {
                    if (data.data.length > 0) {
                        if (guide_data.fabrics){
                            $scope.guideErrorObject = data.data;
                            $scope.guideError = "The following Storage Systems discovered in the Guide are not attached to a Network:";
                            $scope.guideErrorSolution = "Check that you have added the correct Fabric Managers and they have discovered successfully before continuing to the next step.";
                        }
                        finishChecking();
                    } else {
                        $scope.completedSteps = 5;
                        callback();
                    }
                });
                break;
            case 6:
                ssid= "";
                guide_data=angular.fromJson(readCookie(dataCookieKey));
                if(guide_data){
                    arrayCookie = guide_data.storage_systems;
                    if (arrayCookie) {
                        jQuery.each(arrayCookie, function() {
                            if (ssid){ssid += ","}
                            ssid += this.id;
                        });
                    }
                }
                guide_data=angular.fromJson(readCookie(dataCookieKey));
                $http.get(routes.VirtualArrays_getDisconnectedStorage({'ids':ssid})).then(function (data) {
                    if (data.data.length > 0) {
                        if (guide_data.varrays){
                            $scope.guideErrorObject = data.data;
                            $scope.guideError = "The following Storage Systems discovered in the Guide are not attached to a Virtual Array:";
                            $scope.guideErrorSolution = "To complete step, run Virtual Array creation step again.";
                         }
                        finishChecking();
                    } else {
                        $scope.completedSteps = 6;
                        callback();
                    }
                });
                break;
            case 7:
                if(checkCookie(dataCookieKey)){
                    ssid= "";
                    guide_data=angular.fromJson(readCookie(dataCookieKey));
                    if(guide_data){
                        arrayCookie = guide_data.storage_systems;
                        if (arrayCookie) {
                            jQuery.each(arrayCookie, function() {
                                if (ssid){ssid += ","}
                                ssid += this.id;
                            });
                        }
                    }
                    $http.get(routes.VirtualPools_checkDisconnectedStoragePools({'ids':ssid})).then(function (data) {
                        if (data.data.length != 0) {
                            if (guide_data.vpools){
                                $scope.guideErrorObject = data.data;
                                $scope.guideError = "The following Storage Systems discovered in the Guide are not attached to a Virtual Pool:";
                                $scope.guideErrorSolution = "To complete step, run Virtual Pool creation step again.";
                            }
                            finishChecking();
                        } else {
                            $scope.completedSteps = 7;
                            callback();
                        }
                    });
                } else {
                    finishChecking();
                }
                break;
            case 8:
                $http.get(routes.Projects_list()).then(function (data) {
                    if (data.data.aaData.length != 0) {
                        $scope.completedSteps = 8;
                        callback();
                    } else {
                        finishChecking();
                    }
                });
                break;
            case maxSteps:
                $scope.completedSteps = maxSteps;
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

        guide_data=angular.fromJson(readCookie("GUIDE_DATA"));

        if(guide_data){
            arrayCookie = guide_data.storage_systems;
            if (arrayCookie) {
                $scope.guide_storageArray = "";
                jQuery.each(arrayCookie, function() {
                    if ($scope.guide_storageArray){$scope.guide_storageArray += ","}
                    $scope.guide_storageArray += this.name;
                });
            }
            else if ($scope.completedSteps > 3){
                $scope.guide_storageArray = "Skipped";
            }
            varrayCookie = guide_data.varrays;
            if (varrayCookie) {
                $scope.guide_varray = "";
                jQuery.each(varrayCookie, function() {
                    if ($scope.guide_varray){$scope.guide_varray += ","}
                    $scope.guide_varray += this.name;
                });
            }
            else if ($scope.completedSteps > 5){
                $scope.guide_varray = "Skipped";
            }
            vpoolCookie = guide_data.vpools;
            if (vpoolCookie) {
                $scope.guide_vpool = "";
                jQuery.each(vpoolCookie, function() {
                    if ($scope.guide_vpool){$scope.guide_vpool += ","}
                    $scope.guide_vpool += this.name;
                });
            }
            else if ($scope.completedSteps > 6){
                $scope.guide_vpool = "Skipped";
            }
            fabricCookie = guide_data.fabrics;
            if (fabricCookie) {
                $scope.guide_fabric = "";
                jQuery.each(fabricCookie, function() {
                    if ($scope.guide_fabric){$scope.guide_fabric += ","}
                    $scope.guide_fabric += this.name;
                });
            }
            else if ($scope.completedSteps > 4){
                $scope.guide_fabric = "Skipped";
            }
            projectCookie = guide_data.projects;
            if (projectCookie) {
                $scope.guide_project = "";
                jQuery.each(projectCookie, function() {
                    if ($scope.guide_project){$scope.guide_project += ","}
                    $scope.guide_project += this.name;
                });
            }
            else if ($scope.completedSteps > 7){
                $scope.guide_project = "Skipped";
            }
        }
    }

    checkCookie = function(cookie) {
        var guideCookie = readCookie(cookie);

        if (guideCookie) {
            return true;
        }
        return false;
    }

    var PINNED_COOKIE = 'isMenuPinned';
    var MAIN_MENU = '#mainMenu';
    var NAV = '.rootNav';
    var MAIN_MENU_NAV = MAIN_MENU + ' ' + NAV;
    var MENU = '.navMenu';
    var MENU_NAME = 'subnav';
    var MENU_PIN = '.menuPin';
    var CONTENT_AREA = '#contentArea';

    // --- CSS Classes ---
    var MAIN_MENU_OPEN = 'selected';
    var MENU_OPEN = 'menu-open';
    var MENU_PINNED = 'menu-pinned';
    var ACTIVE_INDICATOR = 'blueArrow';

    function openMenu() {
        $menu = $('a.rootNav.active');
        closeMenus();
        var name = $menu.data(MENU_NAME);
        if (name) {
            var $subMenu = $('#' + name);
            if ($subMenu) {
                $menu.addClass(MAIN_MENU_OPEN);
                $subMenu.addClass(MENU_OPEN);
                $(CONTENT_AREA).addClass(MENU_OPEN);
                $(CONTENT_AREA).addClass(MENU_PINNED);
                $("#wizard").addClass('guide-menuopen');
                createCookie(PINNED_COOKIE, 'true', 'session');
            }
        }
        //updateActiveIndicator();
    }
    function closeMenus() {
        createCookie(PINNED_COOKIE, 'false', 'session');
        $(MAIN_MENU_NAV).removeClass(MAIN_MENU_OPEN);
        $(MENU).removeClass(MENU_OPEN);
        $(CONTENT_AREA).removeClass(MENU_OPEN);
        $("#wizard").removeClass('guide-menuopen');
        //updateActiveIndicator();
    }
    function isMenuOpened() {
        var elements = $('div.'+MENU_OPEN);
        if (elements.length > 0) {
            return true;
        }
        return false;
    }

    $scope.$watch('currentStep', function(currentStep) {
        if($scope.guideVisible && $scope.completedSteps >= 2) {
            switch(currentStep) {
                case 1:
                    setActiveMenu("License");
                    break;
                case 2:
                    setActiveMenu("General Configuration");
                    break;
                case 3:
                    setActiveMenu("");
                    break;
                case 4:
                	if($scope.failedType == "PROVIDER") {
                		setActiveMenu("Storage Providers");
                	}
                	else {
                    	setActiveMenu("Storage Systems");
                    }
                    break;
                case 5:
                    setActiveMenu("Fabric Managers");
                    break;
                case 6:
                    setActiveMenu("Virtual Arrays");
                    break;
                case 7:
                    setActiveMenu("Block Virtual Pools");
                    break;
                case 8:
                    setActiveMenu("Projects");
                    break;
                case 9:
                    setActiveMenu("View Catalog");
                    break;
            }
        }

    });

    function setActiveMenu(name,parent) {
        $(".navMenu .active , #mainMenu .active").removeClass("active");
        if(name){
            parentSelector = $(".navMenu li:contains("+name+")").closest(".navMenu").attr('id');
            $(".navMenu li:contains("+name+")").addClass("active");
            $("a[data-subnav='"+parentSelector+"']").addClass("active");
        } else if (parent){
            $(".rootNav:contains("+parent+")").addClass("active");
        }

        openMenu();
    }

    $(colWiz).on('shown.bs.popover', function(){
        $(colWiz).popover('toggle');
    });

    $(colWiz).popover({
        delay : {
            show : 0,
            hide : 5000
        },
        placement : 'bottom',
        html : true,
        trigger : 'manual',
        content : translate("gettingStarted.popover"),
        selector : 'colWiz'

    });

    $('.menuTree .active').on('shown.bs.popover', function(){
        $('.menuTree .active').popover('toggle');
    });

    $('.menuTree .active').popover({
        delay : {
            show : 0,
            hide : 5000
        },
        placement : 'bottom',
        html : true,
        trigger : 'manual',
        content : translate("gettingStarted.navmenu.popover"),
        selector : '.menuTree .active'

    });

    $('.wizard-side-next').on('shown.bs.popover', function(){
        $('.wizard-side-next').popover('toggle');
    });
    $('.wizard-side-next').popover({
        delay : {
            show : 0,
            hide : 3000
        },
        placement : 'bottom',
        html : true,
        trigger : 'manual',
        content : translate("gettingStarted.step.popover"),
        selector : '.wizard-side-next'

    });

    var guideMonitor;

    var checkCookieChanged = function() {

        return function() {

            var currentCookie = angular.fromJson(readCookie(cookieKey));

            if (currentCookie != null && currentCookie.completedSteps !== cookieObject.completedSteps && guideDataAvailable===true) {
                window.clearInterval(guideMonitor);
                $scope.currentStep = 3;
                $scope.guideMode='full';
                $scope.staleData = true;
                $scope.$apply();
            }
        };
    }();

    $scope.refreshPage = function() {
        $window.location.reload(true);
    }

    $scope.$watchCollection('[guideVisible, guideMode]', function(newValues) {
        var guideVisible = newValues[0];
        var guideMode = newValues[1];
        var body = $(document.body);
        if (guideVisible && guideMode === "full") {
            body.addClass('noscroll');
            window.scrollTo(0, 0);
        } else {
            body.removeClass('noscroll');
        }
     });

    $scope.$watch('guideVisible', function(newValue, oldValue) {
        if (newValue != oldValue){
            if(newValue){
                openMenu();
                $(colWiz).popover('hide');
            }
            else {
                closeMenus();
                $(colWiz).popover('show');
            }
        }
        if(newValue) {
            $('.rootNav , .navMenu a').on('click', function(event) {
                $('.wizard-side-next').popover('show');
                $('.menuTree .active').popover('show');
                return false;
            });
            guideMonitor = window.setInterval(checkCookieChanged, 500);
        } else {
            window.clearInterval(guideMonitor);
            $('.rootNav , .navMenu a').off('click');
        }
    });

    document.getElementById('wizard').addEventListener('mousedown', mouseDown, false);
    window.addEventListener('mouseup', mouseUp, false);

    function pauseEvent(e){
        if(e.stopPropagation) e.stopPropagation();
        if(e.preventDefault) e.preventDefault();
        e.cancelBubble=true;
        e.returnValue=false;
        return false;
    }

    function mouseUp()
    {
        window.removeEventListener('mousemove', divMove, true);
    }

    function mouseDown(e){
        var senderElement = e.target;
        if(this === e.target && this.className.split(" ").indexOf("wizard-side") >= 0) {
      		window.addEventListener('mousemove', divMove, true);
            pauseEvent(e);
        }
    }

    function divMove(e){
          var div = document.getElementById('wizard');
          div.style.position = 'absolute';
          if (e.clientY < $(window).height()-div.scrollHeight && e.clientY > 0+$(".navbar").height()) {
            div.style.top = e.clientY + 'px';
          }
    }

    $scope.$watch('guideMode', function(newValue, oldValue) {
        if (newValue !== oldValue){
            var div = document.getElementById('wizard');
            div.removeAttribute("style");
        }
        if (newValue==='side' && $scope.guideVisible === true){
            $(".dataTableContainer").addClass("wizard-side-move-content");
        }
    });
});
