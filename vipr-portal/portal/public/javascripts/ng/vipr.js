/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * @ngdoc overview
 * @name vipr
 * @description
 *
 * # vipr
 * The vipr module includes common functionality used by the {@link fields fields} and {@link tags tags}
 * modules, as well as individual pages.
 *
 * <div doc-module-components="vipr"></div>
 */

angular.module('vipr', ["config"]).factory({
    /**
     * @ngdoc service
     * @name vipr.service:translate
     *
     * @description
     * Provides a translated, interpolated message from a key and optional arguments.
     *
     * By default, `translate` will return the message key for missing translations. Call `translate.raw` to return `null` instead.
     *
     * @param {string} key The message key.
     * @param {...args=} args Arguments for interpolation.
     * @returns {string} The translated message.
     * @example
     <example module="vipr">
     <file name="index.html">
     <div ng-controller="ExampleCtrl">
     <pre>
     {{greeting}}
     {{customGreeting}}
     {{missing}}
     {{missingRaw}}
     </pre>
     </div>
     </file>
     <file name="script.js">
     //typically auto-generated from our Play messages
     angular.module("config").value("messages", {
         'simple.translation':"Hello world",
         'simple.interpolation':"Hello %s"
     });

     angular.module("vipr").controller("ExampleCtrl", function($scope, translate) {
         angular.extend($scope, {
             greeting: translate('simple.translation'),
             customGreeting: translate('simple.interpolation', 'User'),
             missing: translate('missing.translation'),
             missingRaw: translate.raw('missing.translation')
         });
     });
     </file>
     </example>
     */
    translate: function(messages, $window) {
        var format = function(key) {
            var msg = messages[key], args = Array.prototype.slice.call(arguments, 1);
            return $window.sprintf.apply(null, [msg].concat(args));
        };

        var translate = function(key) {
            return format.apply(null, arguments) || key;
        };
        translate.raw = format;
        return translate;
    },
    /**
     * @ngdoc service
     * @name vipr.service:binder
     *
     * @description
     * Creates a two way binding between two objects in scope.  Used by {@link fields.directive:vField vField}.
     *
     * Use `binder.oneWay` to create a one way binding. `binder.twoWay` also aliases to `binder`, for symmetry.
     *
     * @param {Scope} firstScope The scope to bind from.
     * @param {string} firstExp The expression to bind from.
     * @param {string} secondExp The expression to bind to.
     * @param {Scope=} secondScope The scope to bind to. `firstScope` if null.
     * @returns {object} The current value of the binding `firstScope`.
     * @example
     <example module="vipr">
     <file name="index.html">
     <div ng-controller="ExampleCtrl">
     Parent: <input ng-model="parent"><br>
     Child: <input ng-model="child">
     </div>
     </file>
     <file name="script.js">
     angular.module("vipr").controller("ExampleCtrl", function($scope, binder) {
         $scope.parent = 'hello';
         binder($scope, 'parent', 'child');
     });
     </file>
     </example>
     */
    binder: function($parse) {
        var one = function(firstScope, firstExp, secondExp, secondScope) {
            var currentValue = firstScope.$eval(firstExp);
            secondScope = secondScope || firstScope;
            firstScope.$watch(firstExp, function(value) {
                if (!angular.equals(value, currentValue)) {
                    $parse(secondExp).assign(secondScope, value);
                    currentValue = value;
                }
            });
            return currentValue;
        };
        var two = function(firstScope, firstExp, secondExp, secondScope) {
            secondScope = secondScope || firstScope;
            one(secondScope, secondExp, firstExp, firstScope);
            return one(firstScope, firstExp, secondExp, secondScope);
        };

        return angular.extend(two, {oneWay: one, twoWay:two});
    },
    /**
     * @ngdoc service
     * @name vipr.service:convert
     *
     * @description
     * Tries to convert a string to a number, boolean, array or object.
     *
     * @param {string} val The string to convert.
     * @param {'number'|'boolean'|'object'|'array'=} type Type to convert to.
     * @returns {string|number|boolean|object|array|null} The converted object.
     * @example
     <example module="vipr">
     <file name="index.html">
     <div ng-controller="ExampleCtrl">
     Input: <input ng-model="input"> {{getType(input)}}
     </div>
     </file>
     <file name="script.js">
     angular.module("vipr").controller("ExampleCtrl", function($scope, convert, typeOf) {
         $scope.input = "[1,2,3]";
         $scope.getType = function(val) {
             return typeOf(convert(val));
         };
     });
     </file>
     </example>
     */
    convert:function($window, typeOf) {
        return function(val, type) {
            try {
                val = JSON.parse(val = val.toString())
            } catch(e) {}

            return !type || type == typeOf(val) ? val : null;
        }
    },
    /**
     * @ngdoc service
     * @name vipr.service:optionals
     *
     * @description
     * Used internally by tags and the {@link vipr.service:tag tag service} to accept default values, and to introduce
     * values into scope.
     *
     * * `attribute-name-val` attributes will be added to scope as is.
     * * `attribute-name-key` attributes will be used as a key for {@link vipr.service:translate}.
     * * `attribute-name` attributes will be added if they're in the defaults object
     *
     * @param {scope} scope The scope to add values to.
     * @param {attrs} attrs Attrs collection from a directive.
     * @param {object=} defaults Default values to start with.
     * @returns {scope} The updated scope.
     */
    optionals: function(translate, convert) {
        var optionals = function(scope, attrs, defaults) {
            var attributes = {};
            for (var attr in attrs.$attr) {
                if (defaults && defaults[attr] !== undefined) {
                    attributes[attr] = convert(attrs[attr]);
                } else if (attr.match(/\w+Val$/)) {
                    attributes[attr.replace(/Val$/, "")] = convert(attrs[attr]);
                } else if (attr.match(/\w+Key$/)) {
                    var translated = translate(attrs[attr]);
                    attributes[attr.replace(/Key$/, "")] = translated;
                }
            }

            return angular.extend(scope, defaults, attributes);
        };
        /**
         * @ngdoc method
         * @name vipr.service:optionals#extend
         * @methodOf vipr.service:optionals
         * @description
         *
         * Copy properties from `src` to `dst`. Don't overwrite keys in `dst`.
         *
         * @param {Object} dst Destination object.
         * @param {...Object} src Source object(s).
         * @returns {Object} Reference to `dst`.
         */
        optionals.extend = function(dst) {
            var merged = angular.extend.apply(null, Array.prototype.slice.call(arguments, 1).concat(dst));
            return angular.extend(dst, merged);
        };
        return optionals;
    },
    /**
     * @ngdoc service
     * @name vipr.service:tag
     *
     * @description
     * Used internally to create directives. `tag.wrapper` will look for a `<content \>` tag in the template
     * and replace it with the body content.
     *
     * {@link vipr.service:optionals optionals} are used to introduce passed attributes into scope.
     *
     * Directives are created with an isolated scope by default. `tag.wrapper` directives place their body
     * in the parent scope.
     *
     * A unique id will be added to the scope as `uid`. The `uniqueId` function will also be added to scope,
     * if additional ids are required.
     *
     * If `field` exists in the parent scope, it will also be added to the isolated scope.
     *
     * @param {string} directiveName The name of the directive to create.
     * @param {object} opts A set of options.
     *
     * In addition to standard angular {@link https://docs.angularjs.org/guide/directive directive} options,
     * this will take a `defaults` object, which will automatically be introduced into scope.
     *
     * @returns {scope} The constructed directive.
     * @example
     * ```js
     angular.module('vipr').directive("collapse", function(tag) {
       return tag.wrapper('collapse', {
         defaults: {icon: null},
         link: function(scope, element, attrs) {
           element.find(".collapse").on('hide.bs.collapse', function() {
             scope.$apply('state = null');
           }).on('show.bs.collapse', function() {
             scope.$apply('state = "in"');
           });
         }
       });
     });
     * ```
     */
    tag: function(optionals, uniqueId) {
        var tag = function(directiveName, opts) {
            opts = opts || {};
            var afterLink = opts.link;
            opts.link = {
                pre: opts.preLink,
                post: function(scope, element, attrs, controller, transclude) {
                    scope.field = scope.$parent.field;
                    scope.uid = uniqueId();
                    scope.uniqueId = uniqueId;

                    (transclude || angular.noop)(scope.$parent, function(content) {
                        element.find("content").replaceWith(content);
                    });
                    optionals(scope, attrs, opts.defaults);
                    (afterLink || angular.noop)(scope, element, attrs);
                }
            };

            return angular.extend({
                scope: {},
                priority: 0,
                transclude: false,
                templateUrl: '/public/templates/' + directiveName + '.html',
                restrict: 'E'
            }, opts);
        };
        tag.wrapper = function(directiveName, opts) {
            return tag(directiveName, angular.extend({ transclude:true }, opts));
        };
        return tag;
    },
    /**
     * @ngdoc service
     * @name vipr.service:uniqueId
     *
     * @description
     * Generates a new unique id each time it's called, suitable for use as the id attribute
     * of a DOM element.
     *
     * @returns {string} A unique id.
     */
    uniqueId: function() {
        var index = 0;
        return function() {
            return "uid" + index++;
        }
    },
    /**
     * @ngdoc service
     * @name vipr.service:typeOf
     *
     * @description
     * A replacement for the javascript typeof operator, with support for arrays, objects
     * and classes.
     *
     * @param {object} obj The object we're interested in.
     *
     * @returns {string} The type, lowercased.
     */
    typeOf: function() {
        //http://javascriptweblog.wordpress.com/2011/08/08/fixing-the-javascript-typeof-operator/
        return function(obj) {
            return ({}).toString.call(obj).match(/\s([a-zA-Z]+)/)[1].toLowerCase();
        }
    }
}).filter({
    /**
     * @ngdoc filter
     * @name vipr.filter:t
     *
     * @description
     * Provides a translated, interpolated message from a key and optional arguments.
     * Uses {@link vipr.service:translate translate} under the covers.
     *
     * @param {string} key The message key.
     * @param {...args=} args Optional arguments for interpolation. Currently supports %s and %d.
     * @returns {string} The translated message.
     * @example
     <example module="vipr">
     <file name="index.html">
     {{ 'label.name' | t }}: <input ng-model="name">
     <div ng-show="name">{{ 'user.greeting' | t:name }}</div>
     </file>
     <file name="script.js">
     //typically auto-generated from our Play messages
     angular.module("config").value("messages", {
         'label.name':"Your name",
         'user.greeting':"Hello %s"
     });
     </file>
     </example>
     */
    t: function(translate) {
        return function() {
            return translate.apply(null, arguments);
        }
    },
    /**
     * @ngdoc filter
     * @name vipr.filter:convert
     *
     * @description
     * Tries to convert a string to a number, boolean, array or object.
     *
     * @param {string} val The string to convert.
     * @param {'number'|'boolean'|'object'|'array'=} type Type to convert to.
     * @returns {string|number|boolean|object|array|null} The converted object.
     * @example
     <example module="vipr">
     <file name="index.html">
     <input type="text" ng-model="text" ><br>
     Type: {{text | convert | typeOf}}<br>
     As a number: {{text | convert:'number'}}
     </file>
     </example>
     */
    convert: function(convert) {
        return function(val, type) {
            return convert(val, type);
        }
    },
    /**
     * @ngdoc filter
     * @name vipr.filter:typeOf
     *
     * @description
     * The JS typeof operator as a filter, with additional support for arrays, objects and
     * classes.
     *
     * @param {string} val An object.
     * @returns {string|undefined} The type.
     * @example
     * see {@link vipr.filter:convert convert}
     */
    typeOf: function(typeOf) {
        return function(val) {
            return typeOf(val);
        }
    },
    /**
     * @ngdoc filter
     * @name vipr.filter:parseBytes
     *
     * @description
     * Formating the value given to show highest unit value possible
     *
     * @param {string} bytes A value in bytes.
     * @param {string} sig The number of significant digits to display
     * @returns {string} The formated text.
     * @example
     <example module="vipr">
     <file name="index.html">
     <input type="text" ng-model="bytes"><br>
     Formated number: {{bytes | parseBytes:2}}
     </file>
     </example>
     */
    parseBytes: function(){
        return function(bytes, sig) {
            return util.parseBytes(bytes, sig);
        }
    },
    /**
     * @ngdoc filter
     * @name vipr.filter:percentage
     *
     * @description
     * Evaluates and returns a percentage
     *
     * @param {string} input The percentage to be formated.
     * @param {string} sig The number of significant digits to display
     * @returns {string} The formated text.
     * @example
     <example module="vipr">
     <file name="index.html">
     <input type="text" ng-model="input" ><br>
     Formated text: {{input | percentage}}
     </file>
     </example>
     */
    percentage: function() {
        return function(input, sig) {
            if (!isNaN(parseFloat(input)) && isFinite(input)) {
                sig = sig ? sig : 0;
                return (input*100).toFixed(sig)+'%';
            }
            return '0%'
        }
    },
    /**
     * @ngdoc filter
     * @name vipr.filter:titleCase
     *
     * @description
     * Evaluates and returns the pass string in lower case, starting each word with a capital letter.
     * For example CREATE VOLUME becomes Create Volume
     *
     * @param {string} input The text to be formatted
     * @returns {string} The formated text.
     * @example
     <example module="vipr">
     <file name="index.html">
     <input type="text" ng-model="input" ><br>
     Formated text: {{input | titleCase}}
     </file>
     </example>
     */
    titleCase: function() {
        return function (input) {
            return input.replace(/\w\S*/g, function (txt) {
                return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();
            });
        }
    },
    /**
     * @ngdoc filter
     * @name vipr.filter:keys
     *
     * @description
     * Returns the keys of an object as an array
     *
     * @param {object} obj Any object.
     * @returns {array} The object keys.
     * @example
     */
    keys: function() {
        return function(obj) {
            return Object.keys(angular.copy(obj));
        }
    },
    /**
     * @ngdoc filter
     * @name vipr.filter:timeAgoInWords
     *
     * @description
     * Formats the timestamp into a humanized string representing how much time has passed using the moment library
     *
     * @param {timestamp} input Date timestamp in long format
     * @returns {string} Text description of the elapsed time
     * @example
     <example module="vipr">
     <file name="index.html">
     <input type="text" ng-model="input" ><br>
     Formated text: {{input | moment}}
     </file>
     </example>
     */
    timeAgoInWords: function() {
        return function (input) {
            return moment.duration(input).humanize();
        }
    },
    /**
     * @ngdoc filter
     * @name vipr.filter:values
     *
     * @description
     * Returns the values of an object as an array
     *
     * @param {object} obj Any object.
     * @returns {array} The object values.
     * @example
     */
    values: function() {
        return function(obj) {
            return Object.keys(angular.copy(obj)).map(function(key) {
                return obj[key];
            });
        }
    },
    /**
     * @ngdoc reverse
     * @name vipr.filter:reverse
     *
     * @description
     * Reverses the values in the array, if enabled.
     *
     * @param {array} array The array to reverse
     * @param {boolean} enabled Whether the reversal is enabled
     * @returns {array} The reversed array
     */
    reverse: function() {
        return function(array, enabled) {
            if (array && enabled) {
                return array.slice().reverse();
            }
            else {
                return array;
            }
        }
    }

}).directive({
    /**
     * @ngdoc directive
     * @name vipr.directive:vRequireController
     *
     * @description
     * Ignore all other angular directives if the element isn't contained within an angular controller.
     *
     * This allows us to add angular tags to our existing groovy tags, which will automatically be
     * disabled on pages that don't use angular.

     */
    vRequireController: function($compile) {
        return {
            priority: 1000,
            terminal: true,
            compile: function() {
                return {
                    pre: function(scope, element, attrs) {
                        if (element.controller()) {
                            element.removeAttr(attrs.$attr.vRequireController);
                            $compile(element)(scope);
                        }
                    }
                }
            }
        }
    },
    /**
     * @ngdoc directive
     * @name vipr.directive:vSetDefaultModelValue
     *
     * @description
     * Automatically default the model value to the form field value. Allows our server generated
     * groovy tags to be used with angular.
     *
     * @element INPUT|SELECT
     *
     * @example
     <example module="vipr">
     <file name="index.html">
     <input ng-model="val" value="hello"> my value is {{val}}<br>
     <input ng-model="val2" v-set-default-model-value value="hello"> my value is {{val2}}
     </file>
     </example>
     */
    vSetDefaultModelValue: function($parse) {
        return {
            require: "ngModel",
            link: {
                pre: function(scope, element, attrs) {
                    $parse(attrs.ngModel).assign(scope, element.val());
                }
            }
        }
    },
    /**
     * @ngdoc directive
     * @name vipr.directive:vScope
     *
     * @description
     * Creates a new scope. Also adds the new scope to the directive's controller, which
     * can then be accessed from other directive. Used by {@link vipr.directive:vTrack vTrack}.
     *
     * @element INPUT|SELECT
     *
     * @example
     * see {@link vipr.directive:vTrack vTrack}.
     */
    vScope: function() {
        return {
            restrict: 'AE',
            scope: true,
            controller: function($scope) {
                this.scope = $scope;
            }
        }
    },
    /**
     * @ngdoc directive
     * @name vipr.directive:vTrack
     *
     * @description
     * Tracks changes inside of a {@link vipr.directive:vScope vScope}.
     * Introduces the boolean `dirty` and the number `changeCount` to scope, indicating
     * the current dirty state and change count.
     *
     * @element INPUT|SELECT
     *
     * @example
     <example module="vipr">
     <file name="index.html">
     <div v-scope>
       <input v-track value='change me'>
       <input v-track value='change me too'>
       <span ng-show="dirty">{{changeCount}} changes</span>
     </div>
     </file>
     </example>
     */
    vTrack: function($timeout) {
        return {
            require: "?^vScope",
            link: function(scope, element, attrs, scopeCtrl) {
                if (scopeCtrl) {
                    var origVal = null,
                        wasDirty = false;

                    $timeout(function() {
                        origVal = element.val();
                    });

                    element.on("change input", function() {
                        var isDirty = origVal !== element.val(),
                            count = scopeCtrl.scope.changeCount || 0;

                        count += (isDirty != wasDirty) * (isDirty || -1);
                        wasDirty = isDirty;
                        angular.extend(scopeCtrl.scope, {changeCount: count, dirty: count > 0});
                        scopeCtrl.scope.$apply();
                    });
                }
            }
        }
    },
    /**
     * @ngdoc directive
     * @name vipr.directive:vDefaultValue
     *
     * @description
     * Provide a default value for cases where the model might be `null`. This will also update the model value.
     *
     * @param {expression} vDefaultValue {@link https://docs.angularjs.org/guide/expression Expression} which evalues
     * to the default value for the model.
     *
     * @example
     <example module="vipr">
     <file name="index.html">
     <select ng-options="value for value in ['ViPR', 'VMAX']" ng-model="val1"></select>{{val1}}<br>
     <select ng-options="value for value in ['ViPR', 'VMAX']" ng-model="val2" v-default-value="'ViPR'"></select>{{val2}}<br>
     </div>
     </file>
     </example>
     */
    vDefaultValue: function() {
        return function(scope, element, attrs) {
            if (attrs.ngModel && scope.$eval(attrs.ngModel) == null) {
                scope.$eval(attrs.ngModel + ' = ' + attrs.vDefaultValue);
            }
        }
    }
});
