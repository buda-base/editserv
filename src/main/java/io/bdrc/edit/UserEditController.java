package io.bdrc.edit;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.auth.Access;
import io.bdrc.auth.model.AuthDataModelBuilder;
import io.bdrc.auth.model.User;
import io.bdrc.auth.rdf.RdfAuthModel;
import io.bdrc.edit.patch.UserPatches;
import io.bdrc.edit.users.BudaUser;
import io.bdrc.edit.users.UserDataService;
import io.bdrc.libraries.BudaMediaTypes;
import io.bdrc.libraries.StreamingHelpers;

@Controller
@RequestMapping("/")
public class UserEditController {

    public final static Logger log = LoggerFactory.getLogger(UserEditController.class.getName());

    public UserEditController() {
        // TODO Auto-generated constructor stub
    }

    @GetMapping(value = "/resource-nc/user/me")
    public ResponseEntity<StreamingResponseBody> meUser(HttpServletResponse response, HttpServletRequest request) throws IOException {
        log.info("Call meUser()");
        String token = getToken(request.getHeader("Authorization"));
        if (token == null) {
            return ResponseEntity.status(401).body(StreamingHelpers.getStream("No token available"));
        } else {
            Access acc = (Access) request.getAttribute("access");
            log.info("meUser() Access >> {}", acc);
            // TODO there should be a function in bdrc-auth-lib that does this
            String auth0Id = acc.getUser().getAuthId();
            log.info("meUser() auth0Id >> {}", auth0Id);
            auth0Id = auth0Id.substring(auth0Id.indexOf("|") + 1);
            Resource usr = BudaUser.getRdfProfile(auth0Id);
            log.info("meUser() usr >> {}", usr);
            if (usr == null) {
                UserDataService.addNewBudaUser(acc.getUser());
                usr = BudaUser.getRdfProfile(auth0Id);
                log.info("meUser() User Resource >> {}", usr);
            }
            return ResponseEntity.status(200).header("Location", "/resource-nc/user/" + usr.getLocalName()).body(StreamingHelpers.getModelStream(BudaUser.getUserModel(true, usr), "jsonld"));
        }
    }

    @PatchMapping(value = "/resource-nc/user/{res}")
    public ResponseEntity<StreamingResponseBody> userPatch(@PathVariable("res") final String res, HttpServletResponse response, HttpServletRequest request, @RequestParam(value = "type", defaultValue = "NONE") final String type,
            @RequestBody String patch) throws Exception {
        log.info("Call userPatch()");
        String token = getToken(request.getHeader("Authorization"));
        if (token == null) {
            return ResponseEntity.status(403).body(StreamingHelpers.getStream("You must be authenticated in order to disable this user"));
        } else {
            String auth0Id = BudaUser.getAuth0IdFromUserId(res).asNode().getURI();
            User usr = RdfAuthModel.getUser(auth0Id.substring(auth0Id.lastIndexOf("/") + 1));
            String n = auth0Id.substring(auth0Id.lastIndexOf("/") + 1);
            Access acc = (Access) request.getAttribute("access");
            log.info("userPatch() Token User {}", acc.getUser());
            if (acc.getUser().isAdmin()) {
                // case user unlock
                if (type.equals("unblock")) {
                    // first update the Buda User rdf profile
                    BudaUser.update(res, UserPatches.getSetActivePatch(res, true));
                    // next, mark (patch) the corresponding Auth0 user as "unblocked'
                    AuthDataModelBuilder.patchUser(usr.getAuthId(), "{\"blocked\":false}");
                    // next, update RdfAuthModel (auth0 users)
                    Thread t = new Thread(new RdfAuthModel());
                    t.start();
                } else {
                    // specialized or generic patching here
                }
                return ResponseEntity.status(200).body(StreamingHelpers.getModelStream(BudaUser.getUserModel(true, BudaUser.getRdfProfile(n)), "jsonld"));

            }
            return ResponseEntity.status(200).body(StreamingHelpers.getModelStream(BudaUser.getUserModel(false, BudaUser.getRdfProfile(n)), "jsonld"));
        }
    }

    @DeleteMapping(value = "/resource-nc/user/{res}")
    public ResponseEntity<StreamingResponseBody> userDelete(@PathVariable("res") final String res, HttpServletResponse response, HttpServletRequest request) throws Exception {
        log.info("Call userDelete()");
        String token = getToken(request.getHeader("Authorization"));
        if (token == null) {
            return ResponseEntity.status(403).body(StreamingHelpers.getStream("You must be authenticated in order to disable this user"));
        } else {
            String auth0Id = null;
            Access acc = (Access) request.getAttribute("access");
            log.info("userDelete() Token User {}", acc.getUser());
            if (acc.getUser().isAdmin()) {
                auth0Id = BudaUser.getAuth0IdFromUserId(res).asNode().getURI();
                User usr = RdfAuthModel.getUser(auth0Id.substring(auth0Id.lastIndexOf("/") + 1));
                // first update the Buda User rdf profile
                BudaUser.update(res, UserPatches.getSetActivePatch(res, false));
                // next, mark (patch) the corresponding Auth0 user as "blocked'
                AuthDataModelBuilder.patchUser(usr.getAuthId(), "{\"blocked\":true}");
                // next, update RdfAuthModel (auth0 users)
                Thread t = new Thread(new RdfAuthModel());
                t.start();
            }
            String n = auth0Id.substring(auth0Id.lastIndexOf("/") + 1);
            if (acc.getUser().isAdmin()) {
                return ResponseEntity.status(200).body(StreamingHelpers.getModelStream(BudaUser.getUserModel(true, BudaUser.getRdfProfile(n)), "jsonld"));
            }
            return ResponseEntity.status(200).body(StreamingHelpers.getModelStream(BudaUser.getUserModel(false, BudaUser.getRdfProfile(n)), "jsonld"));
        }
    }

    @GetMapping(value = "/userEditPolicies")
    public ResponseEntity<StreamingResponseBody> getUserEditPolicies() {
        log.info("Call to getUserEditPolicies()");
        return ResponseEntity.ok().contentType(BudaMediaTypes.getMimeFromExtension("json")).body(StreamingHelpers.getJsonObjectStream(BudaUser.getUserPropsEditPolicies()));
    }

    private String getToken(final String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7);
    }

}
