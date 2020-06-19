package io.bdrc.edit.commons.ops;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
import io.bdrc.edit.commons.data.QueryProcessor;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.Models;
import io.bdrc.libraries.Prefixes;

public class CommonsRead {

    public final static Logger log = LoggerFactory.getLogger(CommonsRead.class);

    public static List<Resource> FOCUS_SHAPES;
    public static final String GRAPH_NAME_TYPE = "graph_name_type";
    public static final String GRAPH_URI_TYPE = "graph_uri_type";
    public static final String GRAPH_RESOURCE = "graph_resource";

    public static String SHAPES_SCHEMA = "http://purl.bdrc.io/graph/shapesSchema";

    public static Property NODE_SHAPE_TYPE = ResourceFactory.createProperty(EditConstants.BDS + "nodeShapeType");
    public static Property SHACL_PROP = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#property");
    public static Property SHACL_PATH = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#path");
    public static Resource EXTERNAL_SHAPE = ResourceFactory.createResource(EditConstants.BDS + "ExternalShape");
    public static Property LOCAL_SHAPE = ResourceFactory.createProperty(EditConstants.BDS + "localShapeGraph");
    public static Property TOP_SHAPE = ResourceFactory.createProperty(EditConstants.BDS + "topShapeGraph");
    public static Property UI_SHAPE = ResourceFactory.createProperty(EditConstants.BDS + "uiShapeGraph");
    public static HashMap<String, Model> ENTITY_MAP;
    static final String ONT_GRAPH_URL = "/graph/ontologySchema.ttl";

    static {
        FOCUS_SHAPES = new ArrayList<>();
        FOCUS_SHAPES.add(ResourceFactory.createResource(EditConstants.BDS + "FacetShape"));
        FOCUS_SHAPES.add(ResourceFactory.createResource(EditConstants.BDS + "InternalShape"));
        ENTITY_MAP = new HashMap<>();
    }

    public static Model getEntityModel(String prefixedUri) {
        Model m = ENTITY_MAP.get(prefixedUri);
        if (m == null) {
            String shortName = prefixedUri.substring(prefixedUri.lastIndexOf(":") + 1);
            m = QueryProcessor.describeModel(EditConstants.BDO + shortName);
            m.write(System.out, "TURTLE");
            ENTITY_MAP.put(prefixedUri, m);
        }
        return m;
    }

    public static String getLocalShapeUri(String entityPrefixedUri) {
        Model ent = getEntityModel(entityPrefixedUri);
        String shortName = entityPrefixedUri.substring(entityPrefixedUri.lastIndexOf(":") + 1);
        Statement st = ent.getProperty(ResourceFactory.createResource(EditConstants.BDO + shortName), LOCAL_SHAPE);
        if (st != null) {
            return st.getObject().asNode().getURI();
        }
        return null;
    }

    public static String getTopShapeUri(String entityPrefixedUri) {
        Model ent = getEntityModel(entityPrefixedUri);
        String shortName = entityPrefixedUri.substring(entityPrefixedUri.lastIndexOf(":") + 1);
        Statement st = ent.getProperty(ResourceFactory.createResource(EditConstants.BDO + shortName), TOP_SHAPE);
        if (st != null) {
            return st.getObject().asNode().getURI();
        }
        return null;
    }

    public static String getUIShapeUri(String entityPrefixedUri) {
        Model ent = getEntityModel(entityPrefixedUri);
        String shortName = entityPrefixedUri.substring(entityPrefixedUri.lastIndexOf(":") + 1);
        Statement st = ent.getProperty(ResourceFactory.createResource(EditConstants.BDO + shortName), UI_SHAPE);
        if (st != null) {
            return st.getObject().asNode().getURI();
        }
        return null;
    }

    public static Model getLocalShapeModel(String entityPrefixedUri) {
        Model m = QueryProcessor.getGraph(getLocalShapeUri(entityPrefixedUri));
        return m;
    }

    public static Model getTopShapeModel(String entityPrefixedUri) {
        Model m = QueryProcessor.getGraph(getTopShapeUri(entityPrefixedUri));
        return m;
    }

    public static Model getUIShapeModel(String entityPrefixedUri) {
        Model m = QueryProcessor.getGraph(getUIShapeUri(entityPrefixedUri));
        return m;
    }

    public static Model getValidationShapesForType(String entityPrefixedUri) {
        Model m = ModelFactory.createDefaultModel();
        m.add(getTopShapeModel(entityPrefixedUri));
        m.add(getLocalShapeModel(entityPrefixedUri));
        return m;
    }

    public static Model getValidationShapesForResource(String prefixedUri) throws UnknownBdrcResourceException, NotModifiableException, IOException {
        String entityPrefixedUri = getResourceTypeUri(prefixedUri, true);
        Model m = ModelFactory.createDefaultModel();
        m.add(getTopShapeModel(entityPrefixedUri));
        m.add(getLocalShapeModel(entityPrefixedUri));
        return m;
    }

    public static Model getFullDataValidationModel(Model model) {
        Model m = ModelFactory.createDefaultModel();
        m.read("http://" + EditConfig.getProperty("shapeServerRoot") + ONT_GRAPH_URL, "TTL");
        m.add(model);
        return m;
    }

    public static List<String> getExternalUris(String prefixedType) throws IOException, ParameterFormatException {
        Model m = getValidationShapesForType(prefixedType);
        List<String> shapesUris = new ArrayList<>();
        ResIterator itFacet = m.listSubjectsWithProperty(ResourceFactory.createProperty(EditConstants.BDS + "nodeShapeType"), EXTERNAL_SHAPE);
        while (itFacet.hasNext()) {
            String url = itFacet.next().getURI();
            shapesUris.add(url.replace("purl.bdrc.io", EditConfig.getProperty("serverRoot")));
        }
        return shapesUris;
    }

    public static String getResourceTypeUri(String prefixedUri, boolean prefixed)
            throws UnknownBdrcResourceException, NotModifiableException, IOException {
        String shortName = prefixedUri.substring(prefixedUri.indexOf(":") + 1);
        Model m = QueryProcessor.describeModel(EditConstants.BDR + shortName);
        NodeIterator it = m.listObjectsOfProperty(ResourceFactory.createResource(EditConstants.BDR + shortName), RDF.type);
        RDFNode n = it.next();
        if (prefixed) {
            String tmp = n.asResource().getURI();
            return "bdo:" + tmp.substring(tmp.lastIndexOf("/") + 1);

        } else {
            return n.asResource().getURI();
        }
    }

    public static String getFullResourceTypeUri(String prefixedUri, Model m, boolean prefixed)
            throws UnknownBdrcResourceException, NotModifiableException, IOException {
        log.info("Getting type of {} - prefixed= {}  model.size= {}", prefixedUri, prefixed, m.size());
        String shortName = "";
        if (prefixed) {
            shortName = prefixedUri.substring(prefixedUri.lastIndexOf(":") + 1);
        } else {
            shortName = prefixedUri.substring(prefixedUri.lastIndexOf("/") + 1);
        }
        log.info("ShortName of {} is {} - building Selector  with {}", prefixedUri, shortName, EditConstants.BDR + shortName);
        SimpleSelector ss = new SimpleSelector(ResourceFactory.createResource(EditConstants.BDR + shortName), RDF.type, (RDFNode) null);
        StmtIterator stit = m.listStatements(ss);
        return stit.next().getObject().asResource().getURI();
    }

    public static String getFullUriResourceFromPrefixed(String prefixedUri) {
        return (EditConstants.BDR + prefixedUri.substring(prefixedUri.indexOf(":") + 1));
    }

    public static List<String> getBestShapes(String prefixedUri)
            throws UnknownBdrcResourceException, NotModifiableException, IOException, ParameterFormatException {
        List<String> shapesUris = new ArrayList<>();
        String typeUri = getResourceTypeUri(prefixedUri, true);
        log.info("BEST SHAPES TYPE URIS {} ", typeUri);
        shapesUris.add(getLocalShapeUri(typeUri));
        log.info("BEST SHAPES LOCAL SHAPES URIS {} ", getLocalShapeUri(typeUri));
        shapesUris.add(getTopShapeUri(typeUri));
        log.info("BEST SHAPES URIS {} ", shapesUris);
        return shapesUris;
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
        List<String> ignoredProps = getExternalUris(prefRes);
        for (String ignore : ignoredProps) {
            SimpleSelector ss = new SimpleSelector((Resource) null, ResourceFactory.createProperty(ignore), (RDFNode) null);
            StmtIterator ignit = res.listStatements(ss);
            while (ignit.hasNext()) {
                Statement st = ignit.next();
                if (st.getPredicate().getURI().equals(ignore)) {
                    res.remove(st);
                }
            }
        }
        res.setNsPrefixes(Prefixes.getPrefixMapping());
        return res;
    }

    public static Model getEditorGraph(String prefRes, Model m)
            throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        return getEditorGraph(prefRes, m, getBestShapes(prefRes));
    }

    public static Model getEditorGraph(String prefRes)
            throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        return getEditorGraph(prefRes, CommonsGit.getGraphFromGit(prefRes), getBestShapes(prefRes));
    }

    public static List<String> getFocusPropsFromShape(List<String> shapeGraphs, String sourceType, String prefixedUri)
            throws IOException, ParameterFormatException, UnknownBdrcResourceException, NotModifiableException {
        List<String> uris = new ArrayList<>();
        String shortName = prefixedUri.substring(prefixedUri.indexOf(":") + 1);
        Model mm = CommonsGit.getGraphFromGit(EditConstants.BDR + shortName);
        NodeIterator it = mm.listObjectsOfProperty(ResourceFactory.createResource(EditConstants.BDR + shortName), RDF.type);
        String typeUri = it.next().asResource().getURI();
        Model mod = getValidationShapesForType(typeUri.replace(EditConstants.BDO, "bdo:"));
        for (String uri : shapeGraphs) {
            Model m = ModelFactory.createDefaultModel();
            m.read(uri + ".ttl", "TTL");
            NodeIterator it1 = m.listObjectsOfProperty(SHACL_PROP);
            while (it1.hasNext()) {
                RDFNode n = it1.next();
                if (n.asResource() != null) {
                    String rdf = n.asResource().getURI();
                    Resource r = mod.createResource(rdf).getPropertyResourceValue(SHACL_PATH);
                    if (r != null) {
                        uris.add(mod.createResource(rdf).getPropertyResourceValue(SHACL_PATH).getURI());
                    }
                }
            }
        }
        return uris;
    }

    public static String getCommit(Model m, String graphUri) {
        String commit = null;
        String shortName = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        SimpleSelector s = new SimpleSelector(ResourceFactory.createResource(Models.BDA + shortName),
                ResourceFactory.createProperty(Models.ADM + "gitRevision"), (RDFNode) null);
        log.info("Selector {}", ResourceFactory.createProperty(Models.ADM + "gitRevision"));
        StmtIterator it = m.listStatements(s);
        if (it.hasNext()) {
            Statement st = it.next();
            if (st.getObject().isLiteral()) {
                commit = st.getObject().asLiteral().toString();
            }
        }
        return commit;
    }

    public static void main(String[] arg) throws IOException, ParameterFormatException, UnknownBdrcResourceException, NotModifiableException {
        EditConfig.init();

        System.out.println("BEST SHAPES >> " + getBestShapes("bdr:P707"));
        Model res = getEditorGraph("bdr:P1583");
        System.out.println("---------------------------------------------------");
        res.setNsPrefixes(Prefixes.getPrefixMapping());
        res.write(System.out, "TTL");
        /*
         * System.out.println(getLocalShapeUri("bdo:Person"));
         * System.out.println(getTopShapeUri("bdo:Person"));
         * System.out.println(getUIShapeUri("bdo:Person"));
         * System.out.println(getLocalShapeModel("bdo:Person")); Model mm =
         * getValidationShapesForType("bdo:Person");
         * mm.setNsPrefixes(Prefixes.getMap()); mm.write(System.out, "TURTLE");
         */
    }

}
