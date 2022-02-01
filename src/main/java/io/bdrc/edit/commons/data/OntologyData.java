package io.bdrc.edit.commons.data;

import org.apache.jena.ontology.OntDocumentManager;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.reasoner.Reasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.helpers.EditServReasoner;

public class OntologyData {

    public final static Logger log = LoggerFactory.getLogger(OntologyData.class.getName());
    public final static String ONT_URL = "http://purl.bdrc.io/ontology/admin/";
    public static OntModel ONTOLOGY = null;
    public static Reasoner Reasoner = null;

    public static void init() {
        ONTOLOGY = loadOntologyData();
        Reasoner = getEditServReasoner();
    }

    private static OntModel loadOntologyData() {
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

}
