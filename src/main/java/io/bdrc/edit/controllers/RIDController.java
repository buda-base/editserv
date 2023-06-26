package io.bdrc.edit.controllers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.auth.AccessInfo;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.txn.exceptions.EditException;

@Controller
@RequestMapping("/")
public class RIDController {
    public final static Logger log = LoggerFactory.getLogger(RIDController.class.getName());

    public static Map<String,Integer> prefixIndexes = null;
    public static final String fileName = "prefix-indexes.json";
    public static final ObjectMapper mapper = new ObjectMapper();
    public static final TypeReference<HashMap<String,Integer>> typeRef 
        = new TypeReference<HashMap<String,Integer>>() {};
    
    public static void initPrefixIndexes() {
        File f = new File(fileName);
        if (f.isFile()) {
            try {
                log.info("reading prefix file {}", f.getAbsolutePath());
                prefixIndexes = new ConcurrentHashMap<>(mapper.readValue(f, typeRef));
            } catch (IOException e) {
                prefixIndexes = null;
                log.error("cannot read prefix indexes file", e);
            }
            return;
        }
        log.info("didn't find prefix file, loading generic one");
        final ClassLoader classLoader = RIDController.class.getClassLoader();
        final InputStream inputStream = classLoader.getResourceAsStream("prefix-idx.json");
        try {
            prefixIndexes = new ConcurrentHashMap<>(mapper.readValue(inputStream, typeRef));
        } catch (IOException e) {
            prefixIndexes = null;
            log.error("cannot read prefix indexes file", e);
            return;
        }
    }
    
    public static synchronized void writePrefixIndexes() throws JsonGenerationException, JsonMappingException, IOException {
        File f = new File(fileName);
        log.info("write prefix values in {}", f.getAbsolutePath());
        mapper.writeValue(f, prefixIndexes);
    }
    
    public static final List<String> entity_prefix_3 = Arrays.asList("WAS", "ITW", "PRA");
    public static final List<String> entity_prefix_2 = Arrays.asList("WA", "MW", "PR", "IE", "UT", "IT");
    public static final List<String> entity_prefix_1 = Arrays.asList("W", "P", "G", "R", "L", "C", "T", "I", "U");
    public static final List<String> entitySubs = Arrays.asList("I", "UT");
    
    public static String getTypePrefix(final String lname) {
        if (lname.isEmpty()) return null;
        if (lname.length() >= 3 && entity_prefix_3.contains(lname.substring(0,3)))
            return lname.substring(0,3);
        if (lname.length() >= 2 && entity_prefix_2.contains(lname.substring(0,2)))
            return lname.substring(0,2);
        if (entity_prefix_1.contains(lname.substring(0,1)))
            return lname.substring(0,1);
        return null;
    }
    
    public static boolean prefixIsValid(final String prefix) {
        if (prefixIndexes.containsKey(prefix))
            return true;
        String rest;
        if (prefix.isEmpty()) return false;
        if (prefix.length() >= 3 && entity_prefix_3.contains(prefix.substring(0,3))) {
            rest = prefix.substring(3);
        } else if (prefix.length() >= 2 && entity_prefix_2.contains(prefix.substring(0,2))) {
            rest = prefix.substring(2);
        } else if (entity_prefix_1.contains(prefix.substring(0,1))) {
            rest = prefix.substring(1);
        } else {
            return false;
        }
        if (rest.isEmpty())
            return true;
        return rest.matches("[0-9][A-Z][A-Z]");
    }
    
    public static String prefixFromId(final String id) {
        return id.replaceAll("[0-9]*$", "");
    }
    
    public static String lastId(final String prefix) {
        Integer last = prefixIndexes.get(prefix);
        if (last == null)
            return null;
        return prefix+String.valueOf(last);
    }
    
    public static boolean idExists(final String id) {
        final String query = "ASK  { { <http://purl.bdrc.io/resource/"+id+"> ?p ?o } union { ?s ?p <http://purl.bdrc.io/resource/"+id+"> } }";
        final Query q = QueryFactory.create(query);
        log.error("Fuseki: "+FusekiWriteHelpers.FusekiSparqlEndpoint);
        final QueryExecution qe = QueryExecution.service(FusekiWriteHelpers.FusekiSparqlEndpoint).query(q).build();
        boolean res = qe.execAsk();
        log.info("id {} exists on fuseki? {}", id, res);
        // if not on Fuseki, we look on Git, just in case
        // we don't have image group git repository though (they are in the image instances)
        if (!res && !id.startsWith("I"))
            try {
                return CommonsGit.resourceExists(id);
            } catch (EditException e) {
                log.error("exception in idExists", e);
                return true;
            }
        return res;
    }
    
    public static final String foldToMW(final String prefix) {
        if (prefix.startsWith("WAS"))
            return prefix;
        if (prefix.startsWith("WA"))
            return "MW"+prefix.substring(2);
        if (prefix.startsWith("W"))
            return "M"+prefix;
        return prefix;
    }

    public static synchronized List<String> getNextIDs(final String prefix, final int n, final String foldedPrefix) {
        Integer guess = prefixIndexes.get(foldedPrefix);
        log.info("current index for {} (-> {}) is {}", prefix, foldedPrefix, guess);
        if (guess == null) 
            guess = 0;
        guess = guess+1;
        while (idExists(foldedPrefix+String.valueOf(guess))) {
            guess += 10;
        }
        log.info("guessed next index for {} to be {}", prefix, guess);
        // here we take a bit of a leap of faith and consider that the n next IDs are free
        prefixIndexes.put(foldedPrefix, guess+n-1);
        try {
            writePrefixIndexes();
        } catch (Exception e) {
            log.error("can't write index file", e);
            return null;
        }
        final List<String> res = new ArrayList<>();
        for (int i = guess; i < guess+n; i++) {
            res.add(prefix+String.valueOf(i));
        }
        return res;
    }
    
    // gets the largest ID
    // no admin rights required
    @GetMapping(value = "/ID/{prefix}")
    public ResponseEntity<String> getLatestID(@PathVariable("prefix") String prefix) {
        String latestId = lastId(prefix);
        if (latestId == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok().body(latestId);
    }
    
    // creates a new ID in a prefix, requires admin rights
    @PutMapping(value = "/ID/{prefix}")
    public ResponseEntity<String> reserveNextID(@PathVariable("prefix") String prefix, @RequestParam(value = "n", defaultValue = "1") Integer n, HttpServletRequest request) {
        if (EditConfig.useAuth) {
        	AccessInfo acc = (AccessInfo) request.getAttribute("access");
            if (acc == null || !acc.isLogged())
                return ResponseEntity.status(401).body("this requires being logged in");
            if (!acc.isAdmin() && !acc.isEditor() && !acc.isContributor())
                return ResponseEntity.status(403).body("this requires being logged in with an admin, editor or contributor account");
        }
        if (!prefixIsValid(prefix))
            return ResponseEntity.status(400).body("invalid prefix");
        final String foldedPrefix = foldToMW(prefix);
        final List<String> nextIdx = getNextIDs(prefix, n, foldedPrefix);
        if (nextIdx == null) {
            return ResponseEntity.status(500).body("can't determine next ID for this prefix");
        }
        final String res = StringUtils.join(nextIdx, "\n");
        log.info("reserving {}" , res);
        return ResponseEntity.ok().body(res);
    }
    
    // creates a new ID in a prefix, requires admin rights
    @PutMapping(value = "/ID/{prefix}/{ID}")
    public ResponseEntity<String> reserveIDEndpoint(@PathVariable("prefix") String prefix, @PathVariable("ID") String id, HttpServletRequest request) throws JsonGenerationException, JsonMappingException, IOException {
        if (EditConfig.useAuth) {
        	AccessInfo acc = (AccessInfo) request.getAttribute("access");
            if (acc == null || !acc.isLogged())
                return ResponseEntity.status(401).body("this requires being logged in");
            if (!acc.isAdmin() && !acc.isEditor() && !acc.isContributor())
                return ResponseEntity.status(403).body("this requires being logged in with an admin, editor or contributor account");
        }
        if (!prefixIsValid(prefix))
            return ResponseEntity.status(400).body("invalid prefix");
        if (!id.startsWith(prefix))
            return ResponseEntity.status(400).body("ID must start with prefix");
        final Integer idInt;
        try {
            idInt = Integer.parseInt(id.substring(prefix.length()));
        } catch (NumberFormatException ex){
            return ResponseEntity.status(400).body("invalid ID");
        }
        if (idExists(id)) {
            return ResponseEntity.status(422).body("a resource with this ID already exists");
        }
        Integer last = prefixIndexes.get(prefix);
        if (last == null)
            last = 0;
        if (last < idInt) {
            prefixIndexes.put(prefix, idInt);
            writePrefixIndexes();
        }
        log.info("reserving {}", id);
        return ResponseEntity.ok().body(id);
    }
    
    // creates a new ID, requires admin rights
    @PutMapping(value = "/ID/full/{ID}")
    public ResponseEntity<String> reserveFullID(@PathVariable("ID") String id, HttpServletRequest request) throws JsonGenerationException, JsonMappingException, IOException {
        if (EditConfig.useAuth) {
        	AccessInfo acc = (AccessInfo) request.getAttribute("access");
            if (acc == null || !acc.isLogged())
                return ResponseEntity.status(401).body("this requires being logged in");
            if (!acc.isAdmin() && !acc.isEditor() && !acc.isContributor())
                return ResponseEntity.status(403).body("this requires being logged in with an admin, editor or contributor account");
        }
        final String prefix = prefixFromId(id);
        if (!prefixIsValid(prefix))
            return ResponseEntity.status(400).body("invalid prefix "+prefix);
        final Integer idInt;
        try {
            idInt = Integer.parseInt(id.substring(prefix.length()));
        } catch (NumberFormatException ex){
            return ResponseEntity.status(400).body("invalid ID");
        }
        if (idExists(id)) {
            return ResponseEntity.status(422).body("a resource with this ID already exists");
        }
        Integer last = prefixIndexes.get(prefix);
        if (last == null)
            last = 0;
        if (last < idInt) {
            prefixIndexes.put(prefix, idInt);
            writePrefixIndexes();
        }
        log.info("reserving {}", id);
        return ResponseEntity.ok().body(id);
    }
    
    public static void reserveFullIdSimple(final String id) throws EditException {
        final String prefix = prefixFromId(id);
        if (!prefixIsValid(prefix))
            throw new EditException(400, "invalid prefix "+prefix);
        final Integer idInt;
        try {
            idInt = Integer.parseInt(id.substring(prefix.length()));
        } catch (NumberFormatException ex){
            throw new EditException(400, "invalid ID");
        }
        if (idExists(id)) {
            throw new EditException(422, "a resource with this ID already exists");
        }
        Integer last = prefixIndexes.get(prefix);
        if (last == null)
            last = 0;
        if (last < idInt) {
            prefixIndexes.put(prefix, idInt);
            try {
                writePrefixIndexes();
            } catch (IOException e) {
                throw new EditException(500, "cannot write prefixes", e);
            }
        }
        log.info("reserving {}", id);
    }
    
}
