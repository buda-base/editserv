package io.bdrc.edit.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.commons.ops.CommonsRead;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.controllers.MainEditController;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { MainEditController.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
public class TestValidation {

    String validModel = "P707.ttl";
    String nameErrModel = "P707_missingName.ttl";
    static List<Triple> removed = new ArrayList<Triple>();
    static HashMap<String, List<Triple>> removedByGraph = new HashMap<>();
    public static Logger log = LoggerFactory.getLogger(TestValidation.class);

    @Autowired
    Environment environment;

    @BeforeClass
    public static void init() throws Exception {
        EditConfig.init();
        OntologyData.init();
        prepareData();
    }

    // @Test
    public void validateValidModelAndSave() throws IOException {
        InputStream in = TestValidation.class.getClassLoader().getResourceAsStream(validModel);
        StringWriter writer = new StringWriter();
        IOUtils.copy(in, writer, "UTF-8");
        String model = writer.toString();
        // System.out.println("Read Model to validate >>" + model);
        HttpClient client = HttpClientBuilder.create().build();
        HttpPut put = new HttpPut("http://localhost:" + environment.getProperty("local.server.port") + "/putresource/bdr:P707");
        StringEntity entity = new StringEntity(model);
        put.setEntity(entity);
        put.setHeader("Content-type", "text/turtle");
        HttpResponse response = client.execute(put);
        System.out.println(response);
    }

    // @Test
    public void validateMissingNameErrorModelAndSave() throws IOException {
        InputStream in = TestValidation.class.getClassLoader().getResourceAsStream(nameErrModel);
        StringWriter writer = new StringWriter();
        IOUtils.copy(in, writer, "UTF-8");
        String model = writer.toString();
        // System.out.println("Read Model to validate >>" + model);
        HttpClient client = HttpClientBuilder.create().build();
        HttpPut put = new HttpPut("http://localhost:" + environment.getProperty("local.server.port") + "/putresource/bdr:P707");
        StringEntity entity = new StringEntity(model);
        put.setEntity(entity);
        put.setHeader("Content-type", "text/turtle");
        HttpResponse response = client.execute(put);
        System.out.println(response);
    }

    // @Test
    public void processRemovedTriples() throws IOException, UnknownBdrcResourceException, NotModifiableException {

        Model initial = ModelFactory.createDefaultModel();
        Model edited = ModelFactory.createDefaultModel();
        InputStream in = TestModelUtils.class.getClassLoader().getResourceAsStream("P1583.ttl");
        initial.read(in, null, "TTL");
        in = TestModelUtils.class.getClassLoader().getResourceAsStream("P1583_missingProps.ttl");
        edited.read(in, null, "TTL");
        in.close();
        Set<Statement> removed = ModelUtils.ModelComplementAsSet(initial, edited);
        System.out.println("Removed >>" + removed);
        List<Statement> inverses = CommonsValidate.getNeighboursFromInverse(removed);
        List<Statement> symetrics = CommonsValidate.getNeighboursFromSymmetric(removed);
        System.out.println("Neighbours Inverse >>" + inverses);
        System.out.println("Neighbours Symmetric >>" + symetrics);
        HashMap<String, List<Triple>> toDelete = CommonsValidate.getTriplesToRemove(symetrics, inverses);
        System.out.println("ToDelete >>" + toDelete);
        for (String graph : toDelete.keySet()) {
            // Helpers.deleteTriples(graph, toDelete.get(graph),
            // "http://buda1.bdrc.io:13180/fuseki/testrw/");
            System.out.println("-----------REMOVING TRIPLES IN GRAPH ----------->>" + graph);
            Model m = ModelUtils.removeTriples(graph, toDelete.get(graph));
            // and here
            // put to fuseki
            // put to git
        }
    }

    @Test
    public void processAddedTriples() throws IOException, UnknownBdrcResourceException, NotModifiableException {

        Model initial = ModelFactory.createDefaultModel();
        Model edited = ModelFactory.createDefaultModel();
        InputStream in = TestModelUtils.class.getClassLoader().getResourceAsStream("P1583.ttl");
        initial.read(in, null, "TTL");
        in = TestModelUtils.class.getClassLoader().getResourceAsStream("P1583_moreProps.ttl");
        edited.read(in, null, "TTL");
        in.close();
        Set<Statement> added = CommonsValidate.getDiffAddedTriples(initial, edited);
        System.out.println("added >>" + added);
        List<Statement> inverses = CommonsValidate.getNeighboursFromInverse(added);
        List<Statement> symetrics = CommonsValidate.getNeighboursFromSymmetric(added);
        System.out.println("Neighbours Inverse >>" + inverses);
        System.out.println("Neighbours Symmetric >>" + symetrics);
        HashMap<String, List<Triple>> toDelete = CommonsValidate.getTriplesToRemove(symetrics, inverses);
        // HashMap<String, List<Triple>> toAdd = CommonsValidate.getTriplesToRemove(new
        // HashSet<>(symetrics), new HashSet<>(inverses));
        System.out.println("ToDelete >>" + toDelete);
        assertTrue(toDelete.get(EditConstants.BDG + "P2JM206") != null);
        assertTrue(toDelete.get(EditConstants.BDG + "P2JM206").size() == 4);
        // for (String graph : toDelete.keySet()) {
        // Helpers.deleteTriples(graph, toDelete.get(graph),
        // "http://buda1.bdrc.io:13180/fuseki/testrw/");
        // System.out.println("-----------REMOVING TRIPLES IN GRAPH ----------->>" +
        // graph);
        // Model m = ModelUtils.removeTriples(graph, toDelete.get(graph));
        // and here
        // put to fuseki
        // put to git
        // }
    }

    public static void prepareData() {
        Triple t = new Triple(NodeFactory.createURI("http://purl.bdrc.io/resource/P8528"),
                NodeFactory.createURI("http://purl.bdrc.io/ontology/core/kinWith"), NodeFactory.createURI("http://purl.bdrc.io/resource/P1583"));
        removed.add(t);
        List<Triple> ltp = new ArrayList<Triple>();
        ltp.add(t);
        removedByGraph.put("http://purl.bdrc.io/resource/P8528", ltp);
        t = new Triple(NodeFactory.createURI("http://purl.bdrc.io/resource/P1585"),
                NodeFactory.createURI("http://purl.bdrc.io/ontology/core/kinWith"), NodeFactory.createURI("http://purl.bdrc.io/resource/P1583"));
        ltp = new ArrayList<Triple>();
        ltp.add(t);
        removedByGraph.put("http://purl.bdrc.io/resource/P1585", ltp);
        removed.add(t);
        t = new Triple(NodeFactory.createURI("http://purl.bdrc.io/resource/P2JM192"),
                NodeFactory.createURI("http://purl.bdrc.io/ontology/core/personStudentOf"),
                NodeFactory.createURI("http://purl.bdrc.io/resource/P1583"));
        ltp = new ArrayList<Triple>();
        ltp.add(t);
        removedByGraph.put("http://purl.bdrc.io/resource/P2JM192", ltp);
        removed.add(t);
        t = new Triple(NodeFactory.createURI("http://purl.bdrc.io/resource/P2JM193"),
                NodeFactory.createURI("http://purl.bdrc.io/ontology/core/personStudentOf"),
                NodeFactory.createURI("http://purl.bdrc.io/resource/P1583"));
        ltp = new ArrayList<Triple>();
        ltp.add(t);
        removedByGraph.put("http://purl.bdrc.io/resource/P2JM193", ltp);
        removed.add(t);
        t = new Triple(NodeFactory.createURI("http://purl.bdrc.io/resource/P2JM194"),
                NodeFactory.createURI("http://purl.bdrc.io/ontology/core/personStudentOf"),
                NodeFactory.createURI("http://purl.bdrc.io/resource/P1583"));
        ltp = new ArrayList<Triple>();
        ltp.add(t);
        removedByGraph.put("http://purl.bdrc.io/resource/P2JM194", ltp);
        removed.add(t);
    }

    // Using full graph and ontology data
    // @Test
    public void straightModelTQValidation() throws IOException {
        Model initial = ModelFactory.createDefaultModel();
        InputStream in = null;
        initial.read("http://purl.bdrc.io/resource/P707.ttl", null, "TTL");
        initial = CommonsRead.getFullDataValidationModel(initial);
        Resource valid = CommonsValidate.validateTQ(initial, "bdo:Person");
        valid.getModel().write(System.out, "TURTLE");
        assert (!conforms(valid));
    }

    // shapes graph parsing error for now
    // @Test
    public void editorModelValidationJenaShacl() throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        Model initial = CommonsRead.getEditorGraph("bdr:P707");
        initial.write(System.out, "TURTLE");
        boolean conforms = CommonsValidate.validate(initial, "bdr:P707").conforms();
        System.out.println("Conforms >> " + conforms);
        assert (conforms);
        initial = ModelFactory.createDefaultModel();
        InputStream in = TestModelUtils.class.getClassLoader().getResourceAsStream("P707_editor_missingName.ttl");
        initial.read(in, null, "TTL");
        in.close();
        conforms = CommonsValidate.validate(initial, "bdr:P707").conforms();
        System.out.println("Conforms >> " + conforms);
        assertTrue(!conforms);
    }

    // Using pre-processed graph (editor graph) and ontology data
    // @Test
    public void editorModelValidationTQShacl() throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        log.info("Running validation test on graph editor + ont data with valid graph");
        Model initial = CommonsRead.getEditorGraph("bdr:P707");
        initial = CommonsRead.getFullDataValidationModel(initial);
        Resource r = CommonsValidate.validateTQ(initial, "bdo:Person");
        r.getModel().write(System.out, "TURTLE");
        assertTrue(conforms(r));
        log.info("Running validation test on graph editor + ont data with missing name");
        Model err = ModelFactory.createDefaultModel();
        InputStream in = TestModelUtils.class.getClassLoader().getResourceAsStream("P707_editor_missingName.ttl");
        err.read(in, null, "TTL");
        err = CommonsRead.getFullDataValidationModel(err);
        in.close();
        r = CommonsValidate.validateTQ(err, "bdo:Person");
        r.getModel().write(System.out, "TURTLE");
        assertTrue(!conforms(r));
    }

    public boolean conforms(Resource r) {
        Model m = r.getModel();
        Property conforms = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#conforms");
        SimpleSelector ss = new SimpleSelector((Resource) null, conforms, (RDFNode) null);
        Statement st = m.listStatements(ss).next();
        return st.getObject().asLiteral().getString().equals("true");
    }

}
