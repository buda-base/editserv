package io.bdrc.edit.controllers;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.auth.Access;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsRead;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.user.BudaUser;
import io.bdrc.libraries.BudaMediaTypes;
import io.bdrc.libraries.StreamingHelpers;

@Controller
@RequestMapping("/")
public class UserEditController {

    public final static Logger log = LoggerFactory.getLogger(UserEditController.class.getName());

    @GetMapping(value = "/me/focusgraph")
    public static ResponseEntity<StreamingResponseBody> meUser(@RequestHeader("Accept") String format, HttpServletResponse response, HttpServletRequest request)
            throws IOException, GitAPIException, ModuleException {
        try {
            log.info("Call meUser()");
            Access acc = (Access) request.getAttribute("access");
            if (!acc.isUserLoggedIn())
                return ResponseEntity.status(401).body(null);
            log.info("meUser() Access >> {} ", acc);
            String authId = acc.getUser().getAuthId();
            if (authId == null || authId.isEmpty()) {
                log.error("couldn't find authId");
                return ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON)
                        .body(null);
            }
            String auth0Id = authId.substring(authId.lastIndexOf("|") + 1);
            log.info("meUser() auth0Id >> {}", auth0Id);
            Resource usr = BudaUser.getRdfProfile(auth0Id);
            // usr is bdu:UXXX
            log.info("meUser() Buda usr >> {}", usr);
            GitInfo gi = null;
            if (usr == null) {
                usr = BudaUser.createUserId(acc.getUser());
                gi = BudaUser.createAndSaveUser(acc.getUser(), usr);
                log.info("meUser() User new created Resource >> {}", usr);
            } else {
                gi = CommonsGit.gitInfoForResource(usr, true);
                if (gi.ds == null) {
                    log.error("couldn't find user model for authId "+authId);
                    return ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON)
                            .body(null);
                }
            }
            Model userModel = ModelUtils.getMainModel(gi.ds);
            final Resource shape = CommonsRead.getShapeForEntity(usr);
            userModel = CommonsRead.getFocusGraph(userModel, usr, shape);
            userModel.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
            MediaType mediaType = BudaMediaTypes.selectVariant(format, BudaMediaTypes.resVariantsNoHtml);
            if (mediaType == null) { mediaType = MediaType.APPLICATION_JSON ; }
            String ext = BudaMediaTypes.getExtFromMime(mediaType);
            return ResponseEntity.status(200).contentType(mediaType)
                    .header("Location", "/resource-nc/user/" + usr.getLocalName())
                    .header("Etag", gi.revId)
                    .body(StreamingHelpers.getModelStream(userModel, ext,
                            usr.getURI(), null, EditConfig.prefix.getPrefixMap()));
        } catch (IOException | GitAPIException e) {
            log.error("/me/focusgraph failed ", e);
            throw e;
        }
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
