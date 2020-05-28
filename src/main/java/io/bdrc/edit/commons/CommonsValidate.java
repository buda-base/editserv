package io.bdrc.edit.commons;

import java.io.IOException;

import org.apache.jena.graph.Graph;
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
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.validation.ValidationEngineConfiguration;
import org.topbraid.shacl.validation.ValidationUtil;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
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
        Model current = QueryProcessor.getGraph(graphUri);
        try {
            if (CommonsRead.getCommit(newModel, graphUri).equals(CommonsRead.getCommit(current, graphUri))) {
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

    static Model completeReport(Shapes shapes, Graph dataGraph, Model top) {
        Model complete = ModelFactory.createDefaultModel();
        complete.add(top);

        if (top.contains((Resource) null, SH_CONFORMS, FALSE)) {
            StmtIterator valItr = top.listStatements((Resource) null, SH_VALUE, (RDFNode) null);
            while (valItr.hasNext()) {
                Statement valStmt = valItr.removeNext();
                RDFNode valNode = valStmt.getObject();

                if (valNode.isResource()) {
                    Model subReport = process(shapes, dataGraph, (Resource) valNode);
                    // subReport.remove(subReport.listStatements(null, SH_CONFORMS, (RDFNode)
                    // null));
                    complete.add(subReport);
                }
            }
        }

        return complete;
    }

    static Model process(Shapes shapes, Graph dataGraph, Resource rez) {
        log.info("Validating Node {} with {}", rez.getLocalName(), shapes);
        ValidationReport report = ShaclValidator.get().validate(shapes, dataGraph, rez.asNode());
        Model reportModel = report.getModel();
        return reportModel;
    }

    public static boolean validateShacl(Model newModel, String resUri)
            throws IOException, ParameterFormatException, UnknownBdrcResourceException, NotModifiableException {
        String shortName = resUri.substring(resUri.lastIndexOf("/") + 1);
        Resource res = ResourceFactory.createResource(Models.BDR + shortName);
        ShaclValidator sv = ShaclValidator.get();
        Graph shapesGraph = CommonsRead.getValidationShapesForResource("bdr:" + shortName).getGraph();
        Shapes shapes = Shapes.parse(shapesGraph);
        Graph dataGraph = CommonsRead.getFullDataValidationModel(newModel).getGraph();
        log.info("Validating Node {} with {}", res.getLocalName(), shapes);
        ValidationReport report = sv.validate(shapes, dataGraph, res.asNode());
        Model finalReport = completeReport(shapes, dataGraph, report.getModel());
        SimpleSelector ss = new SimpleSelector(null, ResourceFactory.createProperty(SH + "conforms"), (RDFNode) null);
        StmtIterator it = finalReport.listStatements(ss);
        return Boolean.getBoolean(it.next().getObject().asLiteral().getString());
    }

    private static boolean test() throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        Model full = ModelFactory.createDefaultModel();
        full.read("http://ldspdi-dev.bdrc.io/resource/P707", "TTL");
        // full.write(System.out, "TURTLE");
        Model focus = CommonsRead.getEditorGraph("bdr:P707");
        focus.write(System.out, "TURTLE");
        // Resource r = validateNode(focus,
        // CommonsRead.getShapesForType(CommonsRead.getResourceTypeUri("bdr:P707")),
        // ResourceFactory.createResource("http://purl.bdrc.io/resource/P707"), true);
        log.info("PRINTING report.getModel()");
        // RDFDataMgr.write(System.out, r.getModel(), Lang.TTL);
        return true;
    }

    public static Resource validateNode(Model dataModel, Model shapesModel, Resource focus, boolean validateShapes) {
        return validateNode(dataModel, shapesModel, focus, new ValidationEngineConfiguration().setValidateShapes(validateShapes));
    }

    public static Resource validateNode(Model dataModel, Model shapesModel, Resource focus, ValidationEngineConfiguration configuration) {

        ValidationEngine engine = ValidationUtil.createValidationEngine(dataModel, shapesModel, configuration);
        engine.setConfiguration(configuration);
        log.info("ValidationEngine Shapes graph {}", engine.getShapesGraphURI());
        log.info("ValidationEngine .getShapesModel().size() = {}", engine.getShapesModel().size());
        try {
            engine.applyEntailments();
            return engine.validateNode(focus.asNode());
        } catch (InterruptedException ex) {
            return null;
        }
    }

    public static void main(String[] args) throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        EditConfig.init();
        // System.out.println(existResource(Models.BDR + "P1583"));
        test();

    }

}
