/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
var NAV = (function() {
    var PINNED_COOKIE = 'isMenuPinned';
    // --- JQuery selectors ---
    var MAIN_MENU = '#mainMenu';
    var NAV = '.rootNav';
    var MAIN_MENU_NAV = MAIN_MENU + ' ' + NAV;
    var MENU = '.navMenu';
    var MENU_NAME = 'subnav';
    var MENU_PIN = '.menuPin';
    var CONTENT_AREA = '#contentArea';

    // --- CSS Classes ---
    var MAIN_MENU_OPEN = 'selected';
    var MENU_OPEN = 'menu-open';
    var MENU_PINNED = 'menu-pinned';
    var ACTIVE_INDICATOR = 'blueArrow';

    var CONTENT_AREA_CHANGED_EVENT = 'contentAreaChanged';
    
    /** The container for all menu items. */
    var $container;

    /**
     * Determines if the menu is in a pinned state.
     */
    function isMenuPinned() {
        if (readCookie(PINNED_COOKIE) == 'true') {
            return true;
        }
        return false;
    }

    /**
     * Toggles the menu pinned state.
     */
    function toggleMenuPinned() {
        if (readCookie(PINNED_COOKIE) == 'true') {
            createCookie(PINNED_COOKIE, 'false', 'session');
            $(CONTENT_AREA).removeClass(MENU_PINNED);
            $(MENU_PIN).removeClass(MENU_PINNED);
        }
        else {
            createCookie(PINNED_COOKIE, 'true', 'session');
            $(CONTENT_AREA).addClass(MENU_PINNED);
            $(MENU_PIN).addClass(MENU_PINNED);
        }
    }

    function isMenuOpened() {
        var elements = $('div.'+MENU_OPEN);
        if (elements.length > 0) {
            return true;
        }
        return false;
    }

    function updateActiveIndicator() {
        if (isMenuOpened()) {
            $('a.rootNav.active').removeClass(ACTIVE_INDICATOR);
        }
        else {
            $('a.rootNav.active').addClass(ACTIVE_INDICATOR);
        }
    }

    /**
     * Closes all open menus.
     */
    function closeMenus() {
        $(MAIN_MENU_NAV, $container).removeClass(MAIN_MENU_OPEN);
        $(MENU, $container).removeClass(MENU_OPEN);
        $(CONTENT_AREA).removeClass(MENU_OPEN);
        updateActiveIndicator();
    }

    /**
     * Opens the provided menu.
     * 
     * @param $menu
     *        the jquery object containing the menu.
     */
    function openMenu($menu) {
        closeMenus();
        var name = $menu.data(MENU_NAME);
        if (name) {
            var $subMenu = $('#' + name);
            if ($subMenu) {
                $menu.addClass(MAIN_MENU_OPEN);
                $subMenu.addClass(MENU_OPEN);
                $(CONTENT_AREA).addClass(MENU_OPEN);
            }
        }
        updateActiveIndicator();
    }

    /**
     * Performs a change to the content area and triggers an event if the content area actually changes.
     */
    function applyContentAreaChange(changeHandler) {
        var $contentArea = $(CONTENT_AREA);
        var oldPinnedOpen = $contentArea.hasClass(MENU_PINNED) && $contentArea.hasClass(MENU_OPEN);
        
        changeHandler();
        
        var newPinnedOpen = $contentArea.hasClass(MENU_PINNED) && $contentArea.hasClass(MENU_OPEN);
        if (oldPinnedOpen != newPinnedOpen) {
            $contentArea.trigger(CONTENT_AREA_CHANGED_EVENT);
        }
    }
    
    return {
        createMenu : function(selector) {
            $container = $(selector);
            if (isMenuPinned()) {
                $(CONTENT_AREA).addClass(MENU_PINNED);
            }
            if (isMenuOpened()) {
                $(CONTENT_AREA).addClass(MENU_OPEN);
            }
            $(MAIN_MENU_NAV, $container).tooltip();
            $(MAIN_MENU, $container).on('click', NAV, function(event) {
                var $menu = $(this);
                applyContentAreaChange(function() {
                    if ($menu.hasClass(MAIN_MENU_OPEN)) {
                        if (!isMenuPinned()) {
                            closeMenus();
                            $menu.tooltip('show');
                        }
                    }
                    else {
                        openMenu($menu);
                        $menu.tooltip('hide');
                    }
                    event.stopImmediatePropagation();
                });
            });
            $(MENU_PIN, $container).on('click', function(event) {
                applyContentAreaChange(toggleMenuPinned);
            });
            $(CONTENT_AREA, $container).on('click', function(event) {
                if (!isMenuPinned()) {
                    applyContentAreaChange(closeMenus);
                }
            });
            updateActiveIndicator();
        },
        onContentAreaChanged: function(handler) {
            $(CONTENT_AREA).on(CONTENT_AREA_CHANGED_EVENT, handler);
        }
    }
})();
