package io.bdrc.edit.controllers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.auth.Access;
import io.bdrc.auth.model.User;
import io.bdrc.auth.rdf.RdfAuthModel;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.user.BudaUser;
import io.bdrc.libraries.BudaMediaTypes;
import io.bdrc.libraries.Models;
import io.bdrc.libraries.StreamingHelpers;

@Controller
@RequestMapping("/")
public class UserEditController {

    public final static Logger log = LoggerFactory.getLogger(UserEditController.class.getName());

    public UserEditController() {
        // TODO Auto-generated constructor stub
    }

    @GetMapping(value = "/resource-nc/user/me")
    public ResponseEntity<StreamingResponseBody> meUser(@RequestHeader("Accept") String format, HttpServletResponse response, HttpServletRequest request)
            throws IOException, GitAPIException, NoSuchAlgorithmException {
        try {
            log.info("Call meUser()");
            Access acc = (Access) request.getAttribute("access");
            if (!acc.isUserLoggedIn())
                return ResponseEntity.status(401).body(null);
            log.info("meUser() Access >> {} ", acc);
            String authId = acc.getUser().getAuthId();
            if (authId == null || authId.isEmpty()) {
                log.error("couldn't find authId");
                return ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON_UTF8)
                        .body(null);
            }
            String auth0Id = authId.substring(authId.lastIndexOf("|") + 1);
            log.info("meUser() auth0Id >> {}", auth0Id);
            Resource usr = BudaUser.getRdfProfile(auth0Id);
            // usr is bdu:UXXX
            log.info("meUser() Buda usr >> {}", usr);
            Model userModel = null;
            if (usr == null) {
                usr = BudaUser.createUserId(acc.getUser());
                userModel = BudaUser.createAndSaveUser(acc.getUser(), usr);
                log.info("meUser() User new created Resource >> {}", usr);
            } else {
                userModel = BudaUser.getUserModelFromFuseki(usr);
            }
            if (userModel == null) {
                log.error("couldn't find user model for authId "+authId);
                return ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON_UTF8)
                        .body(null);
            }
            MediaType mediaType = BudaMediaTypes.selectVariant(format, BudaMediaTypes.resVariantsNoHtml);
            if (mediaType == null) { mediaType = MediaType.APPLICATION_JSON ; }
            String ext = BudaMediaTypes.getExtFromMime(mediaType);
            return ResponseEntity.status(200).contentType(mediaType)
                    .header("Location", "/resource-nc/user/" + usr.getLocalName())
                    .body(StreamingHelpers.getModelStream(userModel, ext,
                            usr.getURI(), null, EditConfig.prefix.getPrefixMap()));
        } catch (IOException | GitAPIException e) {
            log.error("/resource-nc/user/me failed ", e);
            throw e;
        }
    }

    public static ResponseEntity<String> userPut(final String qname, HttpServletResponse response, HttpServletRequest request, String model) throws Exception {
        log.info("Call userPut()");
        if (!qname.startsWith("bdu:"))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("you can only modify entities in the bdr namespace in this endpoit");
        final Resource user = ResourceFactory.createResource(EditConstants.BDU+qname.substring(4));
        if (EditConfig.useAuth) {
            //final String auth0IdOfRes = BudaUser.getAuth0IdFromUser(user).asNode().getURI();
            //final String auth0IdOfResLN = auth0IdOfRes.substring(auth0IdOfRes.lastIndexOf("/") + 1);
            //User usrOfRes = RdfAuthModel.getUser(auth0IdOfResLN);
            final Access acc = (Access) request.getAttribute("access");
            if (!acc.isUserLoggedIn())
                return ResponseEntity.status(401).body("you need to be logged in to use this service");
            String authId = acc.getUser().getAuthId();
            if (authId == null) {
                log.error("couldn't find authId for {}"+acc.toString());
                return ResponseEntity.status(500).body("couldn't find authId");
            }
            final String auth0Id = authId.substring(authId.lastIndexOf("|") + 1);
            log.info("meUser() auth0Id >> {}", auth0Id);
            final Resource usr = BudaUser.getRdfProfile(auth0Id);
            log.info("userPatch() Token User {}", acc.getUser());
            if (EditConfig.useAuth && !acc.getUserProfile().isAdmin() && !usr.getLocalName().equals(qname)) {
                return ResponseEntity.status(403).body("only admins can modify other users");
            }
        }
        final MediaType med = MediaType.parseMediaType(request.getHeader("Content-Type"));
        if (med == null) {
            log.error("Invalid or missing Content-Type header {}", request.getHeader("Content-Type"));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot parse Content-Type header " + request.getHeader("Content-Type"));
        }
        final Lang jenaLang = BudaMediaTypes.getJenaLangFromExtension(BudaMediaTypes.getExtFromMime(med));
        log.info("MediaType {} and extension {} and jenaLang {}", med, med.getSubtype(), jenaLang);
        final Model inModel = ModelFactory.createDefaultModel();
        final InputStream in = new ByteArrayInputStream(model.getBytes());
        inModel.read(in, null, jenaLang.getLabel());
        final String revId = MainEditController.saveResource(inModel, user);
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.ok().body(revId);
    }

    @GetMapping(value = "/userEditPolicies", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<StreamingResponseBody> getUserEditPolicies() {
        log.info("Call to getUserEditPolicies()");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8)
                .body(StreamingHelpers.getJsonObjectStream(BudaUser.getUserPropsEditPolicies()));
    }

    @PostMapping(value = "/callbacks/updateFuseki/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> updateFuseki() {
        log.info("updating Fuseki users data >>");
        BudaUser.rebuiltFuseki();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("Fuseki users data has been rebuilt");
    }

}
