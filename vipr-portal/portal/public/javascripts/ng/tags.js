/**
 * @ngdoc overview
 * @name tags
 * @description
 *
 * # tags
 * Custom tags. Mainly templates with default values that can be overridden.
 *
 * <div doc-module-components="tags"></div>
 */
angular.module('tags', ['vipr', 'fields']).directive({

    /**
     * @ngdoc directive
     * @name tags.directive:controlGroup
     *
     * @description
     * Our standard form control container.
     *
     * @param {number=3} labelWidth The width of the label.
     * @param {number=6} width The width of the control.
     * @param {boolean=} required Make the field required.
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <div ng-controller='TagsCtrl'>
     <control-group v-field='storage'>
       <select-one options="options"></select-one>
     </control-group>
     </div>
     </file>
     <file name="script.js">
     angular.module("config").value("messages", {
         "storage":"Storage type",
         "storage.help":"Select the type of storage"
     });

     angular.module('tags').controller('TagsCtrl', function($scope) {
        $scope.options = [{id:'vipr', name:'ViPR'}, {id:'vmax', name:'VMAX'}];
        $scope.storage = 'vipr';
     });
     </file>
     </example>
     */
    controlGroup: function(tag) {
        return tag.wrapper('controlGroup', {
            priority: 1,
            defaults: { labelWidth: 3, width: 6 },
            link: function(scope, element, attrs) {
                scope.errorWidth = 12 - (scope.width + scope.labelWidth);
                if (attrs.required || attrs.required === '') {
                    scope.required = "required";
                }
            }
        })
    },
    /**
     * @ngdoc directive
     * @name tags.directive:buttonBar
     *
     * @description
     * Format buttons to match the rest of our app.
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <button-bar>
         <button type="submit" v-submit-form class="btn btn-primary"><i v-icon=ok-sign></i> Save</button>
     </button-bar>
     </file>
     </example>
     */
    buttonBar: function(tag) {
        return tag.wrapper('buttonBar');
    },
    /**
     * @ngdoc directive
     * @name tags.directive:formHeader
     *
     * @description
     * Displays a form header
     *
     * @param {string=} title The title to display.
     * @param {string=} title-key Lookup the title from a message bundle.
     * @param {string=} description Additional information about the form.
     * @param {string=} descrption-key Lookup the description from a message bundle.
     *
     * @example
     <example module="tags">
     <file name="index.html">
        <form-header description="I'm a form header" title="header"></form-header>
     </file>
     </example>
     */
    formHeader: function(tag) {
        return tag('formHeader', { defaults: { description:null, title:null }});
    },
    /**
     * @ngdoc directive
     * @name tags.directive:collapse
     *
     * @description
     * A bootstrap collapsible region.
     *
     * @param {string=} title The title to display in the region header.
     * @param {string=} titleKey A message key to look up the title to display in the region header.
     * @param {string=} icon An icon to display in the header.
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <collapse title="Dogs" icon="vipricon-home">
        <h1>Dogs!</h1>
     </collapse>
     <collapse title="Cats" icon="vipricon-home">
        <h1>Cats!</h1>
     </collapse>
     </file>
     </example>
     */
    collapse: function(tag) {
        return tag.wrapper('collapse', {
            transclude: true,
            defaults: {icon: null, title: null},
            link: function(scope, element, attrs) {

                element.find(".collapse").on('hide.bs.collapse', function(e) {
                    e.stopPropagation();
                    scope.$apply('state = null');
                }).on('show.bs.collapse', function() {
                    scope.$apply('state = "in"');
                });

                setTimeout(function() {
                    if (element.find(".has-error").length) {
                        element.find(".collapse").addClass("in");
                    }
                });
            }
        });
    },
    /**
     * @ngdoc directive
     * @name tags.directive:vIcon
     *
     * @description
     * Display an icon
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <i v-icon=vipricon-home></i><br>
     <i v-icon=ok-sign></i>
     </file>
     </example>
     */
    vIcon: function(tag) {
        return tag.wrapper('icon', {
            restrict: 'A',
            link: function(scope, element, attrs) {
                attrs.$observe('vIcon', function (icon) {
                    if (icon.match(/^glyphicon-/)) {
                        scope.icon = "glyphicon " + icon;
                    } else if (icon.match(/^vipricon-/)) {
                        scope.icon = "vipricon " + icon;
                    } else {
                        scope.icon = "glyphicon glyphicon-" + icon;
                    }
                });

                attrs.$observe('vClass', function(vclass) {
                    scope.class = vclass;
                });
            }
        });
    },

    /**
     * @ngdoc directive
     * @name tags.directive:knob
     *
     * @description
     * Generates a JQuery Knob
     * 
     * @restrict E
     *
     * @param {string=} title-val The title to display in the region header.
     * @param {string=} title-key Lookup the title from a message bundle.
     * @param {string=} danger Expression for when to display bootstrap -danger color
     * @param {string=} warn Expression for when to display bootstrap -warning color
     * @param {number} value Current value of the knob
     * @param {number} max Max value of the knob
     * @param {boolean=} capacity If true knob is displaying a capacity (bytes)
     * @param {number=100} width Width of the knob in px
     * @param {number=100} height Height of the knob in px
     * @param {number=-125} angleOffset Angle offset to start the knob in degrees
     * @param {number=250} angleArc Arc portion to display of the knob
     * @param {boolean=true} readOnly Disable the mouse and keyboard events for the knob
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <knob value="50" max="100" title-val="Test Knob"></knob>
     </file>
     </file>
     <file name="script.js">
     angular.module("config").value("colors", {success: "green"});
     </file>
     </example>
     */
    knob: function(tag, $timeout, colors, $parse) {
        return tag('knob', {
            scope: {value: '=', max: '=', capacity: '='},
            defaults: {
                'width':100,
                'height': 100,
                'angleOffset':-125,
                'angleArc':250,
                'readOnly': true,
                'displayInput': false,
                'warnColor': colors.warn,
                'dangerColor': colors.danger,
                'successColor': colors.success
            },
            link: function (scope, elem, attrs) {
                var $elem = elem.find('input');
                var $display = elem.find('.knobValue');
                
                var changeColor = function(color) {
                    $elem.trigger('configure', {'fgColor': color});
                    $display.each(function() {$(this).css('color', color);});
                }

                scope.$watch("value", function(value, oldValue) {
                    if (attrs.danger && scope.$eval(attrs.danger)) {
                        changeColor(scope.dangerColor);
                    } else if (attrs.warn && scope.$eval(attrs.warn)) {
                        changeColor(scope.warnColor);
                    } else {
                        changeColor(scope.successColor);
                    }
                    $elem.val(scope.value);
                    $elem.change();
                });
                $elem.knob(scope);
            }
        });
    },
    /**
     * @ngdoc directive
     * @name tags.directive:breadcrumbContainer
     * 
     * @restrict E
     *
     * @description
     * Wrapper for the breadcrumb navigation
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <breadcrumb-container>
         <breadcrumb url='#' title-key='parent'></breadcrumb>
         <breadcrumb title-key="child"></breadcrumb>
     </breadcrumb-container>
     </file>
     </example>
     */
    breadcrumbContainer: function(tag) {
        return tag.wrapper('breadcrumbContainer');
    },
    /**
     * @ngdoc directive
     * @name tags.directive:breadcrumb
     * 
     * @restrict E
     *
     * @description
     * Breadcrumb navigation
     * 
     * @param {string} url The url to be linked to the breadcrumb
     * @param {boolean} active Check to add the 'active' class to the breadcrumb
     * @param {string} title The display value for the breadcrumb 
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <breadcrumb-container>
         <breadcrumb url='#' title-key='parent'></breadcrumb>
         <breadcrumb title-key="child"></breadcrumb>
     </breadcrumb-container>
     </file>
     </example>
     */
    breadcrumb: function(tag) {
        return tag('breadcrumb',{
            scope:{url: '@', active: '='},
            replace:true
        });
    },
    /**
     * @ngdoc directive
     * @name tags.directive:breadcrumbDropdown
     * 
     * @restrict E
     *
     * @description
     * Breadcrumb navigation dropdown
     * 
     * @param {boolean} active Check to add the 'active' class to the breadcrumb
     * @param {string} title The display value for the breadcrumb 
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <breadcrumb-container>
         <breadcrumb url='#' title-key='parent'></breadcrumb>
         <breadcrumb-dropdown title-key="child">
             <li><a>Option 1</a></li>
             <li><a>Option 2</a></li>
         </breadcrumb-dropdown>
     </breadcrumb-container>
     </file>
     </example>
     */
    breadcrumbDropdown: function(tag) {
        return tag.wrapper('breadcrumbDropdown',{
            scope: {active: '='},
            replace:true
        });
    },

    /**
     * @ngdoc directive
     * @name tags.directive:status
     *
     * @description Renders a generic status badge
     *
     * @restrict E
     *
     * @param {string} type The status type. An object that exists under config.statusTypes.
     * @param {string} status The status to be rendered
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <status type="alert" status="success"></status><br />
     <status type="cluster" status="stable"></status>
     </file>
     <file name="script.js">
     //typically auto-generated from our Play messages
     angular.module("config").value({
       statusTypes: {
         alert: {success: { icon: 'ok', class: 'text-success', key: 'status.success' }},
         cluster: {stable: { icon: 'ok', class: 'label label-success', key: 'status.stable' }}
       },
       messages: {
         "status.success":"Success",
         "status.stable":"Stable"
       }
     });
     </file>
     </example>
     */
    status: function(statusTypes) {
        return {
            restrict: "E",
            template: '<span ng-class="status.class"><i v-icon="{{status.icon}}" v-class="{{status.iconClass}}"></i>&nbsp;{{status.key | t}}</span>',
            scope: {},
            link: function(scope, elem, attrs) {
                attrs.$observe('status', function(status) {
                    if (!attrs['status'] || !attrs['type']) {
                        throw "The attributes status and type must be provided: type=" + attrs.type + ", status=" + attrs.status;
                    }
                    var statusType = statusTypes[attrs.type];
                    if (!statusType) {
                        throw "Unable to find statusType for type " + attrs.type;
                    }
                    scope.status = statusType[attrs.status] || statusType.default;
                });
            }
        }
    },

    /**
     * @ngdoc directive
     * @name tags.directive:resourceLink
     *
     * @description Renders a link with icon for the specified resource ID.  If no link is found, in the config, then only an icon is rendered
     *
     * @restrict E
     *
     * @param {string} required id The URN ID of the resource
     * @param {string=34} The pixel size of the resource icon
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <v-resource-link id="urn:storageos:Volume:e4ce01bd-8380-441b-9987-cc6ffa6910c8:vdc1" size="40>maddid-123512</v-resource-link>
     </file>
     <file name="script.js">
     //typically auto-generated from our Play messages
     angular.module("config").value({
        resourceTypes: {
            Volume : {
                        icon:'volume.png',
                        link: #{jsAction @resources.BlockVolumes.volume(':id') /}
            },
             ExportGroup: {
                        icon:'export.png',
                        link: #{jsAction @resources.BlockExportGroups.exportGroup(':id') /}
            }
        }
     });
     </file>
     </example>
     */
    vResourceLink: function(resourceTypes) {
        return  {
            restrict: 'E',
            transclude: true,
            template: "<img ng-if='image' src='/public/img/assets/{{image}}' ng-style='{{style}}'><a ng-if='link' href='{{link}}' ng-transclude></a><span ng-if='!link' ng-transclude></span>",
            link: function (scope, element, attrs) {
                if (!attrs['id']) {
                    console.error("vResourceLink requires resource URN id")
                    return;
                }

                var match = /urn:storageos:([^\\:]+)/.exec(attrs.id);
                scope.type = match[1];
                scope.size = 34; // Default Image Size
                if (attrs['size']) {
                    scope.size = attrs.size;
                }

                scope.style = {
                    'width':scope.size
                }

                if (resourceTypes[scope.type]) {
                    scope.image = resourceTypes[scope.type].icon;
                    if (resourceTypes[scope.type].link) {
                        scope.link = resourceTypes[scope.type].link({id: attrs.id});
                    }
                }
                else {
                    scope.image = undefined
                }
            }
        }
    },
    /**
     * @ngdoc directive
     * @name tags.directive:loading
     *
     * @description Renders a loading spinner
     *
     * @restrict E
     *
     * @example
     <example module="tags">
     <file name="index.html">
     <loading></loading>
     </file>
     </example>
     */
    loading: function(tag) {
        return tag('loading');
    },

    bindOnce: function() {
        return {
            scope: true,
            link: function( $scope ) {
                setTimeout(function() {
                    $scope.$destroy();
                }, 0);
            }
        }
    }
});
