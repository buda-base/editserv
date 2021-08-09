package io.bdrc.edit.controllers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.auth.Access;
import io.bdrc.auth.rdf.RdfAuthModel;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsRead;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.helpers.Shapes;
import io.bdrc.edit.txn.exceptions.VersionConflictException;
import io.bdrc.libraries.BudaMediaTypes;
import io.bdrc.libraries.Models;
import io.bdrc.libraries.StreamingHelpers;

@Controller
@RequestMapping("/")
public class MainEditController {

    public final static Logger log = LoggerFactory.getLogger(MainEditController.class.getName());

    @GetMapping(value = "/focusGraph/{qname}", produces = "text/turtle")
    public ResponseEntity<StreamingResponseBody> getFocusGraph(@PathVariable("qname") String qname,
            HttpServletRequest req, HttpServletResponse response) {
        Model m = null;
        if (!qname.startsWith("bdr:"))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                    .body(StreamingHelpers.getStream("No graph could be found for " + qname));
        final String lname = qname.substring(4);
        try {
            m = CommonsGit.getGraphFromGit(lname);
            if (m == null || m.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("No graph could be found for " + qname));
            Resource shape = CommonsRead.getShapeForEntity(lname);
            m = CommonsRead.getFocusGraph(m, m.createResource(Models.BDR+lname), shape);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                    .body(StreamingHelpers.getStream("No graph could be found for " + qname));
        }
        response.addHeader("Content-Type", "text/turtle");
        return ResponseEntity.ok().body(StreamingHelpers.getModelStream(m, "ttl", null, null, EditConfig.prefix.getPrefixMap()));
    }
    
    @PutMapping(value = "/putresource/{qname}")
    public ResponseEntity<String> putResource(@PathVariable("qname") String qname, HttpServletRequest req,
            HttpServletResponse response, @RequestBody String model) throws Exception {
        Access acc = (Access) req.getAttribute("access");
        if (acc == null || !acc.isUserLoggedIn())
            return ResponseEntity.status(401).body("this requires being logged in with an admin account");
        if (!acc.getUserProfile().isAdmin())
            return ResponseEntity.status(403).body("this requires being logged in with an admin account");
        InputStream in = new ByteArrayInputStream(model.getBytes());
        // for testing purpose
        // InputStream in =
        // MainEditController.class.getClassLoader().getResourceAsStream("P705.ttl");
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
        inModel.read(in, null, jenaLang.getLabel());
        final String lname = qname.substring(4);
        final Resource subject = ResourceFactory.createResource(Models.BDR+lname);
        final Resource shape = CommonsRead.getShapeForEntity(lname);
        // TODO: validate in that step
        final Model inFocusGraph = CommonsRead.getFocusGraph(inModel, subject, shape);
        if (!CommonsValidate.validateFocusing(inModel, inFocusGraph)) {
            throw new VersionConflictException("Graph does not conform shape");
        }
        final Model gitGraph = CommonsGit.getGraphFromGit(lname);
        final Model gitFocusGraph = CommonsRead.getFocusGraph(gitGraph, subject, shape);
        if (!CommonsValidate.validateCommit(inFocusGraph, gitFocusGraph, qname.substring(4))) {
            throw new VersionConflictException("Version conflict while trying to save " + qname);
        }
        if (!CommonsValidate.validateShacl(inFocusGraph)) {
            throw new VersionConflictException("Shacl did not validate, check logs");
        }
        if (!CommonsValidate.validateExtRIDs(inFocusGraph)) {
            throw new VersionConflictException("Some external resources do not have a correct RID, check logs");
        }
        final Model newGitGraph = CommonsRead.createFinalGraph(inFocusGraph, gitGraph, gitFocusGraph);
        String commitId = CommonsGit.putAndCommitSingleResource(newGitGraph, lname);
        if (commitId == null) {
            ResponseEntity.status(HttpStatus.CONFLICT).body("Request cannot be processed - Git commitId is null");
        }
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.ok().body(commitId);
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
