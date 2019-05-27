package io.bdrc.edit.patch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.EditConfig;

public class GitTaskService {

    static Repository taskRepo;

    static {

        try {
            taskRepo = new FileRepositoryBuilder().setGitDir(new File(EditConfig.getProperty("gitTaskRepo") + ".git")).setWorkTree(new File(EditConfig.getProperty("gitTaskRepo"))).readEnvironment().build();
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
        FileOutputStream output = new FileOutputStream(EditConfig.getProperty("gitTaskRepo") + user + "/" + tsk.getId() + ".patch");
        System.out.println("PATCH text >" + tsk.getPatch());
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(output, tsk);
        output.close();
        git.add().addFilepattern(tsk.getId() + ".patch").call();
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

    public List<Session> getAllSessions(String taskId) {
        ArrayList<Session> sessions = new ArrayList<>();
        return sessions;
    }

}
