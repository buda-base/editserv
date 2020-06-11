package io.bdrc.edit.commons.data;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;

public class OntologyData {

    public final static Logger log = LoggerFactory.getLogger(OntologyData.class.getName());
    static Model ONTOLOGY;

    public static void init() {
        ONTOLOGY = loadOntologyData();
    }

    private static Model loadOntologyData() {
        Model m = ModelFactory.createDefaultModel();
        log.info("Ontology data URL {}", EditConfig.getProperty("ontologyDataUrl"));
        m.read(EditConfig.getProperty("ontologyDataUrl"), null, "ttl");
        return m;
        // TODO Auto-generated constructor stub
    }

    public static boolean isSymmetric(String fullPropUri) {
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(fullPropUri), RDF.type, EditConstants.SymmetricPROP);
        StmtIterator it = ONTOLOGY.listStatements(ss);
        if (it.hasNext()) {
            return true;
        }
        return false;
    }

    public static Resource getInverse(String fullPropUri) {
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(fullPropUri), EditConstants.INVERSE_OF, (RDFNode) null);
        StmtIterator it = ONTOLOGY.listStatements(ss);
        if (it.hasNext()) {
            return it.next().getObject().asResource();
        }
        ss = new SimpleSelector(null, EditConstants.INVERSE_OF, ResourceFactory.createResource(fullPropUri));
        it = ONTOLOGY.listStatements(ss);
        if (it.hasNext()) {
            return it.next().getSubject().asResource();
        }
        return null;
    }

    public static void main(String[] args) {
        EditConfig.init();
        OntologyData.init();
        log.info("Ontology data URL 1 {}", EditConfig.getProperty("ontologyDataUrl"));
        // OntologyData.ONTOLOGY.write(System.out, "TURTLE");
        System.out.println(OntologyData.isSymmetric("http://purl.bdrc.io/ontology/core/hasSpouse"));
        Resource res = ResourceFactory.createResource("http://purl.bdrc.io/ontology/core/partOf");
        Resource res1 = OntologyData.getInverse("http://purl.bdrc.io/ontology/core/personTeacherOf");
        System.out.println(res1);
        System.out.println(res1.getURI().equals(res.getURI()));
    }

}
