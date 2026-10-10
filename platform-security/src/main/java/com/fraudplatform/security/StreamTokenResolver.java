package com.fraudplatform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Bearer token from the {@code Authorization} header everywhere, and from an {@code access_token}
 * query parameter <b>only</b> on the listed paths: browsers' {@code EventSource} (SSE) cannot set
 * headers. Query-string tokens can leak into logs, so the exception is kept as narrow as possible
 * and the gateway doesn't log query strings for those paths.
 */
public class StreamTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver headerOnly = new DefaultBearerTokenResolver();
    private final DefaultBearerTokenResolver headerOrQuery = new DefaultBearerTokenResolver();
    private final Set<String> queryParamPaths;

    public StreamTokenResolver(Set<String> queryParamPaths) {
        this.queryParamPaths = Set.copyOf(queryParamPaths);
        headerOrQuery.setAllowUriQueryParameter(true);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        return queryParamPaths.contains(request.getRequestURI()) ? headerOrQuery.resolve(request) : headerOnly.resolve(request);
    }
}
