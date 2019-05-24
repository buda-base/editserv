package io.bdrc.edit.patch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Task {

    public String message;
    public String id;
    public String shortName;
    public String patch;
    public String user;
    public List<Session> sessions;

    private Task() {
        super();
        // TODO Auto-generated constructor stub
    }

    public Task(String message, String id, String shortName, String patch, String user, List<Session> sessions) {
        super();
        this.message = message;
        this.id = id;
        this.shortName = shortName;
        this.patch = patch;
        this.sessions = sessions;
        this.user = user;
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

    public List<Session> getSessions() {
        return sessions;
    }

    public void setSessions(List<Session> sessions) {
        this.sessions = sessions;
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
        return "Task [message=" + message + ", id=" + id + ", shortName=" + shortName + ", patch=" + patch + ", user=" + user + ", sessions=" + sessions + "]";
    }

    public static void main(String[] args) throws JsonGenerationException, JsonMappingException, IOException {
        // Using constructor
        Session s = new Session(1111111, "vferd");
        Session s1 = new Session(5555551, "vcftgeza");
        ArrayList<Session> sessions = new ArrayList<>();
        sessions.add(s);
        sessions.add(s1);
        Task t = new Task("message", "id", "shortName", "payload", "user", sessions);

        // using mapper and create method
        ObjectMapper mapper = new ObjectMapper();
        String test = "{\n" + "    \"id\": \"<uuid:0686c69d-8f89-4496-acb5-744f0157a8db>\",\n" + "    \"shortName\": \"Namthar Collection\",\n" + "    \"message\":\"about the task\",\n" + "    \"user\":\"marc\", \n"
                + "    \"patch\":\"the newest patch version\",    \n" + "    \n" + "    \"sessions\": [\n" + "      {\n" + "        \"date\": 125415221452,\n" + "        \"gitVersion\": \"commitId1\"\n" + "      },\n" + "      {\n"
                + "        \"date\": 125415221899,\n" + "        \"gitVersion\": \"commitId2\"\n" + "      }\n" + "    ]\n" + "  } ";
        Task tk = Task.create(test);
        System.out.println(tk);
        mapper.writeValue(System.out, t);
    }

}
