package io.bdrc.edit.controllers;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import io.bdrc.auth.AccessInfo;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.helpers.SimpleOutline;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.edit.user.BudaUser;
import io.bdrc.libraries.Models;
import io.bdrc.libraries.StreamingHelpers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;


@Controller
@RequestMapping("/")
class CSVOutlineController {
    
    public final static Logger log = LoggerFactory.getLogger(CSVOutlineController.class.getName());
    
    public static final ObjectWriter ow = new ObjectMapper().writer();
    
    public final static MediaType TEXT_CSV_TYPE = new MediaType("text", "csv");
    
    public static final Property status = ResourceFactory.createProperty(EditConstants.ADM + "status");
    public static final Property authorshipStatement = ResourceFactory.createProperty(EditConstants.BDO + "authorshipStatement");
    public static final Property outlineOf = ResourceFactory.createProperty(EditConstants.BDO + "outlineOf");
    
    public static StreamingResponseBody getCsvStream(final List<String[]> rows) {

        return new StreamingResponseBody() {
            @Override
            public void writeTo(final OutputStream os) {
                try (CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(os))) {
                    // add UTF8 BOM
                    os.write(0xef);
                    os.write(0xbb);
                    os.write(0xbf);
                    csvWriter.writeAll(rows);
                } catch (IOException e) {
                    log.error("error writing csv", e);
                }
            }
        };
    }
    
    public static final class WInfo {
        Resource mw = null;
        Resource o = null;
        int mvn = 0;
    }
    
    public static WInfo getWInfo(final Resource w) {
        // returns any released outline, if not return any non-released outline, if not return null
        // also returns the MW as the second value, whether an outline is found or not
        final WInfo res = new WInfo();
        final String query = "SELECT ?o ?mw ?st ?mvn where { { ?mw <"+EditConstants.BDO+"instanceHasReproduction> <"+w.getURI()+"> . ?o <"+EditConstants.BDO+"outlineOf> ?mw . ?oadm <"+EditConstants.ADM+"adminAbout> ?o ; <"+EditConstants.ADM+"status> ?st . } union { ?mw <"+EditConstants.BDO+"instanceHasReproduction> <"+w.getURI()+"> } union { select (max(?vn) as ?mvn) { <"+w.getURI()+"> <"+EditConstants.BDO+"instanceHasVolume> ?ig . ?ig <"+EditConstants.BDO+"volumeNumber> ?vn } }}";
        log.error(query);
        final Query q = QueryFactory.create(query);
        log.error("Fuseki: "+FusekiWriteHelpers.FusekiSparqlEndpoint);
        final QueryExecution qe = QueryExecution.service(FusekiWriteHelpers.FusekiSparqlEndpoint).query(q).build();
        final ResultSet ress = qe.execSelect();
        Resource nonReleasedCandidate = null;
        while (ress.hasNext()) {
            final QuerySolution r = ress.next();
            if (r.contains("mw"))
                res.mw = r.getResource("mw");
            if (r.contains("mvn"))
                res.mvn = r.getLiteral("mvn").getInt();
            final Resource o = r.getResource("o");
            final Resource status = r.getResource("st");
            if (o != null && status != null && status.getLocalName().equals("StatusReleased")) {
                res.o = o;
            } else if (o != null) {
                nonReleasedCandidate = o;
            }
        }
        if (res.o == null) {
            res.o = nonReleasedCandidate;
        }
        return res;
    }

    @GetMapping(value = "/outline/csv/{wqname}")
    public ResponseEntity<StreamingResponseBody> downloadCSV(@RequestParam("oqname") final Optional<String> oqname, @PathVariable("wqname") final String wqname, final HttpServletRequest req, HttpServletResponse response) throws IOException {
        // Set the content type and attachment header.
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        Resource ores = null;
        String olname = null;
        final String wlname = wqname.substring(4);
        Resource w = ResourceFactory.createResource(EditConstants.BDR+wlname);
        if (!oqname.isPresent()) {
            final WInfo winfo = getWInfo(w);
            ores = winfo.o;
            if (winfo.mw == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                        .body(StreamingHelpers.getStream("No data could be found for " + wlname));
            }
            if (ores == null) {
                log.info("no outline found for "+wqname);
                olname = "O"+wlname.substring(1);
                // TODO: check that outline RID doesn't exist yet
                response.setHeader("Content-Disposition", "attachment; filename="+olname+"-"+wlname+".csv");
                return ResponseEntity.status(HttpStatus.OK).contentType(TEXT_CSV_TYPE)
                        .body(getCsvStream(SimpleOutline.getTemplate()));
            } else {
                olname = ores.getLocalName();
            }
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
    
    public static Model createOutlineModel(final Resource o, final Resource mw) {
        final Model m = ModelFactory.createDefaultModel();
        m.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        final Resource oadm = m.createResource(EditConstants.BDA+o.getLocalName());
        final Resource og = m.createResource(EditConstants.BDG+o.getLocalName());
        m.add(oadm, m.createProperty(Models.ADM, "graphId"), og);
        m.add(oadm, m.createProperty(Models.ADM, "adminAbout"), o);
        m.add(oadm, m.createProperty(Models.ADM, "access"), m.createResource(EditConstants.BDA+"AccessOpen"));
        m.add(o, m.createProperty(Models.BDO, "outlineOf"), mw);
        m.add(o, RDF.type, m.createResource(Models.BDO+"Outline"));
        return m;
    }
    
    public static Literal parseAttribution(final Optional<String> attr, final Model m) {
        if (attr == null || !attr.isPresent() || attr.isEmpty()) {
            return null;
        }
        String attrs = attr.get();
        try {
            attrs = java.net.URLDecoder.decode(attrs, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // not going to happen - value came from JDK's own StandardCharsets
        }
        String messageLang = "en";
        final int atIdx = attrs.lastIndexOf('@');
        if (atIdx != -1) {
            messageLang = attrs.substring(atIdx+1, attrs.length());
            attrs = attrs.substring(0, atIdx);
        }
        attrs = StringUtils.strip(attrs, "\" ");
        return m.createLiteral(attrs, messageLang);
    }
    
    @PutMapping(value = "/outline/csv/{wqname}")
    public static ResponseEntity<String> putCSV(@RequestParam("oqname") final Optional<String> oqname, @PathVariable("wqname") String wqname, HttpServletRequest req,
            HttpServletResponse response, @RequestHeader(value = "If-Match") Optional<String> ifMatch, @RequestHeader(value = "Content-Encoding") Optional<String> contentEncoding, @RequestBody byte[] requestBody, @RequestHeader("X-Change-Message") Optional<String> changeMessage, @RequestHeader("X-Outline-Attribution") Optional<String> attribution, @RequestHeader("X-Status") Optional<String> status) throws Exception {
        if (status.isPresent() && !("<"+EditConstants.BDA+"StatusReleased>").equals(status.get()) && !("<"+EditConstants.BDA+"StatusEditing>").equals(status.get()) && !("<"+EditConstants.BDA+"StatusWithdrawn>").equals(status.get())) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("status must be StatusReleased, StatusWithdrawn or StatusEditing");
        }
        String ifMatchS = null;
        if (ifMatch.isPresent() && ifMatch != null) {
            ifMatchS = ifMatch.get().replace("\"", "");
            if (ifMatchS.startsWith("W/"))
                ifMatchS = ifMatchS.substring(2);
        }
        Resource ores = null;
        String olname = null;
        final String wlname = wqname.substring(4);
        Resource w = ResourceFactory.createResource(EditConstants.BDR+wlname);
        Resource mwres = null;
        AccessInfo acc = (AccessInfo) req.getAttribute("access");
        Resource user = null;
        final WInfo winfo = getWInfo(w);
        if (!oqname.isPresent()) {
            ores = winfo.o;
            mwres = winfo.mw;
            if (mwres == null) {
                return ResponseEntity.status(500)
                        .body("No data could be found for "+wlname);
            }
            if (ores == null) {
                log.info("no outline found for "+wqname);
                olname = "O"+wlname.substring(1);
                if (RIDController.idExists(olname)) {
                    return ResponseEntity.status(500)
                            .body(olname+" already exists, please specify a new one");
                }
                ores = ResourceFactory.createResource(EditConstants.BDR + olname);
            } else {
                olname = ores.getLocalName();
            }
        } else {
            // TODO: check that oqname is indeed an outline for wqname
            olname = oqname.get().substring(4);
            ores = ResourceFactory.createResource(EditConstants.BDR+oqname);
        }
        try {
            MainEditController.ensureAccess(acc, ores);
            user = BudaUser.getUserFromAccess(acc);
        } catch (EditException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(e.getMessage());
        }
        if (!req.getHeader("Content-Type").equals("text/csv")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("Content-Type must be text/csv");
        }
        CommonsGit.GitInfo gi = CommonsGit.gitInfoForResource(ores, true);
        if (gi.revId != null && !gi.revId.equals(ifMatchS)) {
            log.error("CSV version don't match: got {} but {} expected", ifMatchS, gi.revId);
        }
        boolean creation = gi.revId == null;
        Model m = null;
        if (gi.ds == null || gi.ds.isEmpty()) {
            m = createOutlineModel(ores, mwres);
        } else {
            m = ModelUtils.getMainModel(gi.ds);
            m.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        }
        String csvString = null;
        if (contentEncoding.isPresent() && "gzip".equalsIgnoreCase(contentEncoding.get())) {
            try {
                csvString = decompressGzip(requestBody);
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body("Cannot uncompress body");
            }
        } else {
            csvString = new String(requestBody, StandardCharsets.UTF_8);
        }
        // remove BOM
        if (csvString.startsWith("\ufeff"))
            csvString = csvString.substring(1);
        final CSVReader reader = new CSVReader(new StringReader(csvString));
        final List<String[]> csvData = reader.readAll();
        reader.close();
        final SimpleOutline so = new SimpleOutline(csvData, ores, mwres, w, winfo.mvn);
        so.insertInModel(m, mwres, w);
        if (so.hasBlockingWarns())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(ow.writeValueAsString(so.warns));
        if (attribution.isPresent()) {
            final Literal attributionL = parseAttribution(attribution, m);
            m.removeAll(ores, m.createProperty(EditConstants.BDO, "authorshipStatement"), (RDFNode) null);
            if (attributionL != null) {
                m.add(ores, m.createProperty(EditConstants.BDO, "authorshipStatement"), attributionL);
            }
        }
        final Resource oadm = m.createResource(EditConstants.BDA+ores.getLocalName());
        m.removeAll(oadm, m.createProperty(EditConstants.ADM, "status"), (RDFNode) null);
        if (status.isPresent() && !status.get().isEmpty()) {
            m.add(oadm, m.createProperty(EditConstants.ADM, "status"), m.createResource(status.get().substring(1, status.get().length()-1)));
        } else {
            m.add(oadm, m.createProperty(EditConstants.ADM, "status"), m.createResource(EditConstants.BDA+"StatusReleased"));
        }
        final String[] parsedChangeMessage = MainEditController.parseChangeMessage(changeMessage.isPresent() ? changeMessage.get() : null, creation);
        ModelUtils.addSimpleLogEntry(m, ores, user, parsedChangeMessage, creation);
        final GitInfo gio;
        try {
            gio = MainEditController.putGraph(m, m.createResource(EditConstants.BDG+olname), parsedChangeMessage, user);
        } catch(EditException e) {
            return ResponseEntity.status(e.getHttpStatus()).body(e.getMessage());
        }
        response.addHeader("Etag", '"'+gio.revId+'"');
        response.addHeader("Content-Type", "text/plain;charset=utf-8");
        return ResponseEntity.status((ifMatchS == null || ifMatchS.isEmpty()) ? HttpStatus.CREATED : HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(ow.writeValueAsString(so.warns));
    }
    
    public final static String decompressGzip(final byte[] compressedData) throws IOException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
             final GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
             final InputStreamReader reader = new InputStreamReader(gzipInputStream)) {

            final char[] buffer = new char[1024];
            final StringBuilder stringBuilder = new StringBuilder();
            int readChars;
            while ((readChars = reader.read(buffer)) != -1) {
                stringBuilder.append(buffer, 0, readChars);
            }

            return stringBuilder.toString();
        }
    }

}
