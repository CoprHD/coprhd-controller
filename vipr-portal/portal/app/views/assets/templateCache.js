*{
    Prefill the template cache. This ensures all parts of a page will appear at the same time,
    and should prevent bugs due to differing cached/uncached behavior.
}*
%{
    templates = util.ViewHelpers.globDirectory("public/templates/")
}%

angular.module("portalApp").run(function($templateCache) {
    #{list templates.keySet(), as:'name'}
        $templateCache.put('${name}', ${util.ViewHelpers.toJson(templates.get(name))});
    #{/list}
});