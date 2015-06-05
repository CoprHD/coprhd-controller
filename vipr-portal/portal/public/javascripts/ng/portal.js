/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * @ngdoc overview
 * @name portalApp
 * @description
 *
 * # portalApp
 * Our angular application. Also provides helper functions for integrating with non-angular JS.
 *
 * <div doc-module-components="portalApp"></div>
 */

window.portalApp = angular.module("portalApp", ['ngAnimate', 'ngCookies', 'vipr', 'tags', 'fields', 'auth', 'services'], function($httpProvider) {

    //play 1 doesn't decode JSON requests natively. Use form encoding instead.
    //from http://victorblog.com/2012/12/20/make-angularjs-http-service-behave-like-jquery-ajax/

    // Use x-www-form-urlencoded Content-Type
    $httpProvider.defaults.headers.post['Content-Type'] = 'application/x-www-form-urlencoded;charset=utf-8';

    /**
     * The workhorse; converts an object to x-www-form-urlencoded serialization.
     * @param {Object} obj
     * @return {String}
     */
    var param = function(obj) {
        var query = '', name, value, fullSubName, subName, subValue, innerObj, i;

        for(name in obj) {
            value = obj[name];

            if(value instanceof Array) {
                for(i=0; i<value.length; ++i) {
                    subValue = value[i];
                    fullSubName = name + '[' + i + ']';
                    innerObj = {};
                    innerObj[fullSubName] = subValue;
                    query += param(innerObj) + '&';
                }
            }
            else if(value instanceof Object) {
                for(subName in value) {
                    subValue = value[subName];
                    fullSubName = name + '[' + subName + ']';
                    innerObj = {};
                    innerObj[fullSubName] = subValue;
                    query += param(innerObj) + '&';
                }
            }
            else if(value !== undefined && value !== null)
                query += encodeURIComponent(name) + '=' + encodeURIComponent(value) + '&';
        }

        return query.length ? query.substr(0, query.length - 1) : query;
    };

    //add auth token to post requests
    $httpProvider.interceptors.push('authenticationTokenInterceptor');
    $httpProvider.interceptors.push('unauthorizedInterceptor');

    //Angular's default of application/json, application/text, */* causes play to
    //default to text format
    $httpProvider.defaults.headers.common['Accept'] = 'application/json, */*';

    // Override $http service's default transformRequest
    $httpProvider.defaults.transformRequest = [function(data) {
        return angular.isObject(data) && String(data) !== '[object File]' ? param(data) : data;
    }];
}).run(function($injector) {
    /**
     * @ngdoc function
     * @name portalApp.function:render
     *
     * @description
     *
     * Render a template using angular.
     *
     * @param {string} template an HTML template
     * @param {object=} locals Locals to add to the scope
     * @param {scope=} scope The scope to render in. A new isolated scope will be created if this is null
     * @returns {object} A jQuery style object with the rendered content. Call `.html()` to get a string.
     */
    portalApp.render = function(template, locals, scope) {
        var output = null;
        $injector.invoke(function($compile, $rootScope) {
            scope = scope || $rootScope.$new(true);
            output = $compile(template)(angular.extend(scope, locals));
        });
        return output;
    };

    /**
     * @ngdoc function
     * @name portalApp.function:insertLater
     *
     * @description
     *
     * Returns a placeholder string of html that is later (after a 0ms timeout) replaced with angular content. This
     * can be used to insert angular content into places that are expecting an HTML string (like render functions).
     *
     * The placeholder must be inserted into the DOM before the next tick to be picked up.
     *
     * @param {string} template an HTML template
     * @param {object=} locals Locals to add to the scope
     * @param {scope=} scope The scope to render in. A new isolated scope will be created if this is null
     * @returns {string} A string of placeholder HTML that will be replaced
     */
    portalApp.insertLater = function(template, locals, scope) {
        var id = null;
        $injector.invoke(function(uniqueId, $timeout) {
            id = uniqueId();
            $timeout(function() {
                angular.element("#" + id).replaceWith(portalApp.render(template, locals, scope));
            });
        });
        return "<span id='" + id + "'></span>"
    };

    /**
     * @ngdoc property
     * @name portalApp.property:$injector
     *
     * @description
     *
     * Our angular {@link https://docs.angularjs.org/api/auto/service/$injector injector}.
     */
    portalApp.$injector = $injector;
});

window.Messages = { get: angular.injector(['ng', 'vipr']).get('translate') };
