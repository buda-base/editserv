package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.controllers.MainEditController;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { MainEditController.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
public class TestValidation {

    String validModel = "P707.ttl";
    String nameErrModel = "P707_missingName.ttl";
    // String nameErrModel = "P707_nameErrs.ttl";

    @Autowired
    Environment environment;

    @BeforeClass
    public static void init() throws Exception {
        EditConfig.init();
        OntologyData.init();
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

    @Test
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
        HashMap<String, List<Triple>> toDelete = CommonsValidate.getTriplesToRemove(new HashSet<>(symetrics), new HashSet<>(inverses));
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

}
