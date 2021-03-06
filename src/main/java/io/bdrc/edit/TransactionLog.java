package io.bdrc.edit;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TransactionLog {

    public HashMap<String, String> header;
    public HashMap<String, String> content;
    public HashMap<String, String> error;
    public String path;

    public static final String HEADER = "header";
    public static final String CONTENT = "content";
    public static final String ERROR = "error";

    public static String USER_ID = "userId";
    public static String EDITOR_ID = "editorId";
    public static String DATE = "date";
    public static String TASK_ID = "taskId";
    public static String TXN_LAST_STATUS = "txn_last_Status";
    public static String ERROR_MSG = "error_msg";
    static String CONTENT_LENGTH = "content_length";
    static String ERROR_LENGTH = "error_length";

    public final static Logger logger = LoggerFactory.getLogger(TransactionLog.class.getName());

    public TransactionLog(String path, String editor_Name, String userId) {
        this.path = path;
        header = new HashMap<>();
        content = new HashMap<>();
        error = new HashMap<>();
        addHeader(EDITOR_ID, editor_Name);
        addHeader(USER_ID, userId);
    }

    public void setLastStatus(String value) {
        header.put(TXN_LAST_STATUS, value);
    }

    public void addHeader(String key, String value) {
        String tmp = header.get(key);
        if (tmp != null) {
            header.put(key, tmp + ";" + value);
        } else {
            header.put(key, value);
        }
        logger.info("HEADER : " + key + ":" + value);
    }

    public void addContent(String key, String value) throws IOException {
        String tmp = content.get(key);
        if (tmp != null) {
            content.put(key, tmp + System.lineSeparator() + value);
        } else {
            content.put(key, value);
            incrementLength("content");
        }
        logger.info("CONTENT : " + key + ":" + value);
    }

    public void addError(String key, String value) {
        String tmp = error.get(key);
        if (tmp != null) {
            error.put(key, tmp + ";" + value);
        } else {
            incrementLength("error");
            error.put(key, value);
        }
        logger.info("ERROR : " + key + ":" + value);
    }

    public String getHeader(String key) {
        return header.get(key);
    }

    private boolean incrementLength(String section) {
        String key = "";
        switch (section) {
        case CONTENT:
            key = CONTENT_LENGTH;
            break;
        case ERROR:
            key = ERROR_LENGTH;
            break;
        default:
            return false;
        }
        String tmp = header.get(key);
        if (tmp != null) {
            header.put(key, Integer.toString(Integer.parseInt(tmp) + 1));
        } else {
            header.put(key, "1");
        }
        return true;
    }

    public static String asJson(TransactionLog log) throws JsonProcessingException {
        HashMap<String, HashMap<String, String>> obj = new HashMap<>();
        obj.put(HEADER, log.header);
        obj.put(CONTENT, log.content);
        obj.put(ERROR, log.error);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(obj);
    }

    public static String asString(TransactionLog log) {
        String lg = System.lineSeparator();
        Set<Entry<String, String>> set = log.header.entrySet();
        for (Entry<String, String> ent : set) {
            lg = lg + ent.getKey() + " : " + ent.getValue() + System.lineSeparator();
        }
        lg = lg + System.lineSeparator();
        set = log.error.entrySet();
        for (Entry<String, String> ent : set) {
            lg = lg + ent.getKey() + " : " + ent.getValue() + System.lineSeparator();
        }
        lg = lg + System.lineSeparator();
        set = log.content.entrySet();
        for (Entry<String, String> ent : set) {
            lg = lg + ent.getKey() + " : " + ent.getValue() + System.lineSeparator();
        }
        return lg;
    }

    public static TransactionLog create(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, TransactionLog.class);
    }

    public String getPath() {
        return path;
    }

}
