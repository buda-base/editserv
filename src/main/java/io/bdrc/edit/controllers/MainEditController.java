package io.bdrc.edit.controllers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.RDF;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.auth.AccessInfo;
import io.bdrc.auth.rdf.RdfAuthModel;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.commons.ops.CommonsRead;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.helpers.Shapes;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.edit.user.BudaUser;
import io.bdrc.libraries.BudaMediaTypes;
import io.bdrc.libraries.StreamingHelpers;

@Controller
@RequestMapping("/")
public class MainEditController {

    public final static Logger log = LoggerFactory.getLogger(MainEditController.class.getName());

    @GetMapping(value = "/{qname}/focusgraph", produces = "text/turtle")
    public static ResponseEntity<StreamingResponseBody> getFocusGraph(@PathVariable("qname") String qname,
            HttpServletRequest req, HttpServletResponse response) {
        return getGraph(qname, req, response, true, null);
    }
    
    @GetMapping(value = "/{qname}/revision/{revId}/focusgraph", produces = "text/turtle")
    public static ResponseEntity<StreamingResponseBody> getFocusGraph(@PathVariable("qname") String qname,
            @PathVariable("revId") String revId, HttpServletRequest req, HttpServletResponse response) {
        return getGraph(qname, req, response, true, revId);
    }
    
    @GetMapping(value = "/{qname}", produces = "text/turtle")
    public static ResponseEntity<StreamingResponseBody> getFullGraph(@PathVariable("qname") String qname,
            HttpServletRequest req, HttpServletResponse response) {
        return getGraph(qname, req, response, false, null);
    }
    
    @GetMapping(value = "/{qname}/revision/{revId}", produces = "text/turtle")
    public static ResponseEntity<StreamingResponseBody> getFullGraph(@PathVariable("qname") String qname,
            @PathVariable("revId") String revId, HttpServletRequest req, HttpServletResponse response) {
        return getGraph(qname, req, response, true, revId);
    }
    
    // TODO: implement the HEAD endpoints
    
    public static ResponseEntity<StreamingResponseBody> getGraph(final String qname,
            final HttpServletRequest req, final HttpServletResponse response, final boolean focus, final String revision) {
        Model m = null;
        final boolean userMode = qname.startsWith("bdu:");
        final Resource res;
        if (userMode) {
            res = ResourceFactory.createResource(EditConstants.BDU+qname.substring(4));
        } else {
            if (!qname.startsWith("bdr:") && (focus || !qname.startsWith("bdg:")))
                return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("No graph could be found for " + qname));
            // oddly enough, just considering bdg: like bdr: works in pretty much all cases
            res = ResourceFactory.createResource(EditConstants.BDR+qname.substring(4));
        }
        boolean isAdmin = false;
        if (EditConfig.useAuth) {
            final AccessInfo acc = (AccessInfo) req.getAttribute("access");
            try {
                ensureAccess(acc, res);
                isAdmin = acc.isAdmin();
            } catch (EditException e) {
                return ResponseEntity.status(e.getHttpStatus())
                        .body(StreamingHelpers.getStream(e.getMessage()));
            }
        }
        try {
            CommonsGit.GitInfo gi = CommonsGit.gitInfoForResource(res, false);
            if (gi.ds == null || gi.ds.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("No graph could be found for " + qname));
            if (!isAdmin && Helpers.isHidden(gi.ds))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("Graph inaccessible: " + qname));
            Resource shape = CommonsRead.getShapeForEntity(res);
            if (gi.revId == null || gi.revId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.valueOf(500)).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("Could not find revision id of " + qname));
            }
            response.addHeader("Etag", '"'+gi.revId+'"');
            m = ModelUtils.getMainModel(gi.ds);
            if (userMode) {
                // dirty patch
                m.removeAll(null, RDF.type, m.createResource(EditConstants.BDO+"Person"));
                m.removeAll(null, RDF.type, m.createResource(EditConstants.FOAF+"Person"));
            }
            if (focus)
                m = CommonsRead.getFocusGraph(m, res, shape);
            m.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                    .body(StreamingHelpers.getStream("No graph could be found for " + qname));
        }
        response.addHeader("Content-Type", "text/turtle");
        return ResponseEntity.ok().body(StreamingHelpers.getModelStream(m, "ttl", null, null, EditConfig.prefix.getPrefixMap()));
    }
    
    public static void ensureAccess(final AccessInfo acc, final Resource res) throws EditException {
        if (acc == null || !acc.isLogged())
            throw new EditException(401, "this requires being logged in");
        // the access control is different for users and general resources
        if (res.getURI().startsWith(EditConstants.BDU)) {
            final String authId = acc.getId();
            if (authId == null) {
                log.error("couldn't find authId for {}"+acc.toString());
                throw new EditException(500, "couldn't find authId");
            }
            final String auth0Id = authId.substring(authId.lastIndexOf("|") + 1);
            Resource usr;
            try {
                usr = BudaUser.getRdfProfile(auth0Id);
            } catch (IOException e) {
                throw new EditException(500, "couldn't get RDF profile", e);
            }
            if (!acc.isAdmin() && !usr.equals(res))
                throw new EditException(403, "only admins can modify other users");
        } else {
            if (!acc.isEditor())
                throw new EditException(403, "this requires being logged in with an editor account");
        }
    }
    
    public static String[] parseChangeMessage(String changeMessage, boolean creation) throws EditException {
        if (changeMessage == null) {
            final String[] res = { (creation ? "create resource" : "update resource"), "en" };
            return res;
        }
        try {
            changeMessage = java.net.URLDecoder.decode(changeMessage, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // not going to happen - value came from JDK's own StandardCharsets
        }
        if (changeMessage.length() > 150)
            throw new EditException(400, "change message too long");
        String messageLang = "en";
        final int atIdx = changeMessage.lastIndexOf('@');
        if (atIdx != -1) {
            messageLang = changeMessage.substring(atIdx+1, changeMessage.length());
            changeMessage = changeMessage.substring(0, atIdx);
        }
        changeMessage = StringUtils.strip(changeMessage, "\" ");
        final String[] res = { changeMessage, messageLang };
        return res;
    }
    
    @PutMapping(value = "/{qname}/focusgraph")
    public static ResponseEntity<String> putFocusGraph(@PathVariable("qname") String qname, HttpServletRequest req,
            HttpServletResponse response, @RequestBody String model, @RequestHeader("X-Change-Message") String changeMessage) throws Exception {
        final Resource res = ResourceFactory.createResource(EditConstants.BDR+qname.substring(4));
        Resource user = null;
        boolean isAdmin = false;
        if (EditConfig.useAuth) {
        	AccessInfo acc = (AccessInfo) req.getAttribute("access");
            try {
                ensureAccess(acc, res);
                user = BudaUser.getUserFromAccess(acc);
                isAdmin = acc.isAdmin();
            } catch (EditException e) {
                return ResponseEntity.status(e.getHttpStatus())
                        .body(e.getMessage());
            }
        }
        final InputStream in = new ByteArrayInputStream(model.getBytes());
        final MediaType med = MediaType.parseMediaType(req.getHeader("Content-Type"));
        Lang jenaLang = null;
        if (med != null) {
            jenaLang = BudaMediaTypes.getJenaLangFromExtension(BudaMediaTypes.getExtFromMime(med));
            log.info("MediaType {} and extension {} and jenaLang {}", med, med.getSubtype(), jenaLang);
        } else {
            log.error("Invalid or missing Content-Type header {}", req.getHeader("Content-Type"));
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("Cannot parse Content-Type header " + req.getHeader("Content-Type"));
        }
        final Model inModel = ModelFactory.createDefaultModel();
        try {
            inModel.read(in, null, jenaLang.getLabel());
        } catch (RiotException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot parse request content in " + req.getHeader("Content-Type"));
        }
        final GitInfo gi;
        try {
            gi = saveResource(inModel, res, null, parseChangeMessage(changeMessage, true), user, isAdmin);
        } catch(EditException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(e.getMessage());
        }
        response.addHeader("Etag", '"'+gi.revId+'"');
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.created(null).body("");
    }

    @PostMapping(value = "/{qname}/focusgraph")
    public static ResponseEntity<String> postFocusGraph(@PathVariable("qname") String qname, HttpServletRequest req,
            HttpServletResponse response, @RequestBody String model, @RequestHeader("Content-Type") String ct, 
            @RequestHeader(value = "If-Match", required = true) String ifMatch,
            @RequestHeader("X-Change-Message") String changeMessage) throws Exception {
        final boolean userMode = qname.startsWith("bdu:");
        final Resource res;
        if (userMode) {
            res = ResourceFactory.createResource(EditConstants.BDU+qname.substring(4));
        } else {
            if (!qname.startsWith("bdr:"))
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("you can only modify entities in the bdr namespace in this endpoit");
            res = ResourceFactory.createResource(EditConstants.BDR+qname.substring(4));
        }
        Resource user = null;
        boolean isAdmin = false;
        if (EditConfig.useAuth) {
        	AccessInfo acc = (AccessInfo) req.getAttribute("access");
            try {
                ensureAccess(acc, res);
                isAdmin = acc.isAdmin();
                user = BudaUser.getUserFromAccess(acc);
            } catch (EditException e) {
                return ResponseEntity.status(e.getHttpStatus())
                        .body(e.getMessage());
            }
        }
        if (ifMatch != null) {
            ifMatch = ifMatch.replace("\"", "");
            if (ifMatch.startsWith("W/"))
                ifMatch = ifMatch.substring(2);
        }
        final InputStream in = new ByteArrayInputStream(model.getBytes());
        final MediaType med = MediaType.parseMediaType(ct);
        Lang jenaLang = null;
        if (med != null) {
            jenaLang = BudaMediaTypes.getJenaLangFromExtension(BudaMediaTypes.getExtFromMime(med));
            log.info("MediaType {} and extension {} and jenaLang {}", med, med.getSubtype(), jenaLang);
        } else {
            log.error("Invalid or missing Content-Type header {}", req.getHeader("Content-Type"));
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("Cannot parse Content-Type header " + req.getHeader("Content-Type"));
        }
        final Model inModel = ModelFactory.createDefaultModel();
        try {
            inModel.read(in, null, jenaLang.getLabel());
        } catch (RiotException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot parse request content in " + req.getHeader("Content-Type"));
        }
        final GitInfo gi;
        try {
            gi = saveResource(inModel, res, ifMatch, parseChangeMessage(changeMessage, false), user, isAdmin);
        } catch(EditException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(e.getMessage());
        }
        response.addHeader("Etag", '"'+gi.revId+'"');
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.ok().body("");
    }

    
    // TODO: finish this
    @PutMapping(value = "/{qname}")
    public static ResponseEntity<String> putResource(@PathVariable("qname") String qname, HttpServletRequest req,
            HttpServletResponse response, @RequestBody String model, 
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("X-Change-Message") String changeMessage) throws Exception {
        // TODO: handle If-Match (optional in this case)
        final boolean userMode = qname.startsWith("bdu:");
        final boolean graphMode = qname.startsWith("bdg:");
        final Resource res;
        if (userMode) {
            res = ResourceFactory.createResource(EditConstants.BDU+qname.substring(4));
        } else if (graphMode) {
            res = ResourceFactory.createResource(EditConstants.BDG+qname.substring(4));
        } else {
            if (!qname.startsWith("bdr:"))
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("you can only modify entities in the bdr namespace in this endpoit");
            res = ResourceFactory.createResource(EditConstants.BDR+qname.substring(4));
        }
        Resource user = null;
        if (EditConfig.useAuth) {
        	AccessInfo acc = (AccessInfo) req.getAttribute("access");
            try {
                ensureAccess(acc, res);
                user = BudaUser.getUserFromAccess(acc);
            } catch (EditException e) {
                return ResponseEntity.status(e.getHttpStatus())
                        .body(e.getMessage());
            }
        }
        InputStream in = new ByteArrayInputStream(model.getBytes());
        MediaType med = MediaType.parseMediaType(req.getHeader("Content-Type"));
        Lang jenaLang = null;
        if (med != null) {
            jenaLang = BudaMediaTypes.getJenaLangFromExtension(BudaMediaTypes.getExtFromMime(med));
            log.info("MediaType {} and extension {} and jenaLang {}", med, med.getSubtype(), jenaLang);
        } else {
            log.error("Invalid or missing Content-Type header {}", req.getHeader("Content-Type"));
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("Cannot parse Content-Type header " + req.getHeader("Content-Type"));
        }
        final Model inModel = ModelFactory.createDefaultModel();
        try {
            inModel.read(in, null, jenaLang.getLabel());
        } catch (RiotException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot parse request content in " + req.getHeader("Content-Type"));
        }
        // TODO: this probably doesn't work for bdr: qname values
        final GitInfo gi = putGraph(inModel, res, parseChangeMessage(changeMessage, false), user);
        response.addHeader("Etag", '"'+gi.revId+'"');
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.ok().body("");
    }
    
    // previousRev is the previous revision that the resource must have
    // when null, the resource must not exist already
    public static GitInfo saveResource(final Model inModel, final Resource r, final String previousRev, final String[] changeMessage, final Resource user, final boolean isAdmin) throws EditException, IOException, GitAPIException {
        final Resource shape = CommonsRead.getShapeForEntity(r);
        final Model inFocusGraph = ModelUtils.getValidFocusGraph(inModel, r, shape);
        log.error("save in git");
        final GitInfo gi = CommonsGit.saveInGit(inFocusGraph, r, shape, previousRev, changeMessage, user, isAdmin);
        log.error("save in Fuseki");
        FusekiWriteHelpers.putDataset(gi);
        return gi;
    }
    
    // bypasses all checks and just write the model in the relevant graph on git and Fuseki
    // this is not the normal API and should be kept for edge cases only
    public static GitInfo putGraph(final Model inModel, final Resource graph, final String[] changeMessage, final Resource user) throws IOException, GitAPIException, EditException {
        final GitInfo gi = CommonsGit.gitInfoForResource(graph, false);
        final Dataset result = DatasetFactory.create();
        // TODO: adjust for user graphs
        result.addNamedModel(graph.getURI(), inModel);
        gi.ds = result;
        // this writes gi.ds in the relevant file, creates a commit, updates gi.revId and pushes if relevant
        CommonsGit.commitAndPush(gi, CommonsGit.getCommitMessage(graph, changeMessage, user));
        FusekiWriteHelpers.putDataset(gi);
        return gi;
    }
    
    @RequestMapping(value = "/callbacks/model/bdrc-auth", method = RequestMethod.POST, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> readAuthModel() {
        log.info("updating Auth data model() >>");
        RdfAuthModel.readAuthModel();
        return ResponseEntity.ok("Updated auth Model was read into editserv");
    }

    @PostMapping(value = "/callbacks/github/shapes")
    public ResponseEntity<String> updateShapesOntology() throws IOException {
        Shapes sh = new Shapes();
        Thread t = new Thread(sh);
        t.start();
        return ResponseEntity.ok().body("Shapes Ontologies are being updated");
    }
    
}
