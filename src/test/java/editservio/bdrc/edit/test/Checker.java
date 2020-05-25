package editservio.bdrc.edit.test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.TreeMap;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.libraries.GlobalHelpers;
import io.bdrc.libraries.Prefixes;

public class Checker {

    // Checks the resource ID is now the object of a given ?s ?p
    public static boolean checkResourceInConstruct(String checkFile, String resId) throws IOException {
        String q = PostTaskCheck.getResourceFileContent(checkFile);
        final Query qy = QueryFactory.create(Prefixes.getPrefixesString() + " " + q);
        final QueryExecution qe = QueryExecutionFactory.sparqlService(EditConfig.getProperty(EditConfig.FUSEKI_URL), qy);
        Model m = qe.execConstruct();
        NodeIterator ni = m.listObjects();
        while (ni.hasNext()) {
            RDFNode node = ni.next();
            if (node.asResource().getLocalName().equals(resId)) {
                return true;
            }
        }
        return false;
    }

    public static TreeMap<Long, TransactionLog> getLogsForUser(String user) throws IOException {
        // This tree map is ordered by last modified (as long)
        List<String> list = GlobalHelpers.getFileList(EditConfig.getProperty("logRootDir"), ".log");
        TreeMap<Long, TransactionLog> map = new TreeMap<>();
        for (String s : list) {
            if (s.contains(EditConfig.getProperty("logRootDir") + user + "/")) {
                String content = GlobalHelpers.readFileContent(s);
                TransactionLog log = TransactionLog.create(content);
                map.put(new Long(new File(s).lastModified()), log);
            }
        }
        return map;
    }

    public static TreeMap<Long, Task> getAllTransactions() throws IOException {
        // This tree map is ordered by last modified (as long)
        List<String> list = GlobalHelpers.getFileList(EditConfig.getProperty("gitTransactDir"), ".patch");
        TreeMap<Long, Task> map = new TreeMap<>();
        for (String s : list) {
            String content = GlobalHelpers.readFileContent(s);
            Task tk = Task.create(content);
            map.put(new Long(new File(s).lastModified()), tk);
        }
        return map;
    }

    public static TreeMap<Long, Task> getUserTransactions(String user) throws IOException {
        // This tree map is ordered by last modified (as long)
        List<String> list = GlobalHelpers.getFileList(EditConfig.getProperty("gitTransactDir"), ".patch");
        TreeMap<Long, Task> map = new TreeMap<>();
        for (String s : list) {
            String content = GlobalHelpers.readFileContent(s);
            Task tk = Task.create(content);
            if (tk.getUser().equals(user)) {
                map.put(new Long(new File(s).lastModified()), tk);
            }
        }
        return map;
    }

    public static void main(String[] args) throws IOException {
        EditConfig.init();
        System.out.println(GlobalHelpers.getFileList(EditConfig.getProperty("logRootDir"), ".log"));
        System.out.println(Checker.getLogsForUser("marc"));
        System.out.println(Checker.getAllTransactions());
        System.out.println(Checker.getUserTransactions("marc"));
    }

}
