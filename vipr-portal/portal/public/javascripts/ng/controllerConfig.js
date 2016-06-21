angular
    .module("portalApp")

    .directive("generatePreview", function(customConfigService) {
        return function($scope, $element, $attrs) {
            $scope.$watch($attrs.generatePreview, function() {
                var config = $scope.$eval($attrs.generatePreview);
                if ($scope.getSelectedConfigType().type === "Boolean") {
                    return;
                }
                customConfigService.getPreview($scope.configType, config, $scope.token)
                .success(function(preview) {
                    if (preview.detailedMessage) {
                        config.error = true;
                        $element.html(preview.detailedMessage);
                    } else if ($scope.getConfigType(config).metaType === "CustomName" && $scope.getConfigType(config).type === "String" && config.value.indexOf("{") == -1) {
                        config.error = true;
                        $element.html($scope.$filter("t")("CustomConfigs.noVariables.error"));
                    } else if (preview.resolvedValue) {
                        config.error = false;
                        $element.html($scope.$filter("t")("CustomConfigs.example") + ": " + preview.resolvedValue);
                    }
                });
            }, true);
        }
    })

    .directive("highlightOptionErrors", function() {
        var doHighlight = function($scope, $element, $attrs) {
            var options = $scope.$eval($attrs.options);
            if (!options) {
                return;
            }
            $element.children("option").each(function(index) {
                if ($scope.numErrors(options[index].id) > 0) {
                    $(this).addClass("errorMessage");
                } else {
                    $(this).removeClass("errorMessage");
                }
            });
            $element.trigger("chosen:updated");
        }
        return function($scope, $element, $attrs) {
            $scope.$watch($attrs.options, function() {
                doHighlight($scope, $element, $attrs);
            });
            $scope.$watch($attrs.highlightOptionErrors, function() {
                doHighlight($scope, $element, $attrs);
            });
        }
    })

    .directive("watchForErrors", function() {
        return function($scope, $element, $attrs) {
            var config = $scope.$eval($attrs.watchForErrors);
            $scope.$watchCollection("config", function(newValue, oldValue) {
                $scope.errorCount = $scope.numErrors("");
            });
        }
    })

    .controller("CustomConfigCtrl", function($scope, customConfigService, $timeout, $filter, $location) {
        $scope.configType = "";
        $scope.configTypes = [];
        $scope.categoryOptions = {};
        $scope.typeOptions = [];
        $scope.valueOptions = [];
        $scope.selectedCategory;
        $scope.categories = [ "SanZone", "VMAX", "VNX", "VPlex", "PortAllocation", "XtremIO", "HDS", "Nas", "Isilon", "VolumeNaming", "Other" ];
        $scope.configs = [];
        $scope.filteredConfigs = [];
        $scope.variables = [];
        $scope.token = $("input[name=authenticityToken]").val();
        $scope.$location = $location;
        $scope.$filter = $filter;
        $scope.errorCount = 0;

        $scope.getConfigType = function(config) {
            for (var i = 0; i < $scope.configTypes.length; i++) {
                var configType = $scope.configTypes[i];
                if (config.configType === configType.configType) {
                    return configType;
                }
            }
        }

        $scope.numChanges = function() {
            var count = 0;
            for (var i = 0; i < this.configs.length; i++) {
                if (this.configs[i].dirty) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Returns the number of invalid custom configs for the specified category.
         * If category is an empty string, returns the total number of errors.
         */
        $scope.numErrors = function(category) {
            var count = 0;
            for (var i = 0; i < this.configs.length; i++) {
                var config = this.configs[i];
                if (config.remove || !startsWith(config.configType, category)) {
                    continue;
                }
                if (!isValid(config)) {
                    count++;
                }
            }
            return count;
        }

        function isValid(config) {
            return !config.error && config.scopeType && config.scopeValue;
        }

        /**
         * Returns the configs that will be displayed.
         */
        $scope.getFilteredConfigs = function() {
            this.filteredConfigs.length = 0;
            for (var i = 0; i < this.configs.length; i++) {
                var config = this.configs[i];
                if (config.configType === this.configType && !config.remove) {
                    this.filteredConfigs.push(config);
                }
            }
            return this.filteredConfigs;
        }

        $scope.setCategory = function(category) {
            $scope.selectedCategory = category;
            $scope.configTypeChanged($scope.getConfigTypeOptions()[0].id);
        }

        $scope.getConfigTypeOptions = function() {
            return this.categoryOptions[this.selectedCategory];
        }

        $scope.getTypeOptions = function() {
            return this.typeOptions[this.configType];
        }

        $scope.getValueOptions = function(config) {
            if (!config.valueOptions) {
                config.valueOptions = [];
            }
            config.valueOptions.length = 0;
            if (!this.valueOptions[config.scopeType]) {
                return config.valueOptions;
            }
            for (var i = 0; i < this.valueOptions[config.scopeType].length; i++) {
                config.valueOptions.push(this.valueOptions[config.scopeType][i]);
            }
            if (config.systemDefault) {
                return config.valueOptions;
            }
            for (var i = 0; i < this.filteredConfigs.length; i++) {
                var filteredConfig = this.filteredConfigs[i];
                if (filteredConfig.systemDefault || config === filteredConfig) {
                    continue;
                }
                for (var j = config.valueOptions.length - 1; j >= 0; j--) {
                    if (config.valueOptions[j].id === filteredConfig.scopeValue) {
                        config.valueOptions.splice(j, 1);
                    }
                }
            }
            return config.valueOptions;
        }

        $scope.getBooleanOptions = function() {
            var options = [];
            var ids = [ "true", "false" ];
            for (var i = 0; i < ids.length; i++) {
                options.push({ id: ids[i], name: $scope.$filter("t")("boolean." + ids[i]) });
            }
            return options;
        }

        $scope.add = function() {
            this.configs.push({ configType: this.configType, scopeType: null, scopeValue: null, value: "" });
        }

        $scope.remove = function(config) {
            config.dirty = true;
            config.remove = true;
            config.error = false;

            if (!config.id) {
                var index = this.configs.indexOf(config);
                this.configs.splice(index, 1);
            }
        }

        $scope.save = function() {
            var count = 0;

            var submitWhenDone = function() {
                count++;
                if (count == $scope.numChanges()) {
                    location.reload();
                }
            }

            for (var i = 0; i < this.configs.length; i++) {
                var config = this.configs[i];

                if (config.dirty) {
                    if (config.remove) {
                        customConfigService.deleteConfig(config, this.token).success(submitWhenDone);
                    } else if (config.id) {
                        customConfigService.updateConfig(config, this.token).success(submitWhenDone);
                    } else {
                        customConfigService.createConfig(config, this.token).success(submitWhenDone);
                    }
                }
            }
        }

        $scope.cancel = function() {
            location.reload();
        }

        function getData() {
            customConfigService.getConfigTypes().then(
                function(response) {
                    $scope.configTypes = response.aaData;
                    buildTypeOptions();
                    $timeout(function() {
                        var path = $scope.$location.$$path.replace("/", "");
                        if (path) {
                            $("#contentArea ul.nav li a#" + path).click();
                        } else {
                            $("#contentArea ul.nav li a").first().click();
                        }
                    });

                    customConfigService.getConfigs().then(
                        function(configs) {
                            $scope.configs = configs.aaData;
                        }
                    );
                }
            );
        }

        $scope.configTypeChanged = function(value) {
            $scope.configType = value;
            $scope.variables = this.getSelectedConfigType().variables;
            buildOptions();
        }

        $scope.getSelectedConfigType = function() {
            for (var i = 0; i < $scope.configTypes.length; i++) {
                if ($scope.configTypes[i].configType === $scope.configType) {
                    return $scope.configTypes[i];
                }
            }
        }

        function buildTypeOptions() {
            for (var i = 0; i < $scope.categories.length; i++) {
                var category = $scope.categories[i];
                if (category === "Other") {
                    continue;
                }

                var options = [];
                for (var j = 0; j < $scope.configTypes.length; j++) {
                    var configType = $scope.configTypes[j];
                    if (startsWith(configType.configType, category)) {
                        options.push({ id: configType.configType, name: $scope.$filter("t")("CustomConfigs.configType." + configType.configType) });
                    }
                }
                $scope.categoryOptions[category] = options;
            }

            var options = [];
            for (var i = 0; i < $scope.configTypes.length; i++) {
                var configType = $scope.configTypes[i];
                if (isOther(configType)) {
                    options.push({ id: configType.configType, name: $scope.$filter("t")("CustomConfigs.configType." + configType.configType) });
                }
            }
            $scope.categoryOptions["Other"] = (options.length > 0) ? options : null;
        }

        function isOther(configType) {
            for (var i = 0; i < $scope.categories.length; i++) {
                var category = $scope.categories[i];
                if (startsWith(configType.configType, category)) {
                    return false;
                }
            }
            return true;
        }

        function startsWith(str, prefix) {
            return str.substring(0, prefix.length) === prefix;
        }

        function buildOptions() {
            $scope.typeOptions = [];
            $scope.valueOptions = [];

            var configType = $scope.getSelectedConfigType();

            var scopeTypeOptions = [];
            for (var i = 0; i < configType.scopes.length; i++) {
                var scope = configType.scopes[i];
                scopeTypeOptions.push({ id: scope.type, name: $filter("t")("CustomConfigs.scopeType." + scope.type) });
                var scopeValueOptions = [];
                for (var j = 0; j < scope.value.length; j++) {
                    scopeValueOptions.push({ id: scope.value[j], name: $filter("t")("CustomConfigs.scopeValue." + scope.value[j]) });
                }
                $scope.valueOptions[scope.type] = scopeValueOptions;
            }
            $scope.typeOptions[configType.configType] = scopeTypeOptions;
        }

        getData();
    })
    
    .service("customConfigService", function($http, $q) {
        return {
            getConfigTypes : getConfigTypes,
            createConfig   : createConfig,
            getConfigs     : getConfigs,
            updateConfig   : updateConfig,
            deleteConfig   : deleteConfig,
            getPreview     : getPreview
        };

        function getConfigTypes() {
            return $http({
                method: "get",
                url: "/customConfigs/configTypes"
            })
            .then(handleSuccess, handleError);
        }

        function getPreview(configType, config, token) {
            return $http({
                method: "post",
                url: "/customConfigs/preview",
                data: {
                    configType: configType,
                    scopeType: config.scopeType,
                    scopeValue: config.scopeValue,
                    value: config.value,
                    authenticityToken: token
                }
            })
        }

        function createConfig(config, token) {
            return $http({
                method: "post",
                url: "/customConfigs/create",
                data: {
                    configType: config.configType,
                    scopeType: config.scopeType,
                    scopeValue: config.scopeValue,
                    value: config.value,
                    authenticityToken: token
                }
            })
        }

        function getConfigs() {
            return $http({
                method: "get",
                url: "/customConfigs/listConfigs"
            })
            .then(handleSuccess, handleError);
        }

        function updateConfig(config, token) {
            return $http({
                method: "post",
                url: "/customConfigs/update",
                data: {
                    id: config.id,
                    configType: config.configType,
                    scopeType: config.scopeType,
                    scopeValue: config.scopeValue,
                    value: config.value,
                    authenticityToken: token
                }
            })
        }

        function deleteConfig(config, token) {
            return $http({
                method: "post",
                url: "/customConfigs/delete",
                data: {
                    id: config.id,
                    authenticityToken: token
                }
            })
        }

        function handleSuccess(response) {
            return response.data;
        }

        function handleError(response) {
            return (!angular.isObject(response.data) || !response.data.message)
                    ? $q.reject("Unknown error")
                    : $q.reject(response.data.message);
        }
    })

;
