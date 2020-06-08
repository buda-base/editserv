package io.bdrc.edit.commons.ops;

import java.io.IOException;
import java.io.InputStream;

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
import io.bdrc.edit.commons.data.QueryProcessor;
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

    public static boolean validateShacl(Model newModel, String resUri) {
        try {
            String shortName = resUri.substring(resUri.lastIndexOf("/") + 1);
            Resource res = ResourceFactory.createResource(Models.BDR + shortName);
            ShaclValidator sv = ShaclValidator.get();
            Graph shapesGraph = CommonsRead.getValidationShapesForResource("bdr:" + shortName).getGraph();
            Shapes shapes = Shapes.parse(shapesGraph);
            Graph dataGraph = CommonsRead.getFullDataValidationModel(newModel).getGraph();
            log.info("Validating Node {} with {}", res.getLocalName(), shapes);
            ValidationReport report = sv.validate(shapes, dataGraph, res.asNode());
            log.info("Validating Node {} with {} returns {}", res.getLocalName(), shapes, report.conforms());
            report.getModel().write(System.out, "TURTLE");
            Model finalReport = completeReport(shapes, dataGraph, report.getModel());
            finalReport.write(System.out, "TURTLE");
            SimpleSelector ss = new SimpleSelector(null, ResourceFactory.createProperty(SH + "conforms"), (RDFNode) null);
            StmtIterator it = finalReport.listStatements(ss);
            boolean b = true;
            while (it.hasNext()) {
                Statement st = it.next();
                System.out.println("STMT >>" + st);
                b = b && Boolean.valueOf(st.getObject().asLiteral().getString());
            }
            return b;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private static boolean test() throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        InputStream in = CommonsValidate.class.getClassLoader().getResourceAsStream("P707_missingName.ttl");
        Model m = ModelFactory.createDefaultModel();
        m.read(in, null, "TTL");
        boolean b = validateShacl(m, "http://purl.bdrc.io/resource/P707");

        log.info("PRINTING report.getModel()");
        log.info("Is Model Valid ? {}", b);
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

    public static boolean reportConforms(Model m) {
        SimpleSelector ss = new SimpleSelector(null, ResourceFactory.createProperty(SH + "conforms"), (RDFNode) null);
        StmtIterator it = m.listStatements(ss);
        return Boolean.getBoolean(it.next().getObject().asLiteral().getString());
    }

    public static void main(String[] args) throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        EditConfig.init();
        // System.out.println(existResource(Models.BDR + "P1583"));
        test();

    }

}
