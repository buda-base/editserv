package io.bdrc.edit;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.bdrc.auth.AccessInfo;
import io.bdrc.auth.AccessInfoAuthImpl;
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
            if (EditConfig.useAuth && !method.equalsIgnoreCase("OPTIONS")) {
                HttpServletRequest req = (HttpServletRequest) request;
                boolean isSecuredEndpoint = true;
                request.setAttribute("access", new AccessInfoAuthImpl());
                String token = getToken(req.getHeader("Authorization"));
                TokenValidation validation = null;
                String path = req.getServletPath();
                Endpoint end;
                try {
                    end = RdfAuthModel.getEndpoint(path);
                    log.debug("for path {} ENDPOINT IN FILTER id {} ", path, end);
                } catch (Exception e) {
                    log.error("can't get endpoint for "+path, e);
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
                    log.info("validation is {}", validation);
                    log.info("userprof is {}", prof);
                    log.info("user is {}", prof.getUser());
                }
                if (isSecuredEndpoint) {
                    if (validation  == null) {
                        ((HttpServletResponse) response).setStatus(403);
                        return;
                    }
                    // Endpoint is secure
                    AccessInfoAuthImpl access = new AccessInfoAuthImpl(prof, end);
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
                        AccessInfo acc = new AccessInfoAuthImpl(prof, end);
                        request.setAttribute("access", acc);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Auth filter did not go through properly ", e);
            throw e;
        }
        chain.doFilter(request, response);
    }

    public static String getToken(final String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7);
    }

}