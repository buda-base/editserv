package io.bdrc.edit.commons;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
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
import io.bdrc.libraries.Prefixes;

public class CommonsRead {

    public final static Logger log = LoggerFactory.getLogger("default");

    public static List<Resource> FOCUS_SHAPES;
    public static final String GRAPH_NAME_TYPE = "graph_name_type";
    public static final String GRAPH_URI_TYPE = "graph_uri_type";
    public static final String GRAPH_RESOURCE = "graph_resource";

    public static String SHAPES_SCHEMA = "http://purl.bdrc.io/graph/shapesSchema";

    public static Property NODE_SHAPE_TYPE = ResourceFactory.createProperty(EditConstants.BDS + "nodeShapeType");
    public static Property SHACL_PROP = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#property");
    public static Property SHACL_PATH = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#path");
    public static Resource IGNORED_SHAPE = ResourceFactory.createResource(EditConstants.BDS + "IgnoreShape");

    static {
        FOCUS_SHAPES = new ArrayList<>();
        FOCUS_SHAPES.add(ResourceFactory.createResource(EditConstants.BDS + "FacetShape"));
        FOCUS_SHAPES.add(ResourceFactory.createResource(EditConstants.BDS + "InternalShape"));
    }

    public static Model getGraph(String graphUri) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        String rootId = "";
        if (graphUri.indexOf("/") > 0 && !graphUri.startsWith(Models.BDR)) {
            throw new UnknownBdrcResourceException(graphUri + " is not a BDRC resource Uri");
        }
        if (graphUri.indexOf("/") == -1 && !graphUri.startsWith("bdr:")) {
            throw new UnknownBdrcResourceException(graphUri + " is not a BDRC resource Uri");
        }
        if (graphUri.indexOf("/") > 0) {
            rootId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        }
        if (graphUri.indexOf("/") == -1) {
            rootId = graphUri.substring(graphUri.lastIndexOf(":") + 1);
        }
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

    public static List<String> getIgnoredUris(String prefixedType) throws IOException, ParameterFormatException {
        Model m = getShapesForType(prefixedType);
        List<String> shapesUris = new ArrayList<>();
        ResIterator itFacet = m.listSubjectsWithProperty(ResourceFactory.createProperty(EditConstants.BDS + "nodeShapeType"), IGNORED_SHAPE);
        while (itFacet.hasNext()) {
            String url = itFacet.next().getURI();
            shapesUris.add(url.replace("purl.bdrc.io", EditConfig.getProperty("serverRoot")));
        }
        return shapesUris;
    }

    public static Model getShapesForType(String prefixedType) throws IOException, ParameterFormatException {
        String shortName = prefixedType.substring(prefixedType.indexOf(":") + 1);
        System.out.println("GRAPH URI : " + EditConstants.BDG + shortName + "Shapes");
        Model m = QueryProcessor.getGraph(EditConstants.BDG + shortName + "Shapes");
        return m;
    }

    public static Model getUIShapesForType(String prefixedType) throws IOException, ParameterFormatException {
        String shortName = prefixedType.substring(prefixedType.indexOf(":") + 1);
        Model m = QueryProcessor.getGraph(EditConstants.BDG + shortName + "UIShapes");
        return m;
    }

    public static List<String> getBestShapes(String prefixedUri)
            throws UnknownBdrcResourceException, NotModifiableException, IOException, ParameterFormatException {
        List<String> shapesUris = new ArrayList<>();
        String shortName = prefixedUri.substring(prefixedUri.indexOf(":") + 1);
        Model m = getGraph(EditConstants.BDR + shortName);
        NodeIterator it = m.listObjectsOfProperty(ResourceFactory.createResource(EditConstants.BDR + shortName), RDF.type);
        String typeUri = it.next().asResource().getURI();
        Model mod = getShapesForType(typeUri.replace(EditConstants.BDO, "bdo:"));
        // 1. Getting the uri of the rootShape resource
        NodeIterator itRoot = mod.listObjectsOfProperty(ResourceFactory.createResource(typeUri),
                ResourceFactory.createProperty(EditConstants.BDS + "rootShape"));
        String uri = itRoot.next().asResource().getURI();
        shapesUris.add(uri.replace("purl.bdrc.io", EditConfig.getProperty("serverRoot")));
        // 2. Getting uris of facetShape resources
        for (Resource r : FOCUS_SHAPES) {
            ResIterator itFacet = mod.listSubjectsWithProperty(ResourceFactory.createProperty(EditConstants.BDS + "nodeShapeType"), r);
            while (itFacet.hasNext()) {
                String url = itFacet.next().getURI();
                shapesUris.add(url.replace("purl.bdrc.io", EditConfig.getProperty("serverRoot")));
            }
        }
        return shapesUris;
    }

    public static Model getAssociatedLabels(String prefixedUri) {
        String query = "construct {\n" + "  ?s skos:preflabel ?o. \n" + "  ?s rdfs:label ?o.} \n" + "where { { \n" + prefixedUri + " ?resp ?s . \n"
                + "  {  \n" + "  ?s skos:prefLabel ?o.\n" + "  }\n" + "  union{\n" + "      ?s rdfs:label ?o.\n" + "  }\n" + "}}";
        return QueryProcessor.getQueryGraph(null, query);
    }

    public static Model getEditorGraph(String prefRes, Model resMod, List<String> shapeUris)
            throws IOException, ParameterFormatException, UnknownBdrcResourceException, NotModifiableException {
        String shortName = prefRes.substring(prefRes.lastIndexOf(":") + 1);
        Model res = ModelFactory.createDefaultModel();
        List<String> propsUri = getFocusPropsFromShape(shapeUris, CommonsRead.GRAPH_NAME_TYPE, prefRes);
        Iterator<Statement> it = resMod.listStatements();
        while (it.hasNext()) {
            Statement stmt = it.next();
            String subject = stmt.getSubject().getURI();
            subject = subject.substring(subject.lastIndexOf("/") + 1);
            if (subject.equals(shortName)) {
                if (propsUri.contains(stmt.getPredicate().getURI()) && stmt.getSubject().getURI().equals(EditConstants.BDR + shortName)) {
                    res.add(stmt);
                    if (stmt.getObject().isResource()) {
                        SimpleSelector ss = new SimpleSelector(stmt.getObject().asResource(), (Property) null, (RDFNode) null);
                        // looking up -in res model- the statements pertaining to the object and add
                        // them to the focus graph for validation
                        StmtIterator stit = resMod.listStatements(ss);
                        while (stit.hasNext()) {
                            Statement st = stit.next();
                            res.add(st);
                        }
                    }
                }
            }
        }
        return res;
    }

    public static Model getEditorGraph(String prefRes)
            throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        return getEditorGraph(prefRes, getGraph(prefRes), getBestShapes(prefRes));
    }

    public static List<String> getFocusPropsFromShape(List<String> shapeGraphs, String sourceType, String prefixedUri)
            throws IOException, ParameterFormatException, UnknownBdrcResourceException, NotModifiableException {
        List<String> uris = new ArrayList<>();
        String shortName = prefixedUri.substring(prefixedUri.indexOf(":") + 1);
        Model mm = getGraph(EditConstants.BDR + shortName);
        NodeIterator it = mm.listObjectsOfProperty(ResourceFactory.createResource(EditConstants.BDR + shortName), RDF.type);
        String typeUri = it.next().asResource().getURI();
        Model mod = getShapesForType(typeUri.replace(EditConstants.BDO, "bdo:"));
        for (String uri : shapeGraphs) {
            Model m = ModelFactory.createDefaultModel();
            m.read(uri + ".ttl", "TTL");
            NodeIterator it1 = m.listObjectsOfProperty(SHACL_PROP);
            while (it1.hasNext()) {
                String rdf = it1.next().asResource().getURI();
                System.out.println("URIS >>" + rdf);
                uris.add(mod.createResource(rdf).getPropertyResourceValue(SHACL_PATH).getURI());
            }
        }
        System.out.println("URIS >>" + uris);
        return uris;
    }

    public static void main(String[] arg) throws IOException, ParameterFormatException, UnknownBdrcResourceException, NotModifiableException {
        EditConfig.init();
        Model res = getEditorGraph("bdr:P1583");
        res.setNsPrefixes(Prefixes.getPrefixMapping());
        res.write(System.out, "TTL");
    }

}
