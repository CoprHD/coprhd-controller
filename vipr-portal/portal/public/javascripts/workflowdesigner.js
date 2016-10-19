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

function dragEndFunc2(e) {
        console.log(e);
        x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft; 
        y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
        var dropPosition = {top:y, left:x};
        var randomIdHash = Math.random().toString(36).substring(7);
        var stepName = prompt("Please enter step name", "Step Name");
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
    $( ".draggable-card" ).draggable({helper: "clone",scroll: false});
    $( "#sb-site" ).droppable({drop: dragEndFunc2});
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