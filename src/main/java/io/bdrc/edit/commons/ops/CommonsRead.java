package io.bdrc.edit.commons.ops;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import io.bdrc.edit.controllers.RIDController;
import io.bdrc.edit.helpers.Shapes;
import io.bdrc.edit.txn.exceptions.NotModifiableException;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;
import io.bdrc.edit.txn.exceptions.UnknownBdrcResourceException;
import io.bdrc.libraries.Models;

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
    public static Property TOP_SHAPE = ResourceFactory.createProperty(EditConstants.BDS + "topShapeGraph");
    public static Property UI_SHAPE = ResourceFactory.createProperty(EditConstants.BDS + "uiShapeGraph");
    public static HashMap<String, Model> ENTITY_MAP;
    static final String ONT_GRAPH_URL = "/graph/ontologySchema.ttl";
    
    public final static Map<String,String> shapeUriToFocusSparql = new HashMap<>();
    
    public final static Map<String,Resource> prefixToTopShape = new HashMap<>();
    static {
        prefixToTopShape.put("WAS", ResourceFactory.createResource(EditConstants.BDS+"SerialWorkShape"));
        prefixToTopShape.put("ITW", ResourceFactory.createResource(EditConstants.BDS+"ItemShape"));
        prefixToTopShape.put("PRA", ResourceFactory.createResource(EditConstants.BDS+"SubscriberShape"));
        prefixToTopShape.put("WA", ResourceFactory.createResource(EditConstants.BDS+"WorkShape"));
        prefixToTopShape.put("MW", ResourceFactory.createResource(EditConstants.BDS+"InstanceShape"));
        prefixToTopShape.put("PR", ResourceFactory.createResource(EditConstants.BDS+"CollectionShape"));
        prefixToTopShape.put("IE", ResourceFactory.createResource(EditConstants.BDS+"EtextInstanceShape"));
        //prefixToTopShape.put("UT", "etexts");
        prefixToTopShape.put("IT", ResourceFactory.createResource(EditConstants.BDS+"ItemShape"));
        prefixToTopShape.put("W", ResourceFactory.createResource(EditConstants.BDS+"ImageInstanceShape"));
        prefixToTopShape.put("P", ResourceFactory.createResource(EditConstants.BDS+"PersonShape"));
        prefixToTopShape.put("G", ResourceFactory.createResource(EditConstants.BDS+"PlaceShape"));
        prefixToTopShape.put("R", ResourceFactory.createResource(EditConstants.BDS+"RoleShape"));
        prefixToTopShape.put("L", ResourceFactory.createResource(EditConstants.BDS+"LineageShape"));
        prefixToTopShape.put("C", ResourceFactory.createResource(EditConstants.BDS+"CorporationShape"));
        prefixToTopShape.put("T", ResourceFactory.createResource(EditConstants.BDS+"TopicShape"));
    }

    static {
        FOCUS_SHAPES = new ArrayList<>();
        FOCUS_SHAPES.add(ResourceFactory.createResource(EditConstants.BDS + "FacetShape"));
        FOCUS_SHAPES.add(ResourceFactory.createResource(EditConstants.BDS + "InternalShape"));
        ENTITY_MAP = new HashMap<>();
    }

    public static Resource getShapeForEntity(final String lname) {
        final String typePrefix = RIDController.getTypePrefix(lname);
        return prefixToTopShape.get(typePrefix);
    }

    public static final class ShaclProps {
        // recursive type, map is sparql path as key, ShaclProps as object
        // object is non-null only in the case of facets (not datatype properties or external properties)
        public Map<String, ShaclProps> properties = null;
        public int depth = 0;
    }
    
    public static ShaclProps getShaclPropsFor(Resource shape) {
        StmtIterator it = Shapes.fullMod.listStatements(shape, EditConstants.SH_PROPERTY, (RDFNode) null);
        ShaclProps res = new ShaclProps();
        while (it.hasNext()) {
            final Resource shProp = it.next().getResource();
            final Resource path = shProp.getPropertyResourceValue(EditConstants.SH_PATH);
            Resource pathInverse = null;
            String sparqlPath = null;
            boolean pathIsInverse = false;
            if (path != null && !path.isAnon()) {
                sparqlPath = path.getURI();
            } else if (path != null && path.isAnon()) {
                pathInverse = shProp.getPropertyResourceValue(EditConstants.SH_INVERSE_PATH);
                if (pathInverse != null) {
                    sparqlPath = "^"+pathInverse.getURI();
                    pathIsInverse = true;
                }
            }
            if (sparqlPath == null)
                continue;
            ShaclProps sp = null;
            final Resource shapeType = shProp.getPropertyResourceValue(EditConstants.PROPERTY_SHAPE_TYPE);
            if (EditConstants.FACET_SHAPE.equals(shapeType)) {
                // find shape that shapes the object of the property:
                if (pathIsInverse) {
                    StmtIterator shapeForObjectIt = Shapes.fullMod.listStatements(null, EditConstants.SH_TARGETOBJECTSOF, path);
                    if (shapeForObjectIt.hasNext()) {
                        Resource subShape = shapeForObjectIt.next().getSubject();
                        sp = getShaclPropsFor(subShape);
                    }
                } else {
                    StmtIterator shapeForObjectIt = Shapes.fullMod.listStatements(null, EditConstants.SH_TARGETSUBJECTSOF, pathInverse);
                    if (shapeForObjectIt.hasNext()) {
                        Resource subShape = shapeForObjectIt.next().getSubject();
                        sp = getShaclPropsFor(subShape);
                    }
                }
            }
            if (sp != null && sp.depth >= res.depth)
                res.depth = sp.depth +1;
            if (res.depth == 0)
                res.depth = 1;
            if (res.properties == null)
                res.properties = new HashMap<>();
            res.properties.put(sparqlPath, sp);
        }
        return res;
    }
    
    public static List<String> getExternalUris(String prefixedType) throws IOException, ParameterFormatException {
        List<String> shapesUris = new ArrayList<>();
        ResIterator itFacet = m.listSubjectsWithProperty(ResourceFactory.createProperty(EditConstants.BDS + "nodeShapeType"), EXTERNAL_SHAPE);
        while (itFacet.hasNext()) {
            String url = itFacet.next().getURI();
            shapesUris.add(url.replace("purl.bdrc.io", EditConfig.getProperty("serverRoot")));
        }
        return shapesUris;
    }

    public static String uriFromQname(String qname) {
        return (EditConstants.BDR + qname.substring(qname.indexOf(":") + 1));
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
        res.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        return res;
    }

    public static Model getEditorGraph(String qname, Model m)
            throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        return getEditorGraph(qname, m, getBestShapes(qname, m));
    }

    public static Model getEditorGraph(String qname)
            throws IOException, UnknownBdrcResourceException, NotModifiableException, ParameterFormatException {
        final Model resM = CommonsGit.getGraphFromGit(qname);
        return getEditorGraph(qname, resM, getBestShapes(qname, resM));
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

    public static final Property admGraphId = ResourceFactory.createProperty(Models.ADM + "graphId");
    public static final Property gitRevision = ResourceFactory.createProperty(Models.ADM + "gitRevision");
    public static String getCommit(final Model m, final String graphUri) {
        final Resource graph = ResourceFactory.createResource(graphUri);
        final StmtIterator si = m.listStatements(null, admGraphId, graph);
        if (!si.hasNext())
            return null;
        final Resource admin = si.next().getSubject();
        SimpleSelector s = new SimpleSelector(admin, gitRevision, (RDFNode) null);
        StmtIterator it = m.listStatements(s);
        if (it.hasNext()) {
            Statement st = it.next();
            if (st.getObject().isLiteral()) {
                return st.getObject().asLiteral().toString();
            }
        }
        return null;
    }

    public static void main(String[] arg) throws Exception {
        EditConfig.init();
        //System.out.println("BEST SHAPES >> " + getBestShapes("bdr:P707", null));
        Model res = getEditorGraph("bdr:P1019");
        System.out.println("---------------------------------------------------");
        res.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
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
