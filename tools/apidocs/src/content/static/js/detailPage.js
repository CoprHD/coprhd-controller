function requestViewChange() {
    var selected = $("#payloadSelect").val();

    $("#requestTable").hide();
    $("#requestJSON").hide();
    $("#requestXml").hide();

    if (selected == "xml") {
        $("#requestXml").show();
    }

    if (selected == "json") {
        $("#requestJSON").show();
    }


    if (selected == "table") {
        $("#requestTable").show();
    }
}

