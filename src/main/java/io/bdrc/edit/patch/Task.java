package io.bdrc.edit.patch;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class Task {

    public String message;
    public String id;
    public String shortName;
    public String patch;
    public String user;
    public String saveMsg;

    private Task() {
        super();
        // TODO Auto-generated constructor stub
    }

    public Task(String saveMsg, String message, String id, String shortName, String patch, String user) {
        super();
        this.message = message;
        this.id = id;
        this.shortName = shortName;
        this.patch = patch;
        this.user = user;
        this.saveMsg = saveMsg;
    }

    public String getSaveMsg() {
        return saveMsg;
    }

    public void setSaveMsg(String saveMsg) {
        this.saveMsg = saveMsg;
    }

    public static Task create(String json) throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, Task.class);
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPatch() {
        return patch;
    }

    public void setPatch(String patch) {
        this.patch = patch;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    @Override
    public String toString() {
        return "Task [message=" + message + ", id=" + id + ", shortName=" + shortName + ", patch=" + patch + ", user=" + user + ", saveMsg=" + saveMsg + "]";
    }

    public static String getPatchString(Session s) throws JsonParseException, JsonMappingException, IOException {
        Task t = Task.create(s.getTaskVersion());
        return t.getPatch();
    }

    public static void main(String[] args) throws JsonGenerationException, JsonMappingException, IOException, NoFilepatternException, GitAPIException, ServiceException {
        EditConfig.init();
        // Using constructor
        Task t = new Task("saveMsg", "message", "id", "shortName", "payload", "user");
        System.out.println(t);
        // using mapper and create method
        ObjectMapper mapper = new ObjectMapper();
        // mapper.writeValue(System.out, t);
        String test = "  {\n" + "    \"id\": \"YYYYYY\",\n" + "    \"shortName\": \"Yoga Collection\",\n" + "    \"message\":\"about the task\",\n" + "    \"user\":\"marc\", \n"
                + "    \"patch\":\"here is one more version of the content of the patch YYY\" \n" + "    \n" + "  } ";
        Task tk = Task.create(test);
        System.out.println(tk);
        System.out.println("PATCH text >" + tk.getPatch());
        TaskGitManager.saveTask(tk);
        System.out.println("Read task >" + TaskGitManager.getTask("XXXXXX", "marc"));
        List<Session> sess = TaskGitManager.getAllSessions("YYYYYY", "marc");
        System.out.println("SESSIONS >" + sess);
        for (Session s : sess) {
            System.out.println(Task.getPatchString(s));
        }
        System.out.println("FILES >" + TaskGitManager.getAllOngoingTask("marc"));
        System.out.println("JSON >" + TaskGitManager.getTaskAsJson("XXXXXX", "marc"));
    }

}
