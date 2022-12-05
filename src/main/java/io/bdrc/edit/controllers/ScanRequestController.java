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
    
    public static int ensureNVolumes(final Model iim, final Resource imageInstance, final int nbvols, final Resource user, final String idPrefix, final String now, final Resource lg) throws EditException {
        final String queryStr = "select distinct (count(?i) as ?nbvols) (max(?inum) as ?maxvnum) where  {  <" + imageInstance.getURI() + "> <"+EditConstants.BDO+"instanceHasVolume> ?i . ?i <"+EditConstants.BDO+"volumeNumber> ?inum . }";
        final Query query = QueryFactory.create(queryStr);
        final QueryExecution qexec = QueryExecutionFactory.create(query, iim);
        final ResultSet rs = qexec.execSelect();
        int existing_nbvols = 0;
        int existing_maxvnum = 0;
        if (rs.hasNext()) {
            final QuerySolution qs = rs.next();
            log.error(qs.toString());
            existing_nbvols = qs.getLiteral("nbvols").getInt();
            if (existing_nbvols != 0) {
                if (qs.contains("maxvnum")) {
                    existing_maxvnum = qs.getLiteral("maxvnum").getInt();
                } else {
                    throw new EditException(500, "cannot determine existing max volume number");
                }
            }
        }
        final int nb_vols_to_create = nbvols - existing_nbvols;
        log.error("need to create {} volumes", nb_vols_to_create);
        if (nb_vols_to_create < 1)
            return 0;
        final List<String> volumeIds = RIDController.getNextIDs("I"+idPrefix, nb_vols_to_create);
        iim.removeAll(imageInstance, numberOfVolumes, (RDFNode) null);
        iim.add(imageInstance, numberOfVolumes, iim.createTypedLiteral(nbvols, XSDDatatype.XSDinteger));
        lg.addProperty(RDF.type, ScanRequested);
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
    public static final Property numberOfVolumes = ResourceFactory.createProperty(Models.BDO, "numberOfVolumes");
    public static final Property status = ResourceFactory.createProperty(Models.ADM, "status");
    public static final Property restrictedInChina = ResourceFactory.createProperty(Models.ADM, "restrictedInChina");
    public static final Property access = ResourceFactory.createProperty(Models.ADM, "access");
    public static final Resource statusReleased = ResourceFactory.createResource(Models.BDA + "StatusReleased");
    public static final Property instanceHasReproduction = ResourceFactory.createProperty(Models.BDO, "instanceHasReproduction");
    public static final Resource ImageInstance = ResourceFactory.createResource(Models.BDO + "ImageInstance");
    
    public static void ensureScanInfo(final Model m, final Resource imageInstance, final String scanInfoStr, final String lang, final Resource user, final String now) throws EditException {
        m.removeAll(imageInstance, scanInfo, (RDFNode) null);
        m.add(imageInstance, scanInfo, m.createLiteral(scanInfoStr, lang));
    }
    
    public static void addReproduction(final Model im, final Resource instance, final Resource imageInstance, final Resource user, final String now) {
        im.add(instance, instanceHasReproduction, imageInstance);
    }
    
    public static void addLogEntry(final Model im, final Resource instance, final Resource user, final String now, final int nb_volumes_created, final Resource lg) throws EditException {
        if (nb_volumes_created > 0)
            lg.addProperty(RDF.type, ScanRequested);
        else
            lg.addProperty(RDF.type, ModelUtils.UpdateData);
        lg.addProperty(ModelUtils.logMethod, ModelUtils.BatchMethod);
        lg.addProperty(ModelUtils.logDate, im.createTypedLiteral(now, XSDDatatype.XSDdateTime));
        lg.addProperty(ModelUtils.logWho, user);
        if (nb_volumes_created == 0)
            lg.addProperty(ModelUtils.logMessage, im.createLiteral("changes (from scan request tool)", "en"));
        else
            lg.addProperty(ModelUtils.logMessage, im.createLiteral("add "+nb_volumes_created+" volumes (from scan request tool)", "en"));
        final Resource adm = im.createResource(EditConstants.BDA+instance.getLocalName());
        im.add(adm, ModelUtils.logEntry, lg);
    }
    
    public static void addInitialImageInstanceData(final Model iim, final Resource imageinstance, final Resource user, final String now, final String access_qname, Boolean ric, final Resource lg) throws EditException {
        lg.addProperty(RDF.type, ModelUtils.InitialDataCreation);
        lg.addProperty(ModelUtils.logMethod, ModelUtils.BatchMethod);
        lg.addProperty(ModelUtils.logDate, iim.createTypedLiteral(now, XSDDatatype.XSDdateTime));
        lg.addProperty(ModelUtils.logWho, user);
        lg.addProperty(ModelUtils.logMessage, iim.createLiteral("create through scan request tool", "en"));
        final Resource adm = iim.createResource(EditConstants.BDA+imageinstance.getLocalName());
        iim.add(adm, ModelUtils.logEntry, lg);
        iim.add(adm, ModelUtils.admAbout, imageinstance);
        iim.add(adm, RDF.type, ModelUtils.AdminData);
        iim.add(imageinstance, ModelUtils.admAbout, imageinstance);
        iim.add(imageinstance, RDF.type, ImageInstance);
        iim.add(adm, status, statusReleased);
        iim.add(adm, access, iim.createResource(EditConstants.BDA+access_qname.substring(4)));
        if (ric == null)
            ric = false;
        iim.add(adm, restrictedInChina, iim.createTypedLiteral(ric));
    }
    
    private boolean ensureAccess(Model iim, Resource imageInstance, String access_qname) {
        // TODO Auto-generated method stub
        return false;
    }
    
    @GetMapping(value = "/scanrequest", produces="application/zip")
    public ResponseEntity<StreamingResponseBody> getLatestID(
            @RequestParam(value = "onlynonsynced", required = false, defaultValue = "false") Boolean onlynonsynced, 
            @RequestParam(value = "IDPrefix", required = false) String IDPrefix,
            @RequestParam(value = "nbvols", required = false, defaultValue = "0") Integer nbvols,
            @RequestParam(value = "scaninfo", required = false) String scaninfo,
            @RequestParam(value = "scaninfo_lang", required = false) String scaninfo_lang,
            @RequestParam(value = "ric", required = false) Boolean ric,
            @RequestParam(value = "access", required = false) String access_qname,
            @RequestParam(value = "instance", required = false) String instance_qname,
            @RequestParam(value = "iinstance", required = false) String iinstance_qname,
            HttpServletRequest req, HttpServletResponse response) throws IOException, EditException, GitAPIException {
        if (iinstance_qname == null || iinstance_qname.isEmpty()) {
            if (instance_qname == null || !instance_qname.startsWith("bdr:MW")) {
                log.error("called with invalid instance argument "+instance_qname);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(null);
            }
            iinstance_qname = "bdr:"+instance_qname.substring(5);
        } else if (!iinstance_qname.startsWith("bdr:W")) {
            log.error("called with invalid iinstance argument "+iinstance_qname);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(null);
        }
        if (access_qname != null && !access_qname.startsWith("bda:Access")) {
            log.error("called with invalid access argument "+access_qname);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(null);
        }
        final Resource imageInstance = ResourceFactory.createResource(EditConstants.BDR+iinstance_qname.substring(4));
        Resource user = null;
        if (EditConfig.useAuth) {
        	AccessInfo acc = (AccessInfo) req.getAttribute("access");
        	if (imageInstance != null) {
                try {
                    MainEditController.ensureAccess(acc, imageInstance);
                } catch (EditException e) {
                    return ResponseEntity.status(e.getHttpStatus())
                            .body(null);
                }
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
            if (user == null) {
                throw new EditException(500, "couldn't get RDF profile");
            }
        } else {
            user = EditConstants.TEST_USER;
        }
        final GitInfo gi = CommonsGit.gitInfoForResource(imageInstance, true);
        GitInfo gi_mw = null;
        Resource instance = null;
        final Model iim;
        final String now = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        boolean needsSaving = false;
        boolean creating = false;
        final Resource lg;
        if (gi.ds == null) {
            creating = true;
            if (instance_qname == null || !instance_qname.startsWith("bdr:MW"))
                throw new EditException(404, "valid instance qname required (ex: bdr:MW123) but got "+instance_qname);
            if (scaninfo == null || scaninfo.isEmpty() || access_qname == null || access_qname.isEmpty())
                throw new EditException(404, "scan info and access mandatory for the creation of a new scan request");
            RIDController.reserveFullIdSimple(iinstance_qname.substring(4));
            instance = ResourceFactory.createResource(EditConstants.BDR+instance_qname.substring(4));
            gi_mw = CommonsGit.gitInfoForResource(instance, true);
            if (gi_mw.ds == null)
                throw new EditException(404, "cannot find "+instance.getURI());
            gi.ds = CommonsGit.createDatasetForNewResource(ModelFactory.createDefaultModel(), imageInstance);
            iim = ModelUtils.getMainModel(gi.ds);
            lg = ModelUtils.newSubject(iim, EditConstants.BDR+"LG0"+imageInstance.getLocalName()+"_");
            addInitialImageInstanceData(iim, imageInstance, user, now, access_qname, ric, lg);
            final Model im = ModelUtils.getMainModel(gi_mw.ds);
            addReproduction(im, instance, imageInstance, user, now);
            needsSaving = true;
        } else {
            iim = ModelUtils.getMainModel(gi.ds);
            lg = ModelUtils.newSubject(iim, EditConstants.BDR+"LG0"+imageInstance.getLocalName()+"_");
        }
        if (scaninfo != null) {
            log.info("change scan info");
            ensureScanInfo(iim, imageInstance, scaninfo, scaninfo_lang, user, now);
            needsSaving = true;
        }
        int nb_vols_created = 0;
        if (nbvols > 0) {
            nb_vols_created = ensureNVolumes(iim, imageInstance, nbvols, user, IDPrefix, now, lg);
            if (nb_vols_created > 0)
                needsSaving = true;
        }
        if (!creating && access_qname != null) {
            needsSaving = ensureAccess(iim, imageInstance, access_qname);
        }
        if (needsSaving) {
            if (!creating)
                addLogEntry(iim, imageInstance, user, now, nb_vols_created, lg);
            CommonsGit.commitAndPush(gi, "["+user.getLocalName()+"]"+"["+imageInstance.getLocalName()+"] generate scan request");
            if (gi_mw != null) {
                CommonsGit.commitAndPush(gi_mw, "["+user.getLocalName()+"]"+"["+instance.getLocalName()+"] generate scan request");
            }
        }
        final List<VolInfo> volInfos = getVolumesFromModel(iim, imageInstance, onlynonsynced);
        if (volInfos.isEmpty()) {
            log.error("couldn't find volumes for {}", imageInstance.getLocalName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }
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
