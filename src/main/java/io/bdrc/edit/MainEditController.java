package io.bdrc.edit;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.edit.commons.CommonsGit;
import io.bdrc.edit.commons.CommonsRead;
import io.bdrc.libraries.BudaMediaTypes;
import io.bdrc.libraries.StreamingHelpers;

@Controller
@RequestMapping("/")
public class MainEditController {

    public final static Logger log = LoggerFactory.getLogger(MainEditController.class.getName());

    @GetMapping(value = "/focusGraph/{prefixedId}", produces = "application/json")
    public ResponseEntity<StreamingResponseBody> getFocusGraph(@PathVariable("prefixedId") String prefixedId, HttpServletRequest req,
            HttpServletResponse response) {
        Model m = ModelFactory.createDefaultModel();
        try {
            m = CommonsRead.getEditorGraph(prefixedId);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                    .body(StreamingHelpers.getStream("No graph could be found for " + prefixedId));
        }
        response.addHeader("Content-Type", "text/turtle;charset=utf-8");
        return ResponseEntity.ok().body(StreamingHelpers.getModelStream(m, "ttl", null, null));
    }

    @PutMapping(value = "/putresource/{prefixedId}")
    public ResponseEntity<String> putResource(@PathVariable("prefixedId") String prefixedId, HttpServletRequest req, HttpServletResponse response,
            @RequestBody String model) throws Exception {
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot parse Content-Type header " + req.getHeader("Content-Type"));
        }
        Model m = ModelFactory.createDefaultModel();
        m.read(in, null, jenaLang.getLabel());
        // m.write(System.out, "TURTLE");
        String commitId = CommonsGit.putResource(m, prefixedId);
        if (commitId == null) {

        }
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.ok().body(commitId);
    }

}
