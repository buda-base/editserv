package io.bdrc.edit.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.controllers.MainEditController;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { MainEditController.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
public class ToSkipForNowTestValidation {

    String validModel = "P707.ttl";
    String nameErrModel = "P707_missingName.ttl";
    static List<Triple> removed = new ArrayList<Triple>();
    static HashMap<String, List<Triple>> removedByGraph = new HashMap<>();
    public static Logger log = LoggerFactory.getLogger(ToSkipForNowTestValidation.class);

    @Autowired
    Environment environment;

    @BeforeClass
    public static void init() throws Exception {
        EditConfig.init();
        prepareData();
    }

    // @Test
    public void validateValidModelAndSave() throws IOException {
        InputStream in = ToSkipForNowTestValidation.class.getClassLoader().getResourceAsStream(validModel);
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
        InputStream in = ToSkipForNowTestValidation.class.getClassLoader().getResourceAsStream(nameErrModel);
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
        //initial = CommonsRead.getFullDataValidationModel(initial);
        boolean valid = CommonsValidate.validateShacl(initial);
        assert (!valid);
    }

    // shapes graph parsing error for now
    // @Test
    public void editorModelValidationJenaShacl() throws IOException {
        Model initial = ModelFactory.createDefaultModel();
        InputStream in = ToSkipForNowTestValidation.class.getClassLoader().getResourceAsStream("P707_editor_missingName.ttl");
        initial.read(in, null, "TTL");
        in.close();
        boolean conforms = CommonsValidate.validateShacl(initial);
        assertTrue(!conforms);
    }

    // Using pre-processed graph (editor graph) and ontology data
    // @Test
    public void editorModelValidationTQShacl() throws IOException {
        Model err = ModelFactory.createDefaultModel();
        InputStream in = ToSkipForNowTestValidation.class.getClassLoader().getResourceAsStream("P707_editor_missingName.ttl");
        err.read(in, null, "TTL");
        in.close();
        boolean r = CommonsValidate.validateShacl(err);
        assertTrue(!r);
    }

    public boolean conforms(Resource r) {
        Model m = r.getModel();
        Property conforms = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#conforms");
        SimpleSelector ss = new SimpleSelector((Resource) null, conforms, (RDFNode) null);
        Statement st = m.listStatements(ss).next();
        return st.getObject().asLiteral().getString().equals("true");
    }

}
