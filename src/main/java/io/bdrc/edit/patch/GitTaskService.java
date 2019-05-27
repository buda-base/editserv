package io.bdrc.edit.patch;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

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
        String patch = tsk.getPatch();
        Git git = new Git(taskRepo);
        boolean exist = new File(EditConfig.getProperty("gitTaskRepo") + user + "/" + tsk.getId()).exists();
        if (!exist) {
            new File(EditConfig.getProperty("gitTaskRepo") + user + "/").mkdir();
        }
        FileWriter fw = new FileWriter(EditConfig.getProperty("gitTaskRepo") + user + "/" + tsk.getId() + ".patch");
        System.out.println("PATCH text >" + tsk.getPatch());
        fw.write(tsk.getPatch());
        fw.close();
        git.add().addFilepattern(tsk.getId() + ".patch").call();
        git.commit().setMessage(tsk.getId() + ":" + tsk.getSaveMsg()).call();

    }

    public List<Session> getSessions(String taskId) {
        ArrayList<Session> sessions = new ArrayList<>();
        return sessions;
    }

}
