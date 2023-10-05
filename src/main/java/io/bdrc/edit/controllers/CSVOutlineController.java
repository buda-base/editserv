package io.bdrc.edit.controllers;

import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.sparql.core.DatasetGraph;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.opencsv.CSVWriter;

import io.bdrc.auth.AccessInfo;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.helpers.SimpleOutline;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.BudaMediaTypes;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.StreamingHelpers;
import io.bdrc.libraries.formatters.JSONLDFormatter;
import io.bdrc.libraries.formatters.TTLRDFWriter;
import io.bdrc.libraries.formatters.JSONLDFormatter.DocType;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;


@Controller
@RequestMapping("/")
class CSVOutlineController {
    
    public final static Logger log = LoggerFactory.getLogger(CSVOutlineController.class.getName());
    
    public final static MediaType TEXT_CSV_TYPE = new MediaType("text", "csv");
    
    public static StreamingResponseBody getCsvStream(final List<String[]> rows) {

        return new StreamingResponseBody() {
            @Override
            public void writeTo(final OutputStream os) {
                try (CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(os))) {
                    csvWriter.writeAll(rows);
                } catch (IOException e) {
                    log.error("error writing csv", e);
                }
            }
        };
    }

    @GetMapping(value = "/outline/csv/{oqname}/{wqname}")
    public ResponseEntity<StreamingResponseBody> downloadCSV(@PathVariable("oqname") final String oqname, @PathVariable("wqname") final String wqname, final HttpServletRequest req, HttpServletResponse response) throws IOException {
        // Set the content type and attachment header.
        response.setContentType("text/csv");
        final String olname = oqname.substring(4);
        if (olname.startsWith("O"))
            return ResponseEntity.status(404).body(StreamingHelpers.getStream(""));
        Resource ores = ResourceFactory.createResource(EditConstants.BDR+oqname);
        final String wlname = wqname.substring(4);
        response.setHeader("Content-Disposition", "attachment; filename="+olname+"-"+wlname+".csv");
        boolean isAdmin = false;
        Model m = null;
        if (EditConfig.useAuth) {
            final AccessInfo acc = (AccessInfo) req.getAttribute("access");
            isAdmin = acc.isAdmin();
        }
        try {
            CommonsGit.GitInfo gi = CommonsGit.gitInfoForResource(ores, false);
            if (gi.ds == null || gi.ds.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("No graph could be found for " + oqname));
            if (!isAdmin && Helpers.isHidden(gi.ds))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("Graph inaccessible: " + oqname));
            if (gi.revId == null || gi.revId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.valueOf(500)).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("Could not find revision id of " + oqname));
            }
            response.addHeader("Etag", '"'+gi.revId+'"');
            m = ModelUtils.getMainModel(gi.ds);
            m.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                    .body(StreamingHelpers.getStream("No graph could be found for " + oqname));
        }
        
        // link model in resource
        ores = m.createResource(ores.getURI());
        Resource w = m.createResource(EditConstants.BDR+wlname);
        
        final SimpleOutline so = new SimpleOutline(ores, w);
        final List<String[]> csvRows = so.asCsv();
        
        return ResponseEntity.status(HttpStatus.OK).contentType(TEXT_CSV_TYPE)
                .body(getCsvStream(csvRows));
    }
}
