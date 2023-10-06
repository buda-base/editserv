package io.bdrc.edit.controllers;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.opencsv.CSVWriter;

import io.bdrc.auth.AccessInfo;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.helpers.SimpleOutline;
import io.bdrc.libraries.StreamingHelpers;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Optional;


@Controller
@RequestMapping("/")
class CSVOutlineController {
    
    public final static Logger log = LoggerFactory.getLogger(CSVOutlineController.class.getName());
    
    public final static MediaType TEXT_CSV_TYPE = new MediaType("text", "csv");
    
    public static final Property status = ResourceFactory.createProperty(EditConstants.ADM + "status");
    public static final Property authorshipStatement = ResourceFactory.createProperty(EditConstants.BDO + "authorshipStatement");
    public static final Property outlineOf = ResourceFactory.createProperty(EditConstants.BDO + "outlineOf");
    
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
    
    public static Resource getLikelyOutline(final Resource w) {
        // returns any released outline, if not return any non-released outline, if not return null
        final String query = "SELECT ?o ?st where { ?mw <"+EditConstants.BDO+"instanceHasReproduction> <"+w.getURI()+"> . ?o <"+EditConstants.BDO+"outlineOf> ?mw . ?oadm <"+EditConstants.ADM+"adminAbout> ?o ; <"+EditConstants.ADM+"status> ?st . }";
        log.error(query);
        final Query q = QueryFactory.create(query);
        log.error("Fuseki: "+FusekiWriteHelpers.FusekiSparqlEndpoint);
        final QueryExecution qe = QueryExecution.service(FusekiWriteHelpers.FusekiSparqlEndpoint).query(q).build();
        final ResultSet res = qe.execSelect();
        Resource nonReleasedCandidate = null;
        while (res.hasNext()) {
            final QuerySolution r = res.next();
            final Resource status = r.getResource("st");
            if (status.getLocalName().equals("StatusReleased"))
                return r.getResource("o");
            nonReleasedCandidate = r.getResource("o");
        }
        return nonReleasedCandidate;
    }

    @GetMapping(value = "/outline/csv/{wqname}")
    public ResponseEntity<StreamingResponseBody> downloadCSV(@RequestParam("oqname") final Optional<String> oqname, @PathVariable("wqname") final String wqname, final HttpServletRequest req, HttpServletResponse response) throws IOException {
        // Set the content type and attachment header.
        response.setContentType("text/csv");
        Resource ores = null;
        String olname = null;
        final String wlname = wqname.substring(4);
        Resource w = ResourceFactory.createResource(EditConstants.BDR+wlname);
        if (!oqname.isPresent()) {
            ores = getLikelyOutline(w);
            if (ores == null) {
                log.info("no outline found for "+wqname);
                olname = "O"+wlname.substring(1);
                // TODO: check that outline RID doesn't exist yet
                response.setHeader("Content-Disposition", "attachment; filename="+olname+"-"+wlname+".csv");
                return ResponseEntity.status(HttpStatus.OK).contentType(TEXT_CSV_TYPE)
                        .body(getCsvStream(SimpleOutline.getTemplate()));
            }
            olname = ores.getLocalName();
        } else {
            olname = oqname.get().substring(4);
            if (olname.startsWith("O"))
                return ResponseEntity.status(404).body(StreamingHelpers.getStream(""));
            ores = ResourceFactory.createResource(EditConstants.BDR+oqname);
        }
        
        response.setHeader("Content-Disposition", "attachment; filename="+olname+"-"+wlname+".csv");
        boolean isAdmin = false;
        Model m = null;
        Resource mw = null;
        if (EditConfig.useAuth) {
            final AccessInfo acc = (AccessInfo) req.getAttribute("access");
            isAdmin = acc.isAdmin();
        }
        try {
            CommonsGit.GitInfo gi = CommonsGit.gitInfoForResource(ores, false);
            if (gi.ds == null || gi.ds.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("No graph could be found for " + olname));
            if (!isAdmin && Helpers.isHidden(gi.ds))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("Graph inaccessible: " + olname));
            if (gi.revId == null || gi.revId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.valueOf(500)).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("Could not find revision id of " + olname));
            }
            response.addHeader("Etag", '"'+gi.revId+'"');
            m = ModelUtils.getMainModel(gi.ds);
            m.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
            // add X-Outline-Attribution and X-Status
            final Resource oadm = m.createResource(EditConstants.BDA+olname);
            final Resource st = oadm.getPropertyResourceValue(status);
            response.addHeader("X-Status", '<'+st.getURI()+'>');
            // link model in resource
            ores = m.createResource(ores.getURI());
            final Statement as = ores.getProperty(authorshipStatement);
            if (as != null)
                response.addHeader("X-Outline-Attribution", '"'+as.getString()+'"');
            mw = ores.getPropertyResourceValue(outlineOf);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                    .body(StreamingHelpers.getStream("No graph could be found for " + olname));
        }

        w = m.createResource(EditConstants.BDR+wlname);
        final SimpleOutline so = new SimpleOutline(mw, w);
        final List<String[]> csvRows = so.asCsv();
        
        return ResponseEntity.status(HttpStatus.OK).contentType(TEXT_CSV_TYPE)
                .body(getCsvStream(csvRows));
    }
}
