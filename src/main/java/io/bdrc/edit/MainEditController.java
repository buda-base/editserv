package io.bdrc.edit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.edit.commons.CommonsRead;
import io.bdrc.libraries.StreamingHelpers;

@Controller
@RequestMapping("/")
public class MainEditController {

    public final static Logger log = LoggerFactory.getLogger(MainEditController.class.getName());

    @GetMapping(value = "/focusGraph/{prefixedId}", produces = "application/json")
    public ResponseEntity<StreamingResponseBody> getTask(@PathVariable("prefixedId") String prefixedId, HttpServletRequest req,
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

}
