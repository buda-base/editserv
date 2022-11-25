package io.bdrc.edit.helpers;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.ops.CommonsRead;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.controllers.SyncNotificationController;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.jena.sttl.STriGWriter;
import io.bdrc.libraries.Models;

public class ModelUtils {
    
    public static Logger log = LoggerFactory.getLogger(ModelUtils.class);

    public static String modelToTtl(final Model m) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        m.write(baos, "TTL");
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }
    
    public final static char[] symbols = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    
    public static String randomId(final int len) {
        final Random random = ThreadLocalRandom.current();
        final char[] buf = new char[len];
        for (int idx = 0; idx < len; ++idx)
            buf[idx] = symbols[random.nextInt(symbols.length)];
        return new String(buf);
    }
    
    public static Resource newSubject(final Model m, final String prefix) throws EditException {
        int i = 0;
        while (i < 10) {
            final Resource candidate = m.createResource(prefix+randomId(12));
            if (!m.contains(candidate, null))
                return candidate;
        }
        throw new EditException("cannot find free subject id!!");
    }
    
    public static String datasetToTrig(final Dataset ds) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new STriGWriter().write(baos, ds.asDatasetGraph(), EditConfig.prefix.getPrefixMap(), null, Helpers.createWriterContext());
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static Resource getMainGraph(final Dataset ds) {
        // TODO: add unit tests
        final Iterator<String> graphUrisIt = ds.listNames();
        final List<String> graphUris = new ArrayList<String>();
        while (graphUrisIt.hasNext()) {
            graphUris.add(graphUrisIt.next());
        }
        if (graphUris.size() == 0)
            return null;
        if (graphUris.size() == 1)
            return ResourceFactory.createResource(graphUris.get(0));
        Resource res = null;
        // the only valid case with two graphs in a dataset is the user profile
        // in that case the graphs URIs will be like:
        // bda:U0ESXXX - bda = http://purl.bdrc.io/admindata/
        // bdgu:U0ESXXX - bdgu = http://purl.bdrc.io/graph-nc/user/
        // bdgup: U0ESXXX - bdgup = http://purl.bdrc.io/graph-nc/user-private/
        // in which case we take the bdgup one, and will rederive the public one
        if (graphUris.size() == 3) {
            for (final String uri : graphUris) {
                if (uri.startsWith(EditConstants.BDGUP)) {
                    return ResourceFactory.createResource(uri);
                }
            }
        }
        return res;
    }
    
    public static Model getMainModel(final Dataset ds) {
        final Resource r = getMainGraph(ds);
        if (r == null)
            return null;
        return ds.getNamedModel(r.getURI());
    }
    
    public static Model getPublicUserModel(final Dataset ds) {
        final Iterator<String> graphUrisIt = ds.listNames();
        Model res = ModelFactory.createDefaultModel();
        while (graphUrisIt.hasNext()) {
            final String graphUri = graphUrisIt.next();
            if (graphUri.startsWith(EditConstants.BDA) || graphUri.startsWith(EditConstants.BDGU)) {
                res.add(ds.getNamedModel(graphUri));
            }
        }
        return res;
    }
    
    public static Resource getPublicUserGraph(final Dataset ds) {
        final Iterator<String> graphUrisIt = ds.listNames();
        while (graphUrisIt.hasNext()) {
            final String graphUri = graphUrisIt.next();
            if (graphUri.startsWith(EditConstants.BDGU)) {
                return ResourceFactory.createResource(graphUri);
            }
        }
        return null;
    }
    
    public static Resource getPrivateUserGraph(final Dataset ds) {
        final Iterator<String> graphUrisIt = ds.listNames();
        while (graphUrisIt.hasNext()) {
            final String graphUri = graphUrisIt.next();
            if (graphUri.startsWith(EditConstants.BDGUP)) {
                return ResourceFactory.createResource(graphUri);
            }
        }
        return null;
    }
    
    public static Model getPrivateUserModel(final Dataset ds) {
        final Iterator<String> graphUrisIt = ds.listNames();
        Model res = ModelFactory.createDefaultModel();
        while (graphUrisIt.hasNext()) {
            final String graphUri = graphUrisIt.next();
            if (graphUri.startsWith(EditConstants.BDA) || graphUri.startsWith(EditConstants.BDGUP)) {
                res.add(ds.getNamedModel(graphUri));
            }
        }
        return res;
    }
    
    public static final class ChangeInfo {
        public Model minus;
        public Model plus;
    }
    
    public static final List<Property> toCopy = Arrays.asList(SKOS.prefLabel, SKOS.altLabel, RDF.type);
    
    public static Model publicUserModelFromPrivate(final Model m, final Resource r) {
        final Model res = ModelFactory.createDefaultModel();
        for (final Property p : toCopy) {
            final StmtIterator it = m.listStatements(null, p, (RDFNode) null);
            while (it.hasNext()) {
                res.add(it.next());
            }
        }
        return res;
    }
    
    public static boolean isUser(final Resource r) {
        return r.getURI().startsWith(Models.BDU);
    }
    
    // changes completeSet with a new model (later can return plus and minus)
    public static void mergeModel(final Dataset completeSet, final String graphUri, Model newFocusModel, final Resource r, final Resource shape, final String repoLname, final String[] changeMessage, final Resource user) throws EditException {
        final boolean isUser = repoLname.equals("GR0100");
        log.info("merging new model for {}", r);
        final Model original = completeSet.getNamedModel(graphUri);
        if (log.isDebugEnabled())
            log.debug("original model is {}", modelToTtl(original));
        final Model focusedOriginal = CommonsRead.getFocusGraph(original, r, shape);
        if (log.isDebugEnabled())
            log.debug("focused original model is {}", modelToTtl(focusedOriginal));
        // don't validate commit for users
        //if (!isUser && !CommonsValidate.validateCommit(newFocusModel, focusedOriginal, r)) {
        //    throw new ModuleException(500, "Version conflict while trying to save " + r.getURI());
        //}
        final Model outOfFocusOriginal = original.difference(focusedOriginal);
        if (log.isDebugEnabled())
            log.debug("out of focused original model is {}", modelToTtl(outOfFocusOriginal));
        final Model resModel = outOfFocusOriginal.add(newFocusModel);
        if (log.isDebugEnabled()) {
            log.debug("result of the merge is {}", modelToTtl(resModel));
            //log.debug("patch: {}", getPatchStr(focusedOriginal, newFocusModel, ResourceFactory.createResource(graphUri)));
        }
        // add a simple log entry, except in the case of users
        if (!isUser(r))
            ModelUtils.addSimpleLogEntry(resModel, r, user, changeMessage, false);
        completeSet.replaceNamedModel(graphUri, resModel);
        if (isUser) {
            // derive the public model and replace it
            final Model publicModel = publicUserModelFromPrivate(resModel, r);
            final Resource publicGraph = ModelUtils.getPublicUserGraph(completeSet);
            completeSet.replaceNamedModel(publicGraph.getURI(), publicModel);
            // no need to change the admin model
        }
        // TODO: option to also return removed / added symmetric and inverse triples in other models
    }
    
    // given a model from the user, the main resource and a shape, return a valid focus graph
    // throw ModuleException if model is invalid
    public static Model getValidFocusGraph(final Model inModel, final Resource r, final Resource shape) throws EditException {
        final Model inFocusGraph = CommonsRead.getFocusGraph(inModel, r, shape);
        if (!CommonsValidate.validateFocusing(inModel, inFocusGraph)) {
            Model diff = inModel.difference(inFocusGraph);
            log.error("Focus graph is not the same size as initial graph, difference is {}", ModelUtils.modelToTtl(diff));
            throw new EditException(400, "Focus graph is not the same size as initial graph");
        }
        if (!CommonsValidate.validateShacl(inFocusGraph, shape)) {
            throw new EditException(400, "Shacl did not validate, check logs");
        }
        if (!CommonsValidate.validateExtRIDs(inFocusGraph)) {
            throw new EditException(400, "Some external resources do not have a correct RID, check logs");
        }
        return inFocusGraph;
    }
    
    public static final Pattern simpleLangPattern = Pattern.compile("^[a-z\\-]+$");
    
    public static final Property admAbout = ResourceFactory.createProperty(Models.ADM, "adminAbout");
    public static final Property volumePagesTotal = ResourceFactory.createProperty(Models.BDO, "volumePagesTotal");
    public static final Property logEntry = ResourceFactory.createProperty(Models.ADM, "logEntry");
    public static final Property logDate = ResourceFactory.createProperty(Models.ADM, "logDate");
    public static final Property logMethod = ResourceFactory.createProperty(Models.ADM, "logMethod");
    public static final Property logWho = ResourceFactory.createProperty(Models.ADM, "logWho");
    public static final Property logMessage = ResourceFactory.createProperty(Models.ADM, "logMessage");
    public static final Resource InitialDataCreation = ResourceFactory.createResource(Models.ADM + "InitialDataCreation");
    public static final Resource UpdateData = ResourceFactory.createResource(Models.ADM + "UpdateData");
    public static final Resource AdminData = ResourceFactory.createResource(Models.ADM + "AdminData");
    public static final Resource ImagesUpdated = ResourceFactory.createResource(Models.ADM + "ImagesUpdated");
    public static final Resource Synced = ResourceFactory.createResource(Models.ADM + "Synced");
    public static final Resource BatchMethod = ResourceFactory.createResource(Models.BDA + "BatchMethod");
    public static void addSimpleLogEntry(final Model m, final Resource r, final Resource user, final String[] changeMessage, final boolean creation) throws EditException {
        final ResIterator admIt = m.listSubjectsWithProperty(admAbout, r);
        if (!admIt.hasNext())
            throw new EditException(500, "can't find admin data for "+r.getURI());
        final Resource admData = admIt.next();
        final List<String> logEntryLocalNames = new ArrayList<String>();
        final StmtIterator lgIt = admData.listProperties(logEntry);
        while (lgIt.hasNext()) {
            logEntryLocalNames.add(lgIt.next().getResource().getLocalName());
        }
        if (creation && logEntryLocalNames.size() > 0)
            throw new EditException(500, "log entries already present while adding creation log entry for "+r.getURI());
        // get a random string that is not already present in the log entries
        final String lgLnamePrefix = "LG0"+r.getLocalName()+"_";
        String rand = RandomStringUtils.random(12, true, true).toUpperCase();
        int i = 0;
        while (i < 10) {
            if (!logEntryLocalNames.contains(lgLnamePrefix+rand))
                break;
            rand = RandomStringUtils.random(12, true, true).toUpperCase();
            i += 1;
        }
        final Resource lg = m.createResource(Models.BDA+lgLnamePrefix+rand);
        m.add(admData, logEntry, lg);
        m.add(lg, RDF.type, creation ? InitialDataCreation : UpdateData);
        if (user != null)
            m.add(lg, logWho, user);
        final String now = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT );
        m.add(lg, logDate, m.createTypedLiteral(now, XSDDatatype.XSDdateTime));
        if (!simpleLangPattern.matcher(changeMessage[1]).matches())
            throw new EditException(422, "invalid commit message lang tag "+changeMessage[1]);
        m.add(lg, logMessage, m.createLiteral(changeMessage[0], changeMessage[1]));
    }
    
    public static Resource findLogEntry(final Model m, final Resource admData) {
        final List<String> logEntryLocalNames = new ArrayList<String>();
        final StmtIterator lgIt = admData.listProperties(logEntry);
        while (lgIt.hasNext()) {
            logEntryLocalNames.add(lgIt.next().getResource().getLocalName());
        }
        // get a random string that is not already present in the log entries
        final String lgLnamePrefix = "LG0"+admData.getLocalName()+"_";
        String rand = RandomStringUtils.random(12, true, true).toUpperCase();
        int i = 0;
        while (i < 10) {
            if (!logEntryLocalNames.contains(lgLnamePrefix+rand))
                break;
            rand = RandomStringUtils.random(12, true, true).toUpperCase();
            i += 1;
        }
        return m.createResource(Models.BDA+lgLnamePrefix+rand);
    }
    
    public static void addSyncNotification(final Model m, final Resource w, final Map<String,SyncNotificationController.ImageGroupSyncInfo> iinfos, final Resource user, final String logDateStr) throws EditException {
        final ResIterator wadmIt = m.listSubjectsWithProperty(admAbout, w);
        if (!wadmIt.hasNext())
            throw new EditException("can't find admin data for "+ w);
        final Resource wadmData = wadmIt.next();
        final Resource lg = findLogEntry(m, wadmData);
        for (final Entry<String,SyncNotificationController.ImageGroupSyncInfo> igSyncInfo : iinfos.entrySet()) {
            final String igqname = igSyncInfo.getKey();
            if (!igqname.startsWith("bdr:"))
                continue;
            final Resource ig = ResourceFactory.createResource(Models.BDR + igqname.substring(4));
            final int nbPagesTotal = igSyncInfo.getValue().pages_total;
            final ResIterator admIt = m.listSubjectsWithProperty(admAbout, ig);
            final Resource admData;
            if (!admIt.hasNext()) {
                log.info("create admin data for {}", ig);
                admData = m.createResource(Models.BDA+ig.getLocalName());
                m.add(admData, RDF.type, AdminData);
                m.add(admData, admAbout, ig);
            } else {
                admData = admIt.next();
            }
            m.add(admData, logEntry, lg);
            Resource lgtype = Synced;
            final StmtIterator lgitr = admData.listProperties(logEntry);
            while (lgitr.hasNext()) {
                final Resource otherLg = lgitr.next().getResource();
                if (otherLg.hasProperty(RDF.type, Synced))
                    lgtype = ImagesUpdated;
            }
            m.add(lg, RDF.type, lgtype);
            if (user != null)
                m.add(lg, logWho, user);
            m.add(lg, logDate, m.createTypedLiteral(logDateStr, XSDDatatype.XSDdateTime));
            m.add(lg, logMessage, m.createLiteral("Updated total pages", "en"));
            m.add(lg, logMethod, BatchMethod);
            m.removeAll(ig, volumePagesTotal, null);
            m.add(ig, volumePagesTotal, m.createTypedLiteral(nbPagesTotal, XSDDatatype.XSDinteger));
        }
    }

    public static void addSyncNotification(final Model m, final Resource ig, final int nbPagesTotal, final Resource user) throws EditException {
        final ResIterator admIt = m.listSubjectsWithProperty(admAbout, ig);
        final Resource admData;
        if (!admIt.hasNext()) {
        	log.info("create admin data for {}", ig);
        	admData = m.createResource(Models.BDA+ig.getLocalName());
        	m.add(admData, RDF.type, AdminData);
        	m.add(admData, admAbout, ig);
        } else {
        	admData = admIt.next();
        }
        boolean firstSync = true;
        final List<String> logEntryLocalNames = new ArrayList<String>();
        final StmtIterator lgIt = admData.listProperties(logEntry);
        while (lgIt.hasNext()) {
            final Resource otherLg = lgIt.next().getResource();
            logEntryLocalNames.add(otherLg.getLocalName());
            firstSync = firstSync && !otherLg.hasProperty(RDF.type, Synced);
        }
        // get a random string that is not already present in the log entries
        final String lgLnamePrefix = "LG0"+ig.getLocalName()+"_";
        String rand = RandomStringUtils.random(12, true, true).toUpperCase();
        int i = 0;
        while (i < 10) {
            if (!logEntryLocalNames.contains(lgLnamePrefix+rand))
                break;
            rand = RandomStringUtils.random(12, true, true).toUpperCase();
            i += 1;
        }
        final Resource lg = m.createResource(Models.BDA+lgLnamePrefix+rand);
        m.add(admData, logEntry, lg);
        final Resource lgtype = firstSync ? Synced : ImagesUpdated; 
        m.add(lg, RDF.type, lgtype);
        if (user != null)
            m.add(lg, logWho, user);
        final String now = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT );
        m.add(lg, logDate, m.createTypedLiteral(now, XSDDatatype.XSDdateTime));
        m.add(lg, logMessage, m.createLiteral("Updated total pages", "en"));
        m.add(lg, logMethod, BatchMethod);
        m.removeAll(ig, volumePagesTotal, null);
        m.add(ig, volumePagesTotal, m.createTypedLiteral(nbPagesTotal, XSDDatatype.XSDinteger));
    }
    
}
