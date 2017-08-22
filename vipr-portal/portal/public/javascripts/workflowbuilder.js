
var folderNodeType = "FOLDER";
var workflowNodeType = "Workflow";
var shellNodeType = "script";
var localAnsibleNodeType = "ansible"
var restAPINodeType = "rest"
var viprRestAPINodeType = "vipr";
var remoteAnsibleNodeType = "remote_ansible"
var ASSET_TYPE_OPTIONS;

angular.module("portalApp")

.constant('config' , {
	DEFAULT_WF_TIMEOUT: 28800 , //seconds
	DEFAULT_WF_LOOP: false ,
	DEFAULT_OP_TIMEOUT: 14400 , //seconds
	DEFAULT_POLLING_INTERVAL: 5 , //seconds
	DEFAULT_POLLING: false ,
	DEFAULT_WAIT_FOR_TASK: true
})

.directive('numberFilter' , function() {
	return {
	    restrict: 'A', 
	    require: '?ngModel', 
	    link: function(scope, element, attrs, ngModel) {
	    	ngModel.$parsers.push(function(value) {
	    		var numbers = value ;
	    		if (value) {
	    			numbers =  value.replace(/[^0-9]/g, "") ;
	    			if (numbers !== value) {
	    				ngModel.$setViewValue(numbers);
	    		        ngModel.$render();
	    			}
	    		}
	    		
	    		return numbers ;
	    	}) ;
	    }
	}
})

.directive('vPopover' , function($compile) {
    return {
        restrict: 'A' ,
        require: '?vPopoverContent' ,
        link: function (scope, el, attrs) {
            attrs.vPopoverPlacement = attrs.vPopoverPlacement || 'top' ;
            $(el).popover({
                trigger: 'hover',
                delay: { "show": 500, "hide": 100 } ,
                html: true,
                content: function() {
                    return attrs.vPopoverContent ;
                } ,
                placement: attrs.vPopoverPlacement
            });
        }
    }
})

.factory("workflow" , ['$window' , function($win){//NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")
	return (function(){
		
		var suppressUnloadEvent = false ;
		var workflowInfo = {} ;
		
		
	    var idConverter = function(id) {
	    	return id.replace(/:/g,'') ;
	    }
	    
	    var checkWorkflowModifiedState = function(id) {
	    	var info = workflowInfo[idConverter(id)] ;
			if (info.relatedData === undefined) {
	    		return false ;
	    	}else {
	    		return info.relatedData.modified && 
	    					info.relatedData.workflowData.state !== 'PUBLISHED';
	    	}
	    }
	    
	    var hasChangedWorkflow = function() {
	    	var hasModified = false ;
	    	for (var eid in workflowInfo) {
	    		if(checkWorkflowModifiedState(eid)) {
	    			hasModified = true ;
	    			break ;
	    		}
	    	}
	    	
	    	return hasModified ;
	    }
	    
	    $win.onbeforeunload = function(e) {
	    	if(!hasChangedWorkflow() || suppressUnloadEvent) {
	    		return null ;
	    	}
	    	
	    	e.returnValue = "There are workflows being changed but not saved yet" ;
	    	return e.returnValue ;
	    } ;
	    
		return {
			getWorkflowInfo : function() {
				return workflowInfo ;
			},
			
			convertId: idConverter,
			
			isWorkflowModified: checkWorkflowModifiedState,
			
			hasModifiedWorkflow : hasChangedWorkflow ,
		    
		    suppressUnload:function(opt) {
		    	suppressUnloadEvent = opt ;
		    }
		}
	})() ;
}])

.controller('builderController', ['$scope' , '$rootScope' , '$http' , '$window' , 'workflow', function($scope, $rootScope, $http , $window , wf) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")
	
	$rootScope.$on("addWorkflowTab", function(event, id, name){
       addTab(id,name);
    });
    
    $rootScope.$on("renameWorkflow" , function(event , id , newName) {
    	var tabInfo = $scope.workflowTabs[id] ;
    	if (tabInfo === undefined) {
    		return ;
    	}
    	
    	tabInfo.name = newName ;
    }) ;
    
    $rootScope.$on("deleteWorkflow" , function(event , id) {
    	var tabInfo = $scope.workflowTabs[id] ;
    	if (tabInfo === undefined) {
    		return ;
    	}
    	$scope.closeTab(tabInfo , true) ;
    }) ;

    $scope.workflowTabs = wf.getWorkflowInfo() ;
    $scope.isWorkflowTabsEmpty = function () {
        return $.isEmptyObject($scope.workflowTabs);
    };

    function addTab(id,name) {
        var elementid = id.replace(/:/g,'');
        if ($scope.workflowTabs[elementid] === undefined) {
        	$scope.workflowTabs[elementid] = { id:id, elementid:elementid, name:name, href:'#'+elementid};
        }
        $rootScope.$emit('activateWorkflowTab', elementid) ;
    }
    
    $scope.isTabModified = function (tabInfo) {
    	return wf.isWorkflowModified(tabInfo.id) ;
    } ;
    
    $scope.closeTab = function(tabInfo , force){
    	if ($scope.isTabModified(tabInfo) && !force) {
    		var r = confirm("Do you want to close it without saving the change?")
    		if (r !== true) {
    			return ;
    		}
    	}
        delete $scope.workflowTabs[tabInfo.elementid];
        var nextElemId = Object.keys($scope.workflowTabs)[0] ;
        if (nextElemId) {
        	$rootScope.$emit('activateWorkflowTab' , nextElemId) ;
        } 
    }

    $http.get(routes.Workflow_getAssetOptions()).then(function (resp) {
        if (resp.status == 200) {
            ASSET_TYPE_OPTIONS = resp.data;
        }
    });
 }])

.controller('treeController', function($element, $scope, $compile, $http, $rootScope, translate) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")

    $scope.libOpen = true;
    $scope.toggleLib = function() {
        $("#builderController").toggleClass("collapsedBuilder");
        $("#libSidebar").toggleClass("collapsedSideBar");

        $scope.libOpen = !$scope.libOpen;
    }

    var jstreeContainer = $element.find('#jstree_demo');

    var fileNodeTypes = [shellNodeType, localAnsibleNodeType, remoteAnsibleNodeType, restAPINodeType, workflowNodeType]
    var primitiveNodeTypes = [shellNodeType, localAnsibleNodeType, remoteAnsibleNodeType, restAPINodeType]
    var viprLib = "viprLib";
    var myLib = "myLib";

    initializeJsTree();

    // This is required for IE ('startswith' is not supported)
    if (!String.prototype.startsWith) {
      String.prototype.startsWith = function(searchString, position) {
        position = position || 0;
        return this.indexOf(searchString, position) === position;
      };
    }

    function initializeJsTree(){
        var to = null;
        var searchElem = $element.find(".search-input");
        searchElem.keyup(function() {
            if(to) { clearTimeout(to); }
                to = setTimeout(function() {
                  var searchString = searchElem.val();
                  jstreeContainer.jstree(true).show_all();
                  jstreeContainer.jstree('search', searchString);
                }, 250);
        });

        jstreeContainer.jstree({
            "core": {
                "animation": 0,
                "check_callback": true,
                "themes": {"stripes": false, "ellipsis": true},
                "data": {
                    "url" : "getWFDirectories",
                    "type":"get"
                }
            },
            "types": {
                "#": {
                    "max_children": 1,
                    "max_depth": -1,
                    "valid_children": ["root"]
                },
                "root": {
                    "icon": "glyphicon glyphicon-folder-close",
                    "valid_children": ["default"]
                },
                "FOLDER": {
                    "icon": "builder-jstree-icon builder-folder-icon",
                    "valid_children": ["Workflow","FOLDER", "script", "ansible", "rest", "remote_ansible"]
                },
                "Workflow": {
                    "icon": "builder-jstree-icon builder-jstree-workflow-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "script": {
                    "icon": "builder-jstree-icon builder-jstree-script-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "ansible": {
                    "icon": "builder-jstree-icon builder-jstree-ansible-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "remote_ansible": {
                    "icon": "builder-jstree-icon builder-jstree-remote-ansible-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "vipr": {
                    "icon": "builder-jstree-icon builder-jstree-vipr-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "rest": {
                    "icon": "builder-jstree-icon builder-jstree-rest-icon",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                }
            },
            "plugins": [
                "search", "state", "types", "wholerow", "themes"
            ],
            "search" : {
                  'case_sensitive' : false,
                  'show_only_matches' : true
            }
        }).on('ready.jstree', function() {
            jstreeContainer.find( ".draggable-card" ).draggable({delay: 200,handle: ".jstree-anchor",scroll: false,helper: getDraggableStepHTML,appendTo: 'body',cursorAt: { top: 8, left: -16 }});
        }).bind("rename_node.jstree clear_search.jstree search.jstree open_node.jstree", function() {
            jstreeContainer.find( ".draggable-card" ).draggable({delay: 200,handle: ".jstree-anchor",scroll: false,helper: getDraggableStepHTML,appendTo: 'body',cursorAt: { top: 0, left: 0 }});
        }).on('search.jstree', function (nodes, str) {
              if (str.nodes.length === 0) {
                  $('#jstree_demo').jstree(true).hide_all();
              }
        });
    }

    var getDraggableStepHTML= function(event){
        var stepName=event.target.text;
        var treeId = event.target.parentElement.id
        if (!stepName) {
            stepName=event.target.parentElement.text;
            treeId = event.target.parentElement.parentElement.id
        }
        var $item = '<div style="z-index:999;"class="item"><div class="itemText">' + stepName + '</div></div>';

        var itemData = jstreeContainer.jstree(true).get_json(treeId).data;
        // Data is not populated for workflows. So setting required fields here.
        if($.isEmptyObject(itemData)) {
            itemData = {"friendlyName":stepName,"type":workflowNodeType,"id":treeId};
            $item = '<div style="z-index:999;"class="item-stacked">' +
            '<div style="z-index:999;"class="item-stacked">' +
            '<div class="item">' +
            '<div class="itemText">' + stepName + '</div>' +
            '</div></div></div>';
        }
        $rootScope.primitiveData = itemData;

        return $( $item );
    }

    // jstree actions
    jstreeContainer.on("rename_node.jstree", renameDir);
    jstreeContainer.on("select_node.jstree", selectDir);
    jstreeContainer.on("hover_node.jstree", hoverDir);
    jstreeContainer.on("dehover_node.jstree", dehoverDir);

    function displaySuccessMessage(successMessage) {
    	var alertsDiv = $("#wfAlertsDiv");
    	var successAlert = alertsDiv.find("#alerts_success");
    	if(!successAlert.length) {
    	    // if it doesn't exist, create
    	    var successAlertHTML =
    	    "<p id='alerts_success' class='alert alert-success'>"+
                "<button type='button' class='close' data-dismiss='alert' aria-hidden='true'>×</button>"+
                "<span class='message'></span>"+
            "</p>";
    	    alertsDiv.append(successAlertHTML);
    	    successAlert = $("#alerts_success");
    	}

    	successAlert.find("span").html(successMessage);
        successAlert.show();
    };

    function displayErrorMessage(errorMessage) {
    	var alertsDiv = $("#wfAlertsDiv");
    	var errorAlert = alertsDiv.find("#alerts_error");
    	if(!errorAlert.length) {
    	    // if it doesn't exist, create
    	    var errorAlertHTML =
    	    "<p id='alerts_error' class='alert alert-danger'>"+
                "<button type='button' class='close' data-dismiss='alert' aria-hidden='true'>×</button>"+
                "<span class='message'></span>"+
            "</p>";
    	    alertsDiv.append(errorAlertHTML);
    	    errorAlert = $("#alerts_error");
    	}

    	errorAlert.find("span").html(errorMessage);
        errorAlert.show();
    };

    function createDir(event, data) {
        if (folderNodeType === data.node.type) {
            $http.get(routes.WF_directory_create({"name": data.node.text,"parent": data.node.parent})).success(function(resp) {
                data.instance.set_id(data.node, resp.id);
                displaySuccessMessage(translate("node.create.success"));
            })
            .error(function (error){
                deleteNodeFromJSTreeAndDisplayErrorMsg(data.node, error.details);
            });
        }
        else {
            $http.get(routes.Workflow_create({"workflowName": data.node.text,"dirID": data.node.parent})).success(function(resp) {
                data.instance.set_id(data.node, resp.id);
                displaySuccessMessage(translate("node.create.success"));
                $scope.openWorkflow(data.node) ;
            })
            .error(function (error){
                deleteNodeFromJSTreeAndDisplayErrorMsg(data.node, error.details);
            });
        }
    };

    function deleteNodeFromJSTreeAndDisplayErrorMsg(selectedNode, errorMsg) {
        jstreeContainer.jstree(true).delete_node(selectedNode);
        displayErrorMessage(errorMsg);
    }

    function deleteNodeFromJSTreeAndDisplaySuccessMsg(jstreeRef, selectedNode) {
        jstreeRef.delete_node(selectedNode);

        // By default select "My Library"
        jstreeContainer.jstree("select_node", myLib);

        displaySuccessMessage(translate("node.delete.success"));
    }

    $scope.deleteNode = function() {
      var r = confirm("Are you sure you want to delete?");
      if (r == true) {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected('full',true);
        if(!sel.length) { return false; }
        var nodeId = sel[0].id;
        var nodeType = sel[0].type;
        var nodeParent = sel[0].parent;
        if (folderNodeType === nodeType) {
            $http.get(routes.WF_directory_delete({"id": nodeId})).success(function() {
                deleteNodeFromJSTreeAndDisplaySuccessMsg(ref, sel);
            })
            .error(function (error){
                displayErrorMessage(error.details);
            });
        }
        else if($.inArray(nodeType, primitiveNodeTypes) > -1) {
            $('#deletePrimitiveId').val(nodeId);
            $('#deleteDirId').val(nodeParent);
        	$('#deletePrimitiveForm').submit();
        }
        else {
            $http.get(routes.Workflow_delete({"workflowID": nodeId, "dirID": nodeParent})).success(function() {
                deleteNodeFromJSTreeAndDisplaySuccessMsg(ref, sel);
                $rootScope.$emit("deleteWorkflow" ,nodeId.replace(/:/g , '')) ;
            })
            .error(function (error){
                displayErrorMessage(error.details);
            });
            }
        }
    };

    function revertRename(node, oldText, errorMessage) {
        jstreeContainer.jstree('set_text', node , oldText );
        displayErrorMessage(errorMessage);
        addMoreOptions(node.id, node.type, node.parent);
    }

    function renameDir(event, data) {
        // Identifying if node is not saved to DB yet and creating it.
        if (!(data.node.id).startsWith("urn")) {
            createDir(event, data);
            addMoreOptions(data.node.parent, folderNodeType, "");
        }
        else {
            // if Old text is not equal to new text, then rename
            if (data.old !== data.text) {
                if (folderNodeType === data.node.type) {
                    $http.get(routes.WF_directory_edit_name({"id": data.node.id, "newName": data.text})).success(function() {
                        displaySuccessMessage(translate("node.rename.success"));
                    })
                    .error(function (error){
                        revertRename(data.node, data.old, error.details)
                    });
                }
                else if($.inArray(data.node.type, primitiveNodeTypes) > -1) {
                    $http.get(routes.Primitive_edit_name({"primitiveID": data.node.id, "newName": data.text})).success(function() {
                        displaySuccessMessage(translate("node.rename.success"));
                    })
                    .error(function (error){
                        revertRename(data.node, data.old, error.details)
                    });
                }
                else if (workflowNodeType === data.node.type){
                    $http.get(routes.Workflow_edit_name({"id": data.node.id, "newName": data.text})).success(function() {
                        displaySuccessMessage(translate("node.rename.success"));
                        $rootScope.$emit("renameWorkflow" , data.node.id.replace(/:/g , '') , data.text) ;
                    })
                    .error(function (error){
                        revertRename(data.node, data.old, error.details)
                    });
                }
            }

            addMoreOptions(data.node.id, data.node.type, data.node.parent);
        }
    };


    var optionsHTML =
    "<div id='treeMoreOptionsSel' class='btn-group treeMoreOptions'>"+
       "<button id='optionsBtn' type='button' class='btn btn-xs btn-default dropdown-toggle' title='Options' data-toggle='dropdown'>"+
           "<span class='glyphicon'><img src='/public/img/customServices/ThreeDotsMenu.svg' height='20' width='20'></span>"+
       "</button>"+
       "<ul class='dropdown-menu dropdown-menu-right' role='menu'>"+
            "<li id='editMenu' style='display:none;'><a  href='#' ng-click='editNode();'>"+translate('wfBuilder.menu.edit')+"</a></li>"+
            "<li id='editWFMenu' style='display:none;'><a  href='#' ng-click='openWorkflow();'>"+translate('wfBuilder.menu.edit')+"</a></li>"+
            "<li id='renameMenu' style='display:none;'><a  href='#' ng-click='renameNode();'>"+translate('wfBuilder.menu.rename')+"</a></li>"+
            "<li id='deleteMenu' style='display:none;'><a  href='#' ng-click='deleteNode();'>"+translate('wfBuilder.menu.delete')+"</a></li>"+
            "<li id='importExportSeparator' role='separator' class='divider' style='display:none;'></li>"+
            "<li id='exportMenu' style='display:none;'><a  href='#' ng-click='exportWorkflow();'>"+translate('wfBuilder.menu.export')+"</a></li>"+
       "</ul>"+
    "</div>";

    var validActionsOnMyLib = ["addWorkflowMenu", "addShellMenu", "addLAMenu", "addRestMenu", "addFolderDivider", "addFolderMenu"]
    var validActionsOnFolder = ["editDivider", "renameMenu", "deleteMenu"]
    var validActionsOnWorkflow = ["renameMenu", "editWFMenu", "deleteMenu", "exportMenu", "importExportSeparator"]
    var validActionsOnMyPrimitives = ["renameMenu", "deleteMenu", "editMenu"]

    function showOptions(nodeId, parents) {
        // Do not show 'More options' on ViPR Library nodes & My Library
        if($.inArray(viprLib, parents) > -1 || viprLib === nodeId || myLib === nodeId ) {
            return false;
        }
        return true;
    }

    function addMoreOptions(nodeId, nodeType, parents) {
        //remove any previous element
        $(".treeMoreOptions").remove();

        if(!showOptions(nodeId, parents)) return;

        //find anchor with this id and append "more options"
        $('[id="'+nodeId+'"]').children('a').before(optionsHTML);

        // If current node is vipr library or its parent is vipr library, disable all
        if(workflowNodeType === nodeType){
            // For workflows
            validActions = validActionsOnWorkflow
        }
        else if($.inArray(nodeType, fileNodeTypes) > -1){
            // For other file types (shell, rest, ansible)
            validActions = validActionsOnMyPrimitives;
        }
        else {
            // Other folders in My Library
            validActions = validActionsOnFolder;
        }

        // Show all validActions
        $.each(validActions, function( index, value ) {
            $('#'+value).show();
        });

        var generated = jstreeContainer.jstree(true).get_node(nodeId, true);
        $compile(generated.contents())($scope);
    }

    function selectDir(event, data) {
        $scope.selNodeId = data.node.id;
        addMoreOptions(data.node.id, data.node.type, data.node.parents);

        // If current node is vipr library or its parent is vipr library, disable all
        if(viprLib === data.node.id || $.inArray(viprLib, data.node.parents) > -1 || $.inArray(data.node.type, fileNodeTypes) > -1) {
            // ViPR Library nodes - disable all buttons
            $('#addWorkflow').prop("disabled",true);
        }
        else {
            $('#addWorkflow').prop("disabled",false);
        }
    };

    $scope.hoverOptionsClick = function(event, nodeId){
        jstreeContainer.jstree("deselect_node", $scope.selNodeId);
        jstreeContainer.jstree("select_node", nodeId);
        event.stopPropagation();
        $("#optionsBtn").click();
    }

    function hoverDir(event, data) {
        var nodeId = data.node.id;
        $scope.hoverNodeId = nodeId;

        // Do not show again for selected node
        if (showOptions(nodeId, data.node.parents) && $scope.selNodeId !== nodeId) {
            var optionsHoverHTML =
                "<div id='treeMoreOptionsHover' class='btn-group treeMoreOptions'>"+
                   "<button id='optionsHoverBtn' type='button' class='btn btn-xs btn-default' title='Options' ng-click=\"hoverOptionsClick($event, '" + nodeId + "');\">"+
                       "<span class='glyphicon'><img src='/public/img/customServices/ThreeDotsMenu.svg' height='20' width='20'></span>"+
                   "</button>"+
                "</div>";

            $('[id="'+nodeId+'"]').children('a').before(optionsHoverHTML);
            var generated = jstreeContainer.jstree(true).get_node(nodeId, true);
            $compile(generated.contents())($scope);
        }

    }

    function dehoverDir(event, data) {
        //remove hover options
        $("#treeMoreOptionsHover").remove();
    }

    // Methods for JSTree actions
    $scope.addFolder = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected();
        if(!sel.length) { return false; }
        sel = sel[0];
        sel = ref.create_node(sel, {"type":folderNodeType});
        if(sel) {
            ref.edit(sel);
        }
    }

    $scope.addWorkflow = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected();
        if(!sel.length) { return false; }
        sel = sel[0];
        sel = ref.create_node(sel, {"type":workflowNodeType});
        if(sel) {
            ref.edit(sel);
        }
    }

    $scope.openShellScriptModal = function(){
        var scope = angular.element($('#scriptModal')).scope();
        scope.populateModal(false);
        $('#shellPrimitiveDialog').modal({
            show: true,
            backdrop: 'static',
            keyboard: false
        });
    }

    $scope.openLocalAnsibleModal = function(){
        var scope = angular.element($('#localAnsibleModal')).scope();
        scope.populateModal(false);
        $('#localAnsiblePrimitiveDialog').modal({
            show: true,
            backdrop: 'static',
            keyboard: false
        });
    }

    $scope.openRemoteAnsibleModal = function(){
        var scope = angular.element($('#remoteAnsibleModal')).scope();
        scope.populateModal(false);
        $('#remoteAnsiblePrimitiveDialog').modal({
            show: true,
            backdrop: 'static',
            keyboard: false
        });
    }

    $scope.openRestAPIModal = function(){
        var scope = angular.element($('#restAPIModal')).scope();
        scope.populateModal(false);
        $('#restAPIPrimitiveDialog').modal({
            show: true,
            backdrop: 'static',
            keyboard: false
        });
    }

    // Rename node
    $scope.renameNode = function(){
        var ref = jstreeContainer.jstree(true),
                sel = ref.get_selected('full',true);
        sel = sel[0];
        ref.edit(sel.id);
    }

    // if folder edit name, if primitive - open modal
    $scope.editNode = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected('full',true);

        if(!sel.length) { return false; }
        sel = sel[0];
        if(shellNodeType === sel.type) {
            //open script modal
            var scope = angular.element($('#scriptModal')).scope();
            scope.populateModal(true, sel.id, sel.type);
            $('#shellPrimitiveDialog').modal('show');
        }
        else if(localAnsibleNodeType === sel.type){
            //open script modal
            var scope = angular.element($('#localAnsibleModal')).scope();
            scope.populateModal(true, sel.id, sel.type);
            $('#localAnsiblePrimitiveDialog').modal('show');
        }
        else if(remoteAnsibleNodeType === sel.type){
            //open script modal
            var scope = angular.element($('#remoteAnsibleModal')).scope();
            scope.populateModal(true, sel.id, sel.type);
            $('#remoteAnsiblePrimitiveDialog').modal('show');
        }
        else if(restAPINodeType === sel.type){
            //open script modal
            var scope = angular.element($('#restAPIModal')).scope();
            scope.populateModal(true, sel.id, sel.type);
            $('#restAPIPrimitiveDialog').modal('show');
        }
        else{
            ref.edit(sel.id);
        }
    };

    $scope.openWorkflow = function(selectedNode) {
        selectedNode = selectedNode || jstreeContainer.jstree(true).get_selected(true)[0];
        $rootScope.$emit("addWorkflowTab", selectedNode.id ,selectedNode.text);
    }

    $scope.exportWorkflow = function() {
        var selectedNode = jstreeContainer.jstree(true).get_selected(true)[0];
        $('#exportWorkflowId').val(selectedNode.id);
        $('#exportWorkflowForm').submit();
    }

    $scope.importWorkflow = function() {
        $('#importWorkflow_wfDirID').val(jstreeContainer.jstree(true).get_selected()[0]);
        $('#importWorkflowDialog').modal('show');
    }
})

.controller('tabController', [
	'$element', 
	'$scope', 
	'$compile', 
	'$http', 
	'$rootScope', 
	'$location' ,
	'$sce' ,
	'translate' , 
	'workflow' , 
	'config',  function($element, $scope, $compile, $http, $rootScope, $location , $sce , translate , wf , cfg) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")

    var diagramContainer = $element.find('#diagramContainer');
    var sbSite = $element.find('#sb-site');
    var jspInstance;

    var INPUT_FIELD_OPTIONS = ['text','number','boolean','password'];
    var INPUT_TYPE_OPTIONS_REQUIRED = ['InputFromUser','AssetOptionMulti','AssetOptionSingle','InputFromUserMulti','FromOtherStepOutput','FromOtherStepInput'];
    var INPUT_TYPE_OPTIONS = ['Disabled'];


    var RELATIONSHIP_MAP = {};
    var STEP_INPUT_MAP = {};
    var STEP_OUTPUT_MAP = {};

    $scope.workflowData = {};
    var DEFAULT_OUTPUT_PARAMS = ["operation_output","operation_error","operation_returncode"];

    $scope.modified = false;
    $scope.showAlert = false;
    $scope.selectedId = '';
    $scope.detached = false;

    initializeJsPlumb();
    initializePanZoom();
    
    $rootScope.$on('activateWorkflowTab', function(event , elemId) {
    	activateTab(elemId) ;
    })

    function activateTab(tab , needLoad){
        $('.nav-tabs a[href="#' + tab + '"]').tab('show');
        if (needLoad) {
        	loadJSON($scope.workflowData.document) ;
        	$scope.modified = false ;
        }
    };
    
    $scope.initializeWorkflowData = function(workflowInfo) {
        var elementid = workflowInfo.id.replace(/:/g,'');
        $http.get(routes.Workflow_get({workflowId: workflowInfo.id})).then(function (resp) {

            if (resp.status == 200) {
                $scope.workflowData = resp.data;
                workflowInfo.relatedData = $scope ;
                initWorkflowAttribute($scope.workflowData.document) ;
                initWorkflowStepDict($scope.workflowData) ;
                activateTab(elementid , true);
                
            }
        });
    }
    
    function initWorkflowAttribute(doc) {
    	if (!doc.attributes) {
    		doc.attributes = {
    				loop_workflow: cfg.DEFAULT_WF_LOOP ,
    				timeout: cfg.DEFAULT_WF_TIMEOUT
    		}
    	}else if (doc.attributes['timeout'] === undefined) {
    		doc.attributes['timeout'] = cfg.DEFAULT_WF_TIMEOUT ;
    	}else if (doc.attributes['loop_workflow'] === undefined) {
    		doc.attributes['loop_workflow'] = cfg.DEFAULT_WF_LOOP ;
    	}else{
    		doc.attributes['loop_workflow'] = (doc.attributes['loop_workflow'] === 'true') ;
    	}
    }
    
    function initWorkflowStepDict(workflowData) {
    	workflowData.stepDict = {} ;
    	var stepList =  workflowData.document.steps ;
    	for (var i = 0 ; i < stepList.length ; i++) { 
    		var step = stepList[i] ;
    		workflowData.stepDict[step.id] = step ;
    	}
    }

    function initializePanZoom(){

        var widthHalf = (window.innerWidth / 2) - 75;
        var heightHalf = (window.innerHeight / 2) - 75;

        var $panzoom = diagramContainer.panzoom({
            cursor: "default",
            minScale: 0.5,
            maxScale: 2,
            increment: 0.1,
            duration: 100
        });

        //DOMMouseScroll is needed for firefox
        $panzoom.parent().on('mousewheel.focal DOMMouseScroll', function(e) {
            e.preventDefault();
            var delta = e.delta || e.originalEvent.wheelDeltaY;
            var focalPoint = e;

            //if delta is null then DOMMouseScroll was used,
            //we can map important data to similar delta/focalpoint objects
            if (!delta){
                delta = e.originalEvent.detail;
                focalPoint = {
                    clientX: e.originalEvent.clientX,
                    clientY: e.originalEvent.clientY
                };
            }
            if (delta !== 0) {
                var zoomOut = delta < 0;
                $panzoom.panzoom('zoom', zoomOut, {
                    animate: false,
                    increment: 0.1,
                    focal: focalPoint
                });
            }
        });
        $panzoom.panzoom("pan", -2000 + widthHalf, -2000 + heightHalf, {
            relative: false
        });
        $panzoom.on('panzoomzoom', function(e, panzoom, scale) {
            jspInstance.setZoom(scale);
        });

        var str_down = 'mousedown' + ' pointerdown' + ' MSPointerDown';
        var str_start = 'touchstart' + ' ' + str_down;

        diagramContainer.on(str_start, "*", function(e) {
            e.stopImmediatePropagation();
        });
    }

    function initializeJsPlumb(){
        jspInstance = jsPlumb.getInstance();
        jspInstance.importDefaults({
            DragOptions: {
                cursor: "none"
            },
            ConnectionOverlays: [
                ["PlainArrow", {
                    location: 1,
                    visible:true,
                    id:"ARROW",
                    width: 12,
                    length: 12

                } ]]
        });
        jspInstance.setContainer(diagramContainer);
        jspInstance.setZoom(1);
        sbSite.droppable({drop: dragEndFunc});
        setBindings($element);
    }

    /*
    Shared Endpoint params for each step
    */
    var passEndpoint = {
        endpoint: ["Image", {
            src:"/public/img/customServices/YesDark.svg",
            height:'20',
            width:'20'
        }],
        isSource: true,
        connector: ["Flowchart", {
            cornerRadius: 5
        }],
        anchors: [0.5, 1, 0, 1,0,10],
		connectorStyle:{ strokeStyle:"#3fac49", lineWidth:1 },
        cssClass: "passEndpoint"
    };

    var failEndpoint = {
        endpoint: ["Image", {
            src:"/public/img/customServices/NoDark.svg",
            height:'20',
            width:'20'
        }],
        isSource: true,
        connector: ["Flowchart", {
            cornerRadius: 5
        }],
        anchors: [1, 0.5, 1, 0,10,0],
        connectorStyle:{ strokeStyle:"#ee3825", lineWidth:1 },
        cssClass: "failEndpoint"
    };

    var targetParams = {
        anchors: ["Top","Left"],
        endpoint: ["Dot", {
        	cssClass: "commonEndpoint"
        }],
        dropOptions: {
    		hoverClass: "glow-common-hover" ,
    		activeClass: "glow-common"
        } ,
        allowLoopback: false,
        filter:":not(a)"
    };

    function copyWorkflow(e,workflow) {
        var newSteps = [];
        var idMap = {};
        //create id map
        workflow.steps.forEach(function(step) {
            if (step.id !== "Start" && step.id !== "End"){
                var newId = generateUUID();
                idMap[step.id] = newId;
            }
        });

        //get position offset
        var x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
        var y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
        var scaleMultiplier = 1 / jspInstance.getZoom();;
        var positionY = (y - diagramContainer.offset().top) * scaleMultiplier;
        var positionX = (x - diagramContainer.offset().left) * scaleMultiplier;
        var offsetY = positionY - 2000;
        var offsetX = positionX - 1500;

        workflow.steps.forEach(function(step) {
            if (step.id !== "Start" && step.id !== "End"){
                if (step.next.defaultStep === "End"){
                    delete step.next.defaultStep;
                }
                else if (idMap[step.next.defaultStep]){
                    step.next.defaultStep = idMap[step.next.defaultStep];
                }
                if (step.next.failedStep === "End"){
                    delete step.next.failedStep;
                }
                else if (idMap[step.next.failedStep]){
                    step.next.failedStep = idMap[step.next.failedStep];
                }
                step.id = idMap[step.id];

                step.positionX = step.positionX + offsetX;
                step.positionY = step.positionY + offsetY;

                newSteps.push(step);
            }
        });

        workflow.steps = newSteps;
        loadJSON(workflow);
    }

    /*
    Functions for managing step data on jsplumb instance
    */
    function dragEndFunc(e) {
        var stepData = $rootScope.primitiveData;
        if (stepData.type === "Workflow"){
            $http.get(routes.Workflow_get({workflowId: stepData.id})).then(function (resp) {
                if (resp.status === 200) {
                    copyWorkflow(e,resp.data.document);
                }
            });
            return
        }
        //set ID and text within the step element
        var randomIdHash = generateUUID ();
        //compensate x,y for zoom
        var x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
        var y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
        var scaleMultiplier = 1 / jspInstance.getZoom();;
        var positionY = (y - diagramContainer.offset().top) * scaleMultiplier;
        var positionX = (x - diagramContainer.offset().left) * scaleMultiplier;

        // copy data
        stepData.operation = stepData.id;
        stepData.id = randomIdHash;
        stepData.positionY = positionY;
        stepData.positionX = positionX;

        // Add default params
        if (!stepData.output) {
            stepData.output = [];
        }
        
        $scope.workflowData.stepDict[stepData.id] = stepData ;
        $scope.modified = true;
        loadStep(stepData);

    }

    // Function to generate UUID for step Id that compliant with RFC-4122 version 4
    function generateUUID () {
        var d = new Date().getTime();
        if (typeof performance !== 'undefined' && typeof performance.now === 'function'){
            d += performance.now(); //use high-precision timer if available
        }
        return 'xxxxxxxx_xxxx_4xxx_yxxx_xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            var r = (d + Math.random() * 16) % 16 | 0;
            d = Math.floor(d / 16);
            return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
        });
    }
    
    $scope.addStepCondition = function(data , field) {
        if (!data.attributes[field]) {
            data.attributes[field] = [] ;
        }
    	data.attributes[field].push({
    		output_name: "" , 
    		check_Value: ""
    	})
    }
    
    $scope.deleteStepCondition = function(data , field , idx) {
    	data.attributes[field].splice(idx , 1) ;
    	$scope.modified = true ;
    }
    
    $scope.getInputOptions=function(id){
        return STEP_INPUT_MAP[id];
    }

    $scope.getOutputOptions=function(id){
        return STEP_OUTPUT_MAP[id];
    }

    $scope.getParentOptions=function(id){
        var parents = [];
        parents=getAllParents(id,parents);
        return parents;
    }

    function findWithAttr(array, attr, value) {
        for(var i = 0; i < array.length; i += 1) {
            if(array[i][attr] === value) {
                return i;
            }
        }
        return -1;
    }

    function getAllParents(id,result){
        if (null == result){
            result = [];
        }
        var parents = RELATIONSHIP_MAP[id];
        if (null != parents) {
            for(var parent in parents) {
                if (parents.hasOwnProperty(parent)) {
                    var index = findWithAttr(result,"id",parent);
                    if( index === -1){
                        result.push({id:parent,name:parents[parent]});
                        result=getAllParents(parent,result);
                    }
                }
            }
        }
        return result;
    }

    function addRelation(source,target,sourceData) {
        if(source === 'Start'||target === 'End'){
            return;
        }
        var parents = RELATIONSHIP_MAP[target];
        if (null != parents) {
            if(!parents.hasOwnProperty(source)) {
                parents[source]=sourceData.friendlyName;
            }
        } else {
            parents = {};
            parents[source]=sourceData.friendlyName;
        }
        RELATIONSHIP_MAP[target]=parents;

        var inputParamsList = STEP_INPUT_MAP[source];
        if (null == inputParamsList)  {
            inputParamsList = [];
            if("inputGroups" in sourceData) {
                for(var inputGroupEntry in sourceData.inputGroups) {
                    if(sourceData.inputGroups.hasOwnProperty(inputGroupEntry)) {
                        var inparams = sourceData.inputGroups[inputGroupEntry].inputGroup;
                        for(var inputparam in inparams) {
                            if(inparams.hasOwnProperty(inputparam)) {
                                var inparam_name = inparams[inputparam].name;
                                var stepidconcate = sourceData.id + "." + inparam_name;
                                inputParamsList.push({id:stepidconcate, name:inparam_name});
                            }
                        }
                    }
                }
            }
        }
        STEP_INPUT_MAP[source]=inputParamsList;

        var outputParamsList = STEP_OUTPUT_MAP[source];
        if (null == outputParamsList)  {
            outputParamsList = [];
            var outparams = sourceData.output;
            for(var outputparam in outparams) {
                if(outparams.hasOwnProperty(outputparam)) {
                    var outparam_name = outparams[outputparam].name;
                    var stepidconcate = sourceData.id + "." + outparam_name;
                    outputParamsList.push({id:stepidconcate, name:outparam_name});
                }
            }

            for(var outputparam in DEFAULT_OUTPUT_PARAMS) {
                if(DEFAULT_OUTPUT_PARAMS.hasOwnProperty(outputparam)) {
                    var outparam_name = DEFAULT_OUTPUT_PARAMS[outputparam];
                    var stepidconcate = sourceData.id + "." + outparam_name;
                    outputParamsList.push({id:stepidconcate, name:outparam_name});
                }
            }
        }
        STEP_OUTPUT_MAP[source]=outputParamsList;
    }

    function removeRelation(source,target,sourceData) {
        var parents = RELATIONSHIP_MAP[target];
        if (null != parents) {
            if(sourceData.next.defaultStep !== target && sourceData.next.failedStep !== target && parents.hasOwnProperty(source)) {
                delete parents[source];
                if (jQuery.isEmptyObject(parents)){
                    delete RELATIONSHIP_MAP[target];
                    delete STEP_INPUT_MAP[source];
                    delete STEP_OUTPUT_MAP[source];
                }
            }
        }
    }

    function setBindings() {
        jspInstance.bind("connection", function(connection) {
            var source=$(connection.source);
            var sourceEndpoint=$(connection.sourceEndpoint.canvas);
            var sourceData = source.data("oeData");
            var sourceNext = {};

            if (sourceData.next) {
                sourceNext = sourceData.next;
            }
            if (sourceEndpoint.hasClass("passEndpoint")) {
                sourceNext.defaultStep=connection.targetId
            }
            if (sourceEndpoint.hasClass("failEndpoint")) {
                sourceNext.failedStep=connection.targetId
            }
            // Populate array for input and output from previous steps

            sourceData.next=sourceNext;
            addRelation(sourceData.id,connection.targetId,sourceData)
            $scope.modified = true;
            $scope.$apply();
        });

        jspInstance.bind("connectionDetached", function(connection) {
            var source=$(connection.source);
            var sourceEndpoint=$(connection.sourceEndpoint.canvas);
            var sourceData = source.data("oeData");
            var sourceNext = {};
            if (sourceData.next) {
                sourceNext = sourceData.next;
            }
            if (sourceEndpoint.hasClass("passEndpoint")) {
                delete sourceData.next.defaultStep;
            }
            if (sourceEndpoint.hasClass("failEndpoint")) {
                delete sourceData.next.failedStep;
            }
            sourceData.next=sourceNext;
            removeRelation(sourceData.id,connection.targetId,sourceData)
            $scope.modified = true;
            $scope.$apply();
        });

        jspInstance.bind("connectionDrag", function(connection) {
            //
            if(!$('#'+connection.targetId).hasClass('item')) {
                //Hide Endpoint logic
                $( '.jsplumb-endpoint' ).each(function( index, item ) {
                        if (!$(item).hasClass('jsplumb-endpoint-connected')){
                            $(item).css( "opacity", "0" );
                        }
                    });

                //Glow logic
                /*if($(connection.getAttachedElements()[0].canvas).hasClass('passEndpoint')){
                    $( '.item , #End' ).each(function( index, item ) {
                        if (item.id !== connection.getAttachedElements()[0].id){
                            if (item.id !== connection.sourceId){
                                $(item).addClass('glow-pass');
                            }
                        }
                    });
                }
                if($(connection.getAttachedElements()[0].canvas).hasClass('failEndpoint')){
                    $( '.item , #End' ).each(function( index, item ) {
                        if (item.id !== connection.sourceId){
                            $(item).addClass('glow-fail');
                        }
                    });
                }*/
            }
        });

        jspInstance.bind("connectionDragStop", function(connection) {
            //Hide Endpoint logic
            $( '.jsplumb-endpoint' ).each(function( index, item ) {
                    $(item).css( "opacity", "" );
            });
        });

        jspInstance.bind("beforeDrop", function (info) {
        // No self connections
            if (info.sourceId === info.targetId) { //source and target ID's are same
                return false;
            } else {
                return true;
            }
        });
    }

    $scope.setWorkflowModified = function (state) {
        $scope.modified = state;
    }

    $scope.isInventoryFile = function(step, inputGroupName, input) {
        return step.type == 'ansible' && inputGroupName == 'ansible_options' && input.name == 'inventory_file'
    }

    function buildJSON() {
        var blocks = []
        var filterValidCondition = function(conditions) {
        	var valids = [] ;
        	
        	conditions.forEach(function(cond){
        		if (cond.outputName && cond.checkValue) {
        			valids.push(cond) ;
        		}
        	}) ;
        	
        	return valids ;
        }
        
        diagramContainer.find(" .item,  .item-start-end").each(function(idx, elem) {
            var $elem = $(elem);
            var $wrapper = $elem.parent();
            var data = $elem.data("oeData");
            delete data.$classCounts;
            if (!data.attributes.polling) {
            	data.attributes.interval = cfg.DEFAULT_POLLING_INTERVAL ;
            	data.attributes.successCondition = [] ;
            	data.attributes.failureCondition = [] ;
            }else {
            	data.attributes.successCondition = filterValidCondition(data.attributes.successCondition) ;
            	data.attributes.failureCondition = filterValidCondition(data.attributes.failureCondition) ;
            }
            
            blocks.push($.extend(data,{
                positionX: parseInt($wrapper.css("left"), 10),
                positionY: parseInt($wrapper.css("top"), 10)
            } ));
        });

        $scope.workflowData.document.steps = blocks;
    }

    $scope.saveWorkflow = function() {
        $scope.workflowData.state = 'SAVING';
        buildJSON();
        $http.post(routes.Workflow_save({workflowId : $scope.workflowData.id}),{workflowDoc : $scope.workflowData.document}).then(function (resp) {
            updateWorkflowData(resp,function(){
                $scope.modified = false;
            });
        },
        function(resp){
            $scope.showAlert = true;
            $scope.alert = {status : "INVALID", error : {errorMessage : "An unexpected error occurred while saving the workflow."}};
            if (resp.data.details){
                $scope.alert.error.details = resp.data.details;
            }
            $scope.workflowData.state = 'INVALID';
        });
    }

    function updateWorkflowData(resp,successCallback){
        $scope.workflowData.state = resp.data.state;
        if (successCallback) successCallback();
    }

    $scope.validateWorkflow = function() {
        $scope.workflowData.state = 'VALIDATING';
        delete $scope.alert;
        $http.post(routes.Workflow_validate({workflowId : $scope.workflowData.id})).then(function (resp) {
            $scope.workflowData.state = resp.data.status;
            if (resp.data.status == "INVALID") {
                $scope.showAlert = true;
                $scope.alert = resp.data;
                if ($scope.alert.error) {
                    if (!$scope.alert.error.errorMessage) {
                        $scope.alert.error.errorMessage='Workflow validation failed. There are '+Object.keys(resp.data.error.errorSteps).length+' steps with errors.';
                    }
                }
            } else {
                $scope.showAlert = true;
                $scope.alert = {status : "SUCCESS",success : {successMessage : "Workflow Validated Successfully."}};
            }
        },
        function(){
            $scope.showAlert = true;
            $scope.alert = {status : "INVALID", error : {errorMessage : "An unexpected error occurred while validating the workflow."}};
            $scope.workflowData.state = 'INVALID';
        });
    }

    $scope.testWorkflow = function() {
    	if(wf.hasModifiedWorkflow()) {
    		var choose = confirm("You will be directed to a new location.\n" +
				"Some workflows have been modified but not saved.\nDo you want to proceed?") ;
    		if (choose === true){
    			wf.suppressUnload(true) ;
    		}else {
    			return ;
    		}
    	}
        $scope.workflowData.state = 'TESTING';
        delete $scope.alert;
        var url = routes.ServiceCatalog_showService({serviceId: $scope.workflowData.id});
        window.location.href = url;
    }

    $scope.publishorkflow = function() {
    	if(wf.hasModifiedWorkflow()) {
    		var choose = confirm("You will be directed to a new location.\n" +
    				"Some workflows have been modified but not saved.\nDo you want to proceed?") ;
    		if (choose === true){
    			wf.suppressUnload(true) ;
    		}else {
    			return ;
    		}
    	}
        $scope.workflowData.state = 'PUBLISHING';
        $http.post(routes.Workflow_publish({workflowId : $scope.workflowData.id})).then(function (resp) {
            //redirect automatically on success
            var url = routes.ServiceCatalog_createServiceFromBase({baseService: resp.data.id});
            window.location.href = url;
        });
    }

    $scope.unpublishWorkflow = function() {
        $scope.workflowData.state = 'UNPUBLISHING';
        delete $scope.alert;
        $http.post(routes.Workflow_unpublish({workflowId : $scope.workflowData.id})).then(function (resp) {
                  $scope.workflowData.state = resp.data.status;
                    if (resp.data.status == "INVALID") {
                        $scope.showAlert = true;
                        $scope.alert = resp.data;
                        if ($scope.alert.error) {
                            if (!$scope.alert.error.errorMessage) {
                                $scope.alert.error.errorMessage='Workflow un-publish failed. There are active catalog service using this workflow';
                            }
                        }
                    } else {
                        $scope.showAlert = true;
                        $scope.alert = {status : "SUCCESS",success : {successMessage : "Workflow un-published Successfully."}};
                        updateWorkflowData(resp);
                    }

        },
        function(){
                    $scope.showAlert = true;
                    $scope.alert = {status : "INVALID", error : {errorMessage : "Workflow un-publish failed. There are active catalog service using this workflow"}};
                    $scope.workflowData.state = 'PUBLISHED';
        });
    }
    
    $scope.removeStep = function(stepId) {
        if($scope.selectedId===stepId){
            $scope.selectedId='';
            $scope.closeMenu();
        }
        jspInstance.remove(diagramContainer.find('#' + stepId+'-wrapper'));
        $scope.modified = true;
    }

    $scope.select = function(stepId) {
        $scope.selectedId = stepId;$scope.InputFieldOption=translateList(INPUT_FIELD_OPTIONS,'input.fieldType');
        $scope.AssetOptionTypes=translateList(ASSET_TYPE_OPTIONS,'input');
        var data = diagramContainer.find('#'+stepId).data("oeData");
        $scope.stepData = data;
        
        $scope.menuOpen = true;
        $scope.openPage(0);
    }

    $scope.getUserInputTypeOption = function(required) {
        if (required) {
            return translateList(INPUT_TYPE_OPTIONS_REQUIRED,'input.type');
        } else {
            return translateList(INPUT_TYPE_OPTIONS.concat(INPUT_TYPE_OPTIONS_REQUIRED),'input.type');
        }
    }

    $scope.getDefaultInputFieldType = function(fieldType) {
        switch(fieldType.toLowerCase()) {
            case "integer":
            case "short":
                return "number";
            case "boolean":
                return "boolean";
            default:
                return "text";
        }
    }

    /* creates list of objects for select one drop downs
     * translates key.id from messages file for the name
     */
    function translateList(idList,key) {
        var translateList = [];
        idList.forEach(function(id) {
            translateList.push({id:id, name:translate(key+'.'+id)});
        });
        return translateList;
    }

    $scope.getStepIconClass = function(stepType) {
        return getStepIconClass(stepType);
    }

	var draggableNodeTypes = {"shellNodeType":shellNodeType, "localAnsibleNodeType":localAnsibleNodeType, "remoteAnsibleNodeType":remoteAnsibleNodeType, "restAPINodeType":restAPINodeType, "viprRestAPINodeType":viprRestAPINodeType, "workflowNodeType":workflowNodeType}
    function getStepIconClass(stepType){
        var stepIconClass = "builder-step-icon";
        if(stepType != null) {
            switch(stepType.toLowerCase()){
                case draggableNodeTypes.shellNodeType:
                    stepIconClass = "builder-script-icon";
                    break;
                case draggableNodeTypes.localAnsibleNodeType:
                    stepIconClass = "builder-ansible-icon";
                    break;
                case draggableNodeTypes.remoteAnsibleNodeType:
                    stepIconClass = "builder-remote-ansible-icon";
                    break;
                case draggableNodeTypes.workflowNodeType:
                    stepIconClass = "builder-workflow-icon";
                    break;
                case draggableNodeTypes.restAPINodeType:
                    stepIconClass = "builder-rest-icon";
                    break;
                case draggableNodeTypes.viprRestAPINodeType:
                    stepIconClass = "builder-vipr-icon";
                    break;
            }
        }
        return stepIconClass;
    }

    $scope.hoverErrorIn = function(id) {
        $scope.alert.error.errorSteps[id].visible = true;
    }

    $scope.hoverErrorOut = function(id) {
        $scope.alert.error.errorSteps[id].visible = false;
    }

    $scope.hoverInputErrorIn = function(step,inputGroup,input) {
        $scope.alert.error.errorSteps[step].errorInputGroups[inputGroup].errorInputs[input].visible = true;
    }

    $scope.hoverInputErrorOut = function(step,inputGroup,input) {
        $scope.alert.error.errorSteps[step].errorInputGroups[inputGroup].errorInputs[input].visible = false;
    }

    $scope.getInputError = function(step,inputGroup,input) {
        if ('alert' in $scope && 'error' in $scope.alert && 'errorSteps' in $scope.alert.error && step in $scope.alert.error.errorSteps){
            var stepError = $scope.alert.error.errorSteps[step];
            if ('errorInputGroups' in stepError && inputGroup in stepError.errorInputGroups && 'errorInputs' in stepError.errorInputGroups[inputGroup] && input in stepError.errorInputGroups[inputGroup].errorInputs) {
                var inputError = stepError.errorInputGroups[inputGroup].errorInputs[input];
                return inputError;
            }
        }
    }

    $scope.getStepErrorMessage = function(stepId , errorGroup) {
        if (!$scope.alert || !$scope.alert.error || !$scope.alert.error.errorSteps) {
            return undefined ;
        }
        var stepError = $scope.alert.error.errorSteps[stepId];
        if (!stepError) {
            return undefined ;
        }
        var errors = [] ;
        if (stepError.errorMessages) {
            errors = errors.concat(stepError.errorMessages) ;
        }

        if (stepError.errors) {
            var msg = "Step has " ;
            if (!errorGroup) {
                if (stepError.errors.input) {
                    msg += stepError.errors.input + " input errors" ;
                }

                if (stepError.errors.property) {
                    msg += (stepError.errors.input ? ", " : "") + stepError.errors.property + " property errors" ;
                }
            }else if (!stepError.errors[errorGroup]) {
                return undefined ;
            }else {
                msg += stepError.errors[errorGroup] + " " + errorGroup + " errors" ;
                return msg ;
            }
            errors.push(msg) ;
        }

        var errMsg = "" ;
        for (i in errors) {
            errMsg += "<li>" + errors[i] + "</li>" ;
        }
        return $sce.trustAsHtml(errMsg) ;
    }

    $scope.refreshStepError = function(id) {
        var stepError = $scope.alert.error.errorSteps[id];
        var errorCount = 0;
        if ('errorInputGroups' in stepError){
            for(var inputGroup in stepError.errorInputGroups) {
                if(stepError.errorInputGroups.hasOwnProperty(inputGroup)) {
                    if('errorInputs' in stepError.errorInputGroups[inputGroup]) {
                        errorCount += Object.keys(stepError.errorInputGroups[inputGroup].errorInputs).length;
                    }
                }
            }
            if (errorCount > 0){
                var errMsg = "Step has "+errorCount+" input errors" ;
                if (!stepError.errors) {
                    stepError.errors = {} ;
                }
                stepError.errors.input = errorCount ;
            }
        }

        if ('errorStepAttributes' in stepError) {
            errorCount = Object.keys(stepError.errorStepAttributes).length ;
            if (errorCount > 0 ) {
                if (!stepError.errors) {
                    stepError.errors = {} ;
                }
                stepError.errors.property = errorCount ;
            }
        }
    }

    $scope.getStepFieldError = function (stepId , group , field) {
        if (!$scope.getStepErrorMessage(stepId , group)) {
            return "" ;
        }
        var groupRawKey ;
        if (group === 'input') {
            groupRawKey = 'errorInputGroups' ;
        }else if (group  === 'property') {
            groupRawKey = 'errorStepAttributes' ;
        }else {
            return "" ;
        }

        var stepGroupError =  $scope.alert.error.errorSteps[stepId][groupRawKey] ;
        if (!stepGroupError[field]) {
            return "" ;
        }

        if (stepGroupError[field].errorMessages.length === 1) {
            return stepGroupError[field].errorMessages[0] ;
        }
        var err = "" ;
        for (e in stepGroupError[field].errorMessages) {
            err += ("<li>" + e + "</li>") ;
        }

        return err ;
    }

    $scope.isEmpty = function(obj) {
        return (obj === undefined || obj === null || Object.keys(obj).length === 0);
    }

    function loadStep(step) {
        if(!step.positionY || !step.positionX){
            return;
        }

        var stepId = step.id;
        var stepName = step.friendlyName;

        //create element html
        var stepDivID = stepId + "-wrapper";
        var trimmedStepName = stepName;
        if (stepName.length > 70)
            trimmedStepName = stepName.substring(0,65)+'...';
        var stepHTML =
        "<div id="+stepDivID+" class='example-item-card-wrapper' ng-class=\"{'highlighted':(selectedId == '" + stepId + "' && menuOpen)}\">"+
            "<div ng-if='alert.error.errorSteps." + stepId + "' ng-init='refreshStepError(\"" + stepId + "\")' ng-class=\"{'visible':alert.error.errorSteps."+stepId+".visible}\" class='custom-error-popover custom-error-step-popover top'>"+
                "<div class='arrow'></div><div class='custom-popover-content' ng-bind-html='getStepErrorMessage(\"" + stepId + "\")'></div>"+
            "</div>"+
            "<span id='"+stepId+"-error'  class='glyphicon item-card-error-icon failure-icon' ng-if='alert.error.errorSteps."+stepId+"' ng-mouseover='hoverErrorIn(\"" + stepId + "\")' ng-mouseleave='hoverErrorOut(\"" + stepId + "\")'></span>"+
            "<span id='"+stepId+"-polling' class='glyphicon item-card-polling-icon polling-icon' ng-show='workflowData.stepDict[\""+stepId+"\"].attributes.polling'></span>" + 
            "<div  class='button-container'>"+
                "<a ng-click='removeStep(\"" + stepId + "\")'><div class='builder-removeStep-icon'></div></a>"+
                "<a class='button-edit-step' ng-click='select(\"" + stepId + "\")'><div class='builder-editStep-icon'></div></a>"+
            "</div>"+
            "<div id='"+stepId+"'  class='item'>"+
                "<div class='step-type-image " + getStepIconClass(step.type) + "'>"+
                "</div>"+
                "<div class='itemText'>"+trimmedStepName+"</div>"+
            "</div>"+
        "</div>";

        if (stepId === "Start" || stepId === "End"){
            var stepSEHTML =
            "<div id="+stepDivID+" class='example-item-card-wrapper'>"+
                "<div ng-if='alert.error.errorSteps." + stepId + "' style='bottom: 60px; min-width: 220px;' ng-init='refreshStepError(\"" + stepId + "\")' ng-class=\"{'visible':alert.error.errorSteps."+stepId+".visible}\" class='custom-error-popover custom-error-step-popover top'>"+
                    "<div class='arrow'></div>"+
                    "<div class='custom-popover-content' ng-bind-html='getStepErrorMessage(\"" + stepId + "\")'></div>"+
                "</div>"+
                "<span id='"+stepId+"-error'  class='glyphicon glyphicon-remove-sign item-card-error-icon failure-icon' ng-if='alert.error.errorSteps."+stepId+"' ng-mouseover='hoverErrorIn(\"" + stepId + "\")' ng-mouseleave='hoverErrorOut(\"" + stepId + "\")'></span>"+
                "<div id='"+stepId+"' class='item-start-end' ng-class=\"{'highlighted':selectedId == '" + stepId + "'}\">"+
                    "<div class='itemTextStartEnd'>"+stepName+"</div>"+
                "</div>"+
            "</div>";
            $(stepSEHTML).appendTo(diagramContainer);
        } else {
            $(stepHTML).appendTo(diagramContainer);
        }
        var theNewItemWrapper = diagramContainer.find(' #' + stepId+'-wrapper');
        var theNewItem = diagramContainer.find(' #' + stepId);

        //add data
        if(!step.operation) {step.operation = step.name}
        initStepAttribute(step) ;
        theNewItem.data("oeData",step);

        //set position of element
        $(theNewItemWrapper).css({
            'top': step.positionY,
            'left': step.positionX
        });

        //add jsPlumb options
        if (stepId !== "Start"){
            jspInstance.makeTarget(diagramContainer.find(' #'+stepId), targetParams);
        }
        if(stepId !== "End"){
            jspInstance.addEndpoint(diagramContainer.find(' #'+stepId), {uuid:stepId+"-pass"}, passEndpoint);
        }
        if(stepId !== "Start" && stepId !== "End"){
            jspInstance.addEndpoint(diagramContainer.find(' #'+stepId), {uuid:stepId+"-fail"}, failEndpoint);
        }
        jspInstance.draggable(diagramContainer.find(' #'+stepId+'-wrapper'));

        //updates angular handlers for the new element
        $compile(theNewItemWrapper)($scope);
    }
    
    function initStepAttribute(step) {
    	var defaultAttr =  {
    			timeout: cfg.DEFAULT_OP_TIMEOUT ,
    			waitForTask: cfg.DEFAULT_WAIT_FOR_TASK ,
    			polling: cfg.DEFAULT_POLLING ,
    			interval: cfg.DEFAULT_POLLING_INTERVAL ,
    			successCondition: [] ,
    			failureCondition: []
    		};
    	if (!step.attributes) {
    		step.attributes = angular.copy(defaultAttr) ;
    	}
    	
    	var defaultKeys = Object.keys(defaultAttr)
    	for (var idx = 0 ; idx < defaultKeys.length ; idx++ ) {
    		var k = defaultKeys[idx] ;
    		if (step.attributes[k] === undefined) {
    			if (k === 'successCondition' || k === 'failureCondition') {
    				step.attributes[k] = [] ;
    			}else{
    				step.attributes[k] = defaultAttr[k] ;
    			}
    		}
    		
    		if (step.attributes[k] === 'true' || step.attributes[k] === 'false') {
    			step.attributes[k] = (step.attributes[k] === 'true') ;
    		}
    	}

    	//we should  keep it as what is responded so that the error message should  make sense
    	/*if (step.attributes.interval === 0) {
    		step.attributes.interval = defaultAttr.interval ;
    	}*/
    }

    function loadConnections(step) {
        if(step.next){
            if(step.next.defaultStep){
                var passEndpoint = jspInstance.getEndpoint(step.id+"-pass");
                jspInstance.connect({source:passEndpoint, target:diagramContainer.find('#' + step.next.defaultStep), paintStyle:{ strokeStyle:"#3fac49", lineWidth:1 }});
            }
            if(step.next.failedStep){
                var failEndpoint = jspInstance.getEndpoint(step.id+"-fail");
                jspInstance.connect({source:failEndpoint, target:diagramContainer.find('#' + step.next.failedStep), paintStyle:{ strokeStyle:"#ee3825", lineWidth:1 }});
            }
        }
    }

    function loadJSON(workflowDocument) {

        //load steps with position data
        workflowDocument.steps.forEach(function(step) {
            loadStep(step);
        });

        //load connections
        workflowDocument.steps.forEach(function(step) {
            loadConnections(step);
        });

    }

    $scope.reset = function() {
        jspInstance.deleteEveryEndpoint();
        diagramContainer.find(' .example-item-card-wrapper').each( function(idx,elem) {
            var $elem = $(elem);
            $elem.remove();
        });
        $scope.workflowData = {};
    }

    $scope.menuOpen = false;

    $scope.openPage = function(pageId){
        $scope.menuOpen = true;
    }

    $scope.toggleMenu = function(){
        $scope.menuOpen = !$scope.menuOpen;
    }

    $scope.closeMenu = function() {
        $scope.menuOpen = false;
    }

    $scope.closeAlert = function() {
        $scope.showAlert = false;
    }
}]);

