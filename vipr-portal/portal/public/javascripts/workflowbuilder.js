    /*
    Dummy data for testing functionality
        dummyWorkflowData - represents newly created workflow
        dummyStepData - represents data assosciated with a specific step
        dummyWF - represents workflow data saved by wfbuilder
    TODO: replace dummy data with API data
    */
    var dummyWorkflowData = {
           "WorkflowName":"",
           "Description":"Create Volumes if fails delete the created volumes. Send Email about the Workflow status"
       };

    var dummyStepData = {
         "OpName":"createVolume",
         "Description":"Create Volumes",
         "Type":"ViPR REST API",
         "Input":{
            "Size":{
               "Type":"inputFromUser",
               "FriendlyName":"CreateVolume Size",
               "Required":"true",
               "Default":"",
               "AssetValue":"",
               "Group":"Provisioning",
               "LockDown":""
            },
            "volumeName":{
               "Type":"inputFromUser",
               "FriendlyName":"Create Volume Name",
               "Required":"true",
               "Default":"Mozart-Vol",
               "AssetValue":"",
               "Group":"Provisioning",
               "LockDown":""
            },
            "numOfVolume":{
               "Type":"inputFromUser",
               "FriendlyName":"Num of volumes to create",
               "Required":"true",
               "Default":"1",
               "AssetValue":"",
               "Group":"Provisioning"
            },
            "vArray":{
               "Type":"AssetOption",
               "FriendlyName":"Varray",
               "Required":"true",
               "Default":"",
               "AssetValue":"asset.option.varray",
               "Group":"Controller"
            },
            "vPool":{
               "Type":"AssetOption",
               "FriendlyName":"Vpool",
               "Required":"true",
               "Default":"",
               "AssetValue":"asset.option.vpool",
               "Group":"Controller"
            },
            "project":{
               "Type":"AssetOption",
               "FriendlyName":"Project",
               "Required":"true",
               "Default":"",
               "AssetValue":"asset.option.vpool",
               "Group":"Controller"
            }
         },
         "Output":{
            "createdVols":"ALL task.resource.id"
         },
         "StepAttribute":{
            "WaitForTask":true,
            "Timeout":"60m"
         }
        };

    var dummyWF = {
                    "WorkflowName": "",
                    "Description": "Create Volumes if fails delete the created volumes. Send Email about the Workflow status",
                    "Steps": [
                      {
                        "positionX": 1977,
                        "positionY": 1999,
                        "StepId": "im85huspdbpflmcxr",
                        "FriendlyName": "Create Volume",
                        "OpName": "createVolume",
                        "Description": "Create Volumes",
                        "Type": "ViPR REST API",
                        "Input": {
                          "Size": {
                            "Type": "inputFromUser",
                            "FriendlyName": "CreateVolume Size",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "volumeName": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Create Volume Name",
                            "Required": "true",
                            "Default": "Mozart-Vol",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "numOfVolume": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Num of volumes to create",
                            "Required": "true",
                            "Default": "1",
                            "AssetValue": "",
                            "Group": "Provisioning"
                          },
                          "vArray": {
                            "Type": "AssetOption",
                            "FriendlyName": "Varray",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.varray",
                            "Group": "Controller"
                          },
                          "vPool": {
                            "Type": "AssetOption",
                            "FriendlyName": "Vpool",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          },
                          "project": {
                            "Type": "AssetOption",
                            "FriendlyName": "Project",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          }
                        },
                        "Output": {
                          "createdVols": "ALL task.resource.id"
                        },
                        "StepAttribute": {
                          "WaitForTask": true,
                          "Timeout": "60m"
                        },
                        "Next": {
                          "Default": "pgij9drci8kxcd6lxr",
                          "Failure": "7yjtpb83rn95hotro1or"
                        }
                      },
                      {
                        "positionX": 2391,
                        "positionY": 2108,
                        "StepId": "pgij9drci8kxcd6lxr",
                        "FriendlyName": "e",
                        "OpName": "createVolume",
                        "Description": "Create Volumes",
                        "Type": "ViPR REST API",
                        "Input": {
                          "Size": {
                            "Type": "inputFromUser",
                            "FriendlyName": "CreateVolume Size",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "volumeName": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Create Volume Name",
                            "Required": "true",
                            "Default": "Mozart-Vol",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "numOfVolume": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Num of volumes to create",
                            "Required": "true",
                            "Default": "1",
                            "AssetValue": "",
                            "Group": "Provisioning"
                          },
                          "vArray": {
                            "Type": "AssetOption",
                            "FriendlyName": "Varray",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.varray",
                            "Group": "Controller"
                          },
                          "vPool": {
                            "Type": "AssetOption",
                            "FriendlyName": "Vpool",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          },
                          "project": {
                            "Type": "AssetOption",
                            "FriendlyName": "Project",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          }
                        },
                        "Output": {
                          "createdVols": "ALL task.resource.id"
                        },
                        "StepAttribute": {
                          "WaitForTask": true,
                          "Timeout": "60m"
                        }
                      },
                      {
                        "positionX": 1986,
                        "positionY": 2173,
                        "StepId": "7yjtpb83rn95hotro1or",
                        "FriendlyName": "z",
                        "OpName": "createVolume",
                        "Description": "Create Volumes",
                        "Type": "ViPR REST API",
                        "Input": {
                          "Size": {
                            "Type": "inputFromUser",
                            "FriendlyName": "CreateVolume Size",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "volumeName": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Create Volume Name",
                            "Required": "true",
                            "Default": "Mozart-Vol",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "numOfVolume": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Num of volumes to create",
                            "Required": "true",
                            "Default": "1",
                            "AssetValue": "",
                            "Group": "Provisioning"
                          },
                          "vArray": {
                            "Type": "AssetOption",
                            "FriendlyName": "Varray",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.varray",
                            "Group": "Controller"
                          },
                          "vPool": {
                            "Type": "AssetOption",
                            "FriendlyName": "Vpool",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          },
                          "project": {
                            "Type": "AssetOption",
                            "FriendlyName": "Project",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          }
                        },
                        "Output": {
                          "createdVols": "ALL task.resource.id"
                        },
                        "StepAttribute": {
                          "WaitForTask": true,
                          "Timeout": "60m"
                        },
                        "Next": {
                          "Failure": "btbrmlnqgultzs3y2e29",
                          "Default": "ttyepwky095z74ym0a4i"
                        }
                      },
                      {
                        "positionX": 2220,
                        "positionY": 2231,
                        "StepId": "ttyepwky095z74ym0a4i",
                        "FriendlyName": "x",
                        "OpName": "createVolume",
                        "Description": "Create Volumes",
                        "Type": "ViPR REST API",
                        "Input": {
                          "Size": {
                            "Type": "inputFromUser",
                            "FriendlyName": "CreateVolume Size",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "volumeName": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Create Volume Name",
                            "Required": "true",
                            "Default": "Mozart-Vol",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "numOfVolume": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Num of volumes to create",
                            "Required": "true",
                            "Default": "1",
                            "AssetValue": "",
                            "Group": "Provisioning"
                          },
                          "vArray": {
                            "Type": "AssetOption",
                            "FriendlyName": "Varray",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.varray",
                            "Group": "Controller"
                          },
                          "vPool": {
                            "Type": "AssetOption",
                            "FriendlyName": "Vpool",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          },
                          "project": {
                            "Type": "AssetOption",
                            "FriendlyName": "Project",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          }
                        },
                        "Output": {
                          "createdVols": "ALL task.resource.id"
                        },
                        "StepAttribute": {
                          "WaitForTask": true,
                          "Timeout": "60m"
                        },
                        "Next": {
                          "Default": "nuq104xx4ef1gp2mlsor"
                        }
                      },
                      {
                        "positionX": 2433,
                        "positionY": 2232,
                        "StepId": "nuq104xx4ef1gp2mlsor",
                        "FriendlyName": "q",
                        "OpName": "createVolume",
                        "Description": "Create Volumes",
                        "Type": "ViPR REST API",
                        "Input": {
                          "Size": {
                            "Type": "inputFromUser",
                            "FriendlyName": "CreateVolume Size",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "volumeName": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Create Volume Name",
                            "Required": "true",
                            "Default": "Mozart-Vol",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "numOfVolume": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Num of volumes to create",
                            "Required": "true",
                            "Default": "1",
                            "AssetValue": "",
                            "Group": "Provisioning"
                          },
                          "vArray": {
                            "Type": "AssetOption",
                            "FriendlyName": "Varray",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.varray",
                            "Group": "Controller"
                          },
                          "vPool": {
                            "Type": "AssetOption",
                            "FriendlyName": "Vpool",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          },
                          "project": {
                            "Type": "AssetOption",
                            "FriendlyName": "Project",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          }
                        },
                        "Output": {
                          "createdVols": "ALL task.resource.id"
                        },
                        "StepAttribute": {
                          "WaitForTask": true,
                          "Timeout": "60m"
                        },
                        "Next": {
                          "Default": "tgh820479dfctqhbyb9",
                          "Failure": "rphtli3wwoi8bzdoyldi"
                        }
                      },
                      {
                        "positionX": 2714,
                        "positionY": 2232,
                        "StepId": "tgh820479dfctqhbyb9",
                        "FriendlyName": "w",
                        "OpName": "createVolume",
                        "Description": "Create Volumes",
                        "Type": "ViPR REST API",
                        "Input": {
                          "Size": {
                            "Type": "inputFromUser",
                            "FriendlyName": "CreateVolume Size",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "volumeName": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Create Volume Name",
                            "Required": "true",
                            "Default": "Mozart-Vol",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "numOfVolume": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Num of volumes to create",
                            "Required": "true",
                            "Default": "1",
                            "AssetValue": "",
                            "Group": "Provisioning"
                          },
                          "vArray": {
                            "Type": "AssetOption",
                            "FriendlyName": "Varray",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.varray",
                            "Group": "Controller"
                          },
                          "vPool": {
                            "Type": "AssetOption",
                            "FriendlyName": "Vpool",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          },
                          "project": {
                            "Type": "AssetOption",
                            "FriendlyName": "Project",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          }
                        },
                        "Output": {
                          "createdVols": "ALL task.resource.id"
                        },
                        "StepAttribute": {
                          "WaitForTask": true,
                          "Timeout": "60m"
                        },
                        "Next": {
                          "Default": "pgij9drci8kxcd6lxr",
                          "Failure": "iv5zjcpncqsbbkdcmcxr"
                        }
                      },
                      {
                        "positionX": 2908,
                        "positionY": 2323,
                        "StepId": "iv5zjcpncqsbbkdcmcxr",
                        "FriendlyName": "d",
                        "OpName": "createVolume",
                        "Description": "Create Volumes",
                        "Type": "ViPR REST API",
                        "Input": {
                          "Size": {
                            "Type": "inputFromUser",
                            "FriendlyName": "CreateVolume Size",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "volumeName": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Create Volume Name",
                            "Required": "true",
                            "Default": "Mozart-Vol",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "numOfVolume": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Num of volumes to create",
                            "Required": "true",
                            "Default": "1",
                            "AssetValue": "",
                            "Group": "Provisioning"
                          },
                          "vArray": {
                            "Type": "AssetOption",
                            "FriendlyName": "Varray",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.varray",
                            "Group": "Controller"
                          },
                          "vPool": {
                            "Type": "AssetOption",
                            "FriendlyName": "Vpool",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          },
                          "project": {
                            "Type": "AssetOption",
                            "FriendlyName": "Project",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          }
                        },
                        "Output": {
                          "createdVols": "ALL task.resource.id"
                        },
                        "StepAttribute": {
                          "WaitForTask": true,
                          "Timeout": "60m"
                        },
                        "Next": {
                          "Default": "pgij9drci8kxcd6lxr"
                        }
                      },
                      {
                        "positionX": 2433,
                        "positionY": 2401,
                        "StepId": "rphtli3wwoi8bzdoyldi",
                        "FriendlyName": "a",
                        "OpName": "createVolume",
                        "Description": "Create Volumes",
                        "Type": "ViPR REST API",
                        "Input": {
                          "Size": {
                            "Type": "inputFromUser",
                            "FriendlyName": "CreateVolume Size",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "volumeName": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Create Volume Name",
                            "Required": "true",
                            "Default": "Mozart-Vol",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "numOfVolume": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Num of volumes to create",
                            "Required": "true",
                            "Default": "1",
                            "AssetValue": "",
                            "Group": "Provisioning"
                          },
                          "vArray": {
                            "Type": "AssetOption",
                            "FriendlyName": "Varray",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.varray",
                            "Group": "Controller"
                          },
                          "vPool": {
                            "Type": "AssetOption",
                            "FriendlyName": "Vpool",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          },
                          "project": {
                            "Type": "AssetOption",
                            "FriendlyName": "Project",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          }
                        },
                        "Output": {
                          "createdVols": "ALL task.resource.id"
                        },
                        "StepAttribute": {
                          "WaitForTask": true,
                          "Timeout": "60m"
                        },
                        "Next": {
                          "Failure": "tgh820479dfctqhbyb9",
                          "Default": "n0e651ri9c97rjclvunmi"
                        }
                      },
                      {
                        "positionX": 2727,
                        "positionY": 2401,
                        "StepId": "n0e651ri9c97rjclvunmi",
                        "FriendlyName": "s",
                        "OpName": "createVolume",
                        "Description": "Create Volumes",
                        "Type": "ViPR REST API",
                        "Input": {
                          "Size": {
                            "Type": "inputFromUser",
                            "FriendlyName": "CreateVolume Size",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "volumeName": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Create Volume Name",
                            "Required": "true",
                            "Default": "Mozart-Vol",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "numOfVolume": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Num of volumes to create",
                            "Required": "true",
                            "Default": "1",
                            "AssetValue": "",
                            "Group": "Provisioning"
                          },
                          "vArray": {
                            "Type": "AssetOption",
                            "FriendlyName": "Varray",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.varray",
                            "Group": "Controller"
                          },
                          "vPool": {
                            "Type": "AssetOption",
                            "FriendlyName": "Vpool",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          },
                          "project": {
                            "Type": "AssetOption",
                            "FriendlyName": "Project",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          }
                        },
                        "Output": {
                          "createdVols": "ALL task.resource.id"
                        },
                        "StepAttribute": {
                          "WaitForTask": true,
                          "Timeout": "60m"
                        },
                        "Next": {
                          "Failure": "tgh820479dfctqhbyb9",
                          "Default": "iv5zjcpncqsbbkdcmcxr"
                        }
                      },
                      {
                        "positionX": 1986,
                        "positionY": 2323,
                        "StepId": "btbrmlnqgultzs3y2e29",
                        "FriendlyName": "c",
                        "OpName": "createVolume",
                        "Description": "Create Volumes",
                        "Type": "ViPR REST API",
                        "Input": {
                          "Size": {
                            "Type": "inputFromUser",
                            "FriendlyName": "CreateVolume Size",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "volumeName": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Create Volume Name",
                            "Required": "true",
                            "Default": "Mozart-Vol",
                            "AssetValue": "",
                            "Group": "Provisioning",
                            "LockDown": ""
                          },
                          "numOfVolume": {
                            "Type": "inputFromUser",
                            "FriendlyName": "Num of volumes to create",
                            "Required": "true",
                            "Default": "1",
                            "AssetValue": "",
                            "Group": "Provisioning"
                          },
                          "vArray": {
                            "Type": "AssetOption",
                            "FriendlyName": "Varray",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.varray",
                            "Group": "Controller"
                          },
                          "vPool": {
                            "Type": "AssetOption",
                            "FriendlyName": "Vpool",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          },
                          "project": {
                            "Type": "AssetOption",
                            "FriendlyName": "Project",
                            "Required": "true",
                            "Default": "",
                            "AssetValue": "asset.option.vpool",
                            "Group": "Controller"
                          }
                        },
                        "Output": {
                          "createdVols": "ALL task.resource.id"
                        },
                        "StepAttribute": {
                          "WaitForTask": true,
                          "Timeout": "60m"
                        },
                        "Next": {
                          "Default": "ttyepwky095z74ym0a4i",
                          "Failure": "rphtli3wwoi8bzdoyldi"
                        }
                      }
                    ]
                  };

angular.module("portalApp").controller('builderController', function($scope, $http) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")

    $scope.workflowTabs = {};

    function addTab(tabid) {
      $scope.workflowTabs[tabid] = { id: tabid, href:"#"+tabid };
      $scope.$apply();
    }

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
        $(".search-input").keyup(function() {
            var searchString = $(this).val();
            $('#jstree_demo').jstree('search', searchString);
        });

        $('#jstree_demo').jstree({
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
                "contextmenu", "search",
                "state", "types", "wholerow"
            ],
            "search" : {
                  'case_sensitive' : false,
                  'show_only_matches' : true
            },
            "contextmenu" : {
                 "items": function($node) {
                     var tree = $("#jstree_demo").jstree(true);
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
            $( ".draggable-card" ).draggable({handle: "a",helper: "clone",scroll: false});
        }).bind("rename_node.jstree clear_search.jstree search.jstree open_node.jstree", function() {
            $( ".draggable-card" ).draggable({handle: "a",helper: "clone",scroll: false});
        })
    }

    // jstree actions
    //TODO: do error handling on all actions
    $('#jstree_demo').on("rename_node.jstree", renameDir);
    $('#jstree_demo').on("delete_node.jstree", deleteDir);
    $('#jstree_demo').on("create_node.jstree", createDir);

    $scope.closeTab = function(tabID){
        delete $scope.workflowTabs[tabID];
        $(".nav-tabs li").children('a').first().click();
    };

    // This method will create tab view for workflows
    function previewNode(node) {
        var tabID = "tab_"+node.id;
        addTab(tabID);
    }

    function createDir(event, data) {
        if ("file" != data.node.type) {
            $http.get(routes.WF_directory_create({"name": data.node.text,"parent": data.parent})).then(function (resp) {
                data.instance.set_id(data.node, resp.data.id);
            });
        }
        else {
            //TODO: create workflow (API pending)
            console.log("create workflow pending")
        }
    };

    function deleteDir(event, data) {
        if ("file" != data.node.type) {
            $http.get(routes.WF_directory_delete({"id": data.node.id}));
        }
        else {
            //TODO: delete workflow (API pending)
            console.log("delete workflow pending")
        }
    };

    function renameDir(event, data) {
        if ("file" != data.node.type) {
            $http.get(routes.WF_directory_edit_name({"id": data.node.id, "newName": data.text}));
        }
        else {
            //TODO: edit workflow (API pending)
            console.log("edit workflow pending")
        }
    };
})

.controller('tabController', function($element, $scope, $compile) { //NOSONAR ("Suppressing Sonar violations of max 100 lines in a function and function complexity")

    var diagramContainer = $element.find('#diagramContainer')
    var sbSite = $element.find('#sb-site')
    var jspInstance;
    var workflowData;

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
        var stepName = $(e.toElement).text();

        //compensate x,y for zoom
        var x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
        var y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
        var scaleMultiplier = 1 / jspInstance.getZoom();;
        var positionY = (y - diagramContainer.offset().top) * scaleMultiplier;
        var positionX = (x - diagramContainer.offset().left) * scaleMultiplier;


        //add data
        //TODO:remove FriendlyName, it will be included in step data already
        var stepData = jQuery.extend(true, {}, dummyStepData);
        stepData.StepId = randomIdHash;
        stepData.FriendlyName = stepName;
        stepData.positionY = positionY;
        stepData.positionX = positionX;

        loadStep(stepData);

    }
    function setBindings() {
        jspInstance.bind("connection", function(connection) {
            var source=$(connection.source);
            var sourceEndpoint=$(connection.sourceEndpoint.canvas);
            var sourceData = source.data();
            var sourceNext = {};
            if (sourceData.Next) {
                sourceNext = sourceData.Next;
            }
            if (sourceEndpoint.hasClass("passEndpoint")) {
                sourceNext.Default=connection.targetId
            }
            if (sourceEndpoint.hasClass("failEndpoint")) {
                sourceNext.Failure=connection.targetId
            }
            source.data("Next",sourceNext);
        });

        jspInstance.bind("connectionDetached", function(connection) {
            var source=$(connection.source);
            var sourceEndpoint=$(connection.sourceEndpoint.canvas);
            var sourceData = source.data();
            var sourceNext = {};
            if (sourceData.Next) {
                sourceNext = sourceData.Next;
            }
            if (sourceEndpoint.hasClass("passEndpoint")) {
                delete sourceData.Next.Default;
            }
            if (sourceEndpoint.hasClass("failEndpoint")) {
                delete sourceData.Next.Failure;
            }
            source.data("Next",sourceNext);
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
            var data = $elem.data();
            blocks.push($.extend(data,{
                positionX: parseInt($wrapper.css("left"), 10),
                positionY: parseInt($wrapper.css("top"), 10)
            } ));
        });

        //TODO: return JSON data so that it can be accessed in Export/SaveWorkflow via this method
        workflowData.Steps = blocks;

        dummyWF = jQuery.extend(true, {}, workflowData);
    }

    $scope.removeStep = function(stepId) {
        jspInstance.remove(diagramContainer.find('#' + stepId));
    }

    function loadStep(step) {
        if(!step.positionY || !step.positionX){
            return;
        }

        var stepId = step.StepId;
        var stepName = step.FriendlyName;

        //create element html
        //TODO: move html to separate location instead of building in JS when design available
        var $itemWrapper = '<div id="' + stepId + '-wrapper" class="example-item-card-wrapper"></div>';
        var $closeButton = '<a class="glyphicon glyphicon-remove button-step-close" ng-click="removeStep(\''+stepId+'-wrapper\')"></a>';
        var $item = '<div id="' + stepId + '" class="item">';
        var $itemText = '<div class="itemText">' + stepName + '</div>';
        $($itemText).appendTo(diagramContainer).wrap($itemWrapper).before($closeButton).wrap($item);
        var theNewItemWrapper = diagramContainer.find(' #' + stepId+'-wrapper');
        var theNewItem = diagramContainer.find(' #' + stepId);

        //add data
        theNewItem.data(step);

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
        $compile(theNewItemWrapper.contents())($scope);
    }

    function loadConnections(step) {
        if(step.Next){
            if(step.Next.Default){
                var passEndpoint = jspInstance.getEndpoint(step.StepId+"-pass");
                jspInstance.connect({source:passEndpoint, target:step.Next.Default});
            }
            if(step.Next.Failure){
                var failEndpoint = jspInstance.getEndpoint(step.StepId+"-fail");
                jspInstance.connect({source:failEndpoint, target:step.Next.Failure});
            }
        }
    }

    $scope.loadJSON = function() {

        //TODO: replace dummyWF with json from API or import
        var loadedWorkflow=jQuery.extend(true, {}, dummyWF);
        workflowData.WorkflowName = loadedWorkflow.WorkflowName;
        workflowData.Description = loadedWorkflow.Description;

        //load steps with position data
        loadedWorkflow.Steps.forEach(function(step) {
            loadStep(step);
        });

        //load connections
        loadedWorkflow.Steps.forEach(function(step) {
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

});
