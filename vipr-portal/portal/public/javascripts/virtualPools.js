var virtualPools = (function() {
    var has = function($control, name) {
        if ($control.data(name)) {
            return true;
        }
        else if ($control.attr(name)) {
            return true;
        }
        else {
            return false;
        }
    }
    
    var hasParent = function($control, name) {
        var result = false;
        $control.parents('[data-source]').each(function() {
            var $parent = $(this);
            if (has($parent, name)) {
                result = true;
                return false;
            }
        });
        return result;
    }
    
    var isContainerVisible = function($container) {
        return !hasParent($container, 'hidden');
    }
    
    var setContainerVisible = function($container, visible) {
        if (visible) {
            $container.removeData('hidden');
            $container.show();
        }
        else {
            $container.data('hidden', 'hidden');
            $container.hide();
        }
    }
    
    var isContainerEnabled = function($container) {
        return !hasParent($container, 'disabled');
    }
    
    var setContainerEnabled = function($container, enabled) {
        if (enabled) {
            $container.removeData('disabled');
        }
        else {
            $container.data('disabled', 'disabled');
        }
    }
    
    var isReadOnly = function($control) {
        if (has($control, 'readonly')) {
            return true;
        }
        else {
            return $control.parents('[data-readonly]').size() > 0;
        }
    }
    
    var isMatch = function(value, values) {
        if (!values) {
            return false;
        }
        if (!$.isArray(values)) {
            values = new String(values).split(",");
        }
        return $.inArray(value, values) > -1;
    }
    
    var updateControl = function($control) {
        updateAttributes($control);
    }
    
    /**
     * Configures dependencies of all components.
     */
    var configureDependencies = function($container) {
        $('[data-depends]', $container).each(function() {
            var selector = '#' + $(this).attr('id');
            var depends = $(this).data('depends');
            var update = $(this).data('update');
            var changeHandler = util.delayedHandler(function() {
                util.loadOptions(update, selector);
            }, 100);
            
            // Add a change handler to each control listed in the depends attribute
            var dependsArray = depends.split(",");
            for (var i = 0; i < dependsArray.length; i++) {
                var $dependOn = $('#'+dependsArray[i]);
                $dependOn.on('change', changeHandler);
                $dependOn.trigger('change');
            }
        });
    }
    
    /**
     * Configures sources for visibility and enablement of controls.
     */
    var configureSources = function($container) {
        $('[data-source]', $container).each(function() {
            var $target = $(this);
            var show = $target.data('show');
            var hide = $target.data('hide');
            var enable = $target.data('enable');
            var disable = $target.data('disable');
            
            var $source = $('#' + $target.data('source'));
            $source.on('change', function() {
                var value = util.getControlValue(this);
                if (show) {
                    setContainerVisible($target, isMatch(value, show));
                }
                if (hide) {
                    setContainerVisible($target, !isMatch(value, hide));
                }
                if (enable) {
                    setContainerEnabled($target, isMatch(value, enable));
                }
                if (disable) {
                    setContainerEnabled($target, !isMatch(value, disable));
                }
                
                // Updated all named controls within
                $target.find('[name^="vpool."]').each(function() {
                    updateControl($(this));
                });
            });
            $source.trigger('change');
        });
    }
    
    var updateAttributes = function($control) {
        var attributes = $control.data('attributes');
        if (attributes == null) {
            return;
        }
        
        var containerVisible = isContainerVisible($control);
        var containerEnabled = isContainerEnabled($control);        
        
        var values = attributes.split(',');
        // Standard select box
        if ($control.is('select')) {
            $('option', $control).each(function() {
                var value = $(this).val();
                util.setEnabled(this, isMatch(value, values) && containerVisible && containerEnabled);
            });
            // Trigger an update of the chosen display of the select control
            $control.trigger('chosen:updated');
        }
        else if ($control.is(':checkbox')) {
            var id = $control.attr('id');
            var value = $control.val();
            var match = isMatch(value, values);
            var readOnly = isReadOnly($control);
            var $label = $('label[for="'+id+'"]');
            
            console.log("     "+id+"="+value+" (match: "+match+", readOnly: "+readOnly+")");
            util.setEnabled($control, match && containerVisible && containerEnabled && !readOnly);
            util.setVisible($control, match && containerVisible && containerEnabled);
            util.setVisible($label, match && containerVisible && containerEnabled);
            $control.trigger('change');
        }
    }
    
    var loadAttributes = function(data, textStatus, jqXHR) {
        console.log("Updating virtual array attributes");
        if (!data) {
            return;
        }
        // Each key in the result is the name of a field on the form
        for (var key in data) {
            var value = data[key];
            var attributes = value.join(',');
            
            console.log("  "+key+"="+attributes);
            // Find the control(s) for the given name (some controls are duplicated between block and file)
            var $controls = $('[name="vpool.'+key+'"]');
            $controls.data('attributes', attributes);
            $controls.each(function() {
                updateControl($(this));
            });
        }
    }
    var timeout = null;
    
    var isDisabled = function($control) {
        var disabledField = $control.prop('disabled');
        var hiddenContainer = hasParent($control, 'hidden');
        var disabledContainer = hasParent($control, 'disabled');
        return disabledField || hiddenContainer || disabledContainer;
    }
    
    var disableHiddenFields = function() {
        $('[name^="vpool."]', this).each(function() {
            var $control = $(this);
            if (isDisabled($control)) {
                var id = $control.attr('id');
                console.log("Disabling hidden field: "+id);
                util.setEnabled($control, false);
            }
        });
    }
    
    return {
        init: function(selector, emptyAttributes) {
            var $container = $(selector);
            // Disable hidden fields in the form on submit
            $container.on('submit', 'form', disableHiddenFields);
            
            // Configure dependencies for the entire document
            var $doc = $(document);
            configureDependencies($doc);
            configureSources($doc);
            util.collapsible(selector);
            loadAttributes(emptyAttributes);
        },
        updateVirtualArrayAttributes: function(url, form) {
            var vpool = $(form ? form : 'form').serialize();
            $.post(url, vpool, loadAttributes, 'json');
        }
    }
})();