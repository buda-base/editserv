package io.bdrc.edit.controllers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.auth.AccessInfo;
import io.bdrc.edit.EditConfig;
import io.bdrc.libraries.SparqlCommons;

@Controller
@RequestMapping("/")
public class BulkEditController {

    public final static Logger log = LoggerFactory.getLogger(BulkEditController.class.getName());

    /**
     * Replace references to res A by references to ref to B Mark res A with the
     * Withdrawn status.
     * 
     * Request params:
     * 
     * - withdrawn: the full uri of the resource to replace and mark as withdrawn -
     * - valid: the full uri of the replacement resource - fusekiUrl : the url of
     * the fuseki server
     */
    @PostMapping(value = "/bulk/withdraw")
    public ResponseEntity<String> withdrawn(HttpServletRequest req, HttpServletResponse response, @RequestBody String json) {
    	AccessInfo acc = (AccessInfo) req.getAttribute("access");
        if (!acc.hasEndpointAccess()) {
            log.info("An authorized user is required for this operation " + json);
            return new ResponseEntity<>("An authorized user user is required for this operation " + json, HttpStatus.UNAUTHORIZED);
        }
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            String withdrawn = node.findValue("withdrawn").asText();
            String valid = node.findValue("valid").asText();
            String fusekiUrl = node.findValue("fusekiUrl").asText();
            // TODO!
            //BulkOps.replaceAllDuplicateByValid(withdrawn, valid, fusekiUrl);
        } catch (Exception e) {
            log.error("A error occured while processing withdraw operation for " + json);
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(req.getRemoteAddr(), HttpStatus.OK);
    }

    /**
     * Replace references to res A by references to ref to B Mark res A with the
     * Withdrawn status.
     * 
     * Request required params:
     * 
     * - prop: the full uri of the property to which we add the prop
     * 
     * - value: the full uri of the replacement resource or the literal value of the
     * prop
     * 
     * - literal : boolean (if true, the prop value is a literal, if false, it's a
     * resource)
     * 
     * - lang : the language of the literal value, if any
     * 
     * - graphs : empty string OR the list of uris of the graphs affected by the
     * update (separated by a semi-colon in one single string)
     * 
     * - sparql : empty string OR a sparql request whose result vars are ?g and ?rep
     * where g is a graphUri and rep its git repo object uri
     * 
     * - add : boolean ; should we add a value to this prop or simply set it as its
     * unique value
     * 
     * - fusekiUrl : the url of the fuseki server
     */
    @PostMapping(value = "/bulk/propValue")
    public ResponseEntity<String> addPropVal(HttpServletRequest req, HttpServletResponse response, @RequestBody String json) {
    	AccessInfo acc = (AccessInfo) req.getAttribute("access");
        if (!acc.hasEndpointAccess()) {
            log.info("An authorized user is required for this operation " + json);
            return new ResponseEntity<>("An authorized user user is required for this operation " + json, HttpStatus.UNAUTHORIZED);
        }
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            String value = node.findValue("value").asText();
            String graphs = node.findValue("graphs").asText();
            String sparql = node.findValue("sparql").asText();
            String fusekiUrl = node.findValue("fusekiUrl").asText();
            boolean isLiteral = Boolean.parseBoolean(node.findValue("literal").asText());
            boolean add = Boolean.parseBoolean(node.findValue("add").asText());
            Property p = ResourceFactory.createProperty(node.findValue("property").asText());
            String lang = null;
            if (node.findValue("lang") != null) {
                if (node.findValue("graphs").asText().trim().length() > 0) {
                    lang = node.findValue("graphs").asText();
                }
            }
            
            /*
             * TODO!
             *
            
            // graphs param is not empty : building graphs list
            if (!graphs.equals("")) {
                ArrayList<String> graphUris = (ArrayList<String>) Arrays.asList(graphs.split(";"));
                if (isLiteral) {
                    if (add) {
                        BulkOps.addLiteralValueForModels(graphUris, p, value, lang, fusekiUrl);
                    } else {
                        BulkOps.setLiteralValueForModels(graphUris, p, value, lang, fusekiUrl);
                    }
                } else {
                    if (add) {
                        BulkOps.addPropValueForModels(graphUris, p, ResourceFactory.createResource(value), fusekiUrl);
                    } else {
                        BulkOps.setPropValueForModels(graphUris, p, ResourceFactory.createResource(value), fusekiUrl);
                    }
                }

            } else {
                // using sparql request
                if (!sparql.equals("")) {
                    HashMap<String, ArrayList<String>> map = SparqlCommons.getGraphsByGitRepos(sparql, fusekiUrl, EditConfig.prefix.getPrefixesString());
                    if (isLiteral) {
                        if (add) {
                            BulkOps.addLiteralValueForModels(map, p, value, lang, fusekiUrl);
                        } else {
                            BulkOps.setLiteralValueForModels(map, p, value, lang, fusekiUrl);
                        }
                    } else {
                        if (add) {
                            BulkOps.addPropValueForModels(map, p, ResourceFactory.createResource(value), fusekiUrl);
                        } else {
                            BulkOps.setPropValueForModels(map, p, ResourceFactory.createResource(value), fusekiUrl);
                        }
                    }

                } else {
                    log.error("No graph list or valid sparql has been found to set prop value using params :" + json);
                    return new ResponseEntity<>("No graph list or valid sparql has been found to set prop value using params :" + json,
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
            */

        } catch (Exception e) {
            log.error("A error occured while processing withdraw operation for " + json);
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(req.getRemoteAddr(), HttpStatus.OK);
    }

    /**
     * Replace references to res A by references to ref to B Mark res A with the
     * Withdrawn status.
     * 
     * Request required params:
     * 
     * - oldProp: the full uri of the property to which we add the prop
     * 
     * - newProp: the full uri of the replacement resource or the literal value of
     * the prop
     *
     * - graphs : empty string OR the list of uris of the graphs affected by the
     * update (separated by a semi-colon in one single string)
     * 
     * - sparql : empty string OR a sparql request whose result vars are ?g and ?rep
     * where g is a graphUri and rep its git repo object uri
     *
     * - fusekiUrl : the url of the fuseki server
     */
    @PostMapping(value = "/bulk/renameProp")
    public ResponseEntity<String> renameProp(HttpServletRequest req, HttpServletResponse response, @RequestBody String json) {
    	AccessInfo acc = (AccessInfo) req.getAttribute("access");
        if (!acc.hasEndpointAccess()) {
            log.info("An authorized user is required for this operation " + json);
            return new ResponseEntity<>("An authorized user user is required for this operation " + json, HttpStatus.UNAUTHORIZED);
        }
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            String oldProp = node.findValue("oldProp").asText();
            String newProp = node.findValue("newProp").asText();
            String graphs = node.findValue("graphs").asText();
            String sparql = node.findValue("sparql").asText();
            String fusekiUrl = node.findValue("fusekiUrl").asText();

            // graphs param is not empty : building graphs list
            if (!graphs.equals("")) {
                ArrayList<String> graphUris = (ArrayList<String>) Arrays.asList(graphs.split(";"));
                // TODO!
                //BulkOps.renameProp(graphUris, oldProp, newProp, fusekiUrl);

            } else {
                // using sparql request
                if (!sparql.equals("")) {
                    HashMap<String, ArrayList<String>> map = SparqlCommons.getGraphsByGitRepos(sparql, fusekiUrl, EditConfig.prefix.getPrefixesString());
                    // TODO!
                    //BulkOps.renameProp(map, oldProp, newProp, fusekiUrl);
                } else {
                    log.error("No graph list or valid sparql has been found to rename prop using params :" + json);
                    return new ResponseEntity<>("No graph list or valid sparql has been found to rename prop using params :" + json,
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }

        } catch (Exception e) {
            log.error("A error occured while processing renameProp operation for " + json);
            e.printStackTrace();
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(req.getRemoteAddr(), HttpStatus.OK);
    }

    public String getJsonErrorString(Exception e) {
        return "{ \"exception\": \"" + e.getClass().getCanonicalName() + "\",\n" + "    \"error\": \"" + e.getMessage() + "\"}";
    }
}
