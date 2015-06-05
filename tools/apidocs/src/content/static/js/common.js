/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
function setupTOC(filename) {
    $(".toc a[href='"+filename+"']").css("color","red");
    $(".toc a[href='"+filename+"']").parents().show();
};

function addSearchKeyBinding() {
    $(document).bind('keydown',function (event) {
        if (event.shiftKey && event.keyCode == 83) {
            window.location.href="Search.html";
            return false;
        }
        return true;
    });
}

function openToc(filename) {
    $("a[href='"+filename+"']").parents("ul").show();
    $("a[href='"+filename+"']").addClass("active");
}

function hideMe() {
    $("#toc-holder").toggle();
    $("#doc-content-holder").toggleClass("span8");
    $("#doc-content-holder").toggleClass("span12");
}

// SETUP TOC
$(document).ready(function() {
    $(".toc-title").click(function(event) {
        $(event.target).siblings("UL").slideToggle(50);
        return false;
    })

    $(".toc-main-title").click(function(event) {
        $(event.target).siblings("UL").slideToggle(50);
        return false;
    })

    var jumpToStartPosition; 0
    var jumpTo;

    $(document).ready(function() {
        jumpTo = $("#btn-showHide");
        jumpToStartPosition = jumpTo.offset();
    });

    var isFixed = false;
    $(window).scroll(function() {
        if ($(window).scrollTop() <= jumpToStartPosition.top && isFixed) {
            jumpTo.css("position","relative");
            jumpToStartPosition = jumpTo.offset(); // Update in case window has been resized
            isFixed = false;
        }

        if ($(window).scrollTop() >= jumpToStartPosition.top && !isFixed) {
            jumpTo.css("top","0");
            jumpTo.css("position","fixed");
            isFixed = true;
        }
    });
});

