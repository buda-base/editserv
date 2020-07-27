package editservio.bdrc.edit.test;

import java.util.List;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.OntologyData;
import junit.framework.Assert;

public class OntologyDataTest {

    static Resource res = ResourceFactory.createResource("http://purl.bdrc.io/ontology/core/partOf");
    static Property hasFather = ResourceFactory.createProperty("http://purl.bdrc.io/ontology/core/hasFather");
    static Property hasMother = ResourceFactory.createProperty("http://purl.bdrc.io/ontology/core/hasMother");
    static Property hasParent = ResourceFactory.createProperty("http://purl.bdrc.io/ontology/core/hasParent");

    @BeforeClass
    public static void init() {
        EditConfig.init();
        OntologyData.init();
    }

    @Test
    public void testSymmetricProp() {
        Assert.assertTrue(!OntologyData.isSymmetric(""));
        Assert.assertTrue(OntologyData.isSymmetric("http://purl.bdrc.io/ontology/core/hasSpouse"));
    }

    @Test
    public void testInverseOfProp() {
        List<Property> props = OntologyData.getInverseListProperty("http://purl.bdrc.io/ontology/core/hasSonWHATEVER");
        Assert.assertTrue(props.size() == 0);
        props = OntologyData.getInverseListProperty("http://purl.bdrc.io/ontology/core/hasSon");
        Assert.assertTrue(props.contains(hasFather));
        Assert.assertTrue(props.contains(hasMother));
        Assert.assertTrue(props.contains(hasParent));
    }

}
