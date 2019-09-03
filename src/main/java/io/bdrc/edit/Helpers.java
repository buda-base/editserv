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

import io.bdrc.edit.txn.TransactionLog;

public class Helpers {

    public static List<String> getLogsFiles() throws IOException {
        List<String> files = new ArrayList<>();
        Path dpath = Paths.get(EditConfig.getProperty("logRootDir"));
        Stream<Path> walk = Files.walk(dpath);
        files = walk.map(x -> x.toString()).filter(f -> f.endsWith(".log")).collect(Collectors.toList());
        return files;
    }

    public static TreeMap<Long, TransactionLog> getLogsForUser(String user) throws IOException {
        // This tree map is ordered by last modified (as long)
        List<String> list = Helpers.getLogsFiles();
        TreeMap<Long, TransactionLog> map = new TreeMap<>();
        for (String s : list) {
            if (s.contains(EditConfig.getProperty("logRootDir") + user + "/")) {
                File f = new File(s);
                FileInputStream fileInputStream = new FileInputStream(f);
                byte[] b = new byte[(int) f.length()];
                fileInputStream.read(b);
                fileInputStream.close();
                String content = new String(b, "UTF-8");
                TransactionLog log = TransactionLog.create(content);
                map.put(new Long(f.lastModified()), log);
                System.out.println(TransactionLog.asJson(log));
            }
        }
        return map;
    }

    public static void main(String[] args) throws IOException {
        EditConfig.init();
        System.out.println(Helpers.getLogsFiles());
        System.out.println(Helpers.getLogsForUser("marc"));
    }

}
