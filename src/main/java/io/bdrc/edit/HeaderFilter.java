package io.bdrc.edit;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class HeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        ((HttpServletResponse) response).addHeader("Access-Control-Allow-Origin", "*");
        ((HttpServletResponse) response).addHeader("Access-Control-Allow-Credentials", "true");
        ((HttpServletResponse) response).addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS, POST, PUT");
        ((HttpServletResponse) response).addHeader("Access-Control-Allow-Headers",
                "Origin, Accept, X-Requested-With, Content-Type, " 
                        + "Access-Control-Request-Method, Access-Control-Request-Headers, "
                        + "Authorization, Keep-Alive, User-Agent, If-Modified-Since, If-Match, X-Change-Message, If-None-Match, Cache-Control");
        ((HttpServletResponse) response).addHeader("Access-Control-Expose-Headers",
                "ETag, Last-Modified, Content-Type, Cache-Control, Vary, Access-Control-Max-Age");
        chain.doFilter(req, res);
    }

}
