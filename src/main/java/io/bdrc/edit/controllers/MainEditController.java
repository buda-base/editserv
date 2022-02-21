package io.bdrc.edit.controllers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RiotException;
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

import io.bdrc.auth.Access;
import io.bdrc.auth.rdf.RdfAuthModel;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.commons.ops.CommonsRead;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.helpers.Shapes;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.VersionConflictException;
import io.bdrc.edit.user.BudaUser;
import io.bdrc.libraries.BudaMediaTypes;
import io.bdrc.libraries.StreamingHelpers;

@Controller
@RequestMapping("/")
public class MainEditController {

    public final static Logger log = LoggerFactory.getLogger(MainEditController.class.getName());

    @GetMapping(value = "/{qname}/focusGraph", produces = "text/turtle")
    public static ResponseEntity<StreamingResponseBody> getFocusGraph(@PathVariable("qname") String qname,
            HttpServletRequest req, HttpServletResponse response) {
        return getGraph(qname, req, response, true, null);
    }
    
    @GetMapping(value = "/{qname}/revision/{revId}/focusGraph", produces = "text/turtle")
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
        if (EditConfig.useAuth && userMode) {
            Access acc = (Access) req.getAttribute("access");
            try {
                ensureAccess(acc, res);
            } catch (ModuleException e) {
                return ResponseEntity.status(e.getHttpStatus())
                        .body(StreamingHelpers.getStream(e.getMessage()));
            }
        }
        // TODO: handle revision
        try {
            CommonsGit.GitInfo gi = CommonsGit.gitInfoForResource(res);
            if (gi.ds == null || gi.ds.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("No graph could be found for " + qname));
            Resource shape = CommonsRead.getShapeForEntity(res);
            response.addHeader("Etag", gi.revId);
            m = ModelUtils.getMainModel(gi.ds);
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
    
    public static void ensureAccess(Access acc, Resource res) throws ModuleException {
        if (acc == null || !acc.isUserLoggedIn())
            throw new ModuleException(401, "this requires being logged in");
        // the access control is different for users and general resources
        if (res.getURI().startsWith(EditConstants.BDU)) {
            String authId = acc.getUser().getAuthId();
            if (authId == null) {
                log.error("couldn't find authId for {}"+acc.toString());
                throw new ModuleException(500, "couldn't find authId");
            }
            final String auth0Id = authId.substring(authId.lastIndexOf("|") + 1);
            Resource usr;
            try {
                usr = BudaUser.getRdfProfile(auth0Id);
            } catch (IOException e) {
                throw new ModuleException(500, "couldn't get RDF profile", e);
            }
            if (!acc.getUserProfile().isAdmin() && !usr.equals(res))
                throw new ModuleException(403, "only admins can modify other users");
        } else {
            if (!acc.getUserProfile().isAdmin())
                throw new ModuleException(403, "this requires being logged in with an admin account");
        }
    }
    
    @PutMapping(value = "{qname}/focusgraph")
    public static ResponseEntity<String> putFocusGraph(@PathVariable("qname") String qname, HttpServletRequest req,
            HttpServletResponse response, @RequestBody String model) throws Exception {
        final Resource res = ResourceFactory.createResource(EditConstants.BDR+qname.substring(4));
        if (EditConfig.useAuth) {
            Access acc = (Access) req.getAttribute("access");
            try {
                ensureAccess(acc, res);
            } catch (ModuleException e) {
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot parse Content-Type header " + req.getHeader("Content-Type"));
        }
        final Model inModel = ModelFactory.createDefaultModel();
        try {
            inModel.read(in, null, jenaLang.getLabel());
        } catch (RiotException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot parse request content in " + req.getHeader("Content-Type"));
        }
        final GitInfo gi = saveResource(inModel, res, null);
        response.addHeader("Etag", gi.revId);
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.ok().body("");
    }

    @PostMapping(value = "{qname}/focusgraph")
    public static ResponseEntity<String> postFocusGraph(@PathVariable("qname") String qname, HttpServletRequest req,
            HttpServletResponse response, @RequestBody String model, @RequestHeader("Content-Type") String ct, @RequestHeader(value = "If-Match", required = true) String ifMatch) throws Exception {
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
        
        if (EditConfig.useAuth) {
            Access acc = (Access) req.getAttribute("access");
            try {
                ensureAccess(acc, res);
            } catch (ModuleException e) {
                return ResponseEntity.status(e.getHttpStatus())
                        .body(e.getMessage());
            }
        }
        final InputStream in = new ByteArrayInputStream(model.getBytes());
        final MediaType med = MediaType.parseMediaType(ct);
        Lang jenaLang = null;
        if (med != null) {
            jenaLang = BudaMediaTypes.getJenaLangFromExtension(BudaMediaTypes.getExtFromMime(med));
            log.info("MediaType {} and extension {} and jenaLang {}", med, med.getSubtype(), jenaLang);
        } else {
            log.error("Invalid or missing Content-Type header {}", req.getHeader("Content-Type"));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot parse Content-Type header " + req.getHeader("Content-Type"));
        }
        final Model inModel = ModelFactory.createDefaultModel();
        try {
            inModel.read(in, null, jenaLang.getLabel());
        } catch (RiotException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot parse request content in " + req.getHeader("Content-Type"));
        }
        final GitInfo gi = saveResource(inModel, res, ifMatch);
        response.addHeader("Etag", gi.revId);
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.ok().body("");
    }

    
    // TODO: finish this
    @PutMapping(value = "{qname}")
    public static ResponseEntity<String> putResource(@PathVariable("qname") String qname, HttpServletRequest req,
            HttpServletResponse response, @RequestBody String model) throws Exception {
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
        if (EditConfig.useAuth) {
            Access acc = (Access) req.getAttribute("access");
            try {
                ensureAccess(acc, res);
            } catch (ModuleException e) {
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
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
        final GitInfo gi = putGraph(inModel, res);
        response.addHeader("Etag", gi.revId);
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.ok().body("");
    }
    
    // previousRev is the previous revision that the resource must have
    // when null, the resource must not exist already
    public static GitInfo saveResource(final Model inModel, final Resource r, final String previousRev) throws IOException, VersionConflictException, GitAPIException, ModuleException {
        final Resource shape = CommonsRead.getShapeForEntity(r);
        final Model inFocusGraph = ModelUtils.getValidFocusGraph(inModel, r, shape);
        final GitInfo gi = CommonsGit.saveInGit(inFocusGraph, r, shape, previousRev);
        FusekiWriteHelpers.putDataset(gi);
        return gi;
    }
    
    // bypasses all checks and just write the model in the relevant graph on git and Fuseki
    // this is not the normal API and should be kept for edge cases only
    public static GitInfo putGraph(final Model inModel, final Resource graph) throws IOException, VersionConflictException, GitAPIException, ModuleException {
        final GitInfo gi = CommonsGit.gitInfoForResource(graph);
        final Dataset result = DatasetFactory.create();
        // TODO: adjust for user graphs
        result.addNamedModel(graph.getURI(), inModel);
        gi.ds = result;
        // this writes gi.ds in the relevant file, creates a commit, updates gi.revId and pushes if relevant
        CommonsGit.commitAndPush(gi, "force full graph replacement");
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
