package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.Test;

import io.bdrc.edit.helpers.EditServReasoner;

public class EDTFTest {

    @Test
    public void edtfFromInterval() {
        assert(EditServReasoner.intervalToEDTF("1700", "1799").equals("17XX"));
        assert(EditServReasoner.intervalToEDTF("1600", "1799").equals("1600/1799"));
        assert(EditServReasoner.intervalToEDTF("1600", "1609").equals("160X"));
        assert(EditServReasoner.intervalToEDTF("1717", "1718").equals("1717/1718"));
    }
    
    //@Test
    public void inferEdtf() throws IOException {
        InputStream in = TestCommons.class.getClassLoader().getResourceAsStream("P667-focus.ttl");
        Model m = ModelFactory.createDefaultModel();
        m.read(in, null, "TTL");
        in.close();
        EditServReasoner.addinferredEDTFStrings(m);
        m.write(System.out, "TTL");
    }
    
}
