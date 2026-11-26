package com.guvi.recommendationsystem.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** Browser CSRF defense in addition to Strict cookies and JSON-only mutation bodies. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+2)
public class SameOriginMutationFilter extends OncePerRequestFilter {
    private final String publicOrigin;
    public SameOriginMutationFilter(){publicOrigin=null;}
    @org.springframework.beans.factory.annotation.Autowired
    public SameOriginMutationFilter(@org.springframework.beans.factory.annotation.Value("${app.public-url:http://localhost:8080}") String origin){publicOrigin=origin.replaceAll("/+$","");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
            FilterChain chain) throws ServletException,IOException {
        if(request.getRequestURI().startsWith("/api/") && !Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) {
            String origin=request.getHeader("Origin");
            boolean allowed=!"cross-site".equals(request.getHeader("Sec-Fetch-Site"));
            if(origin!=null) {
                try {
                    URI supplied=URI.create(origin),expected=URI.create(publicOrigin==null?request.getRequestURL().toString():publicOrigin);
                    allowed &= supplied.getUserInfo()==null && supplied.getQuery()==null && supplied.getFragment()==null
                            && (supplied.getRawPath()==null || supplied.getRawPath().isEmpty())
                            && expected.getScheme().equalsIgnoreCase(supplied.getScheme())
                            && expected.getHost().equalsIgnoreCase(supplied.getHost()) && port(expected)==port(supplied);
                } catch(RuntimeException ex){allowed=false;}
            }
            if(!allowed){response.setStatus(403);response.setContentType("application/json");
                response.getWriter().write("{\"status\":403,\"message\":\"Cross-origin changes are not allowed.\"}");return;}
        }
        chain.doFilter(request,response);
    }
    private static int port(URI uri){return uri.getPort()!=-1?uri.getPort():"https".equalsIgnoreCase(uri.getScheme())?443:80;}
}
