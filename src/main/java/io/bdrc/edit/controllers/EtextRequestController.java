package io.bdrc.edit.controllers;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.bdrc.auth.AccessInfo;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.edit.user.BudaUser;
import io.bdrc.libraries.Models;

@Controller
@RequestMapping("/")
public class EtextRequestController {
    
    public final static Logger log = LoggerFactory.getLogger(EtextRequestController.class.getName());

    public static List<String> getVolumesFromModel(final Model iim, final Resource etextInstance, boolean onlynonsynced) throws IOException, EditException {
        // TODO: implement onlynonsynced
        final List<String> res = new ArrayList<>();
        final String queryStr = "select distinct ?ve where  {  <" + etextInstance.getURI() + "> <"+EditConstants.BDO+"instanceHasVolume> ?ve . }";
        final Query query = QueryFactory.create(queryStr);
        final QueryExecution qexec = QueryExecutionFactory.create(query, iim);
        final ResultSet rs = qexec.execSelect();
        while (rs.hasNext()) {
            final QuerySolution qs = rs.next();
            res.add(qs.getResource("ve").getLocalName());
        }
        return res;
    }
    
    public static final Resource EtextRequested = ResourceFactory.createResource(EditConstants.ADM+"EtextRequested");
    public static final Resource EtextVolume = ResourceFactory.createResource(EditConstants.BDO+"EtextVolume");
    
    public static int ensureNVolumes(final Model iim, final Resource etextInstance, final int nbvols, final Resource user, final String idPrefix, final String now, final Resource lg) throws EditException {
        final String queryStr = "select distinct (count(?ve) as ?nbvols) (max(?venum) as ?maxvnum) where  {  <" + etextInstance.getURI() + "> <"+EditConstants.BDO+"instanceHasVolume> ?ve . ?ve <"+EditConstants.BDO+"volumeNumber> ?venum . }";
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
        final List<String> volumeIds = RIDController.getNextIDs("VE"+idPrefix, nb_vols_to_create, "VE"+idPrefix);
        iim.removeAll(etextInstance, ScanRequestController.numberOfVolumes, (RDFNode) null);
        iim.add(etextInstance, ScanRequestController.numberOfVolumes, iim.createTypedLiteral(nbvols, XSDDatatype.XSDinteger));
        lg.addProperty(RDF.type, EtextRequested);
        for (int i = 0 ; i < nb_vols_to_create ; i++ ) {
            final Resource vol = iim.createResource(EditConstants.BDR+volumeIds.get(i));
            iim.add(vol, RDF.type, EtextVolume);
            iim.add(vol, ScanRequestController.volumeNumber, iim.createTypedLiteral(existing_maxvnum+i+1, XSDDatatype.XSDinteger));
            iim.add(etextInstance, ScanRequestController.instanceHasVolume, vol);
            final Resource volAdm = iim.createResource(EditConstants.BDA+volumeIds.get(i));
            iim.add(volAdm, RDF.type, ModelUtils.AdminData);
            iim.add(volAdm, ModelUtils.admAbout, vol);
            iim.add(volAdm, ModelUtils.logEntry, lg);
        }
        return nb_vols_to_create;
    }
    
    public static void sendScanRequest(final ZipOutputStream zout, final List<String> ves, final String ielname) throws IOException {
        zout.putNextEntry(new ZipEntry(ielname+"/"));
        zout.putNextEntry(new ZipEntry(ielname+"/sources/"));
        zout.putNextEntry(new ZipEntry(ielname+"/toprocess/"));
        for (final String ve : ves) {
            final String dirname = ielname+"/toprocess/"+ielname+"-"+ve+"/";
            zout.putNextEntry(new ZipEntry(dirname));
        }
    }
    
    public static final Property etextInfo = ResourceFactory.createProperty(Models.BDO, "etextInfo");
    public static final Resource EtextInstance = ResourceFactory.createResource(Models.BDO + "EtextInstance");
    
    public static void ensureScanInfo(final Model m, final Resource etextInstance, final String scanInfoStr, final String lang, final Resource user, final String now) throws EditException {
        m.removeAll(etextInstance, etextInfo, (RDFNode) null);
        m.add(etextInstance, etextInfo, m.createLiteral(scanInfoStr, lang));
    }
    
    public static void addReproduction(final Model im, final Resource instance, final Resource etextInstance, final Resource user, final String now) {
        im.add(instance, ScanRequestController.instanceHasReproduction, etextInstance);
    }
    
    public static void addLogEntry(final Model im, final Resource instance, final Resource user, final String now, final int nb_volumes_created, final Resource lg) throws EditException {
        if (nb_volumes_created > 0)
            lg.addProperty(RDF.type, EtextRequested);
        else
            lg.addProperty(RDF.type, ModelUtils.UpdateData);
        lg.addProperty(ModelUtils.logMethod, ModelUtils.BatchMethod);
        lg.addProperty(ModelUtils.logDate, im.createTypedLiteral(now, XSDDatatype.XSDdateTime));
        lg.addProperty(ModelUtils.logWho, user);
        if (nb_volumes_created == 0)
            lg.addProperty(ModelUtils.logMessage, im.createLiteral("changes (from etext request tool)", "en"));
        else
            lg.addProperty(ModelUtils.logMessage, im.createLiteral("add "+nb_volumes_created+" volumes (from etext request tool)", "en"));
        final Resource adm = im.createResource(EditConstants.BDA+instance.getLocalName());
        im.add(adm, ModelUtils.logEntry, lg);
    }
    
    public static void addInitialImageInstanceData(final Model eim, final Resource etextinstance, final Resource user, final String now, final String access_qname, Boolean ric, final Resource lg) throws EditException {
        lg.addProperty(RDF.type, ModelUtils.InitialDataCreation);
        lg.addProperty(ModelUtils.logMethod, ModelUtils.BatchMethod);
        lg.addProperty(ModelUtils.logDate, eim.createTypedLiteral(now, XSDDatatype.XSDdateTime));
        lg.addProperty(ModelUtils.logWho, user);
        lg.addProperty(ModelUtils.logMessage, eim.createLiteral("create through etext request tool", "en"));
        final Resource adm = eim.createResource(EditConstants.BDA+etextinstance.getLocalName());
        eim.add(adm, ModelUtils.logEntry, lg);
        eim.add(adm, ModelUtils.admAbout, etextinstance);
        eim.add(adm, RDF.type, ModelUtils.AdminData);
        eim.add(etextinstance, RDF.type, EtextInstance);
        eim.add(adm, ScanRequestController.status, ScanRequestController.statusReleased);
        eim.add(adm, ScanRequestController.access, eim.createResource(EditConstants.BDA+access_qname.substring(4)));
        if (ric == null)
            ric = false;
        eim.add(adm, ScanRequestController.restrictedInChina, eim.createTypedLiteral(ric));
    }
    
    // returns true if access has been changed
    private boolean ensureAccess(final Model eim, final Resource etextInstance, final String access_qname) {
        final Resource access_value = eim.createResource(EditConstants.BDA + access_qname.substring(4));
        final Resource adm = eim.createResource(EditConstants.BDA+etextInstance.getLocalName());
        if (eim.contains(adm, ScanRequestController.access, access_value))
            return false;
        eim.removeAll(adm, ScanRequestController.access, (RDFNode) null);
        eim.add(adm, ScanRequestController.access, access_value);
        return true;
    }
    
    @GetMapping(value = "/etextrequest", produces="application/zip")
    public ResponseEntity<StreamingResponseBody> getLatestID(
            @RequestParam(value = "onlynonsynced", required = false, defaultValue = "false") Boolean onlynonsynced, 
            @RequestParam(value = "IDPrefix", required = false) final String IDPrefix,
            @RequestParam(value = "nbvols", required = false, defaultValue = "0") Integer nbvols,
            @RequestParam(value = "etextInfo", required = false) final String etextinfo,
            @RequestParam(value = "etextInfo_lang", required = false) final String etextinfo_lang,
            @RequestParam(value = "ric", required = false) Boolean ric,
            @RequestParam(value = "access", required = false) final String access_qname,
            @RequestParam(value = "instance", required = false) String instance_qname,
            @RequestParam(value = "einstance", required = false) String einstance_qname,
            HttpServletRequest req, HttpServletResponse response) throws IOException, EditException, GitAPIException {
        if (einstance_qname == null || einstance_qname.isEmpty()) {
            if (instance_qname == null || !instance_qname.startsWith("bdr:MW")) {
                log.error("called with invalid instance argument "+instance_qname);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(null);
            }
            einstance_qname = "bdr:IE"+instance_qname.substring(6);
        } else if (!einstance_qname.startsWith("bdr:IE")) {
            log.error("called with invalid iinstance argument "+einstance_qname);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(null);
        }
        if (access_qname != null && !access_qname.startsWith("bda:Access")) {
            log.error("called with invalid access argument "+access_qname);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(null);
        }
        final Resource etextInstance = ResourceFactory.createResource(EditConstants.BDR+einstance_qname.substring(4));
        Resource user = null;
        if (EditConfig.useAuth) {
            AccessInfo acc = (AccessInfo) req.getAttribute("access");
            if (etextInstance != null) {
                try {
                    MainEditController.ensureAccess(acc, etextInstance);
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
        final GitInfo gi = CommonsGit.gitInfoForResource(etextInstance, true);
        GitInfo gi_mw = null;
        Resource instance = null;
        final Model eim;
        final String now = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        boolean needsSaving = false;
        boolean creating = false;
        final Resource lg;
        if (gi.ds == null) {
            creating = true;
            if (instance_qname == null || !instance_qname.startsWith("bdr:MW"))
                throw new EditException(404, "valid instance qname required (ex: bdr:MW123) but got "+instance_qname);
            if (etextinfo == null || etextinfo.isEmpty() || access_qname == null || access_qname.isEmpty())
                throw new EditException(404, "scan info and access mandatory for the creation of a new scan request");
            RIDController.reserveFullIdSimple(einstance_qname.substring(4));
            instance = ResourceFactory.createResource(EditConstants.BDR+instance_qname.substring(4));
            gi_mw = CommonsGit.gitInfoForResource(instance, true);
            if (gi_mw.ds == null)
                throw new EditException(404, "cannot find "+instance.getURI());
            gi.ds = CommonsGit.createDatasetForNewResource(ModelFactory.createDefaultModel(), etextInstance);
            eim = ModelUtils.getMainModel(gi.ds);
            lg = ModelUtils.newSubject(eim, EditConstants.BDR+"LG0"+etextInstance.getLocalName()+"_");
            addInitialImageInstanceData(eim, etextInstance, user, now, access_qname, ric, lg);
            final Model im = ModelUtils.getMainModel(gi_mw.ds);
            addReproduction(im, instance, etextInstance, user, now);
            needsSaving = true;
        } else {
            eim = ModelUtils.getMainModel(gi.ds);
            lg = ModelUtils.newSubject(eim, EditConstants.BDR+"LG0"+etextInstance.getLocalName()+"_");
        }
        if (etextinfo != null) {
            log.info("change scan info");
            ensureScanInfo(eim, etextInstance, etextinfo, etextinfo_lang, user, now);
            needsSaving = true;
        }
        int nb_vols_created = 0;
        if (nbvols > 0) {
            nb_vols_created = ensureNVolumes(eim, etextInstance, nbvols, user, IDPrefix, now, lg);
            if (nb_vols_created > 0)
                needsSaving = true;
        }
        if (!creating && access_qname != null) {
            if (ensureAccess(eim, etextInstance, access_qname))
                needsSaving = true;
        }
        if (needsSaving) {
            if (!creating)
                addLogEntry(eim, etextInstance, user, now, nb_vols_created, lg);
            CommonsGit.commitAndPush(gi, "["+user.getLocalName()+"]"+"["+etextInstance.getLocalName()+"] generate etext request");
            FusekiWriteHelpers.putDataset(gi);
            if (gi_mw != null) {
                CommonsGit.commitAndPush(gi_mw, "["+user.getLocalName()+"]"+"["+instance.getLocalName()+"] generate etext request");
                FusekiWriteHelpers.putDataset(gi_mw);
            }
        }
        final List<String> volInfos = getVolumesFromModel(eim, etextInstance, onlynonsynced);
        if (volInfos.isEmpty()) {
            log.error("couldn't find volumes for {}", etextInstance.getLocalName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }
        return ResponseEntity
                .ok()
                .header("Content-Disposition", "attachment; filename=\"etext-dirs-"+etextInstance.getLocalName()+".zip\"")
                .body(out -> {
                    final ZipOutputStream zipOutputStream = new ZipOutputStream(out);
                    sendScanRequest(zipOutputStream, volInfos, etextInstance.getLocalName());
                    zipOutputStream.close();
                });
    }
    
}
