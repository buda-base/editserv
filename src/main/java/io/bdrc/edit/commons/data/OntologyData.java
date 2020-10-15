package io.bdrc.edit.commons.data;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.ontology.OntDocumentManager;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.reasoner.Reasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.helpers.EditServReasoner;

public class OntologyData {

    public final static Logger log = LoggerFactory.getLogger(OntologyData.class.getName());
    public final static String ONT_URL = "http://purl.bdrc.io/ontology/admin/";
    public static Model ONTOLOGY;
    static Property INVERSE;

    public static void init() {
        ONTOLOGY = loadOntologyData();
        INVERSE = ONTOLOGY.createProperty("http://www.w3.org/2002/07/owl#", "inverseOf");
    }

    private static Model loadOntologyData() {
        OntDocumentManager ontManager = new OntDocumentManager(
                EditConfig.getProperty("owlSchemaBase") + "ont-policy.rdf");
        // not really needed since ont-policy sets it, but what if someone changes the
        // policy
        ontManager.setProcessImports(true);
        OntModelSpec ontSpec = new OntModelSpec(OntModelSpec.OWL_DL_MEM);
        ontSpec.setDocumentManager(ontManager);
        return ontManager.getOntology(ONT_URL, ontSpec);
    }

    public static Reasoner getEditServReasoner() {
        return EditServReasoner.getReasoner(ONTOLOGY,
                EditConfig.getProperty("owlSchemaBase") + "reasoning/kinship.rules", true);
    }

    public static List<Statement> getInverseListStatement(String fullPropUri) {
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(fullPropUri), INVERSE, (RDFNode) null);
        return ONTOLOGY.listStatements(ss).toList();
    }

    public static List<Property> getInverseListProperty(String fullPropUri) {
        List<Property> l = new ArrayList<>();
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(fullPropUri), INVERSE, (RDFNode) null);
        StmtIterator it = ONTOLOGY.listStatements(ss);
        while (it.hasNext()) {
            Statement st = it.nextStatement();
            Property p = ONTOLOGY.getProperty(st.getObject().asResource().getURI());
            if (!l.contains(p)) {
                l.add(p);
            }
        }
        return l;
    }

    public static void main(String[] args) {
        ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#inverseOf");
        EditConfig.init();
        OntologyData.init();
        System.out.println("<-----------Inverseof hasSon------------>");
        System.out.println(getInverseListStatement("http://purl.bdrc.io/ontology/core/hasSon"));
        System.out.println(getInverseListProperty("http://purl.bdrc.io/ontology/core/hasSon"));

        // log.info("Ontology data URL 1 {}",
        // EditConfig.getProperty("ontologyDataUrl"));
        // OntologyData.ONTOLOGY.write(System.out, "TURTLE");
        // System.out.println(OntologyData.isSymmetric("http://purl.bdrc.io/ontology/core/hasSpouse"));
        // Resource res =
        // ResourceFactory.createResource("http://purl.bdrc.io/ontology/core/partOf");
        // Resource res1 =
        // OntologyData.getInverse("http://purl.bdrc.io/ontology/core/personTeacherOf");
        // System.out.println(res1);
        // System.out.println(res1.getURI().equals(res.getURI()));
    }

}
