    /*
    Dummy data for testing functionality
        dummyWorkflowData - represents newly created workflow
        dummyWF - represents workflow data saved by wfbuilder
    TODO: replace dummy data with API data
    */
    var dummyWorkflowData = {
           "name":"",
           "description":"Create Volumes if fails delete the created volumes. Send Email about the Workflow status"
       };

    var dummyWF = {
                   "name":"demo",
                   "description":"Create Volumes if fails delete the created volumes. Send Email about the Workflow status",
                   "steps":[
                      {
                         "positionX": 2110,
                         "positionY": 2098,
                         "id":"Start",
                         "friendly_name":"Start",
                         "next":{
                            "default":"GoBig"
                         }
                      },
                      {
                        "positionX": 2323,
                        "positionY": 2098,
                         "id":"GoBig",
                         "operation":"com.emc.storageos.model.orchestration.internal.BlockServiceCreateVolume",
                         "description":"Create Volumes",
                         "friendly_name":"Create Volume",
                         "type":"ViPR REST API",
                         "input":{
                            "size":{
                               "type":"InputFromUser",
                               "friendly_name":"CreateVolume Size",
                               "required":"true",
                               "default_value":"1GB",
                               "value":"",
                               "group":"Provisioning",
                               "locked":""
                            },
                            "name":{
                               "type":"InputFromUser",
                               "friendly_name":"Create Volume Name",
                               "required":"true",
                               "default_value":"Mozart-Vol",
                               "value":"",
                               "group":"Provisioning",
                               "locked":""
                            },
                            "count":{
                               "type":"InputFromUser",
                               "friendly_name":"Num of volumes to create",
                               "required":"true",
                               "default_value":"1",
                               "value":"",
                               "group":"Provisioning"
                            },
                            "varray":{
                               "type":"AssetOption",
                               "friendly_name":"Varray",
                               "required":"true",
                               "default_value":"urn:storageos:VirtualArray:9ff1c466-4f17-4d0a-aaf9-df9cb06cfde0:vdc1",
                               "value":"assetType.vipr.virtualArray",
                               "group":"Controller"
                            },
                            "vpool":{
                               "type":"AssetOption",
                               "friendly_name":"Vpool",
                               "required":"true",
                               "default_value":"urn:storageos:VirtualPool:8b81adcd-91c8-422a-bc2d-6d245db66998:vdc1",
                               "value":"assetType.vipr.blockVirtualPool",
                               "group":"Controller"
                            },
                            "project":{
                               "type":"AssetOption",
                               "friendly_name":"Project",
                               "required":"true",
                               "default_value":"urn:storageos:Project:51d2cc03-62ad-4e7e-92b1-e60cf614c84f:global",
                               "value":"assetType.vipr.project",
                               "group":"Controller"
                            },
                        "consistencyGroup":{
                           "type":"InputFromUser",
                               "friendly_name":"consistency group",
                               "required":"false",
                               "default_value":"",
                               "group":"Controller"
                        },
                        "computeResource":{
                           "type":"InputFromUser",
                               "friendly_name":"compute resource",
                               "required":"false",
                               "default_value":"",
                               "group":"Controller"
                         }
                         },
                         "success_criteria":"#task_state == 'pending'",
                         "output":{
                            "CreatedVols":"task.resource.id"
                         },
                         "attributes":{
                            "wait_for_task":true,
                            "timeout":"60"
                         },
                         "next":{
                            "default":"WinBig",
                            "failed":"WE123"
                         }
                      },
                      {
                        "positionX": 2312,
                        "positionY": 2248,
                         "id":"WE123",
                         "operation":"deleteVolumes",
                         "description":" Delete the volumes",
                         "friendly_name":"Delete Volume",
                         "type":"ViPR REST API",
                         "input":{
                            "id":{
                               "type":"FromOtherStepOutput",
                               "friendly_name":"Volumes to be deleted",
                               "value":"GoBig.id"
                            }
                         },
                         "output":{
                            "DeletedVols":"task.resource.id"
                         },
                         "success_criteria":null,
                         "next":{
                            "default":"WinBig"
                         }
                      },
                      {
                        "positionX": 2585,
                        "positionY": 2248,
                         "id":"WinBig",
                         "operation":"com.emc.storageos.model.orchestration.internal.LocalAnsible",
                         "description":"Generic Shell Primitive",
                         "friendly_name":"Send Email",
                         "type":"Ansible Script",
                         "input":{
                            "email":{
                               "type":"InputFromUser",
                               "friendly_name":"Email Send To",
                               "required":"true",
                               "default_value":"noReply@dell.com",
                               "group":"Others"
                            },
                            "Subject":{
                               "type":"InputFromUser",
                               "friendly_name":"Subject of SendEmail",
                               "default_value":"Sending Email…",
                               "group":"Others"
                            },
                            "CreatedVolumes":{
                               "type":"FromOtherStepOutput",
                               "friendly_name":"created Volumes",
                               "value":"GoBig.CreatedVols"
                            },
                            "Deletedvolumes":{
                               "type":"FromOtherStepOutput",
                               "friendly_name":"Deleted Volumes",
                               "required":"false",
                               "value":"WE123.DeletedVols"
                            }
                         },
                         "output":null,
                         "success_criteria":null,
                         "next":{
                            "default":"End"
                          } 
                      },
                      {
                        "positionX": 2839,
                        "positionY": 2248,
                         "friendly_name":"End",
                         "id":"End"
                      }
                   ]
                };

angular.module("portalApp").controller('builderController', function($scope, $rootScope) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")
    $rootScope.$on("addWorkflowTab", function(event, id, name){
       addTab(id,name);
    });

    $scope.workflowTabs = {};

    function addTab(id,name) {
      $scope.workflowTabs[id] = { id:id, name:name, href:'#'+id };
    }
    $scope.closeTab = function(tabID){
        delete $scope.workflowTabs[tabID];
        $(".nav-tabs li").children('a').first().click();
    };
})
.controller('treeController', function($element, $scope, $compile, $http, $rootScope) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")

    var jstreeContainer = $element.find('#jstree_demo');

    // -- populate tree data
    //TODO: get ViPR Library nodes from API (pending)
    var dirJSON = [
        {"id":"myLib", "parent":"#","text":"My Library"},
        {"id":"viprLib","parent":"#","text":"ViPR Library"},
        {"id":"viprrest","parent":"viprLib","text":"ViPR REST Primitives"}
    ]


    $http.get(routes.WF_directories()).then(function (data) {
        initializeJsTree(dirJSON.concat(data.data))
    });
    // --

    function initializeJsTree(dirJSON){
        $element.find(".search-input").keyup(function() {
            var searchString = $(this).val();
            jstreeContainer.jstree('search', searchString);
        });

        jstreeContainer.jstree({
            "core": {
                "animation": 0,
                "check_callback": true,
                "themes": {"stripes": false},
                "data": dirJSON
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
                "default": {
                    "icon": "glyphicon glyphicon-folder-close",
                    "valid_children": ["default", "file"]
                },
                "file": {
                    "icon": "glyphicon glyphicon-file",
                    "valid_children": [],
                    "li_attr": {"class": "draggable-card"}
                }
            },
            "plugins": [
                "search", "state", "types", "wholerow"
            ],
            "search" : {
                  'case_sensitive' : false,
                  'show_only_matches' : true
            },
            "contextmenu" : {
                 "items": function($node) {
                     var tree = jstreeContainer.jstree(true);
                     return {
                         "Create": {
                             "separator_before": false,
                             "separator_after": false,
                             "label": "Create",
                             "submenu": {
                                 "create_file" : {
                                     "seperator_before" : false,
                                     "seperator_after" : false,
                                     "label" : "Workflow",
                                     action : function () {
                                         $node = tree.create_node($node,{"type":"file"});
                                         tree.edit($node);
                                     }
                                 },
                                 "create_folder" : {
                                     "seperator_before" : false,
                                     "seperator_after" : false,
                                     "label" : "Folder",
                                     action : function () {
                                         $node = tree.create_node($node);
                                         tree.edit($node);
                                     }
                                 }
                             }
                         },
                         "Rename": {
                             "separator_before": false,
                             "separator_after": false,
                             "label": "Rename",
                             "action": function () {
                                 tree.edit($node);
                             }
                         },
                         "Remove": {
                             "separator_before": false,
                             "separator_after": false,
                             "label": "Remove",
                             "action": function () {
                                 tree.delete_node($node);
                             }
                         },
                         "Preview": {
                             "separator_before": false,
                             "separator_after": false,
                             "label": "Preview",
                             "action": function () {
                                 previewNode($node);
                             }
                         }
                     };
                 }
            }
        }).on('ready.jstree', function() {
            jstreeContainer.find( ".draggable-card" ).draggable({handle: "a",scroll: false,helper: getDraggableStepHTML,appendTo: 'body',cursorAt: { top: 8, left: -16 }});
        }).bind("rename_node.jstree clear_search.jstree search.jstree open_node.jstree", function() {
            jstreeContainer.find( ".draggable-card" ).draggable({handle: "a",scroll: false,helper: getDraggableStepHTML,appendTo: 'body',cursorAt: { top: 0, left: 0 }});
        })
    }

    var getDraggableStepHTML= function(event){
        var stepName=event.target.text;
        var treeId = event.target.parentElement.id
        if (!stepName) {
            stepName=event.target.parentElement.text;
            treeId = event.target.parentElement.parentElement.id
        }
        var $item = '<div style="z-index:999;"class="item"><div class="itemText">' + stepName + '</div></div>';

        //move this
        var itemData = jstreeContainer.jstree(true).get_json(treeId).data;
        $element.data("primitiveData",itemData);

        return $( $item );
    }

    // jstree actions
    //TODO: do error handling on all actions
    jstreeContainer.on("rename_node.jstree", renameDir);
    jstreeContainer.on("delete_node.jstree", deleteDir);
    jstreeContainer.on("select_node.jstree", selectDir);

    // This method will create tab view for workflows
    function previewNode(node) {
        $rootScope.$emit("addWorkflowTab", node.id,node.text);
    }

    function createDir(event, data) {
        if ("file" !== data.node.type) {
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
        if ("file" !== data.node.type) {
            $http.get(routes.WF_directory_delete({"id": data.node.id}));
        }
        else {
            $http.get(routes.Workflow_delete({"workflowID": data.node.id, "dirID": data.parent}));
        }
    };

    function renameDir(event, data) {
        // Identifying if node is not saved to DB yet and creating it.
        if (!(data.node.id).startsWith("urn")) {
            createDir(event, data);
        }
        else {
            if ("file" !== data.node.type) {
                $http.get(routes.WF_directory_edit_name({"id": data.node.id, "newName": data.text}));
            }
            else {
                $http.get(routes.Workflow_edit_name({"id": data.node.id, "newName": data.text}));
            }
        }
    };

    var validActionsOnMyLib = ["addFolder", "addWorkflow"]
    var validActionsOnDirectory = ["addFolder", "addWorkflow", "deleteNode", "editNode"]
    var validActionsOnWorkflow = ["deleteNode", "editNode", "openEditor"]
    var allActions = ["addFolder", "addWorkflow", "deleteNode", "editNode", "openEditor"]
    var viprLibIDs = ["viprrest", "viprLib"]

    var validActions = [];
    function selectDir(event, data) {
        // If current node is vipr library or its parent is vipr library, disable all
        if($.inArray(data.node.id, viprLibIDs) > -1 || $.inArray(data.node.parent, viprLibIDs) > -1) {
            // ViPR Library nodes - disable all buttons
            validActions = [];
        }
        else if("myLib" === data.node.id) {
            // My Library root
            validActions = validActionsOnMyLib;
        }
        else if ("file" === data.node.type) {
            // Workflows
            validActions = validActionsOnWorkflow;
        }
        else {
            // Other directories in My Library
            validActions = validActionsOnDirectory;
        }

        // Enable all validActions, disable others
        $.each(allActions, function( index, value ) {
            if($.inArray(value, validActions)!== -1) {
                $('#'+value).prop("disabled",false);
            }
            else {
                $('#'+value).prop("disabled",true);
            }
        });
    };


    // Methods for JSTree actions
    $scope.addFolder = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected();
        if(!sel.length) { return false; }
        sel = sel[0];
        sel = ref.create_node(sel);
        if(sel) {
            ref.edit(sel);
        }
    }

    $scope.addWorkflow = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected();
        if(!sel.length) { return false; }
        sel = sel[0];
        sel = ref.create_node(sel, {"type":"file"});
        if(sel) {
            ref.edit(sel);
        }
    }

    $scope.editNode = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected();
        if(!sel.length) { return false; }
        sel = sel[0];
        ref.edit(sel);
    };

    $scope.deleteNode = function() {
        var ref = jstreeContainer.jstree(true),
            sel = ref.get_selected();
        if(!sel.length) { return false; }
        ref.delete_node(sel);
    };

    $scope.openWorkflow = function() {
        var selectedNode = jstreeContainer.jstree(true).get_selected(true)[0];
        $rootScope.$emit("addWorkflowTab", selectedNode.id.replace(/:/g,'') ,selectedNode.text);
    }
})

.controller('tabController', function($element, $scope, $compile, $http) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")

    var diagramContainer = $element.find('#diagramContainer');
    var sbSite = $element.find('#sb-site');
    var treecontroller = $element.find('#theSidebar');
    var jspInstance;
    var workflowData;
    $scope.selectedId = '';
    //TODO: get workflowData from the API or user input and remove dummy data
    workflowData = jQuery.extend(true, {}, dummyWorkflowData);

    initializeJsPlumb();
    initializePanZoom();

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
        $panzoom.parent().on('mousewheel.focal', function(e) {
            e.preventDefault();
            var delta = e.delta || e.originalEvent.wheelDeltaY;
            if (delta !== 0) {
                var zoomOut = delta < 0;
                $panzoom.panzoom('zoom', zoomOut, {
                    animate: false,
                    increment: 0.1,
                    focal: e
                });
            }
        });
        $panzoom.panzoom("pan", -2000 + widthHalf, -2000 + heightHalf, {
            relative: false
        });
        $panzoom.on('panzoomzoom', function(e, panzoom, scale) {
            jspInstance.setZoom(scale);
        });
    }

    function initializeJsPlumb(){
        //initialize jsPlumb, will need to make instantiable
        jspInstance = jsPlumb.getInstance();
        jspInstance.importDefaults({
            DragOptions: {
                cursor: "none"
            },
            ConnectionOverlays: [
                ["Arrow", {
                    location: 1,
                    visible:true,
                    id:"ARROW",
                    width: 25,
                    length: 25

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
        endpoint: ["Dot", {
            radius: 10
        }],
        isSource: true,
        connector: ["Flowchart", {
            cornerRadius: 5
        }],
        anchors: [1, 0.5, 1, 0,10,0],
        cssClass: "passEndpoint"
    };

    var failEndpoint = {
        endpoint: ["Dot", {
            radius: 10
        }],
        isSource: true,
        connector: ["Flowchart", {
            cornerRadius: 5
        }],
        anchors: [0.5, 1, 0, 1,0,10],
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
        //TODO:remove friendly_name, it will be included in step data already
        var stepData = jQuery.extend(true, {}, treecontroller.data("primitiveData"));
        stepData.id = randomIdHash;
        stepData.positionY = positionY;
        stepData.positionX = positionX;

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
                sourceNext.default=connection.targetId
            }
            if (sourceEndpoint.hasClass("failEndpoint")) {
                sourceNext.failed=connection.targetId
            }
            sourceData.next=sourceNext;
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
                delete sourceData.next.default;
            }
            if (sourceEndpoint.hasClass("failEndpoint")) {
                delete sourceData.next.failed;
            }
            sourceData.next=sourceNext;
        });
    }
    /*
    Functions for creating JSON from jsplumb diagram and for creating diagram form JSON
        These functions will be used within export/import/save/load
    */
    $scope.buildJSON = function() {
        var blocks = []
        diagramContainer.find(" .item").each(function(idx, elem) {
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
        workflowData.steps = blocks;

        dummyWF = jQuery.extend(true, {}, workflowData);
    }

    $scope.removeStep = function(stepId) {
        jspInstance.remove(diagramContainer.find('#' + stepId));
    }

    $scope.select = function(stepId) {
        $scope.selectedId = stepId;
        var data = diagramContainer.find('#'+stepId).data("oeData");
        $scope.stepData = data;
    }

    function loadStep(step) {
        if(!step.positionY || !step.positionX){
            return;
        }

        var stepId = step.id;
        var stepName = step.friendly_name;

        //create element html
        //TODO: move html to separate location instead of building in JS when design available
        var $itemWrapper = '<div id="' + stepId + '-wrapper" class="example-item-card-wrapper"></div>';
        var $buttonContainer = '<div class="button-container"></div>';
        var $editButton = '<a class="glyphicon glyphicon-pencil button-step-close" ng-click="select(\''+stepId+'\')"></a>';
        var $closeButton = '<a class="glyphicon glyphicon-remove button-step-close" ng-click="removeStep(\''+stepId+'-wrapper\')"></a>';
        var $item = '<div id="' + stepId + '" class="item" ng-class="{\'highlighted\':selectedId == \'' + stepId + '\'}"><div class="itemText">' + stepName + '</div></div>';
        $($closeButton).appendTo(diagramContainer).wrap($itemWrapper).after($item).wrap($buttonContainer).before($editButton);
        var theNewItemWrapper = diagramContainer.find(' #' + stepId+'-wrapper');
        var theNewItem = diagramContainer.find(' #' + stepId);

        //add data
        theNewItem.data("oeData",step);

        //set position of element
        $(theNewItemWrapper).css({
            'top': step.positionY,
            'left': step.positionX
        });

        //add jsPlumb options
        jspInstance.addEndpoint(diagramContainer.find(' #'+stepId), {uuid:stepId+"-pass"}, passEndpoint);
        jspInstance.makeTarget(diagramContainer.find(' #'+stepId), targetParams);
        jspInstance.addEndpoint(diagramContainer.find(' #'+stepId), {uuid:stepId+"-fail"}, failEndpoint);
        jspInstance.draggable(diagramContainer.find(' #'+stepId+'-wrapper'));

        //updates angular handlers for the new element
        $compile(theNewItemWrapper)($scope);
    }

    function loadConnections(step) {
        if(step.next){
            if(step.next.default){
                var passEndpoint = jspInstance.getEndpoint(step.id+"-pass");
                jspInstance.connect({source:passEndpoint, target:step.next.default});
            }
            if(step.next.failed){
                var failEndpoint = jspInstance.getEndpoint(step.id+"-fail");
                jspInstance.connect({source:failEndpoint, target:step.next.failed});
            }
        }
    }

    $scope.loadJSON = function() {

        //TODO: replace dummyWF with json from API or import
        var loadedWorkflow=jQuery.extend(true, {}, dummyWF);
        workflowData.name = loadedWorkflow.name;
        workflowData.description = loadedWorkflow.description;

        //load steps with position data
        loadedWorkflow.steps.forEach(function(step) {
            loadStep(step);
        });

        //load connections
        loadedWorkflow.steps.forEach(function(step) {
            loadConnections(step);
        });
    }

    $scope.reset = function() {
        jspInstance.deleteEveryEndpoint();
        diagramContainer.find(' .example-item-card-wrapper').each( function(idx,elem) {
            var $elem = $(elem);
            $elem.remove();
        });
        workflowData = {};
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

