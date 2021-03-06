package io.bdrc.edit.controllers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.auth.Access;
import io.bdrc.edit.EditConfig;

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
                prefixIndexes = mapper.readValue(f, typeRef);
            } catch (IOException e) {
                prefixIndexes = null;
                log.error("cannot read prefix indexes file", e);
                return;
            }
            return;
        }
        final ClassLoader classLoader = RIDController.class.getClassLoader();
        final InputStream inputStream = classLoader.getResourceAsStream("prefix-idx.json");
        try {
            prefixIndexes = mapper.readValue(inputStream, typeRef);
        } catch (IOException e) {
            prefixIndexes = null;
            log.error("cannot read prefix indexes file", e);
            return;
        }
    }
    
    public static void writePrefixIndexes() throws JsonGenerationException, JsonMappingException, IOException {
        File f = new File(fileName);
        mapper.writeValue(f, prefixIndexes);
    }
    
    public static final List<String> entity_prefix_3 = Arrays.asList("WAS", "ITW", "PRA");
    public static final List<String> entity_prefix_2 = Arrays.asList("WA", "MW", "PR", "IE", "UT", "IT");
    public static final List<String> entity_prefix_1 = Arrays.asList("W", "P", "G", "R", "L", "C", "T", "I");
    public static final List<String> entitySubs = Arrays.asList("I", "UT");
    
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
    
    public static String lastId(final String prefix) {
        Integer last = prefixIndexes.get(prefix);
        if (last == null)
            return null;
        return prefix+String.valueOf(last);
    }
    
    public static boolean idExists(final String id) {
        final String query = "ASK  { { <http://purl.bdrc.io/resource/"+id+"> ?p ?o } union { ?s ?p <http://purl.bdrc.io/resource/"+id+"> } }";
        final Query q = QueryFactory.create(query);
        final String fusekiUrl = EditConfig.getProperty("fusekiData");
        final QueryExecution qe = QueryExecutionFactory.sparqlService(fusekiUrl, q);
        boolean res = qe.execAsk();
        return res;
    }
    
    public static synchronized Integer getNextIndex(final String prefix) {
        Integer guess = prefixIndexes.get(prefix);
        if (guess == null)
            return 1;
        guess = guess+1;
        while (idExists(prefix+String.valueOf(guess))) {
            guess += 100;
        }
        prefixIndexes.put(prefix, guess);
        try {
            writePrefixIndexes();
        } catch (Exception e) {
            log.error("can't write index file", e);
            return null;
        }
        return guess;
    }
    
    // gets the largest ID
    // no admin rights required
    @GetMapping(value = "/ID/prefix/{prefix}")
    public ResponseEntity<String> getLatestID(@PathVariable("prefix") String prefix) {
        String latestId = lastId(prefix);
        if (latestId == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok().body(latestId);
    }
    
    // creates a new ID in a prefix, requires admin rights
    @PutMapping(value = "/ID/prefix/{prefix}")
    public ResponseEntity<String> reserveNextID(@PathVariable("prefix") String prefix, HttpServletRequest request) {
        Access acc = (Access) request.getAttribute("access");
        if (acc == null || !acc.isUserLoggedIn())
            return ResponseEntity.status(401).body("this requires being logged in with an admin account");
        if (!acc.getUserProfile().isAdmin())
            return ResponseEntity.status(403).body("this requires being logged in with an admin account");
        if (!prefixIsValid(prefix))
            return ResponseEntity.status(400).body("invalid prefix");
        Integer nextIdx = getNextIndex(prefix);
        if (nextIdx == null) {
            return ResponseEntity.status(500).body("can't determine next ID for this prefix");
        }
        final String res = prefix+String.valueOf(nextIdx);
        log.info("reserving "+res+" for "+acc.getUser().getUserId());
        return ResponseEntity.ok().body(res);
    }
    
}
