package editservio.bdrc.edit.test;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.OntologyData;
import junit.framework.Assert;

public class OntologyDataTest {

    static Resource res = ResourceFactory.createResource("http://purl.bdrc.io/ontology/core/partOf");

    @BeforeClass
    public static void init() {
        EditConfig.init();
        OntologyData.init();
    }

    @Test
    public void testInverseProp() {
        Assert.assertTrue(!OntologyData.isSymmetric(""));
        Assert.assertTrue(OntologyData.isSymmetric("http://purl.bdrc.io/ontology/core/hasSpouse"));
    }

    @Test
    public void testSymmetricProp() {
        Resource rs = OntologyData.getInverse("http://purl.bdrc.io/ontology/core/hasPart");
        Assert.assertTrue(rs != null);
        Assert.assertTrue(rs.getURI().equals(res.getURI()));
        rs = OntologyData.getInverse("http://purl.bdrc.io/ontology/core/hasSpouse");
        Assert.assertTrue(rs == null);
    }

}
