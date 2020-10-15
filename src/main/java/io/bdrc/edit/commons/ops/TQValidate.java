package io.bdrc.edit.commons.ops;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.validation.ValidationEngineConfiguration;
import org.topbraid.shacl.validation.ValidationUtil;

import io.bdrc.edit.EditConstants;

public class TQValidate {

    public static Logger logger = LoggerFactory.getLogger(TQValidate.class);

    public static Resource validateNode(Model dataModel, String prefixedId, String prefixedResUri) {
        Resource rez = ResourceFactory.createResource(prefixedResUri.replace("bdr:", EditConstants.BDR));
        Model shapesModel = CommonsRead.getValidationShapesForType(prefixedId);
        ValidationEngineConfiguration configuration = new ValidationEngineConfiguration().setValidateShapes(true);
        ValidationEngine engine = ValidationUtil.createValidationEngine(dataModel, shapesModel, configuration);
        engine.setConfiguration(configuration);
        logger.info("ValidationEngine Shapes graph {}", engine.getShapesGraphURI());
        logger.info("ValidationEngine .getShapesModel().size() = {}", engine.getShapesModel().size());
        try {
            engine.applyEntailments();
            return engine.validateNode(rez.asNode());
        } catch (InterruptedException ex) {
            return null;
        }
    }
}
