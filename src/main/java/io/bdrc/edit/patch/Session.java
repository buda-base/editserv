package io.bdrc.edit.patch;

import java.io.IOException;
import java.io.Serializable;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class Session implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 5870830073494191588L;
    public String date;
    public String gitVersion;
    public String shortGitVersion;
    public String taskVersion;
    public Task task;

    public Session(String date, String gitVersion, String taskVersion) throws JsonParseException, JsonMappingException, IOException {
        super();
        this.date = date;
        this.gitVersion = gitVersion;
        this.taskVersion = taskVersion;
        this.task = Task.create(taskVersion);
        this.shortGitVersion = gitVersion.substring(gitVersion.indexOf(" ") + 1, gitVersion.indexOf(" ") + 8);
    }

    private Session() {
        super();
        // TODO Auto-generated constructor stub
    }

    public String getShortGitVersion() {
        return shortGitVersion;
    }

    public void setShortGitVersion(String shortGitVersion) {
        this.shortGitVersion = shortGitVersion;
    }

    public Task getTask() {
        return task;
    }

    public String getTaskVersion() {
        return taskVersion;
    }

    public void setTaskVersion(String taskVersion) {
        this.taskVersion = taskVersion;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getGitVersion() {
        return gitVersion;
    }

    public void setGitVersion(String gitVersion) {
        this.gitVersion = gitVersion;
    }

    @Override
    public String toString() {
        return "Session [date=" + date + ", gitVersion=" + gitVersion + ", taskVersion=" + taskVersion + "]";
    }

}
