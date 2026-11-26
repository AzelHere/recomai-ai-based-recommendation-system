package com.guvi.recommendationsystem.catalog;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;

/** Enforce the JSON byte limit before deserialization, including chunked requests. */
@Component
public class IngestionBodyLimit extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getMethod().equals("POST")||!request.getRequestURI().matches("/api/admin/ingestion/(preview|commit)");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        final int limit=3_000_000;
        byte[] bytes=request.getContentLengthLong()>limit?null:request.getInputStream().readNBytes(limit+1);
        if(bytes==null||bytes.length>limit){response.setStatus(413);response.setContentType("application/json");response.getWriter().write("{\"message\":\"Source batch exceeds 3 MB.\"}");return;}
        chain.doFilter(new HttpServletRequestWrapper(request){
            @Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous metadata import only");}};}
        },response);
    }
}
