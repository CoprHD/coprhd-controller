/**
 * @ngdoc overview
 * @name fields
 * @description
 *
 * # fields
 * Form field controls.
 *
 * <div doc-module-components="fields"></div>
 */
angular.module('fields', ['vipr']).directive({  //NOSONAR ("Suppressing Sonar violation of Avoid trailing comma in array and object literals")
    /**
     * @ngdoc directive
     * @name fields.directive:vField
     *
     * @description
     * Introduces a `field` object into scope with the following attributes:
     *
     * * `value`: a two way data binding (using {@link vipr.service:binder binder}) with the original model.
     * * `id`: the name of the model object, replacing `.` with `_`. This follows our existing conventions.
     * * `name`: a string version of the expression passed to `vField`.
     * * `label`: a {@link vipr.service:translate translated} version of `name`.
     * * `helpText`: a {@link vipr.service:translate translated} version of `name`.help, or `null`.
     * *
     * @param {string=} name Override the default name
     * @param {string=} label Override the default label
     * @param {string=} helpText Override the default helpText
     * @param {string=} error Initialize the field with an error message. Disables the default error message behavior
     * @param {expression=} loading show a loading indicator if true
     * @param {boolean=} ignoreFlash Don't try to pull the default value from the flash
     *
     * All of our fields require a `vField` to be on the scope.
     */
    vField: function(binder, translate, convert, $parse) {
        return {
            scope: true,
            priority: 2,
            controller: angular.noop, //dummy controller. require: "^vField" needs a controller
            link: {
                pre: function(scope, element, attrs) {
                    scope.field = {
                        value: binder(scope.$parent, attrs.vField, 'field.value', scope),
                        id: attrs.vField.replace(".", "_"),
                        name: attrs.name || attrs.vField,
                        label: attrs.label || translate(attrs.labelKey || attrs.vField),
                        helpText: attrs.helpText || translate.raw(attrs.helpKey || attrs.vField + '.help'),
                        loading: binder.oneWay(scope, attrs.loading, 'field.loading')
                    };
                    // Watch for changes in the value of the field name
                    if (attrs.name) {
                        attrs.$observe("name", function(value) {
                            scope.field.name = value;
                        });
                    }
                    
                    if (attrs.error) {
                        scope.field.error = attrs.error;
                    } else {
                        scope.$root.$watch("errors['" + scope.field.name + "'][0]", function(val) {
                            scope.field.error = val;
                        });
                    }
                    if (attrs.ignoreFlash != "" && attrs.ignoreFlash != "true") {
                        var flashValue = scope.$root.$eval("flash['" + scope.field.name + "']");
                        if (flashValue !== undefined) {
                            scope.field.value = convert(flashValue);
                            if (attrs.type == "array") {
                            	scope.field.value = scope.field.value.split(",");
                            }
                            $parse(attrs.vField).assign(scope, scope.field.value);
                        }
                    }
                }
            }
        }
    },
    /**
     * @ngdoc directive
     * @name fields.directive:selectOne
     *
     * @description
     * Creates a single selection dropdown with filtering support. This will also create a hidden field with the
     * proper value, to support form submission (angular mangles the value of the select).
     *
     * @param {string=} type Convert the selected value to a different type.
     * @param {string=} emptyOption Default option when the list is empty.
     * @param {expression=} options Expression pointing to an array of objects with `id` and `name` attributes.
     * @param {string=id} keyProperty the property name of each option value
     * @param {string=name} valueProperty the property name of each option label
     * @param {boolean=} autoDefault automatically select the first option and update the model if the model value is null
     * @param {boolean=} autoSelectIfOne automatically select the option if the list of options only contains one item
     * @param {boolean=} disableEmpty will disable any options that have an empty key value
     *
     * @restrict E
     *
     *@example
     <example module="fields">
     <file name="index.html">
     <div ng-controller='FieldsCtrl' v-field='storage'>
        <select-one options="options"></select-one>{{field.value}}
     </div>
     </file>
     <file name="script.js">
     angular.module('fields').controller('FieldsCtrl', function($scope) {
        $scope.options = [{id:'vipr', name:'ViPR'}, {id:'vmax', name:'VMAX'}];
        $scope.storage = 'vipr';
     });
     </file>
     </example>
     */
    selectOne: function($timeout, $compile, $interpolate, optionals, translate, convertFilter) {
        return {
            require: "^vField",
            restrict: "E",
            replace: true,
            template: function(element, attrs) {
                optionals.extend(attrs, {valueProperty: 'id', labelProperty: 'name'});
                var f = $interpolate, defaultValue = "";
                if (attrs.options && !attrs.ngOptions) {
                    var context = angular.extend({
                        convert: attrs.type ? f('| convert:"{{type}}"')(attrs) : '',
                        source: attrs.emptyOption ?
                            f('[{ {{labelProperty}}:{{emptyOption}}}].concat({{options}})')(attrs) : attrs.options
                    }, attrs);
                    if (attrs.autoDefault || attrs.autoDefault == "") {
                        defaultValue = "v-default-value='" + context.source + "[0]." + attrs.valueProperty + "'";
                    }
                    attrs.ngOptions = f("opt.{{valueProperty}} {{convert}} as opt.{{labelProperty}} for opt in {{source}}")(context);
                }
                return "<select " + defaultValue + " v-track id='{{field.id}}' ng-model='field.value'></select>";
            },
            link: function(scope, element, attrs) {
                optionals(scope, attrs, { disableSearchThreshold: 4, searchContains: true, cssClass: "span6" });
                element.chosen({
                    disable_search_threshold: scope.disableSearchThreshold,
                    search_contains: scope.searchContains,
                    no_results_text: translate("chosen.noResult"),
                    placeholder_text_single: translate("chosen.select.single"),
                    placeholder_text_multiple: translate("chosen.select.multiple")
                });
                element.next().css('width', '').addClass("form-control " + scope.cssClass);

                if (attrs.available != null) {
                    scope.$watch(attrs.available, function(value) {

                        var names = value ? value.map(function(v) {return v[attrs.labelProperty]}) : [];
                        element.find("option").each(function() {
                            $(this).prop('disabled', names.indexOf($(this).text()) == -1);
                        });
                        element.trigger("chosen:updated");
                    }, true);
                }
                // we need to throw away our model value if it doesn't exist in the list of options,
                // but we want to be able to restore it if the option values change
                var discardedValue = null;
                
                var expressions = [attrs.ngDisabled, 'field.value'];
                angular.forEach(expressions, function(exp) {
                    if (scope.field.value) {
                        discardedValue = null;
                    }
                    
                    scope.$watch(exp, function() {
                        $timeout(function() { element.trigger("chosen:updated"); });
                    }, true);
                });
                
                scope.$watch(attrs.options, function(newOptions) {
                    if (attrs.autoSelectIfOne == "true" && newOptions && newOptions.length === 1) {
                    	var firstOptionValue = newOptions[0][attrs.valueProperty];
                    	scope.field.value = firstOptionValue;
                    }
                    
                    if (attrs.disableEmpty == "true") {
	                    element.find("option").each(function(i) {
		                    if (i > 0 && newOptions[i-1] != null) {
		                    	$(this).prop('disabled', newOptions[i-1].key == '');
		                    }
	                    });
                    }
                    
                    if (discardedValue) {
                        scope.field.value = discardedValue;
                    }
                    
                    // find current value in new list of options
                    var found = (newOptions || []).filter(function(opt) {
                        if (attrs.type) {
                            return convertFilter(opt[attrs.valueProperty], attrs.type) == scope.field.value;
                        }
                        else {
                            return opt[attrs.valueProperty] == scope.field.value;
                        }
                    }).length;
                    
                    if (!found) {
                        discardedValue = scope.field.value;
                        scope.field.value = null;
                    }
                    
                    $timeout(function() { element.trigger("chosen:updated"); });
                }, true);                
                
                var content = angular.element('<input type="hidden" name="{{field.name}}" value="{{field.value}}" />');
                content.attr("ng-disabled", attrs.ngDisabled);
                content.insertAfter(element);
                $compile(content)(scope);
            }
        }
    },
    /**
     * @ngdoc directive
     * @name fields.directive:selectMany
     *
     * @description
     * Creates a multi-select checkbox list with filtering support.
     *
     * @param {expression} options Expression pointing to an array of objects with `id` and `name` attributes.
     * @param {number=} searchThreshold Don't show a filter until we cross the threshold.
     * @param {boolean=} noMaxHeight Don't limit the max height of the list.
     *
     * @restrict E
     *
     *@example
     <example module="fields">
     <file name="index.html">
     <div ng-controller='FieldsCtrl' v-field='storage' type='array'>
     <select-many options="options"></select-many>{{field.value}}
     </div>
     </file>
     <file name="script.js">
     angular.module('fields').controller('FieldsCtrl', function($scope) {
        $scope.options = [{id:'vipr', name:'ViPR'}, {id:'vmax', name:'VMAX'}];
     });
     </file>
     </example>
     */
    selectMany: function(tag, $timeout, translate) {
        return tag('selectMany', {
            require: "^vField",
            defaults: {searchThreshold: 4, change: null, valueProperty: 'id', labelProperty: 'name'},
            scope: {options: '=', selected: '&', disabled: '=ngDisabled'},
            controller: function($scope) {
                $scope.checkAll = function(e) {
                    angular.forEach($scope.options, function(option) {
                        $scope.selections[option[$scope.valueProperty]] = true
                    });
                },

                $scope.uncheckAll = function(e) {
                    angular.forEach($scope.options, function(option) {
                        $scope.selections[option[$scope.valueProperty]] = false
                    });
                }
            },
            link: function(scope, element, attrs) {
                if (attrs.noMaxHeight !== undefined) {
                    scope.noMaxHeight = true;
                }
                
                // we need to throw away our model value if it doesn't exist in the list of options,
                // but we want to be able to restore it if the option values change
                var discardedSelections = scope.selections = {};

                scope.$watch('options', function(options) {
                    scope.visibleOptions = scope.options;
                    angular.extend(discardedSelections, scope.selections);
                    scope.selections = {};

                    // figure out which of the previously selected options is
                    // now available, and make the appropriate items selected
                    angular.forEach(options, function(option) {
                    	var optionValue = option[scope.valueProperty];
                        scope.selections[optionValue] = scope.field.value && scope.field.value.indexOf(optionValue) > -1;
                        if (discardedSelections[optionValue]) {
                        	scope.selections[optionValue] = true;
                        }
                    });
                }, true);

                scope.$watch('selections', function(selections, oldSelections) {
                    if (selections != oldSelections) {
                        var value = scope.$parent.field.value = [];
                        angular.forEach(selections, function(selected, id) {
                            if (selected) {
                                value.push(id);
                            }
                        });
                    }
                }, true);

                scope.$watch('searchQuery', function(value, oldValue) {
                    if (value !== oldValue) {
                        scope.visibleOptions = $.grep(scope.options, function(a) {
                            return new RegExp(value, "i").test(a[scope.labelProperty]);
                        });
                    }
                });
            }
        });
    },
    /**
     * @ngdoc directive
     * @name fields.directive:inputText
     *
     * @description
     * A styled text box. Automatically binds to `field.value`.
     *
     * @restrict E
     *
     *@example
     <example module="fields">
     <file name="index.html">
     <div ng-controller='FieldsCtrl' v-field='storage'>
        <input-text></input-text>{{storage}}
     </div>
     </file>
     <file name="script.js">
     angular.module('fields').controller('FieldsCtrl', function($scope) {
        $scope.storage = "ViPR";
     });
     </file>
     </example>
     */
    inputText: function() {
        return {
            require: "^vField",
            restrict: "E",
            replace: true,
            template: '<input type="text" name="{{field.name}}" id="{{field.id}}" ng-model="field.value" class="form-control" autocomplete="off">',
            link: function (scope, element, attrs) {
            	content.attr("ng-disabled",attrs.ngDisabled);
                scope.disabled = scope.$eval(attrs.ngDisabled);
                $compile(content)(scope);
            }
        }
    },
    /**
     * @ngdoc directive
     * @name fields.directive:dateTime
     *
     * @description
     * A styled text box. Automatically binds to `field.value`.
     *
     * @restrict E
     *
     *@example
     <example module="fields">
     <file name="index.html">
     <div ng-controller='FieldsCtrl' v-field='storage'>
        <date-time></date-time>{{storage}}
     </div>
     </file>
     <file name="script.js">
     angular.module('fields').controller('FieldsCtrl', function($scope) {
        $scope.storage = "ViPR";
     });
     </file>
     </example>
     */    
    dateTime: function($compile) {
        return {
            require: "^vField",
            restrict: "E",
            replace: true,
            template: '<input type="datetime-local" ng-model="field.value" class="form-control" autocomplete="on">',
            link: function (scope, element, attrs) {
                var content = angular.element('<input type="hidden" value="{{field.hidden}}" name="{{field.name}}" />');
                content.attr("ng-disabled", attrs.ngDisabled);
                content.insertAfter(element);
                scope.disabled = scope.$eval(attrs.ngDisabled);
                $compile(content)(scope);
            },
            controller: function ($scope) {
            	$scope.$watch('field.value', function(newVal, oldVal) {
					// Gets the value of the date/time in UTC milliseconds
            		var utcDateTimeInMillis = moment(newVal).toDate().getTime();
					// Keeps the date/time in millseconds and formats it
            		$scope.field.hidden = moment.utc(utcDateTimeInMillis).format("YYYY-MM-DD_HH:mm:ss");
                })
            	$scope.field.value = new Date();
            }
        }
    },    
    /**
     * @ngdoc directive
     * @name fields.directive:inputPassword
     *
     * @description
     * A styled password text box. Automatically binds to `field.value`.
     *
     * @restrict E
     *
     *@example
     <example module="fields">
     <file name="index.html">
     <div ng-controller='FieldsCtrl' v-field='password'>
        <input-password></input-password>{{password}}
     </div>
     </file>
     <file name="script.js">
     angular.module('fields').controller('FieldsCtrl', function($scope) {
        $scope.password = "ViPR";
     });
     </file>
     </example>
     */
    inputPassword: function(translate) {
        return {
            require: "^vField",
            restrict: "E",
            replace: true,
            template: '<input type="password" name="{{field.name}}" id="{{field.id}}" ng-model="field.value" class="form-control" autocomplete="off">',
            link: function(scope, element, attrs) {
            	if (attrs.matchWith) {
            		scope.$watch("field.value == " + attrs.matchWith + " || !field.value && !" + attrs.matchWith, function(matched) {
            			var errorMessage = matched ? null : translate(attrs.matchError || 'password.verify.defaultMatchError');
            			if (errorMessage) {
            				scope.errors[scope.field.name] = [errorMessage];
            			}
            			else {
            				scope.errors[scope.field.name] = [];
            			}
            		})
            	}
            }
        }
    },
    /**
     * @ngdoc directive
     * @name fields.directive:booleanCheckbox
     *
     * @description
     * A checkbox that will post a true/false value on form submission.
     *
     * @restrict E
     */
    booleanCheckbox: function($compile) {
        return {
            require: "^vField",
            restrict: 'E',
            template: '<input type=checkbox ng-disabled="disabled" ng-model="field.value">',
            link: function(scope, element, attrs) {
                element.addClass("checkbox");
                var content = angular.element('<input type="hidden" name="{{field.name}}" value="{{field.value}}"/>');
                content.attr("ng-disabled",attrs.ngDisabled);
                scope.disabled = scope.$eval(attrs.ngDisabled);
                content.insertAfter(element);
                $compile(content)(scope);
            }
        }
    },
    /**
     * @ngdoc directive
     * @name fields.directive:vSubmitForm
     *
     * @description
     * Triggers `util.submitForm` on click. This will add a spinner to the button, and performs general
     * housekeeping.
     */
    vSubmitForm: function() {
        return function(scope, element) {
            element.on("click", function(e) {
                e.preventDefault();
                util.submitForm(e.currentTarget, $(e.currentTarget).closest('form'));
            });
        }
    },
    /**
     * @ngdoc directive
     * @name fields.directive:vDisableChildren
     *
     * @description
     * Disable all child inputs, selects and buttons if the provided expression is truthy.
     * This watches the DOM directly, so it works with non angular elements.
     *
     * @params {expression} vDisableChildren Disable children if true.
     * @params {boolean} disableLinks Disable links in addition to inputs.
     *
     */
    vDisableChildren: function($timeout) {
        return function(scope, element, attrs) {
            var disabledElements = $(), timeout = null, disable = null;
            scope.$watch(attrs.vDisableChildren, function(val) {
                disable = val;
                if (disable) {
                    (function disableElements() {
                        if (!timeout && disable) {
                            timeout = $timeout(function() {
                                var selector = "select:enabled, input:enabled, button:enabled, textarea:enabled";
                                if (attrs.disableLinks != null && attrs.disableLinks != "false") {
                                    selector += ", a";
                                }
                                var enabled = element.find(selector);
                                enabled.prop("disabled", true).addClass("disabled").trigger("chosen:updated");
                                disabledElements = disabledElements.add(enabled);
                                //DOMSubtreeModified is deprecated, but seems to work reliably in
                                //Chrome, Safari, FF and IE9+. This may need to be revisited
                                //when the DOM4 mutation events are widely supported
                                element.one("DOMSubtreeModified", disableElements);
                                timeout = null;
                            });
                        }
                    })();
                } else {
                    angular.element(disabledElements).prop("disabled", false).removeClass("disabled").trigger("chosen:updated");
                    disabledElements = $();
                    $timeout.cancel(timeout);
                }
            });
        }
    },
    /**
     * @ngdoc directive
     * @name fields.directive:vDisabled
     *
     * @description
     * Disables a field and insert inserts hidden inputs containing the field's value or values to ensure it's submitted
     * to the server.
     *
     * @params {expression} vDisabled Disable field if true.
     *
     */
    vDisabled: function($compile) {
        return {
            require: "^vField",
            terminal: true,
            priority: 1000,
            link: function (scope, element, attrs) {
                element.removeAttr(attrs.$attr.vDisabled);
                attrs.$set("ngDisabled", attrs.vDisabled);
                $compile(element)(scope);

                scope.$watch("field.value", function (value) {
                    element.siblings("[data-disabled-value-holder]").remove();
                    if (scope.$eval(attrs.vDisabled)) {
                        var values = angular.isArray(value) ? value : [value];
                        angular.forEach(values, function (val) {
                            var elem = angular.element("<input data-disabled-value-holder type='hidden'>");
                            elem.attr("name", scope.field.name);
                            elem.attr("value", val);
                            elem.insertAfter(element);
                        })
                    }
                });
            }
        }
    },
    /**
     * @ngdoc directive
     * @name fields.directive:datePicker
     * 
     * @description
     * Creates a date picker control.
     *
     * @restrict E
     */
    datePicker: function(tag, $timeout) {
        return tag('datePicker', {
            require: "ngModel",
            scope: {
                model: "=ngModel"
            },
            link: function($scope, element, attrs) {
                var datepicker = element.find(".bfh-datepicker");
                
                datepicker.on("change", "input[type=text]", function(e) {
                    // Close the datepicker once a date is selected
                    if (attrs.close == 'true') {
                        datepicker.removeClass("open");
                    }
                    $timeout(function() {
                        $scope.model = getCurrentDate();
                    });
                });
                
                $scope.$watch('model', function(newVal, oldVal) {
                    var currentDate = getCurrentDate();
                    if (newVal !== currentDate) {
                        setCurrentDate(newVal);
                    }
                });
                
                function getCurrentDate() {
                    return datepicker.find("input[type=text]").val();
                }
                
                function setCurrentDate(value) {
                    var date = moment(value, "YYYY-MM-DD");
                    datepicker.data("date", value);
                    datepicker.data("year", date.year());
                    datepicker.data("month", date.month());
                    datepicker.data("day", date.day());
                    
                    var bfhdatepicker = datepicker.data("bfhdatepicker");
                    if (bfhdatepicker) {
                        bfhdatepicker.updateCalendar();
                    }
                    datepicker.find("input[type=text]").val(value).trigger('change');
                }
            }
        });
    },
    /**
     * @ngdoc directive
     * @name fields.directive:timePicker
     * 
     * @description
     * Creates a time picker control.
     *
     * @restrict E
     */
    timePicker: function(tag, $timeout) {
        return tag('timePicker', {
            require: "ngModel",
            scope: {
                model: "=ngModel"
            },
            controller: function($scope) {
                $scope.$watch('model', function() {
                    $scope.hour = zeroPad(getHour($scope.model));
                    $scope.minute = zeroPad(getMinute($scope.model));
                });
                
                $scope.nextHour = function() {
                    setTime(getHour($scope.model) + 1, getMinute($scope.model));
                };
                $scope.previousHour = function() {
                    setTime(getHour($scope.model) - 1, getMinute($scope.model));
                };
                $scope.nextMinute = function() {
                    setTime(getHour($scope.model), getMinute($scope.model) + 1);
                };
                $scope.previousMinute = function() {
                    setTime(getHour($scope.model), getMinute($scope.model) - 1);
                };
                $scope.closePopup = function(e) {
                    $scope.open = false;
                    $timeout(function() {
                        if (e.pageX && e.pageY) {
                            var x = e.pageX;
                            var y = e.pageY;
                            var el = document.elementFromPoint(x, y);
                            angular.element(el).trigger('click');
                        }
                    });
                };
                
                function zeroPad(value) {
                    return (value < 10 ? "0" : "") + value;
                }
                
                function getHour(time) {
                    if (time) {
                        var index = time.indexOf(":");
                        var value = time.substring(0, index);
                        return !isNaN(value) ? Number(value) : 0;
                    }
                    return 0;
                }
                
                function getMinute(time) {
                    if (time) {
                        var index = time.indexOf(":");
                        var value = time.substring(index + 1);
                        return !isNaN(value) ? Number(value) : 0;
                    }
                    return 0;
                }
                
                function setTime(hour, minute) {
                    hour = (hour < 0) ? (hour + 24) : (hour % 24);
                    minute = (minute < 0) ? (minute + 60) : (minute % 60);
                    $scope.model = zeroPad(hour) + ":" + zeroPad(minute);
                }
            }
        });
    },    
});
