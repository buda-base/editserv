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

import org.apache.jena.ontology.OntDocumentManager;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

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

    public static OntModel getOntologyModel() {
        OntDocumentManager ontManager = new OntDocumentManager("owl-schema/ont-policy.rdf;https://raw.githubusercontent.com/buda-base/owl-schema/master/ont-policy.rdf");
        // not really needed since ont-policy sets it, but what if someone changes the
        // policy
        ontManager.setProcessImports(true);
        OntModelSpec ontSpec = new OntModelSpec(OntModelSpec.OWL_DL_MEM);
        ontSpec.setDocumentManager(ontManager);
        OntModel ontModel = ontManager.getOntology("http://purl.bdrc.io/ontology/admin/", ontSpec);
        return ontModel;
    }

    public static String getGitHeadFileContent(String repo, String filepath) throws Exception {

        Repository repository = new FileRepository(repo + "/.git");
        ObjectId lastCommitId = repository.resolve(Constants.HEAD);

        // a RevWalk allows to walk over commits based on some filtering that is defined
        RevWalk revWalk = new RevWalk(repository);
        RevCommit commit = revWalk.parseCommit(lastCommitId);
        // and using commit's tree find the path
        RevTree tree = commit.getTree();
        // now try to find a specific file
        TreeWalk treeWalk = new TreeWalk(repository);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathFilter.create(filepath));
        if (!treeWalk.next()) {
            throw new IllegalStateException("Unable to download file.");
        }
        ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
        System.out.println("File Size : " + loader.getSize() + "bytes");

        // and then one can the loader to read the file
        // loader.copyTo(System.out);

        revWalk.dispose();
        String content = new String(loader.getBytes());
        System.out.println("FILE >>> ");
        System.out.println(content);
        return content;
    }

    public static void main(String[] args) throws IOException {
        EditConfig.init();
        System.out.println(Helpers.getFileList(EditConfig.getProperty("logRootDir"), ".log"));
        System.out.println(Helpers.getLogsForUser("marc"));
        System.out.println(Helpers.getAllTransactions());
        System.out.println(Helpers.getUserTransactions("marc"));
    }

}
