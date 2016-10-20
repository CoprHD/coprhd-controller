var currentX = 0;
var currentY = 0;
var currentScale = 1;

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


    //initialize jsPlumb

    jsPlumb.importDefaults({
        DragOptions: {
            cursor: "none"
        },
        ConnectionOverlays: [
            ["Arrow", {
                location: 1,
                visible: true,
                id: "ARROW",
                width: 25,
                length: 25

            }]
        ]
    });
    jsPlumb.setContainer($('#diagramContainer'));
    jsPlumb.setZoom(1);

    //initialize draggable menu items

    $(".draggable-step").draggable({ helper: "clone", scroll: false });
    $("#sb-site").droppable({ drop: dragEndFunc });

});


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
    var target = $(e.target);
    if (target.is(".example-item-card-wrapper")) {
        text = target.text();
        var stepName = prompt("Please enter step name", text);
        target.find(".itemText").text(stepName);
        target.attr('data-name', stepName);
        jsPlumb.repaintEverything();
    }
}

function dragEndFunc(e) {

    //create element html
    var randomIdHash = Math.random().toString(36).substring(7);
    var stepName = prompt("Please enter step name", "Step Name");
    var $itemWrapper = '<div id="' + randomIdHash + '-wrapper" class="example-item-card-wrapper" data-name="' + stepName + '" ondblclick="changeName(event);"></div>';
    var $item = '<div class="item card example-item">';
    var $itemText = '<div class="itemText">' + stepName + '</div>';
    $($itemText).appendTo('#diagramContainer').wrap($itemWrapper).wrap($item);
    var theNewItem = $('#' + randomIdHash + '-wrapper');

    //compensate x,y for zoom
    x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
    y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
    var scaleMultiplier = 1 / currentScale;
    $(theNewItem).css({
        'top': (y - $('#diagramContainer').offset().top) * scaleMultiplier,
        'left': (x - $('#diagramContainer').offset().left) * scaleMultiplier
    });

    //add jsPlumb options
    jsPlumb.addEndpoint(randomIdHash + '-wrapper', {
        anchor: [0.5, 1, 0, 1, -40, 0],
        //anchors: [ "Bottom","Continuous"],
        cssClass: "failEndpoint"
    }, common_source);
    jsPlumb.addEndpoint(randomIdHash + '-wrapper', {
        //anchors: [ "Top","Continuous"],
        anchor: "Top",
        cssClass: "inputEndpoint"
    }, common_target);
    jsPlumb.addEndpoint(randomIdHash + '-wrapper', {
        anchor: [0.5, 1, 0, 1, 40, 0],
        //anchors: [ "Right","Continuous"],
        cssClass: "passEndpoint"
    }, common_source);
    jsPlumb.draggable(randomIdHash + '-wrapper');

}

function saveJSON() {
    var blocks = []
    $("#diagramContainer .example-item-card-wrapper").each(function(idx, elem) {
        var $elem = $(elem);
        var data = $elem.data();
        blocks.push($.extend({
            blockId: $elem.attr('id'),
            positionX: parseInt($elem.css("left"), 10),
            positionY: parseInt($elem.css("top"), 10)
        }, data));
    });

    var connections = [];
    $.each(jsPlumb.getConnections(), function(idx, connection) {
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
