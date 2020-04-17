package io.bdrc.edit.commons;

import java.io.IOException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.GitRepo;
import io.bdrc.edit.helpers.GitRepositories;
import io.bdrc.edit.helpers.Helpers;
import io.bdrc.edit.sparql.QueryProcessor;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.Models;

public class CommonsRead {

    public final static Logger log = LoggerFactory.getLogger("default");
    public static String SHAPES_ROOT_URI = "http://purl.bdrc.io/ontology/shapes/core/";
    public static String SHAPES_SCHEMA = "http://purl.bdrc.io/graph/shapesSchema";
    public static String SHAPE_CLASS_PROP = "http://www.w3.org/ns/shacl#class";
    // public static String SHAPE_CLASS_PROP =
    // "http://www.w3.org/ns/shacl#targetClass";
    private static Model ALL_SHAPES;

    public static Model getGraph(String graphUri) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        if (!graphUri.startsWith(Models.BDR)) {
            throw new UnknownBdrcResourceException(graphUri + " is not a BDRC resource Uri");
        }
        String rootId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        Model m = QueryProcessor.getGraph(Models.BDG + rootId);
        NodeIterator g_path = m.listObjectsOfProperty(EditConstants.GIT_PATH);
        String gitPath = null;
        if (g_path.hasNext()) {
            gitPath = g_path.next().asLiteral().getString();
        }
        NodeIterator g_repo = m.listObjectsOfProperty(EditConstants.GIT_REPO);
        String gitRepo = null;
        if (g_repo.hasNext()) {
            gitRepo = g_repo.next().asResource().getURI();
        }
        if (gitPath == null || gitRepo == null) {
            throw new NotModifiableException(graphUri + " is not a modifiable BDRC resource - gitPath=" + gitPath + " and gitRepo=" + gitRepo);
        }
        GitRepo repo = GitRepositories.getRepoByUri(gitRepo);
        return ModelFactory.createModelForGraph(Helpers
                .buildGraphFromTrig(GlobalHelpers.readFileContent(EditConfig.getProperty("gitLocalRoot") + repo.getGitRepoName() + "/" + gitPath))
                .getUnionGraph());
    }

    private static Model getAllShapes() {
        if (ALL_SHAPES == null) {
            ALL_SHAPES = QueryProcessor.getGraph(SHAPES_SCHEMA);
        }
        return ALL_SHAPES;
    }

    public static Model getShapesForType(String prefixedType) throws IOException, ParameterFormatException {
        Model m = getAllShapes();
        String typeUri = prefixedType.replace("bdo:", EditConstants.BDO);
        ResIterator it = m.listResourcesWithProperty(ResourceFactory.createProperty(SHAPE_CLASS_PROP), ResourceFactory.createResource(typeUri));
        Model shape = null;
        while (it.hasNext()) {
            Resource r = it.next();
            shape.add(r.getModel());
            System.out.println("RESOURCE: " + r.getURI());
        }
        return shape;
    }

    public static void main(String[] arg) throws IOException, ParameterFormatException {
        EditConfig.init();
        Model m = getShapesForType("bdo:Person");
        m.write(System.out, "TURTLE");
        // getAllShapes().write(System.out, "TURTLE");
    }

}
