package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.CommonsRead;
import io.bdrc.edit.commons.CommonsValidate;
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;

public class TestCommons {

    static {
        EditConfig.init();
    }

    @Test
    public void getGraphTest() {
        String graphUri = "http://purl.bdrc.io/resource/P1583";
        String graphUri1 = "http://purl.bdrc.io/graph/P1583";
        String graphUri2 = "http://purl.bdrc.io/resource/P1583XZ";

        try {
            CommonsRead.getGraph(graphUri1);
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            // TODO Auto-generated catch block
            assert (e instanceof UnknownBdrcResourceException);
        }

        try {
            CommonsRead.getGraph(graphUri2);
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            // TODO Auto-generated catch block
            assert (e instanceof NotModifiableException);
        }

        try {
            Model m = CommonsRead.getGraph(graphUri);
            assert (m.size() > 0);
            m.write(System.out, "TURTLE");
        } catch (UnknownBdrcResourceException | NotModifiableException | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testValidCommit() throws UnknownBdrcResourceException, NotModifiableException, IOException {
        InputStream in = TestCommons.class.getClassLoader().getResourceAsStream("P705.ttl");
        Model m = ModelFactory.createDefaultModel();
        m.read(in, null, "TTL");
        in.close();
        assert (!CommonsValidate.validateCommit(m, "http://purl.bdrc.io/resource/P705"));
        m = QueryProcessor.getGraph("http://purl.bdrc.io/resource/P705");
        assert (CommonsValidate.validateCommit(m, "http://purl.bdrc.io/resource/P705"));
    }

}
