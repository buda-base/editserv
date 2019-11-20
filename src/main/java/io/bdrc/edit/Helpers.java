package io.bdrc.edit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.RDFSRuleReasonerFactory;
import org.apache.jena.vocabulary.ReasonerVocabulary;

import io.bdrc.edit.patch.Task;
import io.bdrc.edit.txn.TransactionLog;

public class Helpers {

    public static List<String> getFileList(String dir, String ext) throws IOException {
        List<String> files = new ArrayList<>();
        Path dpath = Paths.get(dir);
        Stream<Path> walk = Files.walk(dpath);
        files = walk.map(x -> x.toString()).filter(f -> f.endsWith(ext)).collect(Collectors.toList());
        walk.close();
        return files;
    }

    public static TreeMap<Long, TransactionLog> getLogsForUser(String user) throws IOException {
        // This tree map is ordered by last modified (as long)
        List<String> list = Helpers.getFileList(EditConfig.getProperty("logRootDir"), ".log");
        TreeMap<Long, TransactionLog> map = new TreeMap<>();
        for (String s : list) {
            if (s.contains(EditConfig.getProperty("logRootDir") + user + "/")) {
                String content = readFileContent(s);
                TransactionLog log = TransactionLog.create(content);
                map.put(new Long(new File(s).lastModified()), log);
                // System.out.println(TransactionLog.asJson(log));
            }
        }
        return map;
    }

    public static TreeMap<Long, Task> getAllTransactions() throws IOException {
        // This tree map is ordered by last modified (as long)
        List<String> list = Helpers.getFileList(EditConfig.getProperty("gitTransactDir"), ".patch");
        TreeMap<Long, Task> map = new TreeMap<>();
        for (String s : list) {
            String content = readFileContent(s);
            Task tk = Task.create(content);
            map.put(new Long(new File(s).lastModified()), tk);
            // System.out.println(tk);

        }
        return map;
    }

    public static TreeMap<Long, Task> getUserTransactions(String user) throws IOException {
        // This tree map is ordered by last modified (as long)
        List<String> list = Helpers.getFileList(EditConfig.getProperty("gitTransactDir"), ".patch");
        TreeMap<Long, Task> map = new TreeMap<>();
        for (String s : list) {
            String content = readFileContent(s);
            Task tk = Task.create(content);
            if (tk.getUser().equals(user)) {
                map.put(new Long(new File(s).lastModified()), tk);
            }
        }
        return map;
    }

    public static String readFileContent(String filename) throws IOException {
        File f = new File(filename);
        FileInputStream fis = new FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        fis.read(b);
        fis.close();
        return new String(b, "UTF-8");
    }

    public static Reasoner getRDFSRuleReasoner() {
        Resource config = ModelFactory.createDefaultModel().createResource().addProperty(ReasonerVocabulary.PROPsetRDFSLevel, "default");
        config.addProperty(ReasonerVocabulary.PROPruleMode, "hybrid");
        Reasoner reasoner = RDFSRuleReasonerFactory.theInstance().create(config);
        // reasoner.getReasonerCapabilities().write(System.out, "TURTLE");
        return reasoner;
    }

    public static void main(String[] args) throws IOException {
        EditConfig.init();
        System.out.println(Helpers.getFileList(EditConfig.getProperty("logRootDir"), ".log"));
        System.out.println(Helpers.getLogsForUser("marc"));
        System.out.println(Helpers.getAllTransactions());
        System.out.println(Helpers.getUserTransactions("marc"));
        Helpers.getRDFSRuleReasoner();
    }

}
