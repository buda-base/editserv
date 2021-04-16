package io.bdrc.edit;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.bdrc.auth.Access;
import io.bdrc.auth.TokenValidation;
import io.bdrc.auth.UserProfile;
import io.bdrc.auth.model.Endpoint;
import io.bdrc.auth.rdf.RdfAuthModel;

@Component
@Order(2)
public class RdfAuthFilter implements Filter {

    public final static Logger log = LoggerFactory.getLogger(RdfAuthFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            String method = ((HttpServletRequest) request).getMethod();
            if (EditConfig.useAuth() && !method.equalsIgnoreCase("OPTIONS")) {
                HttpServletRequest req = (HttpServletRequest) request;
                boolean isSecuredEndpoint = true;
                request.setAttribute("access", new Access());
                String token = getToken(req.getHeader("Authorization"));
                TokenValidation validation = null;
                String path = req.getServletPath();
                Endpoint end;
                try {
                    end = RdfAuthModel.getEndpoint(path);
                    log.debug("for path {} ENDPOINT IN FILTER id {} ", path, end);
                } catch (Exception e) {
                    e.printStackTrace();
                    end = null;
                }
                UserProfile prof = null;
                if (end == null) {
                    isSecuredEndpoint = false;
                    log.debug("Endpoint with path {} is not secured", path);
                    // endpoint is not secured - Using default (empty endpoint)
                    // for Access Object end=new Endpoint();
                } else {
                    isSecuredEndpoint = end.isSecured(method);
                }
                if (token != null) {
                    // User is logged on
                    // Getting his profile
                    validation = new TokenValidation(token);
                    if (validation == null || !validation.isValid()) {
                        ((HttpServletResponse) response).setStatus(401);
                        return;
                    }
                    prof = validation.getUser();
                    log.debug("validation is {}", validation);
                    log.error("userprof is {}", prof);
                    log.error("user is {}", prof.getUser());
                }
                if (isSecuredEndpoint) {
                    if (validation  == null) {
                        ((HttpServletResponse) response).setStatus(403);
                        return;
                    }
                    // Endpoint is secure
                    Access access = new Access(prof, end);
                    log.info("FILTER Access matchGroup >> {}", access.matchGroup());
                    log.info("FILTER Access matchRole >> {}", access.matchRole());
                    log.info("FILTER Access matchPerm >> {}", access.matchPermissions());
                    if (!access.hasEndpointAccess()) {
                        ((HttpServletResponse) response).setStatus(403);
                        return;
                    }
                    request.setAttribute("access", access);
                } else {
                    // end point not secured
                    // validation might be null in case 
                    if (validation != null) {
                        // token present since validation is not null
                        Access acc = new Access(prof, end);
                        request.setAttribute("access", acc);
                    }
                }
            }
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Auth filter did not go through properly ", e);
            throw e;
        }
    }

    public static String getToken(final String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7);
    }

}