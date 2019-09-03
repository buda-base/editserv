package io.bdrc.edit.txn;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.patch.Task;

public class TransactionLog {

    public HashMap<String, String> header;
    public HashMap<String, String> content;
    public HashMap<String, String> error;

    public static final String HEADER = "header";
    public static final String CONTENT = "content";
    public static final String ERROR = "error";

    public static String USER_ID = "userId";
    public static String DATE = "date";
    public static String TASK_ID = "taskId";
    public static String TXN_LAST_STATUS = "txn_last_Status";
    public static String ERROR_MSG = "error_msg";
    static String CONTENT_LENGTH = "content_length";
    static String ERROR_LENGTH = "error_length";

    public final static Logger logger = LoggerFactory.getLogger(TransactionLog.class.getName());

    public TransactionLog() {

    }

    public TransactionLog(Task tk) {
        header = new HashMap<>();
        content = new HashMap<>();
        error = new HashMap<>();
        addHeader(USER_ID, tk.getUser());
        addHeader(TASK_ID, tk.getId());
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        addHeader(DATE, dateFormat.format(new Date()));
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

    public static TransactionLog create(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, TransactionLog.class);
    }

    public static void main(String[] args) throws IOException {
        EditConfig.init();
        Task tk = new Task("saveMsg", "message", "uuid:1xxx3c4d-5yyyf-7a8b-9c0d-e1kkk3b4c5r6", "shortName", "patch content", "marc");
        TransactionLog log = new TransactionLog(tk);
        log.addContent("test", "whatever");
        log.addContent("test", "whatever");
        String json = TransactionLog.asJson(log);
        System.out.println(json);
        TransactionLog log1 = TransactionLog.create(json);
        System.out.println(log1);
        System.out.println(TransactionLog.asJson(log1));
    }

}
