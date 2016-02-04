/**
 * @ngdoc overview
 * @name services
 * @description
 *
 * # tags
 * Custom tags. Mainly templates with default values that can be overridden.
 *
 * <div doc-module-components="tags"></div>
 */
angular.module("services", []).directive({
    serviceField: function($compile, translate, $http, $interpolate) {
    	
    	function addBlankOptionIfRequired(item) {
    		// item is not required and has options
            if (item && !item.required && item.options && item.options.length > 0) {
            	item.options.unshift({
        			"key": "",
        			"value": translate("common.none")
            	});  
            }    		
    	}
    	
        return {
            restrict: "E",
            scope: true,
            controller: function($scope, $attrs) {
                var fieldDescriptor = $scope.assetFieldDescriptors[$scope.item.name] || {},
                    item = $scope.item;

                item.fullName = $attrs.name ? $interpolate($attrs.name)($scope) : item.name;
                if ($attrs.name) {
                    $attrs.$observe('name', function(value) {
                        item.fullName = $interpolate(value)($scope);
                    });
                }
                item.value = $scope.defaultValues[item.name] ? $scope.defaultValues[item.name] : item.initialValue;

                var getAssetOptionsIfWeHaveAllDependencies = function() {
                    var params = {};

                    angular.forEach(fieldDescriptor.fieldsWeDependOn, function(dependencyName) {
                        var val = $scope.overriddenValues[dependencyName];

                        if (val === undefined) {
                            val = $scope[dependencyName].value;
                        }

                        if (params && (val || val === false)) {
                            var dependencyAssetType = $scope.assetFieldDescriptors[dependencyName].assetType;
                            params[dependencyAssetType] = val;
                        } else {
                            params = null;
                        }
                    });

                    if (params) {
                        item.loading = true;
                        $http.get("/api/options/" + fieldDescriptor.assetType, {params: params }).success(function(data) {
                            item.disabled = false;
                            if (item.select == 'field') {
                            	item.value = data[0].value
                            } else {
                                item.options = data;
                            }
                            if (item.select != 'many') {
                            	addBlankOptionIfRequired(item);
                            }
                        }).error(function(data) {
                            var details = data.details || data;
                            $scope.$root.assetError = sprintf("Failed to retrieve options for field '%s' : %s",
                                item.label, details);
                        }).finally(function() {
                            item.loading = false;
                        });
                    } else {
                    	item.options = "";
                        item.disabled = true;
                    }
                };

                angular.forEach(fieldDescriptor.fieldsWeDependOn, function(itemName) {
                    $scope.$watch(itemName + ".value", getAssetOptionsIfWeHaveAllDependencies);
                });
            },
            link: function(scope, element, attrs) {
                var item = scope.item, type = null, tagAttrs = {}, validation = item.validation || {};
                if (item.type == "text") {
                    type = '<input-text>';
                    tagAttrs = {'maxlength': validation.max || 1024}; //Maximum length of an OrderParameter is 1024
                } else if (item.type == "password" || item.type == "password.verify") {
                    type = '<input-password>';
                    tagAttrs = {'maxlength': validation.max || 1024}; //Maximum length of an OrderParameter is 1024
                    if (item.matchWith) {
                        tagAttrs["match-with"] = item.matchWith;
                        tagAttrs["match-error"] = item.matchError;
                    }
                } else if (item.type.match(/^(number|storageSize|expandSize)$/)) {
                    type = '<input-text>';
                    tagAttrs = {'maxlength': validation.max || 18}; //anything bigger can overflow a long
                } else if (item.type == "boolean") {
                    type = '<boolean-checkbox>';
                    item.value = item.value == 'true' ? true : false;
                } else if (item.type == 'choice' && item.select == 'one') {
                    type = '<select-one>';
                    tagAttrs = {
                        'options': "item.options",
                        'value-property': "key",
                        'label-property': "value",
                        'auto-select-if-one': item.required
                    };
                } else if (item.type == 'choice') {
                    type = '<select-many>';
                    // TODO: support for select many 'choice'
                } else if (item.type.match(/^assetType\./)) {
                	if (item.select == 'field') {
                		type = '<input-text>';
                	}
                	else {
	                    item.options = scope.$root[scope.item.type + "-options"];
	                    tagAttrs = {
	                        'options': "item.options",
	                        'value-property': "key",
	                        'label-property': "value",
	                        'ng-disabled': "item.disabled",
	                        'auto-select-if-one': item.required
	                    };
	                    if (item.select == 'many') {
	                    	type = '<select-many>';
	                    }
	                    else {
	                    	type = '<select-one>';
	                    	addBlankOptionIfRequired(item);
	                    }
                	}
                } else if (item.type == 'dateTime') {
                    type = '<date-time>';
                } else {
                    item.error = " ";
                    type = "<p class='help-inline'>" + translate('serviceField.unsupportedType',item.type) + "</p>";
                }

                var tag = angular.element(type).attr(tagAttrs);

                var containerAttr = {
                    "v-field": "item.value",
                    "loading": "item.loading",
                    "required": item.required,
                    "label": item.label,
                    "name": "{{item.fullName}}",
                    "help-text": item.description,
                    "error": item.error
                };
                var container = angular.element(attrs.controlOnly ? "<table-field-column>" : "<control-group>")
                                       .attr(containerAttr).append(tag);
                element.append($compile(container)(scope));
            }
        }
    },
    serviceGroup: function(tag) {
        return tag('serviceGroup', {
            scope: true,
            preLink: function(scope, element, attrs) {
                scope.group = scope.$eval(attrs.group);
                angular.forEach(scope.group.items, function(value) {
                  scope[value.name] = value;
                });                
            }
        });
    },
    serviceTable: function(tag) {
        return tag('serviceTable', {
            scope: true,
            controller: function($scope, $attrs) {
                $scope.addRow = function() {
                    var items = {};
                    angular.forEach(this.table.items, function(value) {
                        items[value.name] = value;
                    });
                    this.table.value.push(angular.copy(items));
                };

                $scope.initRow = function() {
                    angular.extend(this, this.row);
                };
                
                $scope.deleteRow = function(index) {
                    this.table.value.splice(index, 1);
                };

                $scope.table = $scope.$eval($attrs.table);
                $scope.table.value = [];

                //add rows
                var rows = 0;
                angular.forEach($scope.flash, function(value, key) {
                    var match = key.match(new RegExp("^" + $scope.table.name + "\\[(\\d+)\\]"));
                    if (match && match[1] > rows) {
                        rows = match[1];
                    }
                });

                for (var i = 0; i <= rows; i++) {
                    $scope.addRow();
                }
            }
        });
    },
    tableFieldColumn: function(tag) {
        return tag.wrapper('tableFieldColumn', {
            link: function(scope, element, attrs) {
                if (attrs.required || attrs.required === '') {
                    scope.required = "required";
                }
            }
        })
    }
}).filter({
    unlocked: function($rootScope) {
        var serviceFields = {};
        angular.forEach($rootScope.serviceFields, function(field) {
            serviceFields[field.name] = field;
        });

        return function(items) {
            return items.filter(function(item) {
                return !(item.lockable && (serviceFields[item.name] || {}).override);
            });
        }
    }
});
