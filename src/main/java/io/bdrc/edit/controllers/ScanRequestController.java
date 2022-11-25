package io.bdrc.edit.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.jgit.api.errors.GitAPIException;
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

import io.bdrc.auth.AccessInfo;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.edit.user.BudaUser;
import io.bdrc.libraries.Models;

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
    }

    public static List<VolInfo> getVolumesFromModel(final Model iim, final Resource imageInstance, boolean onlynonsynced) throws IOException, EditException {
        final List<VolInfo> res = new ArrayList<>();
        final String queryStr = "select distinct ?i ?nbt ?nbi where  {  <" + imageInstance.getURI() + "> <"+EditConstants.BDO+"instanceHasVolume> ?i . ?i <"+EditConstants.BDO+"volumePagesTbrcIntro> ?nbi . OPTIONAL { ?i <"+EditConstants.BDO+"volumePagesTotal> ?nbt } }";
        final Query query = QueryFactory.create(queryStr);
        final QueryExecution qexec = QueryExecutionFactory.create(query, iim);
        final ResultSet rs = qexec.execSelect();
        while (rs.hasNext()) {
            final QuerySolution qs = rs.next();
            if (qs.contains("nbt")) {
                final int imagesTotal = qs.getLiteral("nbt").getInt();
                if (onlynonsynced && imagesTotal > 2)
                    continue;
            }
            final VolInfo vi = new VolInfo();
            vi.lname = qs.getResource("i").getLocalName();
            vi.volumePagesTbrcIntro = qs.getLiteral("nbi").getInt();
            res.add(vi);
        }
        return res;
    }
    
    public static final Resource ScanRequested = ResourceFactory.createResource(EditConstants.ADM+"ScanRequested");
    public static final Resource ImageGroup = ResourceFactory.createResource(EditConstants.BDO+"ImageGroup");
    public static final Property volumePagesTbrcIntro = ResourceFactory.createProperty(Models.BDO, "volumePagesTbrcIntro");
    public static final Property instanceHasVolume = ResourceFactory.createProperty(Models.BDO, "instanceHasVolume");
    public static final Property volumeNumber = ResourceFactory.createProperty(Models.BDO, "volumeNumber");
    
    public static int ensureNVolumes(final Model iim, final Resource imageInstance, final int nbvols, final Resource user, final String idPrefix) throws EditException {
        final String queryStr = "select distinct (count(?i) as ?nbvols) (max(?inum) as ?maxvnum) where  {  <" + imageInstance.getURI() + "> <"+EditConstants.BDO+"instanceHasVolume> ?i . ?i <"+EditConstants.BDO+"volumeNumber> ?inum . }";
        final Query query = QueryFactory.create(queryStr);
        final QueryExecution qexec = QueryExecutionFactory.create(query, iim);
        final ResultSet rs = qexec.execSelect();
        int existing_nbvols = 0;
        int existing_maxvnum = 0;
        if (rs.hasNext()) {
            final QuerySolution qs = rs.next();
            existing_nbvols = qs.getLiteral("nbvols").getInt();
            existing_maxvnum = qs.getLiteral("maxvum").getInt();
        }
        final int nb_vols_to_create = nbvols - existing_nbvols;
        if (nb_vols_to_create < 1)
            return 0;
        final List<String> volumeIds = RIDController.getNextIDs(idPrefix, nb_vols_to_create);
        final Resource lg = ModelUtils.newSubject(iim, EditConstants.BDR+"LG0"+imageInstance.getLocalName()+"_");
        lg.addProperty(RDF.type, ScanRequested);
        lg.addProperty(ModelUtils.logMethod, ModelUtils.BatchMethod);
        final String now = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT );
        lg.addProperty(ModelUtils.logDate, iim.createTypedLiteral(now, XSDDatatype.XSDdateTime));
        lg.addProperty(ModelUtils.logWho, user);
        for (int i = 0 ; i < nb_vols_to_create ; i++ ) {
            final Resource vol = iim.createResource(EditConstants.BDR+volumeIds.get(i));
            iim.add(vol, RDF.type, ImageGroup);
            iim.add(vol, volumePagesTbrcIntro, iim.createTypedLiteral(0, XSDDatatype.XSDinteger));
            iim.add(vol, volumeNumber, iim.createTypedLiteral(existing_maxvnum+i+1, XSDDatatype.XSDinteger));
            iim.add(imageInstance, instanceHasVolume, vol);
            final Resource volAdm = iim.createResource(EditConstants.BDA+volumeIds.get(i));
            iim.add(volAdm, RDF.type, ModelUtils.AdminData);
            iim.add(volAdm, ModelUtils.admAbout, vol);
            iim.add(volAdm, ModelUtils.logEntry, lg);
        }
        return nb_vols_to_create;
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
    
    public static final Property scanInfo = ResourceFactory.createProperty(Models.BDO, "scanInfo");
    public static final Resource ImageInstance = ResourceFactory.createResource(Models.BDO + "ImageInstance");
    
    public static void ensureScanInfo(final Model m, final Resource imageInstance, final String scanInfoStr, final String lang) {
        m.remove(imageInstance, scanInfo, (RDFNode) null);
        m.add(imageInstance, scanInfo, m.createLiteral(scanInfoStr, lang));
    }
    
    public static void initImageInstance(final Model m, final Resource imageInstance, final String scanInfoStr, final String lang) {
        m.add(imageInstance, RDF.type, ImageInstance);
    }
    
    @GetMapping(value = "/{qname}/scanrequest", produces="application/zip")
    public ResponseEntity<StreamingResponseBody> getLatestID(@RequestParam(value = "onlynonsynced", required = false, defaultValue = "false") Boolean onlynonsynced, 
            @RequestParam(value = "IDPrefix", required = false) String IDPrefix,
            @RequestParam(value = "nbvols", required = false, defaultValue = "0") Integer nbvols,
            @RequestParam(value = "scaninfo", required = false) String scaninfo,
            @RequestParam(value = "scaninfo_lang", required = false) String scaninfo_lang,
            @RequestParam(value = "instance", required = false) String instance_qname,
            @PathVariable("qname") String qname, HttpServletRequest req, HttpServletResponse response) throws IOException, EditException, GitAPIException {
        if (!qname.startsWith("bdr:W"))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(null);
        final Resource imageInstance = ResourceFactory.createResource(EditConstants.BDR+qname.substring(4));
        log.info("generate scan request zip file for {}", imageInstance);
        Resource user = null;
        if (EditConfig.useAuth) {
        	AccessInfo acc = (AccessInfo) req.getAttribute("access");
            try {
                MainEditController.ensureAccess(acc, imageInstance);
            } catch (EditException e) {
                return ResponseEntity.status(e.getHttpStatus())
                        .body(null);
            }
            String authId = acc.getId();
            if (authId == null) {
                log.error("couldn't find authId for {}"+acc.toString());
                throw new EditException(500, "couldn't find authId");
            }
            final String auth0Id = authId.substring(authId.lastIndexOf("|") + 1);
            try {
                user = BudaUser.getRdfProfile(auth0Id);
            } catch (IOException e) {
                throw new EditException(500, "couldn't get RDF profile", e);
            }
        }
        final GitInfo gi = CommonsGit.gitInfoForResource(imageInstance, true);
        final Model iim;
        boolean needsSaving = false;
        if (gi.ds == null) {
            iim = ModelFactory.createDefaultModel();
            CommonsGit.createDatasetForNewResource(iim, imageInstance);
            needsSaving = true;
            
        } else {
            iim = ModelUtils.getMainModel(gi.ds);
        }
        if (scaninfo != null) {
            ensureScanInfo(iim, imageInstance, scaninfo, scaninfo_lang);
            needsSaving = true;
        }
        if (nbvols > 0) {
            int nb_vols_created = ensureNVolumes(iim, imageInstance, nbvols, user, IDPrefix);
            if (nb_vols_created > 0)
                needsSaving = true;
        }
        if (needsSaving) {
            CommonsGit.commitAndPush(gi, "["+user.getLocalName()+"]"+"["+imageInstance.getLocalName()+"] generate scan requests");
        }
        final List<VolInfo> volInfos = getVolumesFromModel(iim, imageInstance, onlynonsynced);
        if (volInfos.isEmpty()) {
            log.error("couldn't find volumes for {}", imageInstance.getLocalName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }
        
        // TODO: add scan request change log to the resource
        return ResponseEntity
                .ok()
                .header("Content-Disposition", "attachment; filename=\"scan-dirs-"+imageInstance.getLocalName()+".zip\"")
                .body(out -> {
                    final ZipOutputStream zipOutputStream = new ZipOutputStream(out);
                    sendScanRequest(zipOutputStream, volInfos, imageInstance.getLocalName());
                    zipOutputStream.close();
                });
    }
    
}
