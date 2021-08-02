package io.bdrc.edit.commons.ops;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.controllers.RIDController;
import io.bdrc.edit.helpers.Shapes;
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
        public Map<String, Resource> properties = null;
    }
    
    public static Map<Resource, ShaclProps> nodeShapesToProps = new HashMap<>();
    
    public static ShaclProps getShaclPropsFor(Resource shape) {
        if (nodeShapesToProps.containsKey(shape)) {
            return nodeShapesToProps.get(shape);
        }
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
            Resource subShape = null;
            final Resource shapeType = shProp.getPropertyResourceValue(EditConstants.PROPERTY_SHAPE_TYPE);
            if (EditConstants.FACET_SHAPE.equals(shapeType)) {
                // find shape that shapes the object of the property:
                if (pathIsInverse) {
                    StmtIterator shapeForObjectIt = Shapes.fullMod.listStatements(null, EditConstants.SH_TARGETOBJECTSOF, path);
                    if (shapeForObjectIt.hasNext()) {
                        subShape = shapeForObjectIt.next().getSubject();
                    }
                } else {
                    StmtIterator shapeForObjectIt = Shapes.fullMod.listStatements(null, EditConstants.SH_TARGETSUBJECTSOF, pathInverse);
                    if (shapeForObjectIt.hasNext()) {
                        subShape = shapeForObjectIt.next().getSubject();
                    }
                }
            }
            if (res.properties == null)
                res.properties = new HashMap<>();
            res.properties.put(sparqlPath, subShape);
        }
        return res;
    }
    
    public static void addToFocusGraph(final Model m, final Model fg, final Resource subject, final Resource shape) {
        final ShaclProps sp = getShaclPropsFor(shape);
        if (sp == null) 
            return;
        for (Entry<String,Resource> e : sp.properties.entrySet()) {
            final String path = e.getKey();
            final Resource subShape = e.getValue();
            if (path.startsWith("^")) {
                StmtIterator si = m.listStatements(null, m.createProperty(path.substring(1)), subject);
                while (si.hasNext()) {
                    final Statement s = si.next();
                    fg.add(s);
                    if (subShape != null)
                        addToFocusGraph(m, fg, s.getSubject(), subShape);
                }
            } else {
                StmtIterator si = m.listStatements(subject, m.createProperty(path), (RDFNode) null);
                while (si.hasNext()) {
                    fg.add(si.next());
                    final Statement s = si.next();
                    fg.add(s);
                    if (subShape != null)
                        addToFocusGraph(m, fg, s.getResource(), subShape);
                }
            }
        }
    }
    
    public static Model getFocusGraph(final Model m, final Resource subject, final Resource shape) {
        final Model res = ModelFactory.createDefaultModel();
        res.setNsPrefixes(m.getNsPrefixMap());
        addToFocusGraph(m, res, subject, shape);
        return res;
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
        //Model res = getEditorGraph("bdr:P1019");
        System.out.println("---------------------------------------------------");
        //res.setNsPrefixes(EditConfig.prefix.getPrefixMapping());
        //res.write(System.out, "TTL");
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
