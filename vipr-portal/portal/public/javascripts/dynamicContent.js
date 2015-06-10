function DynamicContent() {
    var my = {};
    var valueSelectors = [];
    var childrenSelectors = [];
    var displaySelectors = [];
    var classSelectors = [];
    
    function updateValue(data, selector) {
        var current = $(selector);
        var updated = $(selector, data);
        
        current.replaceWith(updated);
    }

    function updateChildren(data, parent, selector) {
        var currentParent = $(parent);
        var updatedParent = $(parent, data);
        var currentChildren = currentParent.children(selector);
        var updatedChildren = updatedParent.children(selector);
        
        var index = 0;
        
        // Replace or append values
        for (var i = 0; i < updatedChildren.length; i++) {
            if (i < currentChildren.length) {
                $(currentChildren[i]).replaceWith($(updatedChildren[i]));
            }
            else {
                currentParent.append($(updatedChildren[i]));
            }
        }
        // Remove any trailing values
        for (var i = updatedChildren.length; i < currentChildren.length; i++) {
            $(currentChildren[i]).remove();
        }
    }
    
    function updateDisplay(data, selector) {
        var current = $(selector);
        var updated = $(selector, data);
        
        if (current.css('display') == 'none' && updated.css('display') != 'none') {
            current.show('fast');
        }
        else {
            current.css('display', updated.css('display'));
        }
    }

    function updateClasses(data, selector) {
        var current = $(selector);
        var updated = $(selector, data);

        current.attr('class', updated.attr('class'));
    }

    my.watchValue = function(selector) {
        valueSelectors.push(selector);
    }
    my.watchChildren = function(parent, selector) {
        childrenSelectors.push({parent: parent, selector:selector});
    }
    my.watchDisplay = function(selector) {
        displaySelectors.push(selector);
    }
    my.watchClasses = function(selector) {
        classSelectors.push(selector);
    }
    my.watchValueAndDisplay = function(selector) {
        my.watchValue(selector);
        my.watchDisplay(selector);
    }
    my.update = function(data) {
        for (var i = 0; i < valueSelectors.length; i++) {
            updateValue(data, valueSelectors[i]);
        }
        for (var i = 0; i < childrenSelectors.length; i++) {
            updateChildren(data, childrenSelectors[i].parent, childrenSelectors[i].selector);
        }
        for (var i = 0; i < displaySelectors.length; i++) {
            updateDisplay(data, displaySelectors[i]);
        }
        for (var i = 0; i < classSelectors.length; i++) {
            updateClasses(data, classSelectors[i]);
        }
    };
    
    return my;
}