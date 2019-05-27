package io.bdrc.edit.patch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.EditConfig;

public class GitTaskService {

    static Repository taskRepo;

    static {

        try {
            if (!new File(EditConfig.getProperty("gitTaskRepo")).exists()) {
                new File(EditConfig.getProperty("gitTaskRepo")).mkdir();
            }
            boolean isGitRepo = RepositoryCache.FileKey.isGitRepository(new File(EditConfig.getProperty("gitTaskRepo") + ".git"), FS.DETECTED);
            System.out.println("IS GIT REPO ? >>" + isGitRepo);
            if (!isGitRepo) {
                taskRepo = new FileRepositoryBuilder().setGitDir(new File(EditConfig.getProperty("gitTaskRepo") + ".git")).setWorkTree(new File(EditConfig.getProperty("gitTaskRepo"))).readEnvironment().build();
                taskRepo.create();
            } else {
                taskRepo = new FileRepositoryBuilder().setGitDir(new File(EditConfig.getProperty("gitTaskRepo") + ".git")).setWorkTree(new File(EditConfig.getProperty("gitTaskRepo"))).readEnvironment().build();

            }
            System.out.println("GIT REPO >>" + taskRepo);
            isGitRepo = RepositoryCache.FileKey.isGitRepository(new File(EditConfig.getProperty("gitTaskRepo") + ".git"), FS.DETECTED);
            System.out.println("IS GIT REPO 2 ? >>" + isGitRepo);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void saveTask(Task tsk) throws NoFilepatternException, GitAPIException, IOException {
        String user = tsk.getUser();
        Git git = new Git(taskRepo);
        boolean exist = new File(EditConfig.getProperty("gitTaskRepo") + user + "/" + tsk.getId()).exists();
        if (!exist) {
            new File(EditConfig.getProperty("gitTaskRepo") + user + "/").mkdir();
        }
        FileOutputStream output = new FileOutputStream(new File(EditConfig.getProperty("gitTaskRepo") + user + "/" + tsk.getId() + ".patch"));
        System.out.println("PATCH text >" + tsk.getPatch());
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(output, tsk);
        output.close();
        git.add().addFilepattern(user + "/" + tsk.getId() + ".patch").call();
        git.commit().setMessage(tsk.getId() + ":" + tsk.getSaveMsg()).call();
        git.close();
    }

    public static Task getTask(String taskId, String user) throws JsonParseException, JsonMappingException, IOException {
        StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = Files.lines(Paths.get(EditConfig.getProperty("gitTaskRepo") + user + "/" + taskId + ".patch"), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Task.create(contentBuilder.toString());
    }

    public static String getTaskAsJson(String taskId, String user) throws JsonParseException, JsonMappingException, IOException {
        StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = Files.lines(Paths.get(EditConfig.getProperty("gitTaskRepo") + user + "/" + taskId + ".patch"), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return contentBuilder.toString();
    }

    public static List<Session> getAllSessions(String taskId, String user) throws NoHeadException, GitAPIException, RevisionSyntaxException, AmbiguousObjectException, IncorrectObjectTypeException, IOException {
        ArrayList<Session> sessions = new ArrayList<>();
        Git git = new Git(taskRepo);
        ObjectId id = taskRepo.resolve("bb7b56192e46f9a7f935ea61200199cb14879c8d");
        Iterable<RevCommit> rc = git.log().addPath(user + "/" + taskId + ".patch").call();
        System.out.println("TASK REPO DIR >>" + taskRepo.getDirectory());
        System.out.println("ID >>" + id);
        System.out.println("TASK REPO RELATIVIZED >>" + relativize(new File("/etc/buda/editserv/tasks/marc/"), taskRepo.getDirectory()));
        for (RevCommit rvc : rc) {
            System.out.println("REV COMMIT >>" + rvc.getCommitterIdent().getWhen());
            TreeWalk treeWalk = TreeWalk.forPath(taskRepo, user + "/" + taskId + ".patch", rvc.getTree());
            System.out.println("TREE WALK >>" + treeWalk);
            byte[] bytes = null;
            if (treeWalk != null) {
                treeWalk.setRecursive(true);
                CanonicalTreeParser canonicalTreeParser = treeWalk.getTree(0, CanonicalTreeParser.class);

                while (!canonicalTreeParser.eof()) {

                    // if the filename matches, we have a match, so set teh byte array to return
                    if (canonicalTreeParser.getEntryPathString().equals(user + "/" + taskId + ".patch")) {
                        ObjectLoader objectLoader = taskRepo.open(canonicalTreeParser.getEntryObjectId());
                        bytes = objectLoader.getBytes();
                        System.out.println("BYTES >>" + new String(bytes));
                    }
                    canonicalTreeParser.next(1);
                }
            }
            Session s = new Session(new Date(rvc.getCommitTime() * 1000L), rvc.getId().toString(), new String(bytes));
            sessions.add(s);
        }
        git.close();
        return sessions;
    }

    public static String relativize(File file, File startFile) {
        Path path = file.toPath();
        Path startPath = startFile.toPath();
        Path relativePath = startPath.relativize(path);

        return relativePath.toString();
    }

}
