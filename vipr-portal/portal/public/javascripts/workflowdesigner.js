var currentX = 0;
var currentY = 0;
var currentScale = 1;

//will need to move somewhere instantiable
var workflow =
    {
       "WorkflowName":"",
       "Description":"Create Volumes if fails delete the created volumes. Send Email about the Workflow status"
   };

$(function() {

    //initialize oanzoom
    var widthHalf = (window.innerWidth / 2) - 75;
    var heightHalf = (window.innerHeight / 2) - 75;

    var $panzoom = $("#diagramContainer").panzoom({
        cursor: "default",
        minScale: 0.5,
        maxScale: 2,
        increment: 0.1,
        duration: 100
    });
    $panzoom.parent().on('mousewheel.focal', function(e) {
        e.preventDefault();
        var delta = e.delta || e.originalEvent.wheelDeltaY;
        if (delta != 0) {
            var zoomOut = delta < 0;
            $panzoom.panzoom('zoom', zoomOut, {
                animate: false,
                increment: 0.1,
                focal: e
            });
        }
    });
    $panzoom.panzoom("pan", -5000 + widthHalf, -5250 + heightHalf, {
        relative: false
    });
    $panzoom.on('panzoomzoom', function(e, panzoom, scale) {
        jsPlumb.setZoom(scale);
        currentScale = scale;
    });


    //initialize jsPlumb, will need to make instantiable

    jsPlumb.importDefaults({
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
    jsPlumb.setContainer($('#diagramContainer'));
    jsPlumb.setZoom(1);

  $('.example-item-card-wrapper').each(function () {
      jsPlumb.draggable(this);
  });

});

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
                "positionX": 5811,
                "positionY": 4699,
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
                "positionX": 6285,
                "positionY": 5018,
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
                "positionX": 4867,
                "positionY": 4939,
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
                "positionX": 5144,
                "positionY": 4939,
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
                "positionX": 5387,
                "positionY": 4962,
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
                "positionX": 5864,
                "positionY": 5062,
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
                "positionX": 6030,
                "positionY": 5179,
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
                "positionX": 5241,
                "positionY": 5249,
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
                "positionX": 5529,
                "positionY": 5486,
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
                "positionX": 4867,
                "positionY": 5148,
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

var common_source = {
    endpoint: ["Dot", {
        radius: 10
    }],
    isSource: true,
    connector: ["Flowchart", {
        cornerRadius: 5
    }]
};
var common_target = {
    endpoint: ["Dot", {
        radius: 10
    }],
    maxConnections: -1,
    isTarget: true,
    connector: ["Flowchart", {
        cornerRadius: 5
    }]
};

function changeName(e) {
    var target = $(e.currentTarget);
    if (target.is(".example-item-card-wrapper")) {
        text = target.text();
        var stepName = prompt("Please enter step name", text);
        target.find(".itemText").text(stepName);
        target.data('FriendlyName', stepName);
        jsPlumb.repaintEverything();
    }
}

function dragEndFunc(e) {
    //console.log(e);
    //create element html
    var randomIdHash = Math.random().toString(36).substring(7);
    var stepName = $(e.toElement).text();
    var $itemWrapper = '<div id="' + randomIdHash + '" class="example-item-card-wrapper" ondblclick="changeName(event);"></div>';
    var $item = '<div class="item">';
    var $itemText = '<div class="itemText">' + stepName + '</div>';
    $($itemText).appendTo('#diagramContainer').wrap($itemWrapper).wrap($item);
    var theNewItem = $('#' + randomIdHash);

    //add data
    theNewItem.data("StepId",randomIdHash);
    theNewItem.data("FriendlyName",stepName);
    theNewItem.data(dummyStepData);

    //compensate x,y for zoom
    x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
    y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
    var scaleMultiplier = 1 / currentScale;
    $(theNewItem).css({
        'top': (y - $('#diagramContainer').offset().top) * scaleMultiplier,
        'left': (x - $('#diagramContainer').offset().left) * scaleMultiplier
    });

    //add jsPlumb options
    jsPlumb.addEndpoint(randomIdHash, {
        anchors: [0.5, 1, 0, 1,0,10],
        cssClass: "failEndpoint"
    }, common_source);
    jsPlumb.makeTarget(randomIdHash, {
        anchors: ["Top","Left"],
        endpoint: "Blank"
    });
    jsPlumb.addEndpoint(randomIdHash, {
        anchors: [1, 0.5, 1, 0,10,0],
        cssClass: "passEndpoint"
    }, common_source);
    jsPlumb.draggable(randomIdHash);
  
}

jsPlumb.bind("connection", function(connection) {
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

jsPlumb.bind("connectionDetached", function(connection) {
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


function saveJSON() {
    var blocks = []
    $("#diagramContainer .example-item-card-wrapper").each(function(idx, elem) {
        var $elem = $(elem);
        var data = $elem.data();
        blocks.push($.extend(data,{
            positionX: parseInt($elem.css("left"), 10),
            positionY: parseInt($elem.css("top"), 10)
        } ));
    });
    workflow.Steps = blocks;

    //for development purposes. This will be used to export to file or post to backend
    copyData = JSON.stringify(workflow);
    console.log(workflow);
    dummyWF = jQuery.extend(true, {}, workflow);
}

function loadStep(step) {
    if(!step.positionY || !step.positionX){
        return;
    }

    var stepId = step.StepId;
    var stepName = step.FriendlyName;
    //create element html
    var $itemWrapper = '<div id="' + stepId + '" class="example-item-card-wrapper" ondblclick="changeName(event);"></div>';
    var $item = '<div class="item">';
    var $itemText = '<div class="itemText">' + stepName + '</div>';
    $($itemText).appendTo('#diagramContainer').wrap($itemWrapper).wrap($item);
    var theNewItem = $('#' + stepId);

    //add data
    theNewItem.data(step);

    //compensate x,y for zoom
    $(theNewItem).css({
        'top': step.positionY,
        'left': step.positionX
    });

    //add jsPlumb options
    jsPlumb.addEndpoint(stepId, {
        uuid:stepId+"-fail",
        anchors: [ "Bottom"],
        cssClass: "failEndpoint"
    }, common_source);
    jsPlumb.makeTarget(stepId, {
        uuid:stepId+"-target",
        anchors: ["Top","Left"],
        endpoint: "Blank"
    });
    jsPlumb.addEndpoint(stepId, {
        uuid:stepId+"-pass",
        anchors: [ "Right"],
        cssClass: "passEndpoint"
    }, common_source);
    jsPlumb.draggable(stepId);

}

function loadConnections(step) {
    for(key in step.Next){
        var value = step.Next[key];
        if(key==="Default"){
            var source = jsPlumb.getEndpoint(step.StepId+"-pass");
            jsPlumb.connect({source:source, target:value});
        }
        if(key==="Failure"){
            var source = jsPlumb.getEndpoint(step.StepId+"-fail");
            jsPlumb.connect({source:source, target:value});
        }
    };
}

function method1(steps) {
    steps.forEach(function(step) {
        loadStep(step);
    });
}

function importStepsNoPosition(stepsObject) {
    var initStep = stepsObject[0];
    //create key value
    steps = {};
    stepsObject.forEach(function(step) {
        steps[step.StepId] = step;
    });
    stepsObject.forEach(function(step) {
        traverse(step,5000,5000);
    });
}

function traverse(step,x,y){
    if (document.getElementById(step.StepId)){
        return;
    }
    loadStep2(step,x,y);
    if (step.Next){
        var ready = false;
        if (step.Next.Default) {
            var newY =y;
            var newX = x+400;
            var nextId = step.Next.Default;
            while (!ready) {
                var ready2 = true;
                $('.example-item-card-wrapper').each(function(idx,elem){
                    var $elem = $(elem);
                    if (parseInt($elem.css("left"), 10) == newX && parseInt($elem.css("top"), 10) == newY){
                        newY=newY+400;
                        if (((newY - 5000) / 400) % 2 > 0){
                            newX = newX+200;
                        } else {
                            newX = newX - 200;
                        }
                        ready2 = false;
                    }
                });
                if (ready2){
                    ready = true;
                }
            }
        traverse(steps[nextId],newX,newY);
        }
        if (step.Next.Failure) {
            var newY =y+400;
            var newX = x;
            var nextId = step.Next.Failure;
            if (((newY - 5000) / 400) % 2 > 0){
                newX = newX+200;
            } else {
                newX = newX - 200;
            }
            while (!ready) {
                var ready2 = true;
                $('.example-item-card-wrapper').each(function(idx,elem){
                    var $elem = $(elem);
                    if (parseInt($elem.css("left"), 10) == newX && parseInt($elem.css("top"), 10) == newY){
                        newY=newY+400;
                        if (((newY - 5000) / 400) % 2 > 0){
                            newX = newX+200;
                        } else {
                            newX = newX - 200;
                        }
                        ready2 = false;
                    }
                });
                if (ready2){
                    ready = true;
                }
            }

            traverse(steps[nextId],newX,newY);
        }

    }
}

function loadStep2(step,x,y) {
    var stepId = step.StepId;
    var stepName = step.FriendlyName;
    //create element html
    var $itemWrapper = '<div id="' + stepId + '" class="example-item-card-wrapper" ondblclick="changeName(event);"></div>';
    var $item = '<div class="item">';
    var $itemText = '<div class="itemText">' + stepName + '</div>';
    $($itemText).appendTo('#diagramContainer').wrap($itemWrapper).wrap($item);
    var theNewItem = $('#' + stepId);

    $(theNewItem).css({
        'top': y,
        'left': x
    });

    //add data
    theNewItem.data(step);

    //add jsPlumb options
    jsPlumb.addEndpoint(stepId, {
        uuid:stepId+"-fail",
        anchors: [ "Bottom"],
        cssClass: "failEndpoint"
    }, common_source);
    jsPlumb.makeTarget(stepId, {
        uuid:stepId+"-target",
        anchors: ["Top","Left"],
        endpoint: "Blank"
    });
    jsPlumb.addEndpoint(stepId, {
        uuid:stepId+"-pass",
        anchors: [ "Right"],
        cssClass: "passEndpoint"
    }, common_source);
    jsPlumb.draggable(stepId);

}

function loadJSON() {
    var loadedWorkflow=jQuery.extend(true, {}, dummyWF);
    workflow.WorkflowName = loadedWorkflow.WorkflowName;
    workflow.Description = loadedWorkflow.Description;
    //method1(loadedWorkflow.Steps);
    method1(loadedWorkflow.Steps);
    loadedWorkflow.Steps.forEach(function(step) {
        loadConnections(step);
    });
}
function loadJSONNoPosition() {
    var loadedWorkflow=jQuery.extend(true, {}, dummyWF);
    workflow.WorkflowName = loadedWorkflow.WorkflowName;
    workflow.Description = loadedWorkflow.Description;
    //method1(loadedWorkflow.Steps);
    importStepsNoPosition(loadedWorkflow.Steps);
    loadedWorkflow.Steps.forEach(function(step) {
        loadConnections(step);
    });
}

function reset() {
    jsPlumb.deleteEveryEndpoint();
    $('.example-item-card-wrapper').each( function(idx,elem) {
        var $elem = $(elem);
        $elem.remove();
    });
    workflow = {};
    }

$( function() {

    $(".search-input").keyup(function() {
        var searchString = $(this).val();
        console.log(searchString);
        $('#jstree_demo').jstree('search', searchString);
    });

    $('#jstree_demo').jstree({
        "core": {
            "animation": 0,
            "check_callback": true,
            "themes": {"stripes": false},
            'data' : rootjson
        },
        "types": {
            "#": {
                "max_children": 1,
                "max_depth": 4,
                "valid_children": ["root"]
            },
            "root": {
                "valid_children": ["default"]
            },
            "default": {
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
                                         action : function (obj) {
                                             $node = tree.create_node($node,{"type":"file"});
                                             tree.edit($node);
                                         }
                                     },
                                     "create_folder" : {
                                         "seperator_before" : false,
                                         "seperator_after" : false,
                                         "label" : "Folder",
                                         action : function (obj) {
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
                                 "action": function (obj) {
                                     tree.edit($node);
                                 }
                             },
                             "Remove": {
                                 "separator_before": false,
                                 "separator_after": false,
                                 "label": "Remove",
                                 "action": function (obj) {
                                     tree.delete_node($node);
                                 }
                             },
                             "Preview": {
                                 "separator_before": false,
                                 "separator_after": false,
                                 "label": "Preview",
                                 "action": function (obj) {
                                     previewNode($node);
                                 }
                             }
                         };
                     }
                 }




    }).on('ready.jstree', function(e, data) {
        $( ".draggable-card" ).draggable({helper: "clone",scroll: false});
        $( "#sb-site" ).droppable({drop: dragEndFunc});
    }).bind("rename_node.jstree clear_search.jstree search.jstree", function(e, data) {
        $( ".draggable-card" ).draggable({helper: "clone",scroll: false});
    })

    $('#wftabs').on('click','.close',function(){
         console.log('close  ');
         var tabID = $(this).parents('a').attr('href');
         $(this).parents('li').remove();
         $(tabID).remove();

     });

  } );


// TODO: Remove this hardcoded JSON and build it using APIs (when available)
rootjson=[
  {
    "id": 1,
    "text": "My Lib",
    "children": [
      {
        "id": 2,
        "text": "Primitives"
      },
      {
        "id": 3,
        "text": "Workflows"
      }
    ],
    "type": "root"
  },
  {
    "id": 4,
    "text": "ViPR Lib",
    "children": [
      {
        "id": 5,
        "text": "Primitives",
        "children": [
          {
            "text": "Block",
            "children": [
              {
                "id": 10,
                "text": "Create Volume",
                "type": "file",
                "li_attr": {"class": "draggable-card"}
              },
              {
                "id": 8,
                "text": "Export Volume",
                "type": "file",
                "li_attr": {"class": "draggable-card"}
              }
            ]
          },
            {
                "text": "File",
                "children": [
                    {
                        "id": 7,
                        "text": "Create filesystem",
                        "type": "file",
                        "li_attr": {"class": "draggable-card"}
                    }
                ]
            }
        ]
      },
      {
        "id": 6,
        "text": "Workflows",
          "children": [
                    {
                        "id": 9,
                        "text": "Create and Export Volume",
                        "type": "file",
                        "li_attr": {"class": "draggable-card"}
                    }
                ]
      }
    ],
    "type": "root"
  }
]

function previewNode(node) {
    tabID = node.id;
    $("#wftabs").append('<li><a href="#tab'+tabID+'" role="tab" data-toggle="tab">'+node.text+'&nbsp;<button class="close" type="button" title="Close tab"><span aria-hidden="true">&times;</span></button></a></li>')
    $('.tab-content').append($('<div class="tab-pane fade" id="tab' + tabID + '">Tab '+ node.text +' content</div>'));
}