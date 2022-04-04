package io.bdrc.edit.helpers;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

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
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.seaborne.patch.RDFPatchOps;
import org.seaborne.patch.changes.RDFChangesCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.ops.CommonsRead;
import io.bdrc.edit.commons.ops.CommonsValidate;
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
    
    public static String getPatchStr(final Model original, final Model modified, final Resource graph) {
        final RDFChangesCollector cc = new RDFChangesCollector();
        final Model minus = original.difference(modified);
        StmtIterator sti = minus.listStatements();
        while (sti.hasNext()) {
            final Statement st = sti.next();
            cc.delete(graph.asNode(), st.getSubject().asNode(), st.getPredicate().asNode(), st.getObject().asNode());
        }
        final Model plus = modified.difference(original);
        sti = plus.listStatements();
        while (sti.hasNext()) {
            final Statement st = sti.next();
            cc.add(graph.asNode(), st.getSubject().asNode(), st.getPredicate().asNode(), st.getObject().asNode());
        }
        return RDFPatchOps.str(cc.getRDFPatch());
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
            log.debug("patch: {}", getPatchStr(focusedOriginal, newFocusModel, ResourceFactory.createResource(graphUri)));
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
    
    public static final Property admAbout = ResourceFactory.createProperty(Models.ADM, "adminAbout");
    public static final Property logEntry = ResourceFactory.createProperty(Models.ADM, "logEntry");
    public static final Property logDate = ResourceFactory.createProperty(Models.ADM, "logDate");
    public static final Property logWho = ResourceFactory.createProperty(Models.ADM, "logWho");
    public static final Property logMessage = ResourceFactory.createProperty(Models.ADM, "logMessage");
    public static final Property InitialDataCreation = ResourceFactory.createProperty(Models.ADM, "InitialDataCreation");
    public static final Property UpdateData = ResourceFactory.createProperty(Models.ADM, "UpdateData");
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
        m.add(lg, logMessage, m.createLiteral(changeMessage[0], changeMessage[1]));
    }

}
