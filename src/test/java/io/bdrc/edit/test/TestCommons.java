package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDFLib;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsRead;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.helpers.Shapes;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.Models;

public class TestCommons {

    public static Model personData = null;
    final static String TESTDIR = "src/test/resources/";
    
    @BeforeClass
    public static void init() throws Exception {
        EditConfig.initForTests(null);
        personData = ModelFactory.createDefaultModel();
        Graph g = personData.getGraph();
        RDFParser.create()
            .source(TESTDIR+"PersonTest.ttl")
            .lang(RDFLanguages.TTL)
            .parse(StreamRDFLib.graph(g));
        Model m = ModelFactory.createDefaultModel();
        g = m.getGraph();
        RDFParser.create()
            .source(TESTDIR+"PersonShapes.ttl")
            .lang(RDFLanguages.TTL)
            .parse(StreamRDFLib.graph(g));
        Shapes.initFromModel(m);
        //Shapes.init();
    }
    
    @Test
    public void getGraphTest() {
        String graphUri = "http://purl.bdrc.io/resource/P1583";
        String graphUri1 = "http://purl.bdrc.io/graph/P1583";
        String graphUri2 = "http://purl.bdrc.io/resource/P1583XZ";

        try {
            GitInfo gi = CommonsGit.gitInfoForResource(ResourceFactory.createResource(graphUri));
            Model m = ModelUtils.getMainModel(gi.ds);
            assert (m.size() > 0);
            m.write(System.out, "TURTLE");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testResourceExistence() {
        assert (CommonsValidate.existResource("http://purl.bdrc.io/resource/P705YYYYYYYYYYYYYYYYY") == null);
        assert (CommonsValidate.existResource("http://purl.bdrc.io/resource/P705") != null);
    }

    // @Test
    public void testWithdrawn() throws IOException {
        //assert (!CommonsValidate.isWithdrawn("http://purl.bdrc.io/resource/P1583", false));
        //assert (CommonsValidate.isWithdrawn("http://purl.bdrc.io/resource/P1583uuuuuuuuuuu", false));
        InputStream in = TestCommons.class.getClassLoader().getResourceAsStream("P705.ttl");
        Model m = ModelFactory.createDefaultModel();
        m.read(in, null, "TTL");
        in.close();
        assert (CommonsValidate.isWithdrawn(m, "http://purl.bdrc.io/resource/P705", false));
        in = TestCommons.class.getClassLoader().getResourceAsStream("P705Released.ttl");
        m = ModelFactory.createDefaultModel();
        m.read(in, null, "TTL");
        in.close();
        assert (!CommonsValidate.isWithdrawn(m, "http://purl.bdrc.io/resource/P705", false));
    }

    @Test
    public void testFocusGraph() throws IOException {
        System.out.println("toto");
        Model fg = CommonsRead.getFocusGraph(personData, ResourceFactory.createResource(Models.BDR+"P1583"), ResourceFactory.createResource(EditConstants.BDS+"PersonShape"));
        fg.write(System.out, "TTL");
    }
    
}
