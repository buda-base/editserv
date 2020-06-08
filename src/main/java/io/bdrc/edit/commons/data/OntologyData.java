package io.bdrc.edit.commons.data;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;

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

    public static void main(String[] args) {
        EditConfig.init();
        OntologyData.init();
        log.info("Ontology data URL 1 {}", EditConfig.getProperty("ontologyDataUrl"));
        OntologyData.ONTOLOGY.write(System.out, "TURTLE");
    }

}
