package io.bdrc.edit.patch;

import java.io.Serializable;

public class Session implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 5870830073494191588L;
    public String date;
    public String gitVersion;
    public String taskVersion;

    public Session(String date, String gitVersion, String taskVersion) {
        super();
        this.date = date;
        this.gitVersion = gitVersion;
        this.taskVersion = taskVersion;
    }

    private Session() {
        super();
        // TODO Auto-generated constructor stub
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
