package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsValidate;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;

public class TestCommons {

    @BeforeClass
    public static void init() throws Exception {
        EditConfig.init();
    }
    
    @Test
    public void getGraphTest() {
        String graphUri = "http://purl.bdrc.io/resource/P1583";
        String graphUri1 = "http://purl.bdrc.io/graph/P1583";
        String graphUri2 = "http://purl.bdrc.io/resource/P1583XZ";

        try {
            CommonsGit.getGraphFromGit(graphUri1);
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            assert (e instanceof UnknownBdrcResourceException);
        }

        try {
            CommonsGit.getGraphFromGit(graphUri2);
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            assert (e instanceof NotModifiableException);
        }

        try {
            Model m = CommonsGit.getGraphFromGit(graphUri);
            assert (m.size() > 0);
            m.write(System.out, "TURTLE");
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
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
        assert (!CommonsValidate.isWithdrawn("http://purl.bdrc.io/resource/P1583", false));
        assert (CommonsValidate.isWithdrawn("http://purl.bdrc.io/resource/P1583uuuuuuuuuuu", false));
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

}
