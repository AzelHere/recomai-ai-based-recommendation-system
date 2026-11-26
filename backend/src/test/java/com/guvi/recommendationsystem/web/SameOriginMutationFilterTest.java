package com.guvi.recommendationsystem.web;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class SameOriginMutationFilterTest {
    @Test void rejectsForeignMalformedAndNullOriginsButPermitsSameOriginAndNonBrowserClients() throws Exception {
        for(String origin:new String[]{"https://foreign.test","null","http://localhost:81","http://localhost.attacker.test","http://localhost@foreign.test","http://localhost/path"})
            assertThat(status("POST",origin,null)).as(origin).isEqualTo(403);
        assertThat(status("POST","http://localhost",null)).isEqualTo(204);
        assertThat(status("POST","http://localhost:80","same-origin")).isEqualTo(204);
        assertThat(status("POST",null,null)).isEqualTo(204);
        assertThat(status("POST",null,"cross-site")).isEqualTo(403);
        assertThat(status("GET","https://foreign.test","cross-site")).isEqualTo(204);
    }
    private int status(String method,String origin,String site) throws Exception {
        var request=new MockHttpServletRequest(method,"/api/auth/login");
        if(origin!=null)request.addHeader("Origin",origin);if(site!=null)request.addHeader("Sec-Fetch-Site",site);
        var response=new MockHttpServletResponse();
        new SameOriginMutationFilter().doFilter(request,response,(req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(204));
        return response.getStatus();
    }
}
