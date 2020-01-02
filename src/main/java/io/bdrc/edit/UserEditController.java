package io.bdrc.edit;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Resource;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.auth.Access;
import io.bdrc.auth.model.AuthDataModelBuilder;
import io.bdrc.auth.model.User;
import io.bdrc.auth.rdf.RdfAuthModel;
import io.bdrc.edit.modules.GitUserPatchModule;
import io.bdrc.edit.modules.GitUserRevisionModule;
import io.bdrc.edit.modules.UserPatchModule;
import io.bdrc.edit.patch.UserPatches;
import io.bdrc.edit.txn.UserTransaction;
import io.bdrc.edit.txn.exceptions.DataUpdateException;
import io.bdrc.edit.users.BudaUser;
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
    public ResponseEntity<StreamingResponseBody> meUser(HttpServletResponse response, HttpServletRequest request) throws IOException, GitAPIException {
        log.info("Call meUser()");
        String token = getToken(request.getHeader("Authorization"));
        if (token == null) {
            return ResponseEntity.status(401).body(StreamingHelpers.getStream("No token available"));
        } else {
            Access acc = (Access) request.getAttribute("access");
            log.info("meUser() Access >> {}", acc);
            String auth0Id = acc.getUser().getAuthId();
            log.info("meUser() auth0Id >> {}", auth0Id);
            auth0Id = auth0Id.substring(auth0Id.indexOf("|") + 1);
            Resource usr = BudaUser.getRdfProfile(auth0Id);
            log.info("meUser() usr >> {}", usr);
            if (usr == null) {
                BudaUser.addNewBudaUser(acc.getUser());
                usr = BudaUser.getRdfProfile(auth0Id);
                log.info("meUser() User Resource >> {}", usr);
            }
            return ResponseEntity.status(200).header("Location", "/resource-nc/user/" + usr.getLocalName()).body(StreamingHelpers.getModelStream(BudaUser.getUserModel(true, usr), "json"));
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
                    // we might run user edit transactions here...
                    // using a separate endpoint for now (12/19)
                }
                return ResponseEntity.status(200).body(StreamingHelpers.getModelStream(BudaUser.getUserModel(true, BudaUser.getRdfProfile(n)), "jsonld"));

            }
            return ResponseEntity.status(200).body(StreamingHelpers.getModelStream(BudaUser.getUserModel(false, BudaUser.getRdfProfile(n)), "jsonld"));
        }
    }

    @PatchMapping(value = "/resource-nc/user/patch/{res}")
    public ResponseEntity<StreamingResponseBody> userPublicPatch(@PathVariable("res") final String res, HttpServletResponse response, HttpServletRequest request, @RequestBody String patch) throws Exception {
        log.info("Call userPublicPatch() for res:" + res);
        log.info("Call userPublicPatch() for Patch:" + patch);
        try {
            String token = getToken(request.getHeader("Authorization"));
            if (token == null) {
                return ResponseEntity.status(403).body(StreamingHelpers.getStream("You must be authenticated in order to modify this user"));
            } else {
                Access acc = (Access) request.getAttribute("access");
                log.info("userPatch() Token User {}", acc.getUser());
                if (acc.getUser().isAdmin() || BudaUser.isSameUser(acc.getUser(), res)) {
                    UserTransaction ut = new UserTransaction(patch, acc.getUser().getName(), res);
                    ut.addModule(new UserPatchModule(ut.getData(), ut.getLog()), 0);
                    ut.addModule(new GitUserPatchModule(ut.getData(), ut.getLog()), 1);
                    ut.addModule(new GitUserRevisionModule(ut.getData(), ut.getLog()), 2);
                    ut.setStatus(Types.STATUS_PREPARED);
                    ut.commit();
                    return ResponseEntity.status(200).body(StreamingHelpers.getStream("OK"));
                } else {
                    return ResponseEntity.status(403).body(StreamingHelpers.getStream("You must be an admin to modify this user"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(StreamingHelpers.getStream("Error while updating " + res + " " + e.getMessage()));
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

    @PostMapping(value = "/usersCleanup")
    public ResponseEntity<String> cleanUsers() throws DataUpdateException {
        log.info("Call to cleanUsers()");
        BudaUser.cleanAllUsers(true);
        return ResponseEntity.ok().body("OK");
    }

}
