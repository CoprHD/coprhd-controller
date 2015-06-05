/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.security.authentication;

import javax.servlet.*;
import java.io.IOException;

/**
 * do nothing. used for data-services simulation purposes
 */
public class DummyInterNodeHMACAuthFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {

    }
}

