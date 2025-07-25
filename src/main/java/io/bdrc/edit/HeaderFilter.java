package io.bdrc.edit;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

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
                "Origin, Accept, X-Requested-With, Content-Type, Accept-Encoding, Content-Encoding, " 
                        + "Access-Control-Request-Method, Access-Control-Request-Headers, "
                        + "Authorization, Keep-Alive, User-Agent, If-Modified-Since, If-Match, X-Change-Message, X-Status, X-Outline-Attribution, If-None-Match, Cache-Control");
        ((HttpServletResponse) response).addHeader("Access-Control-Expose-Headers",
                "ETag, Content-Encoding, Last-Modified, Content-Type, Cache-Control, Vary, Access-Control-Max-Age, X-Status, X-Outline-Attribution, Content-Disposition");
        chain.doFilter(req, res);
    }

}
