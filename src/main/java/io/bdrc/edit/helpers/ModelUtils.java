package io.bdrc.edit.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.sparql.core.DatasetGraph;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;

public class ModelUtils {

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
