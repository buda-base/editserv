package io.bdrc.edit.helpers;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsRead;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.txn.exceptions.VersionConflictException;
import io.bdrc.jena.sttl.STriGWriter;

public class ModelUtils {
    
    public static Logger log = LoggerFactory.getLogger(ModelUtils.class);

    public static String modelToTtl(final Model m) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        m.write(baos, "TTL");
        return baos.toString(StandardCharsets.UTF_8);
    }
    
    public static String datasetToTrig(final Dataset ds) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new STriGWriter().write(baos, ds.asDatasetGraph(), EditConfig.prefix.getPrefixMap(), null, Helpers.createWriterContext());
        return baos.toString(StandardCharsets.UTF_8);
    }
    
    public static Set<Statement> ModelToSet(Model m) {
        StmtIterator st = m.listStatements();
        List<Statement> stList = IteratorUtils.toList(st);
        return new HashSet<>(stList);
    }

    // All the statements that are in Model A that don't exist in Model B
    public static Set<Statement> ModelComplementAsSet(Model A, Model B) {
        Model diff = A.difference(B);
        return diff.listStatements().toSet();
    }

    public static Set<Statement> mergeModelAsSet(Set<Statement> A, Set<Statement> B) {
        Set<Statement> unionSet = new HashSet<>(A);
        unionSet.addAll(B);
        return unionSet;
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
                if (uri.startsWith("http://purl.bdrc.io/graph-nc/user-private/")) {
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
            if (graphUri.startsWith("http://purl.bdrc.io/admindata/") || graphUri.startsWith("http://purl.bdrc.io/graph-nc/user/")) {
                res.add(ds.getNamedModel(graphUri));
            }
        }
        return res;
    }
    
    public static Resource getPublicUserGraph(final Dataset ds) {
        final Iterator<String> graphUrisIt = ds.listNames();
        while (graphUrisIt.hasNext()) {
            final String graphUri = graphUrisIt.next();
            if (graphUri.startsWith("http://purl.bdrc.io/graph-nc/user/")) {
                return ResourceFactory.createResource(graphUri);
            }
        }
        return null;
    }
    
    public static Resource getPrivateUserGraph(final Dataset ds) {
        final Iterator<String> graphUrisIt = ds.listNames();
        while (graphUrisIt.hasNext()) {
            final String graphUri = graphUrisIt.next();
            if (graphUri.startsWith("http://purl.bdrc.io/graph-nc/user-private/")) {
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
            if (graphUri.startsWith("http://purl.bdrc.io/admindata/") || graphUri.startsWith("http://purl.bdrc.io/graph-nc/user-private/")) {
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
    
    // changes completeSet (later can return plus and minus)
    public static void mergeModel(Dataset completeSet, final String graphUri, Model newFocusModel, final Resource r, final Resource shape, final String repoLname) throws VersionConflictException {
        final boolean isUser = repoLname.equals("GR0100");
        log.info("merging new model for ", r);
        final Model original = completeSet.getNamedModel(graphUri);
        if (log.isDebugEnabled())
            log.debug("original model is ", modelToTtl(original));
        final Model focusedOriginal = CommonsRead.getFocusGraph(original, r, shape);
        if (log.isDebugEnabled())
            log.debug("focused original model is ", modelToTtl(focusedOriginal));
        // don't validate commit for users
        if (!isUser && !CommonsValidate.validateCommit(newFocusModel, focusedOriginal, r)) {
            throw new VersionConflictException("Version conflict while trying to save " + r.getURI());
        }
        final Model outOfFocusOriginal = original.difference(focusedOriginal);
        if (log.isDebugEnabled())
            log.debug("out of focused original model is ", modelToTtl(outOfFocusOriginal));
        final Model resModel = outOfFocusOriginal.add(newFocusModel);
        if (log.isDebugEnabled())
            log.debug("result of the merge is  ", modelToTtl(resModel));
        completeSet.replaceNamedModel(graphUri, resModel);
        if (isUser) {
            // derive the public model and replace it
            
            // no need to change the admin model
        }
        // TODO: option to also return removed / added symmetric and inverse triples in other models
    }
    
    public static Model mergeModel(Model complement, Model focusEdited) {
        Set<Statement> complementModel = ModelToSet(complement);
        Set<Statement> focusModel = ModelToSet(focusEdited);
        Model m = ModelFactory.createDefaultModel();
        return m.add(IteratorUtils.toList(mergeModelAsSet(complementModel, focusModel).iterator()));
    }

    public static Model getEditedModel(Model full, Model focus, Model focusEdited) {
        Model complement = full.difference(focus);
        return mergeModel(complement, focusEdited);
    }

    // List all objects that are not literals or blank nodes
    public List<RDFNode> getObjectResourceNodes(Model m) {
        List<RDFNode> l = new ArrayList<>();
        NodeIterator nit = m.listObjects();
        while (nit.hasNext()) {
            RDFNode n = nit.next();
            if (n.isURIResource()) {
                l.add(n);
            }
        }
        return l;
    }

    public static String checkToFullUri(String resourceUri) {
        try {
            int prefixIndex = resourceUri.indexOf(":");
            if (prefixIndex < 0) {
                return resourceUri;
            } else {
                String prefix = resourceUri.substring(0, prefixIndex);
                return EditConfig.prefix.getFullIRI(prefix) + resourceUri.substring(prefixIndex + 1);
            }
        } catch (Exception ex) {
            return null;
        }
    }
    
    /*

    public static Model removeTriples(String graphUri, List<Statement> tps) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        Model m = CommonsGit.getGraphFromGit(EditConstants.BDR + Helpers.getShortName(graphUri));
        m.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        Dataset ds = DatasetFactory.create();
        ds.addNamedModel(graphUri, m);
        DatasetGraph dg = ds.asDatasetGraph();
        for (Statement st : tps) {
            dg.delete(NodeFactory.createURI(graphUri), st.getSubject().asNode(), st.getPredicate().asNode(), st.getObject().asNode());
        }
        m = ModelFactory.createModelForGraph(dg.getGraph(NodeFactory.createURI(graphUri)));
        // m.write(System.out, "TURTLE");
        return m;
    }

    public static Model addTriples(String graphUri, List<Statement> tps) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        Model m = CommonsGit.getGraphFromGit(EditConstants.BDR + Helpers.getShortName(graphUri));
        m.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        Dataset ds = DatasetFactory.create();
        ds.addNamedModel(graphUri, m);
        DatasetGraph dg = ds.asDatasetGraph();
        for (Statement st : tps) {
            dg.add(NodeFactory.createURI(graphUri), st.getSubject().asNode(), st.getPredicate().asNode(), st.getObject().asNode());
        }
        m = ModelFactory.createModelForGraph(dg.getGraph(NodeFactory.createURI(graphUri)));
        // m.write(System.out, "TURTLE");
        return m;
    }

    */

    public static Model updateGitRevision(String graphUri, Model m, String gitRev) {
        Dataset ds = DatasetFactory.create(m);
        DatasetGraph dsg = ds.asDatasetGraph();
        Triple t = new Triple(NodeFactory.createURI(EditConstants.BDA + Helpers.getShortName(graphUri)),
                NodeFactory.createURI(EditConstants.ADM + "gitRevision"), Node.ANY);
        dsg.deleteAny(NodeFactory.createURI(graphUri), t.getSubject(), t.getPredicate(), t.getObject());
        t = new Triple(NodeFactory.createURI(EditConstants.BDA + Helpers.getShortName(graphUri)),
                NodeFactory.createURI(EditConstants.ADM + "gitRevision"), NodeFactory.createLiteral(gitRev));
        dsg.add(NodeFactory.createURI(graphUri), t.getSubject(), t.getPredicate(), t.getObject());
        return ModelFactory.createModelForGraph(dsg.getUnionGraph());
    }

}
