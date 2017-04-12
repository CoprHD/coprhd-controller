
var folderNodeType = "FOLDER";
var workflowNodeType = "Workflow";
var shellNodeType = "script";
var localAnsibleNodeType = "ansible"
var restAPINodeType = "rest"
var viprRestAPINodeType = "vipr";

angular.module("portalApp").controller('builderController', function($scope, $rootScope) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")
    $rootScope.$on("addWorkflowTab", function(event, id, name){
       addTab(id,name);
    });

    $scope.workflowTabs = {};
    $scope.isWorkflowTabsEmpty = function () {
        return $.isEmptyObject($scope.workflowTabs);
    };

    function addTab(id,name) {
        var elementid = id.replace(/:/g,'');
        $scope.workflowTabs[elementid] = { id:id, elementid:elementid, name:name, href:'#'+elementid };
    }
    $scope.closeTab = function(tabID){
        delete $scope.workflowTabs[tabID];
        $(".workflow-nav-tabs li").children('a').first().click();
    };
})
.controller('treeController', function($element, $scope, $compile, $http, $rootScope) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")

    $scope.libOpen = true;
    $scope.toggleLib = function() {
        $("#libSidebar").toggleClass("collapsedLib");
        $("#builderController").toggleClass("col-md-12 col-md-8");

        $scope.libOpen = !$scope.libOpen;
    }

    // if True, will enable menus on jstree
    $scope.libraryMenu = true;

    $scope.initializeTreeConfig = function(library) {
        $scope.libraryMenu = library;
    }

    var jstreeContainer = $element.find('#jstree_demo');

    var fileNodeTypes = [shellNodeType, localAnsibleNodeType, restAPINodeType, workflowNodeType]
    var viprLibIDs = ["viprrest", "viprLib"]

    initializeJsTree();

    function initializeJsTree(){
        var to = null;
        var searchElem = $element.find(".search-input");
        searchElem.keyup(function() {
            if(to) { clearTimeout(to); }
                to = setTimeout(function() {
                  var searchString = searchElem.val();
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
                    "max_depth": 4,
                    "valid_children": ["root"]
                },
                "root": {
                    "icon": "glyphicon glyphicon-folder-close",
                    "valid_children": ["default"]
                },
                "FOLDER": {
                    "icon": "/public/img/customServices/Folder.png",
                    "valid_children": ["Workflow","FOLDER", "script", "ansible"]
                },
                "Workflow": {
                    "icon": "/public/img/customServices/UserDefinedWF.png",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "script": {
                    "icon": "/public/img/customServices/UserDefinedOperation.png",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "ansible": {
                    "icon": "/public/img/customServices/UserDefinedOperation.png",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                },
                "vipr": {
                    "icon": "/public/img/customServices/ViPROperation.png",
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
            jstreeContainer.find( ".draggable-card" ).draggable({handle: "a",scroll: false,helper: getDraggableStepHTML,appendTo: 'body',cursorAt: { top: 8, left: -16 }});
        }).bind("rename_node.jstree clear_search.jstree search.jstree open_node.jstree", function() {
            jstreeContainer.find( ".draggable-card" ).draggable({handle: "a",scroll: false,helper: getDraggableStepHTML,appendTo: 'body',cursorAt: { top: 0, left: 0 }});
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
            itemData = {"friendlyName":stepName,"type":workflowNodeType};
        }
        $rootScope.primitiveData = itemData;

        return $( $item );
    }

    // jstree actions
    //TODO: do error handling on all actions
    jstreeContainer.on("rename_node.jstree", renameDir);
    jstreeContainer.on("delete_node.jstree", deleteDir);
    jstreeContainer.on("select_node.jstree", selectDir);

    function createDir(event, data) {
        if (folderNodeType === data.node.type) {
            $http.get(routes.WF_directory_create({"name": data.node.text,"parent": data.node.parent})).then(function (resp) {
                data.instance.set_id(data.node, resp.data.id);
            });
        }
        else {
            $http.get(routes.Workflow_create({"workflowName": data.node.text,"dirID": data.node.parent})).then(function (resp) {
                data.instance.set_id(data.node, resp.data.id);
            });
        }
    };

    function deleteDir(event, data) {
    	// ALIK: add delete logic here
        if (folderNodeType === data.node.type) {
            $http.get(routes.WF_directory_delete({"id": data.node.id}));
        }
        else if (shellNodeType === data.node.type || localAnsibleNodeType === data.node.type) {
        	$http.get(routes.Primitive_delete({"primitiveId": data.node.id, "dirID": data.parent}));
        }
        else {
            $http.get(routes.Workflow_delete({"workflowID": data.node.id, "dirID": data.parent}));
        }

        // By default select "My Library"
        jstreeContainer.jstree("select_node", "myLib");
    };

    function renameDir(event, data) {
        // Identifying if node is not saved to DB yet and creating it.
        if (!(data.node.id).startsWith("urn")) {
            createDir(event, data);
            addMoreOptions(data.node.parent, folderNodeType, "");
        }
        else {
            if (folderNodeType === data.node.type) {
                $http.get(routes.WF_directory_edit_name({"id": data.node.id, "newName": data.text}));
            }
            else {
                $http.get(routes.Workflow_edit_name({"id": data.node.id, "newName": data.text}));
            }

            addMoreOptions(data.node.id, data.node.type, data.node.parent);
        }
    };

    // default preview
    $scope.shellPreview = false;
    $scope.noPreview = true;

    var optionsHTML = `
    <div id="treeMoreOptions" class="btn-group" style="float:right;padding-right:5px;">
       <button type="button" class="btn btn-xs btn-default dropdown-toggle" title="Options" data-toggle="dropdown" style="background-color:#b3cadb; border-color:#b3cadb;">
           <span class="glyphicon"><img src="/public/img/customServices/Options.png" height="20" width="24"></span>
       </button>
       <ul class="dropdown-menu dropdown-menu-right" role="menu">
            <li id="addWorkflowMenu" style="display:none;"><a  href="#" ng-click="addWorkflow();">Create Workflow</a></li>
            <li id="addShellMenu" style="display:none;"><a  href="#" ng-click="openShellScriptModal();">Create Shell Script</a></li>
            <li id="addLAMenu" style="display:none;"><a  href="#" ng-click="openLocalAnsibleModal();">Create Local Ansible</a></li>
            <li id="addRestMenu" style="display:none;"><a  href="#" ng-click="openRestAPIModal();">Create Rest API</a></li>
            <li id="addFolderDivider" role="separator" class="divider" style="display:none;"></li>
            <li id="addFolderMenu" style="display:none;"><a  href="#" ng-click="addFolder();">Create Folder</a></li>
            <li id="editDivider" role="separator" class="divider" style="display:none;"></li>
            <li id="renameMenu" style="display:none;"><a  href="#" ng-click="editNode();">Rename</a></li>
            <li id="editMenu" style="display:none;"><a  href="#" ng-click="editNode();">Edit</a></li>
            <li id="deleteMenu" style="display:none;"><a  href="#" ng-click="deleteNode();">Delete</a></li>
            <li id="editWFMenu" style="display:none;"><a  href="#" ng-click="openWorkflow();">Edit</a></li>
       </ul>
    </div>
    `;

    var validActionsOnMyLib = ["addWorkflowMenu", "addShellMenu", "addLAMenu", "addRestMenu", "addFolderDivider", "addFolderMenu"]
    var validActionsOnFolder = ["addWorkflowMenu", "addShellMenu", "addLAMenu", "addRestMenu", "addFolderDivider", "addFolderMenu", "editDivider", "renameMenu", "deleteMenu"]
    var validActionsOnWorkflow = ["renameMenu", "editWFMenu", "deleteMenu"]
    var validActionsOnMyPrimitives = ["deleteMenu", "editMenu"]

    function addMoreOptions(nodeId, nodeType, parentId) {
        if(!$scope.libraryMenu) return;

        //remove any previous element
        $("#treeMoreOptions").remove();

        // Do not show 'More options' on ViPR Library nodes
        if($.inArray(nodeId, viprLibIDs) > -1 || $.inArray(parentId, viprLibIDs) > -1) {
            return;
        }

        //find anchor with this id and append "more options"
        $('[id="'+nodeId+'"]').children('a').after(optionsHTML);

        // If current node is vipr library or its parent is vipr library, disable all
        if("myLib" === nodeId) {
            // My Library root
            validActions = validActionsOnMyLib;
        }
        else if(workflowNodeType === nodeType){
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

        //TODO: check if we can avoid this search on ID
        var generated = jstreeContainer.jstree(true).get_node(nodeId, true);
        $compile(generated.contents())($scope);
    }


    function selectDir(event, data) {
        addMoreOptions(data.node.id, data.node.type, data.node.parent);

        // Enable/Disable Preview Option
        $scope.shellPreview = false;
        $scope.noPreview = true;
        if (shellNodeType === data.node.type) {
            //preview Shell script
            $scope.shellPreview = true;
            $scope.noPreview = false;
        }
    };

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
        $('#shellPrimitiveDialog').modal('show');
    }

    $scope.openLocalAnsibleModal = function(){
        var scope = angular.element($('#localAnsibleModal')).scope();
        scope.populateModal(false);
        $('#localAnsiblePrimitiveDialog').modal('show');
    }

    $scope.openRestAPIModal = function(){
            var scope = angular.element($('#restAPIModal')).scope();
            scope.populateModal(false);
            $('#restAPIPrimitiveDialog').modal('show');
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

    $scope.deleteNode = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected();
        if(!sel.length) { return false; }
        ref.delete_node(sel);
    };

    $scope.openWorkflow = function() {
        var selectedNode = jstreeContainer.jstree(true).get_selected(true)[0];
        $rootScope.$emit("addWorkflowTab", selectedNode.id ,selectedNode.text);
    }
})

.controller('tabController', function($element, $scope, $compile, $http, $rootScope) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")

    var diagramContainer = $element.find('#diagramContainer');
    var sbSite = $element.find('#sb-site');
    var jspInstance;
    $scope.workflowData = {};
    $scope.stepInputOptions = [];
    $scope.stepOutputOptions = [];
    $scope.modified = false;
    $scope.selectedId = '';

    initializeJsPlumb();
    initializePanZoom();

    function activateTab(tab){
        $('.nav-tabs a[href="#' + tab + '"]').tab('show');
        loadJSON();
        $scope.modified = false;
    };

    $scope.initializeWorkflowData = function(workflowInfo) {
        var elementid = workflowInfo.id.replace(/:/g,'');
        $http.get(routes.Workflow_get({workflowId: workflowInfo.id})).then(function (resp) {
            if (resp.status == 200) {
                $scope.workflowData = resp.data;
                activateTab(elementid);
            } else {
                //TODO: show error for workflow failed to load
            }
        });
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
            //TODO add contain: 'invert'
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
            src:"/public/img/customServices/PassEndpoint.png"
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
            src:"/public/img/customServices/FailEndpoint.png"
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
        endpoint: "Blank",
        filter:":not(a)"
    };


    /*
    Functions for managing step data on jsplumb instance
    */
    function dragEndFunc(e) {
        //set ID and text within the step element
        //TODO: retrieve stepname from step data when API is available
        var randomIdHash = Math.random().toString(36).substring(7);

        //compensate x,y for zoom
        var x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
        var y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
        var scaleMultiplier = 1 / jspInstance.getZoom();;
        var positionY = (y - diagramContainer.offset().top) * scaleMultiplier;
        var positionX = (x - diagramContainer.offset().left) * scaleMultiplier;


        //add data
        var stepData = $rootScope.primitiveData;
        stepData.operation = stepData.id;
        stepData.id = randomIdHash;
        stepData.positionY = positionY;
        stepData.positionX = positionX;

        $scope.modified = true;

        loadStep(stepData);

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
            var inparams = [];
			if("inputGroups" in sourceData && "input_params" in sourceData.inputGroups){
    			inparams = sourceData.inputGroups.input_params.inputGroup;
			}
            for(var inputparam in inparams) {
            	if(inparams.hasOwnProperty(inputparam)) {
            		var inparam_name = inparams[inputparam].name;
            		var stepidconcate = sourceData.id + "." + inparam_name;
            		var stepnameconcate = sourceData.friendlyName + " " + inparam_name
            		
            		$scope.stepInputOptions.push({id:stepidconcate, name:stepnameconcate});
            	}
            }
            var outparams = sourceData.output;
            for(var outputparam in outparams) {
            	if(outparams.hasOwnProperty(outputparam)) {
            		var outparam_name = outparams[outputparam].name;
            		var stepidconcate = sourceData.id + "." + outparam_name;
            		var stepnameconcate = sourceData.friendlyName + " " + outparam_name
            		
            		$scope.stepOutputOptions.push({id:stepidconcate, name:stepnameconcate});
            	}
            }            
            sourceData.next=sourceNext;
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
            // Remove source data after unbind array for input and output from previous steps
            var inparams = [];
			if("inputGroups" in sourceData && "input_params" in sourceData.inputGroups){
    			inparams = sourceData.inputGroups.input_params.inputGroup;
			}
            for(var inputparam in inparams) {
            	if(inparams.hasOwnProperty(inputparam)) {
            		var inparam_name = inparams[inputparam].name;
            		var stepidconcate = sourceData.id + "." + inparam_name;
            		
            		for (var i =0; i < $scope.stepInputOptions.length; i++) {
   						if ($scope.stepInputOptions[i].id === stepidconcate) {
      						$scope.stepInputOptions.splice(i,1);
      						break;
  						 }
  					}
            	}
            }
            var outparams = sourceData.output;
            for(var outputparam in outparams) {
            	if(outparams.hasOwnProperty(outputparam)) {
            		var outparam_name = outparams[outputparam].name;
            		var stepidconcate = sourceData.id + "." + outparam_name;
            		
            		for (var i =0; i < $scope.stepOutputOptions.length; i++) {
   						if ($scope.stepOutputOptions[i].id === stepidconcate) {
      						$scope.stepOutputOptions.splice(i,1);
      						break;
  						 }
  					}
            	}
            }            
            $scope.modified = true;
            $scope.$apply();
        });
    }

    function buildJSON() {
        var blocks = []
        diagramContainer.find(" .item,  .item-start-end").each(function(idx, elem) {
            var $elem = $(elem);
            var $wrapper = $elem.parent();
            var data = $elem.data("oeData");
            delete data.$classCounts;
            blocks.push($.extend(data,{
                positionX: parseInt($wrapper.css("left"), 10),
                positionY: parseInt($wrapper.css("top"), 10)
            } ));
        });

        //TODO: return JSON data so that it can be accessed in Export/SaveWorkflow via this method
        $scope.workflowData.document.steps = blocks;
    }

    $scope.saveWorkflow = function() {
        buildJSON();
        $http.post(routes.Workflow_save({workflowId : $scope.workflowData.id}),{workflowDoc : $scope.workflowData.document}).then(function (resp) {
            checkStateResponse(resp,function(){$scope.modified = false;});
        });
    }

    function checkStateResponse(resp,successCallback,failCallback){
        if (resp.status == 200) {
            $scope.workflowData.state = resp.data.state;
            if (successCallback) successCallback();
        } else {
            if (failCallback) failCallback();
            //TODO: show error
        }
    }

    $scope.validateWorkflow = function() {
        $http.post(routes.Workflow_validate({workflowId : $scope.workflowData.id})).then(function (resp) {
            checkStateResponse(resp);
            var url = routes.ServiceCatalog_showService({serviceId: $scope.workflowData.id});
            window.location.href = url;
        });
    }

    $scope.publishorkflow = function() {
        $http.post(routes.Workflow_publish({workflowId : $scope.workflowData.id})).then(function (resp) {
            checkStateResponse(resp);
        });
    }

    $scope.unpublishWorkflow = function() {
        $http.post(routes.Workflow_unpublish({workflowId : $scope.workflowData.id})).then(function (resp) {
            checkStateResponse(resp);
        });
    }

    $scope.removeStep = function(stepId) {
        if($scope.selectedId===stepId){
            $scope.selectedId='';
            $scope.closeMenu();
        }
        jspInstance.remove(diagramContainer.find('#' + stepId+'-wrapper'));
    }

    $scope.select = function(stepId) {
        $scope.selectedId = stepId;
        $scope.InputFieldOption=[{id:'Integer', name:'Integer'}, {id:'Table', name:'Table'}, {id:'Boolean', name:'Boolean'}, {id:'String', name:'String'}];
        $scope.UserInputTypeOption=[{id:'AssetOption', name:'Asset Option'}, {id:'InputFromUser', name:'Input FromUser'}, {id:'FromOtherStepOutput', name:'From OtherStep Output'}, {id:'FromOtherStepInput', name:'From OtherStep Input'}];
        $scope.AssetOptionTypes=[{id:'assetType.vipr.blockVirtualPool', name:'Block Virtual Pool'}, {id:'assetType.vipr.virtualArray', name:'VirtualArray'}, {id:'assetType.vipr.project', name:'Project'}];
        var data = diagramContainer.find('#'+stepId).data("oeData");
        $scope.stepData = data;
        $scope.menuOpen = true;
        $scope.openPage(0);
    }

	var draggableNodeTypes = {"shellNodeType":shellNodeType, "localAnsibleNodeType":localAnsibleNodeType, "restAPINodeType":restAPINodeType, "viprRestAPINodeType":viprRestAPINodeType, "workflowNodeType":workflowNodeType}
    function getStepIcon(stepType){
        console.log(stepType);
        var stepIcon = "UserDefinedOperation.png";
        if(stepType != null) {
            switch(stepType.toLowerCase()){
                case draggableNodeTypes.shellNodeType:
                    stepIcon = "Script.png";
                    break;
                case draggableNodeTypes.localAnsibleNodeType:
                    stepIcon = "LocalAnsible.png";
                    break;
                case draggableNodeTypes.workflowNodeType:
                    stepIcon = "UserDefinedWF.png";
                    break;
                case draggableNodeTypes.restAPINodeType:
                    stepIcon = "REST.png";
                    break;
                case draggableNodeTypes.viprRestAPINodeType:
                    stepIcon = "ViPRREST.png";
                    break;
            }
        }
        return "/public/img/customServices/" + stepIcon;
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

        var stepHTML = `
        <div id="${stepDivID}" class="example-item-card-wrapper">
            <div  class="button-container">
                <a class="glyphicon glyphicon-pencil button-step-close" ng-click="select('${stepId}')"></a>
                <a class="glyphicon glyphicon-remove button-step-close" ng-click="removeStep('${stepId}')"></a>
            </div>
            <div style="width:25%; float:left; padding-left:3px; padding-top:2px;">
                <span class="glyphicon"><img src="${getStepIcon(step.type)}" height="15" width="18"></span>
            </div>
            <div id="${stepId}"  class="item" ng-class="{\'highlighted\':(selectedId == '${stepId}' && menuOpen)}">
                <div class="itemText">${trimmedStepName}</div>
            </div>
        </div>
        `;

        if (stepId === "Start" || stepId === "End"){
            var stepSEHTML = `
            <div id="${stepDivID}" class="example-item-card-wrapper">
                <div id="${stepId}"  class="item-start-end" ng-class="{\'highlighted\':selectedId == '${stepId}'}">
                    <div class="itemTextStartEnd">${stepName}</div>
                </div>
            </div>
            `;
            $(stepSEHTML).appendTo(diagramContainer);
        } else {
            $(stepHTML).appendTo(diagramContainer);
        }
        var theNewItemWrapper = diagramContainer.find(' #' + stepId+'-wrapper');
        var theNewItem = diagramContainer.find(' #' + stepId);

        //add data
        if(!step.operation) {step.operation = step.name}
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

    function loadJSON() {

        //load steps with position data
        $scope.workflowData.document.steps.forEach(function(step) {
            loadStep(step);
        });

        //load connections
        $scope.workflowData.document.steps.forEach(function(step) {
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

    $scope.activePage = 0;
    $scope.menuOpen = false;

    $scope.openPage = function(pageId){
        $scope.menuOpen = true;
        $scope.activePage = pageId;
    }

    $scope.toggleMenu = function(){
        $scope.menuOpen = !$scope.menuOpen;
    }

    $scope.closeMenu = function() {
        $scope.menuOpen = false;
    }
});

