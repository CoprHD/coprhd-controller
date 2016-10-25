  var currentX = 0;
  var currentY = 0;
  var currentScale = 1;
  var exampleColor = "#00cc00";
  var exampleColor2 = "#ff5a33";
  var lightGreen = "#66ff66";
  var tehGreen = "#34eb34";
  var olive = "#999966";
  var exampleDropOptions = {
      tolerance: "touch",
      activeClass: "activeEndpoint",
      hoverClass: "overActiveEndpoint"
  };

  var $panzoom = $("#diagramContainer").panzoom({
      cursor: "default",
      minScale: 0.25,
      increment: 0.1,
      duration: 100
  });
  $panzoom.parent().on('mousewheel.focal', function (e) {
      e.preventDefault();
      var delta = e.delta || e.originalEvent.wheelDelta;
      var zoomOut = delta ? delta < 0 : e.originalEvent.deltaY > 0;
      $panzoom.panzoom('zoom', zoomOut, {
          increment: 0.1,
          animate: true,
          focal: e
      });
  });

  jsPlumb.importDefaults({
      DragOptions: {
          cursor: "none"
      },
      ConnectionOverlays:[[ "Arrow", {
                location: 1,
                visible:true,
                id:"ARROW",
                width: 25,
                length: 25

            } ]]
  });

  jsPlumb.setContainer($('#diagramContainer'));

  $('.example-item-card-wrapper').each(function () {
      jsPlumb.draggable(this);
  });

  jsPlumb.draggable("example-item-primary-card-wrapper");

  jsPlumb.setZoom(1);
  var widthHalf = (window.innerWidth / 2) - 75;
  var heightHalf = (window.innerHeight / 2) - 75;
  $panzoom.on('panzoompan', function (e, panzoom, x, y) {
      /*console.log(y);
      console.log($('#diagramContainer').offset().top);
      currentX = x;
      currentY = y;*/

      //may want to remove
  });
  $panzoom.panzoom("pan", -5000 + widthHalf, -5250 + heightHalf, {
      relative: false
  });
  $panzoom.on('panzoomzoom', function (e, panzoom, scale) {
      jsPlumb.setZoom(scale);
      currentScale = scale;
  });

  $panzoom.panzoom("zoom", 1);

  var common_source = {
    anchors: ["Bottom"],
    endpoint: ["Dot", {
        radius: 10
    }],
    isSource: true,
    connector: ["Flowchart", {
        cornerRadius: 5
    }]
  };
  var common_target = {
    anchors: ["Top"],
    endpoint: ["Dot", {
        radius: 10
    }],
    maxConnections: -1,
    isTarget: true,
    connector: ["Flowchart", {
        cornerRadius: 5
    }]
  };
  /*
  /
  / DRAGGABLE STUFF
  /
  */

  var boxes = $(".element");

function changeName(e) {
  var target = $( e.target );
  if ( target.is( ".example-item-card-wrapper" ) ) {
    text = target.text();
    var stepName = prompt("Please enter step name", text);
    target.find(".itemText").text(stepName);
    target.attr('data-name',stepName);
    jsPlumb.repaintEverything();
  }
}

function dragEndFunc2(e,ui) {
        console.log(e);
        x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft; 
        y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
        var dropPosition = {top:y, left:x};
        var randomIdHash = Math.random().toString(36).substring(7);
        var stepName = prompt("Please enter step name", ui.draggable.text());
        var $newExampleItemWrapper = '<div id="' + randomIdHash + '-wrapper" class="example-item-card-wrapper" data-name="'+stepName+'" ondblclick="changeName(event);"></div>';
        var $newExampleItem = '<div class="item card example-item">';
        var $newExampleItemText = '<div class="itemText">' + stepName + '</div>';
        $($newExampleItemText).appendTo('#diagramContainer').wrap($newExampleItemWrapper).wrap($newExampleItem);
        var theNewItem = $('#' + randomIdHash + '-wrapper');
        var scaleMultiplier = 1 / currentScale;
        console.log(dropPosition.top + (currentY - (currentY * 2)));
        console.log(dropPosition.left + (currentX - (currentX * 2)));
          console.log(jsPlumb.getZoom());
        $(theNewItem).css({
            'top': (y-$('#diagramContainer').offset().top)*scaleMultiplier,
                'left': (x-$('#diagramContainer').offset().left)*scaleMultiplier
        });

    jsPlumb.addEndpoint(randomIdHash + '-wrapper', {
      anchor:[ 0.5, 1, 0, 1, -40, 0 ],
      cssClass: "failEndpoint"
    }, common_source);
    jsPlumb.addEndpoint(randomIdHash + '-wrapper', {
      anchor: "Top",
      cssClass: "inputEndpoint"
    }, common_target);
    jsPlumb.addEndpoint(randomIdHash + '-wrapper', {
      anchor:[ 0.5, 1, 0, 1, 40, 0 ],
      cssClass: "passEndpoint"
    }, common_source);

      jsPlumb.draggable(randomIdHash + '-wrapper');
  
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
                "valid_children": []
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
                                         "label" : "File",
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
        console.log('hi', data);
        $( ".draggable-card" ).draggable({helper: "clone",scroll: false});
        $( "#sb-site" ).droppable({drop: dragEndFunc2});
    });

    $('#wftabs').on('click','.close',function(){
         console.log('close  ');
         var tabID = $(this).parents('a').attr('href');
         $(this).parents('li').remove();
         $(tabID).remove();

         //display first tab
         //var tabFirst = $('#wftabs a:first');
         //tabFirst.tab('show');
     });

  } );

function saveJSON() {
  var blocks = []
  $("#diagramContainer .example-item-card-wrapper").each(function (idx, elem) {
      var $elem = $(elem);
      var data = $elem.data();
      blocks.push($.extend({
          blockId: $elem.attr('id'),
          positionX: parseInt($elem.css("left"), 10),
          positionY: parseInt($elem.css("top"), 10)
      },data));
  });

  var connections = [];
  $.each(jsPlumb.getConnections(), function (idx, connection) {
      connections.push({
          connectionId: connection.id,
          pageSourceId: connection.sourceId,
          pageTargetId: connection.targetId
      });
  });
  var serializedData = JSON.stringify(blocks);
  console.log(serializedData);
  var serializedData = JSON.stringify(connections);
  console.log(serializedData);
}

// JSTREE functions

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