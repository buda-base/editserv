package io.bdrc.edit.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.auth.Access;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.ModuleException;

@Controller
@RequestMapping("/")
public class ScanRequestController {

    public static byte[] firstImage;
    public static byte[] secondImage;
    
    public final static Logger log = LoggerFactory.getLogger(RIDController.class.getName());
    
    public static void init() {
        final ClassLoader classLoader = RIDController.class.getClassLoader();
        InputStream is = classLoader.getResourceAsStream("1.tif");
        try {
            firstImage = IOUtils.toByteArray(is);
            is = classLoader.getResourceAsStream("2.tif");
            secondImage = IOUtils.toByteArray(is);
        } catch (IOException e) {
            log.error("can't read static image files for scanrequest", e);
        }
    }
    
    public static final class VolInfo {
        String lname;
        int volumePagesTbrcIntro = 0;
        int volumePagesTotal = 0;
    }
    
    // reading this from git is less efficient but also more authoritative
    // since efficiency is not an issue, we use this
    public static List<VolInfo> getVolumesFromGit(final Resource imageInstance, boolean onlynonsyncedB) throws IOException, ModuleException {
        GitInfo gi = CommonsGit.gitInfoForResource(imageInstance, true);
        List<VolInfo> res = new ArrayList<>();
        String queryStr = "select distinct ?i ?nbt ?nbi where  {  <" + imageInstance.getURI() + "> <"+EditConstants.BDO+"instanceHasVolume> ?i . ?i <"+EditConstants.BDO+"volumePagesTotal> ?nbt ; <"+EditConstants.BDO+"volumePagesTbrcIntro> ?nbi . }";
        Query query = QueryFactory.create(queryStr);
        QueryExecution qexec = QueryExecutionFactory.create(query, ModelUtils.getMainModel(gi.ds));
        ResultSet rs = qexec.execSelect();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            final int imagesTotal = qs.getLiteral("nbt").getInt();
            if (onlynonsyncedB && imagesTotal > 2)
                continue;
            VolInfo vi = new VolInfo();
            vi.lname = qs.getResource("i").getLocalName();
            vi.volumePagesTbrcIntro = qs.getLiteral("nbi").getInt();
            vi.volumePagesTotal = imagesTotal;
            res.add(vi);
        }
        return res;
    }
    
    public static void sendScanRequest(final ZipOutputStream zout, final List<VolInfo> vis, final String wlname) throws IOException {
        zout.putNextEntry(new ZipEntry(wlname+"/"));
        zout.putNextEntry(new ZipEntry(wlname+"/images/"));
        for (final VolInfo vi : vis) {
            final String dirname = wlname+"/images/"+wlname+"-"+vi.lname+"/";
            zout.putNextEntry(new ZipEntry(dirname));
            if (vi.volumePagesTbrcIntro == 0)
                continue;
            ZipEntry entry = new ZipEntry(dirname+vi.lname+"0001.tif");
            entry.setSize(firstImage.length);
            zout.putNextEntry(entry);
            zout.write(firstImage);
            zout.closeEntry();
            entry = new ZipEntry(dirname+vi.lname+"0002.tif");
            entry.setSize(secondImage.length);
            zout.putNextEntry(entry);
            zout.write(secondImage);
            zout.closeEntry();
        }
    }
    
    @GetMapping(value = "/{qname}/scanrequest", produces="application/zip")
    public ResponseEntity<StreamingResponseBody> getLatestID(@RequestParam(value = "onlynonsynced", required = false) String onlynonsynced, 
            @PathVariable("qname") String qname, HttpServletRequest req, HttpServletResponse response) throws IOException, ModuleException {
        if (!qname.startsWith("bdr:W"))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(null);
        final Resource res = ResourceFactory.createResource(EditConstants.BDR+qname.substring(4));
        log.info("generate scan request zip file for {}", res);
        if (EditConfig.useAuth) {
            Access acc = (Access) req.getAttribute("access");
            try {
                MainEditController.ensureAccess(acc, res);
            } catch (ModuleException e) {
                return ResponseEntity.status(e.getHttpStatus())
                        .body(null);
            }
        }
        final boolean onlynonsyncedB = "true".equals(onlynonsynced);
        final List<VolInfo> volInfos = getVolumesFromGit(res, onlynonsyncedB);
        if (volInfos.isEmpty()) {
            log.error("couldn't find volumes for {}", res.getLocalName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }
        // TODO: add scan request change log to the resource
        return ResponseEntity
                .ok()
                .header("Content-Disposition", "attachment; filename=\"scan-dirs-"+res.getLocalName()+".zip\"")
                .body(out -> {
                    final ZipOutputStream zipOutputStream = new ZipOutputStream(out);
                    sendScanRequest(zipOutputStream, volInfos, res.getLocalName());
                    zipOutputStream.close();
                });
    }
    
}
