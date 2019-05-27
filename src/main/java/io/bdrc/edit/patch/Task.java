package io.bdrc.edit.patch;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.EditConfig;

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

    public String getPatchString(Session s) {
        // TO BE IMPLEMENTED
        // gets the content of patch from git repo given the session gitVersion
        return id;

    }

    public static void main(String[] args) throws JsonGenerationException, JsonMappingException, IOException, NoFilepatternException, GitAPIException {

        EditConfig.init();
        // Using constructor
        Task t = new Task("saveMsg", "message", "id", "shortName", "payload", "user");
        System.out.println(t);
        // using mapper and create method
        ObjectMapper mapper = new ObjectMapper();
        String test = "  {\n" + "    \"id\": \"XXXXXX\",\n" + "    \"shortName\": \"Namthar Collection\",\n" + "    \"message\":\"about the task\",\n" + "    \"user\":\"marc\", \n"
                + "    \"patch\":\"here is the latest version of the content of the patch again\" \n" + "    \n" + "  } ";
        Task tk = Task.create(test);
        System.out.println(tk);
        System.out.println("PATCH text >" + tk.getPatch());
        GitTaskService.saveTask(tk);
    }

}
