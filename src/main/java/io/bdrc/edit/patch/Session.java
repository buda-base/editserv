package io.bdrc.edit.patch;

import java.io.Serializable;

public class Session implements Serializable {

    public long date;
    public String gitVersion;

    public Session(long date, String gitVersion) {
        super();
        this.date = date;
        this.gitVersion = gitVersion;
    }

    private Session() {
        super();
        // TODO Auto-generated constructor stub
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
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
        return "Session [date=" + date + ", gitVersion=" + gitVersion + "]";
    }

}
