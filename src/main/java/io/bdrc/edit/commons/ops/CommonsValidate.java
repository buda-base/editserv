package io.bdrc.edit.commons.ops;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Triple;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.shacl.validation.ValidationUtil;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.commons.data.QueryProcessor;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.edit.txn.exceptions.ValidationException;
import io.bdrc.edit.txn.exceptions.VersionConflictException;
import io.bdrc.libraries.Models;

public class CommonsValidate {

    public static Logger log = LoggerFactory.getLogger(CommonsValidate.class);

    static final String SH = "http://www.w3.org/ns/shacl#";
    static final Property SH_CONFORMS = ResourceFactory.createProperty(SH + "conforms");
    static final Property SH_RESULT = ResourceFactory.createProperty(SH + "result");
    static final Property SH_VALUE = ResourceFactory.createProperty(SH + "value");

    static final Literal FALSE = ModelFactory.createDefaultModel().createTypedLiteral(false);
    static final Literal TRUE = ModelFactory.createDefaultModel().createTypedLiteral(true);

    public static boolean validateCommit(Model newModel, String graphUri) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        String shortName = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        Model current = QueryProcessor.getGraph(Models.BDG + shortName);
        current.write(System.out, "TURTLE");
        try {
            log.info("New model commit >> {}", CommonsRead.getCommit(newModel, graphUri));
            log.info("Current model commit >> {}", CommonsRead.getCommit(current, graphUri));
            if (!CommonsRead.getCommit(newModel, graphUri).equals(CommonsRead.getCommit(current, graphUri))) {
                return false;
            }
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    public static String existResource(String resourceUri) {
        String type = null;
        Model m = QueryProcessor.describeModel(resourceUri, null);
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(resourceUri), RDF.type, (RDFNode) null);
        StmtIterator it = m.listStatements(ss);
        while (it.hasNext()) {
            Statement st = it.next();
            type = st.getObject().asResource().getURI();
        }
        return type;
    }

    public static boolean isWithdrawn(String resourceUri, boolean prefixed) {
        String shortName = "";
        if (prefixed) {
            shortName = resourceUri.substring(resourceUri.lastIndexOf(":") + 1);
        } else {
            shortName = resourceUri.substring(resourceUri.lastIndexOf("/") + 1);
        }
        String query = "select ?o where { <" + Models.BDA + shortName + "> <" + Models.ADM + "status> ?o }";
        ResultSet rs = QueryProcessor.getSelectResultSet(query, null);
        QuerySolution qs = rs.nextSolution();
        if (qs == null) {
            return true;
        }
        RDFNode n = qs.get("?o");
        if (n.asResource().getURI().equals(Models.BDA + "StatusWithdrawn")) {
            return true;
        }
        return false;
    }

    public static boolean isWithdrawn(Model m, String resourceUri, boolean prefixed) {
        String shortName = "";
        if (prefixed) {
            shortName = resourceUri.substring(resourceUri.lastIndexOf(":") + 1);
        } else {
            shortName = resourceUri.substring(resourceUri.lastIndexOf("/") + 1);
        }
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(Models.BDA + shortName),
                ResourceFactory.createProperty(Models.ADM + "status"), (RDFNode) null);
        StmtIterator it = m.listStatements(ss);
        if (!it.hasNext()) {
            return true;
        }
        RDFNode n = it.next().getObject();
        if (n.asResource().getURI().equals(Models.BDA + "StatusWithdrawn")) {
            return true;
        }
        return false;
    }

    public static List<Statement> getNeighboursFromSymmetric(Set<Statement> diff) {
        ArrayList<Statement> symetricStmt = new ArrayList<>();
        for (Statement st : diff) {
            String tmp = st.getPredicate().getURI();
            if (tmp != null && OntologyData.isSymmetric(tmp)) {
                symetricStmt.add(st);
            }
        }
        return symetricStmt;
    }

    public static List<Statement> getNeighboursFromInverse(Set<Statement> diff) {
        ArrayList<Statement> inverseStmt = new ArrayList<>();
        for (Statement st : diff) {
            String tmp = st.getPredicate().getURI();
            Resource rs = null;
            if (tmp != null) {
                rs = OntologyData.getInverse(tmp);
            }
            if (rs != null) {
                inverseStmt.add(st);
            }
        }
        return inverseStmt;
    }

    public static HashMap<String, List<Triple>> getTriplesToProcess(Set<Statement> diffSymetric, Set<Statement> diffInverse) {
        HashMap<String, List<Triple>> map = new HashMap<>();
        List<Statement> symetrics = getNeighboursFromSymmetric(diffSymetric);
        List<Statement> inverses = getNeighboursFromInverse(diffInverse);
        for (Statement st : symetrics) {
            String graphUri = st.getObject().asResource().getURI();
            graphUri = EditConstants.BDG + graphUri.substring(graphUri.lastIndexOf("/") + 1);
            List<Triple> tp = map.get(graphUri);
            if (tp == null) {
                tp = new ArrayList<Triple>();
            }
            Triple t = Triple.create(st.getObject().asNode(), st.getPredicate().asNode(), st.getSubject().asNode());
            tp.add(t);
            map.put(graphUri, tp);
        }
        for (Statement st : inverses) {
            String graphUri = st.getObject().asResource().getURI();
            graphUri = EditConstants.BDG + graphUri.substring(graphUri.lastIndexOf("/") + 1);
            Resource rs = OntologyData.getInverse(st.getPredicate().getURI());
            List<Triple> tp = map.get(graphUri);
            if (tp == null) {
                tp = new ArrayList<Triple>();
            }
            Triple t = Triple.create(st.getObject().asNode(), rs.asNode(), st.getSubject().asNode());
            tp.add(t);
            map.put(graphUri, tp);
        }
        return map;
    }

    public static Set<Statement> getRemovedTriples(Model graphEditor, Model edited) {
        return ModelUtils.ModelComplementAsSet(graphEditor, edited);
    }

    public static Set<Statement> getAddedTriples(Model graphEditor, Model edited) {
        return ModelUtils.ModelComplementAsSet(edited, graphEditor);
    }

    public static boolean completeNeighbours(String graphUri, Model edited) throws IOException, UnknownBdrcResourceException, NotModifiableException,
            ParameterFormatException, ValidationException, InvalidRemoteException, TransportException, VersionConflictException, GitAPIException {
        Model editorGraph = CommonsRead.getEditorGraph(EditConstants.BDR + Helpers.getShortName(graphUri));
        Set<Statement> removed = CommonsValidate.getRemovedTriples(editorGraph, edited);
        Set<Statement> added = CommonsValidate.getRemovedTriples(editorGraph, edited);
        List<Statement> inverses = CommonsValidate.getNeighboursFromInverse(removed);
        List<Statement> symetrics = CommonsValidate.getNeighboursFromSymmetric(removed);
        List<Statement> added_inverses = CommonsValidate.getNeighboursFromInverse(added);
        List<Statement> added_symetrics = CommonsValidate.getNeighboursFromSymmetric(added);
        HashMap<String, List<Triple>> toDelete = CommonsValidate.getTriplesToProcess(new HashSet<>(symetrics), new HashSet<>(inverses));
        HashMap<String, List<Triple>> toAdd = CommonsValidate.getTriplesToProcess(new HashSet<>(added_symetrics), new HashSet<>(added_inverses));
        HashMap<String, Model> models = new HashMap<>();
        HashMap<String, Model> added_models = new HashMap<>();
        for (String graph : toDelete.keySet()) {
            Model m = ModelUtils.removeTriples(graph, toDelete.get(graph));
            models.put(graph, m);
        }
        for (String graph : toAdd.keySet()) {
            Model m = ModelUtils.addTriples(graph, toAdd.get(graph));
            added_models.put(graph, m);
        }
        RDFConnectionFuseki rvf = null;
        try {
            rvf = RDFConnectionFactory.connectFuseki(EditConfig.getProperty("fusekiUrl"));
            for (String graph : models.keySet()) {
                String name = CommonsGit.putAndCommitSingleResource(models.get(graph), Helpers.getShortName(graph));
                Model updated = ModelUtils.updateGitRevision(graphUri, models.get(graph), name);
                rvf.put(graph, updated);
            }
            for (String graph : added_models.keySet()) {
                String name = CommonsGit.putAndCommitSingleResource(added_models.get(graph), Helpers.getShortName(graph));
                Model updated = ModelUtils.updateGitRevision(graphUri, added_models.get(graph), name);
                rvf.put(graph, updated);
            }
            rvf.close();
        } catch (Exception ex) {
            if (!rvf.isClosed()) {
                rvf.close();
            }
            log.error("completeNeighbours failed ", ex);
            return false;
            // eventually send an email;
        }
        return true;
    }

    public static ValidationReport validate(Model m_data, String prefixedId) {
        Model shapes_mod = CommonsRead.getValidationShapesForType(prefixedId);
        shapes_mod.write(System.out, "TURTLE");
        Shapes shapes = Shapes.parse(shapes_mod.getGraph());
        Model data = CommonsRead.getFullDataValidationModel(m_data);
        ShaclValidator sv = ShaclValidator.get();
        ValidationReport report = sv.validate(shapes, data.getGraph());
        return report;
    }

    public static Resource validateTQ(Model m_data, String prefixedId) {
        Model shapes_mod = CommonsRead.getValidationShapesForType(prefixedId);
        Resource r = ValidationUtil.validateModel(m_data, shapes_mod, true);
        return r;
    }

    public static void main(String[] args) throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        EditConfig.init();
        Model m = CommonsRead.getEditorGraph("P:707");
        Resource r = validateTQ(m, "bdo:Person");
        // System.out.println(existResource(Models.BDR + "P1583"));

    }

}