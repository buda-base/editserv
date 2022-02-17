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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.auth.Access;
import io.bdrc.auth.rdf.RdfAuthModel;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.commons.ops.CommonsRead;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.helpers.Shapes;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.VersionConflictException;
import io.bdrc.libraries.BudaMediaTypes;
import io.bdrc.libraries.Models;
import io.bdrc.libraries.StreamingHelpers;

@Controller
@RequestMapping("/")
public class MainEditController {

    public final static Logger log = LoggerFactory.getLogger(MainEditController.class.getName());

    @GetMapping(value = "/focusGraph/{qname}", produces = "text/turtle")
    public static ResponseEntity<StreamingResponseBody> getFocusGraph(@PathVariable("qname") String qname,
            HttpServletRequest req, HttpServletResponse response) {
        Model m = null;
        if (!qname.startsWith("bdr:"))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                    .body(StreamingHelpers.getStream("No graph could be found for " + qname));
        final String lname = qname.substring(4);
        final Resource r = ResourceFactory.createResource(Models.BDR+lname);
        try {
            CommonsGit.GitInfo gi = CommonsGit.gitInfoForResource(r);
            if (gi.ds == null || gi.ds.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("No graph could be found for " + qname));
            Resource shape = CommonsRead.getShapeForEntity(r);
            m = ModelUtils.getMainModel(gi.ds);
            m = CommonsRead.getFocusGraph(m, r, shape);
            m.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                    .body(StreamingHelpers.getStream("No graph could be found for " + qname));
        }
        response.addHeader("Content-Type", "text/turtle");
        return ResponseEntity.ok().body(StreamingHelpers.getModelStream(m, "ttl", null, null, EditConfig.prefix.getPrefixMap()));
    }
    
    @PutMapping(value = "/putresource/{qname}")
    public static ResponseEntity<String> putResource(@PathVariable("qname") String qname, HttpServletRequest req,
            HttpServletResponse response, @RequestBody String model) throws Exception {
        if (qname.startsWith("bdu:"))
            return UserEditController.userPut(qname, response, req, model);
        if (!qname.startsWith("bdr:"))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("you can only modify entities in the bdr namespace in this endpoit");
        if (EditConfig.useAuth) {
            Access acc = (Access) req.getAttribute("access");
            if (acc == null || !acc.isUserLoggedIn())
                return ResponseEntity.status(401).body("this requires being logged in with an admin account");
            if (!acc.getUserProfile().isAdmin())
                return ResponseEntity.status(403).body("this requires being logged in with an admin account");
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
        final String lname = qname.substring(4);
        final Resource subject = ResourceFactory.createResource(Models.BDR+lname);
        final String revId = saveResource(inModel, subject);
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.ok().body(revId);
    }

    public static String saveResource(final Model inModel, final Resource r) throws IOException, VersionConflictException, GitAPIException, ModuleException {
        final Resource shape = CommonsRead.getShapeForEntity(r);
        log.info("use shape {}", shape);
        final Model inFocusGraph = CommonsRead.getFocusGraph(inModel, r, shape);
        if (!CommonsValidate.validateFocusing(inModel, inFocusGraph)) {
            Model diff = inModel.difference(inFocusGraph);
            log.error("Focus graph is not the same size as initial graph, difference is {}", ModelUtils.modelToTtl(diff));
        }
        if (!CommonsValidate.validateShacl(inFocusGraph)) {
            throw new VersionConflictException("Shacl did not validate, check logs");
        }
        if (!CommonsValidate.validateExtRIDs(inFocusGraph)) {
            throw new VersionConflictException("Some external resources do not have a correct RID, check logs");
        }
        final GitInfo gi = CommonsGit.saveInGit(inFocusGraph, r, shape);
        FusekiWriteHelpers.putDataset(gi);
        return gi.revId;
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
