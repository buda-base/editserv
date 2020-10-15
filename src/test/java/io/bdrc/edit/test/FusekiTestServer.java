package io.bdrc.edit.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;

public class FusekiTestServer {
    
    public static String fusekiUrl = "http://localhost:2252/testdata/";
    public static String CORE_FUSEKI;
    public static String TEST_DATA_DIR;
    private Dataset srvds = DatasetFactory.createTxnMem();
    private FusekiServer.Builder builder;
    private FusekiServer server;
    
    public FusekiTestServer() {
        try {
            EditConfig.initForTests(fusekiUrl);
            CORE_FUSEKI=EditConfig.getProperty("fusekiUrl");
            TEST_DATA_DIR=EditConfig.getProperty("testdataDir");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        builder=FusekiServer.create().port(2252); 
        server=builder.add("/testdata", srvds).build();
        server.start();
    }
    
    public void start() {
        server.start();
    }
    
    public void stop() {
        server.stop();
    }
   
    
    //Loads named graphs from Production fuseki give a testName
    //Tis testname correspond to a comma delimited list of names in test.properties
    public void initNamedGraphFromFuseki(List<String> resourceNames) {
        RDFConnectionFuseki rvf = RDFConnectionFactory.connectFuseki(fusekiUrl+"data");
        for(String uri:resourceNames) {
            String graph=EditConstants.BDG+uri+".ttl";
            Model tmp=ModelFactory.createDefaultModel();
            tmp.read(graph, "Turtle");            
            rvf.put(EditConstants.BDG+uri,tmp);
        }        
    }
    
    //Loads named graphs from local dir
    //gets all the ttls in the directory: the file names are the graph names
    //P705_missingSon.ttl gives http://purl.bdrc.io/graph/P705_missingSon in the fuseki dataset
    public void initNamedGraphFromLocalDir(String dataDir) throws IOException {
        RDFConnectionFuseki rvf = RDFConnectionFactory.connectFuseki(fusekiUrl+"data");
        for(String file:dataFiles(dataDir)) {
            InputStream in=new FileInputStream(file);
            Model tmp=ModelFactory.createDefaultModel();
            tmp.read(in, null,"TTL");
            in.close();
            String graphName=file.substring(file.lastIndexOf("/")+1);
            graphName=graphName.substring(0,graphName.indexOf("."));                               
            rvf.put(EditConstants.BDG+graphName,tmp);
        } 
        rvf.close();
    }
    
    //Loads named graphs from Production fuseki give a testName
    //Tis testname correspond to a comma delimited list of names in test.properties
    public void initNamedGraphFromFuseki(String testName) {
        RDFConnectionFuseki rvf = RDFConnectionFactory.connectFuseki(fusekiUrl+"data");
        List<String> resourceNames=Arrays.asList(EditConfig.getProperty(testName).split(","));
        for(String uri:resourceNames) {
            String graph=EditConstants.BDG+uri.trim()+".ttl";
            Model tmp=ModelFactory.createDefaultModel();
            tmp.read(graph, "Turtle");            
            rvf.put(EditConstants.BDG+uri.trim(),tmp);
        }        
    }
    
    public List<String> dataFiles(String dir) {
        List<String> files = new ArrayList<>();
        Path dpath = Paths.get(TEST_DATA_DIR + dir);
        
        try {
            Stream<Path> walk;
            walk = Files.walk(dpath);
            files = walk.map(x -> x.toString()).filter(f -> f.endsWith(".ttl")).collect(Collectors.toList());
            walk.close();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return files;
    }
    
    public static void main(String...arg) throws IOException {
        FusekiTestServer testServer=new FusekiTestServer();
        try {
            
            List<String> resNames=new ArrayList<>();
            resNames.add("P705_missingSon");
            resNames.add("P705");
            //testServer.initNamedGraphFromFuseki(resNames);
            //testServer.initNamedGraphFromFuseki("test1");
            testServer.initNamedGraphFromLocalDir("dataset1");
            RDFConnectionFuseki rvf = RDFConnectionFactory.connectFuseki(fusekiUrl);        
        for(String s:resNames) {
           System.out.println("/************* Graph for >>"+EditConstants.BDG+s+" *********************/");  
           rvf.fetch(EditConstants.BDG+s).write(System.out,"TURTLE");
        } 
            rvf.close();
        }
        catch(Exception ex) {
            testServer.stop();
        }
        testServer.stop();
    }
}
