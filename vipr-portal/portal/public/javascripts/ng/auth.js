/**
 * @ngdoc overview
 * @name auth
 * @description
 *
 * # auth
 * The vipr module contains values, services and directives related to authorization and authentication.
 *
 * <div doc-module-components="auth"></div>
 */
angular.module("auth", []).service({
    /**
     * @ngdoc service
     * @name auth.service:currentUser
     *
     * @description
     *
     * The current authenticated user, or `null`.
     */
    currentUser: function($rootScope) {
        return $rootScope.currentUser;
    },
    /**
     * @ngdoc service
     * @name auth.service:hasAnyRole
     *
     * @description
     *
     * Test to see if we have an authenticated user and they have one of the provide roles.
     *
     * @param {array} roles An array of roles to match against.
     *
     * @returns {boolean} Whether or not one of the roles matches.
     */
    hasAnyRole: function(currentUser) {
        return function(roles) {
            return currentUser && currentUser.roles.filter(function(role) {
                return roles.indexOf(role.roleName) > -1;
            }).length > 0;
        }
    },
    //add auth (CSRF) token to post requests. Configured in portal.js.
    authenticationTokenInterceptor: function($rootScope) {
        return {
            request: function(config) {
                if (config.method == "POST" && config.data && !config.data.authenticityToken && $rootScope.authenticityToken) {
                    config.data.authenticityToken = $rootScope.authenticityToken;
                }
                return config;
            }
        }
    },
    //reload the page on a 401 (which should bump us to the login). Configured in portal.js
    unauthorizedInterceptor: function($window, $q) {
        return {
            responseError: function(response) {
                if (response.status == 401) {
                    $window.location.reload(true);
                }
                else {
                    return $q.reject(response);
                }
            }
        }
    }
}).directive({
    /**
     * @ngdoc directive
     * @name auth.directive:vRestrict
     *
     * @restrict AE
     *
     * @description
     * Restrict the enclosed block to authenticated users with at least one of the provided roles.
     *
     * @param {string} vRestrict|roles A space or comma separated list of roles.
     */
    vRestrict: function(hasAnyRole) {
        return {
            restrict: "AE",
            priority: 1000,
            link: function(scope, element, attrs) {
                var roles = (attrs.roles || attrs.vRestrict).split(/ |,/);

                if (!hasAnyRole(roles)) {
                    element.remove();
                }
            }
        }
    },
    /**
     * @ngdoc directive
     * @name auth.directive:restrict
     *
     * @restrict E
     *
     * @description
     * Element only alias for {@link auth.directive:vRestrict vRestrict}.
     *
     * @param {string} roles A space or comma separated list of roles.
     */
    restrict: function(vRestrictDirective) {
        return angular.extend({}, vRestrictDirective, { restrict: "E" });
    }
});
