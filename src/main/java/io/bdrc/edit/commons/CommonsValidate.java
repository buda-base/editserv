package io.bdrc.edit.commons;

import java.io.IOException;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
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

    public static void putResource(Model m, String prefixedId) {

    }

    public static boolean validateShacl(Model newModel, String resUri)
            throws IOException, ParameterFormatException, UnknownBdrcResourceException, NotModifiableException {
        String shortName = resUri.substring(resUri.lastIndexOf("/") + 1);
        Resource r = validateNode(newModel, CommonsRead.getValidationShapesForType(CommonsRead.getResourceTypeUri("bdr:" + shortName, false)),
                ResourceFactory.createResource(resUri), true);
        log.info("PRINTING report.getModel() for " + resUri);
        RDFDataMgr.write(System.out, r.getModel(), Lang.TTL);
        return true;
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
