//TODO: give this some more thought. Consider animate.css or veloicity.js

angular.module("portalApp").animation(".hideFast", function() {
    return {
        addClass: function(element, className, done) {
            element.removeClass('ng-hide'); //not a fan of this
            element.hide('fast', done);
        }
    }
});