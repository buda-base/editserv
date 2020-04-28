package io.bdrc.edit.commons;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.shacl.engine.Shape;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.model.SHPropertyShape;
import org.topbraid.shacl.model.SHShape;

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

    public static final String GRAPH_NAME_TYPE = "graph_name_type";
    public static final String GRAPH_URI_TYPE = "graph_uri_type";
    public static final String GRAPH_RESOUCE = "graph_resource";

    public static String SHAPES_ROOT_URI = "http://purl.bdrc.io/ontology/shapes/core/";
    public static String SHAPES_SCHEMA = "http://purl.bdrc.io/graph/shapesSchema";
    // public static String SHAPE_CLASS_PROP = "http://www.w3.org/ns/shacl#class";
    public static String SHAPE_CLASS_PROP = "http://www.w3.org/ns/shacl#targetClass";
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

    public static Model getAllShapesForType(String prefixedType) throws IOException, ParameterFormatException {
        String typeUri = prefixedType.replace("bdo:", EditConstants.BDO);
        String shortName = prefixedType.substring(prefixedType.indexOf(":") + 1);
        Model m = QueryProcessor.getGraph(EditConstants.BDG + shortName + "Shapes");
        return m;
    }

    public static Model getShapesForType(String prefixedType) throws IOException, ParameterFormatException {
        String shortName = prefixedType.substring(prefixedType.indexOf(":") + 1);
        Model m = QueryProcessor.getGraph(EditConstants.BDG + shortName + "Shapes");
        return m;
    }

    public static Model getUIShapesForType(String prefixedType) throws IOException, ParameterFormatException {
        String shortName = prefixedType.substring(prefixedType.indexOf(":") + 1);
        Model m = QueryProcessor.getGraph(EditConstants.BDG + shortName + "UIShapes");
        return m;
    }

    public static String getBestShape(String prefixedUri)
            throws UnknownBdrcResourceException, NotModifiableException, IOException, ParameterFormatException {
        String shortName = prefixedUri.substring(prefixedUri.indexOf(":") + 1);
        Model m = getGraph(EditConstants.BDR + shortName);
        NodeIterator it = m.listObjectsOfProperty(ResourceFactory.createResource(EditConstants.BDR + shortName), RDF.type);
        String typeUri = it.next().asResource().getURI();
        Model mod = getShapesForType(typeUri.replace(EditConstants.BDO, "bdo:"));
        mod.write(System.out, "TURTLE");
        NodeIterator it1 = mod.listObjectsOfProperty(ResourceFactory.createResource(typeUri),
                ResourceFactory.createProperty(SHAPES_ROOT_URI + "rootShape"));
        return it1.next().asResource().getURI();
    }

    public static Model getAssociatedLabels(String prefixedUri) {
        String query = "construct {\n" + "  ?s skos:preflabel ?o. \n" + "  ?s rdfs:label ?o.} \n" + "where { { \n" + prefixedUri + " ?resp ?s . \n"
                + "  {  \n" + "  ?s skos:prefLabel ?o.\n" + "  }\n" + "  union{\n" + "      ?s rdfs:label ?o.\n" + "  }\n" + "}}";
        return QueryProcessor.getQueryGraph(null, query);
    }

    public static Model getFocusGraph(String prefRes, Model resMod, String shapeUri) throws IOException {
        String shortName = prefRes.substring(prefRes.lastIndexOf(":") + 1);
        System.out.println("ShortName : " + shortName);
        Model res = ModelFactory.createDefaultModel();
        List<String> propsUri = getFocusPropertiesFromShape(shapeUri, CommonsRead.GRAPH_NAME_TYPE);
        Iterator<Statement> it = resMod.listStatements();
        while (it.hasNext()) {
            Statement stmt = it.next();
            if (propsUri.contains(stmt.getPredicate().getURI()) && stmt.getSubject().getURI().equals(EditConstants.BDR + shortName)) {
                res.add(stmt);
            }
        }
        return res;
    }

    public static Model getFocusGraph(String prefRes, Model resMod, String shapeGraph, String sourceType) throws IOException {
        String shortName = prefRes.substring(prefRes.lastIndexOf(":") + 1);
        System.out.println("ShortName : " + shortName);
        Model res = ModelFactory.createDefaultModel();
        List<String> propsUri = getFocusPropertiesFromShape(shapeGraph, sourceType);
        Iterator<Statement> it = resMod.listStatements();
        while (it.hasNext()) {
            Statement stmt = it.next();
            if (propsUri.contains(stmt.getPredicate().getURI()) && stmt.getSubject().getURI().equals(EditConstants.BDR + shortName)) {
                res.add(stmt);
            }
        }
        return res;
    }

    public static List<String> getFocusPropertiesFromShape(String shapeGraph, String sourceType) throws IOException {
        List<String> uris = new ArrayList<>();
        Model shapes = ModelFactory.createDefaultModel();
        if (sourceType.equals(CommonsRead.GRAPH_URI_TYPE)) {
            shapes.read(shapeGraph, "TTL");
        }
        if (sourceType.equals(CommonsRead.GRAPH_NAME_TYPE)) {
            shapes = QueryProcessor.getGraph(shapeGraph);
        }
        if (sourceType.equals(CommonsRead.GRAPH_NAME_TYPE)) {
            InputStream in = CommonsRead.class.getClassLoader().getResourceAsStream(shapeGraph);
            shapes.read(in, null, "TTL");
            in.close();
        }
        ShapesGraph sg = new ShapesGraph(shapes);
        for (Shape shape : sg.getRootShapes()) {
            SHShape shs = shape.getShapeResource();
            for (SHPropertyShape shp : shs.getPropertyShapes()) {
                Resource path = shp.getPath();
                if (path != null && !uris.contains(path.getURI())) {
                    uris.add(path.getURI());
                }
            }
        }
        return uris;
    }

    public static void main(String[] arg) throws IOException, ParameterFormatException, UnknownBdrcResourceException, NotModifiableException {
        EditConfig.init();
        // String personShapeUri =
        // "https://raw.githubusercontent.com/buda-base/editor-templates/master/templates/core/person.shapes.ttl";
        String personShapeUri = "https://raw.githubusercontent.com/buda-base/editor-templates/master/templates/core/person.event.shapes.ttl";
        // System.out.println(getBestShape("bdr:P707"));
        Model person = ModelFactory.createDefaultModel();
        person.read("http://purl.bdrc.io/resource/P1583.ttl", "TTL");
        // Model res = getFocusGraph("bdr:1583", person,
        // "http://purl.bdrc.io/graph/PersonShapes");
        Model res = getFocusGraph("bdr:P1583", person, "http://purl.bdrc.io/graph/PersonShapes");
        res.write(System.out, "TTL");
    }

}
