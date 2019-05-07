package io.bdrc.edit;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

import io.bdrc.auth.AuthProps;

public class EditConfig {

    public static final String CORE_BASE = "http://purl.bdrc.io/ontology/core/";
    public static final String CORE_RES = "http://purl.bdrc.io/resource/";

    static Properties prop = new Properties();
    public static String FUSEKI_URL = "fusekiUrl";
    public static String FUSEKI_DATA = "fusekiData";
    private static String PREFIXES = "";

    public static void init() {
        try {
            InputStream input = new FileInputStream(System.getProperty("buda-edit.configpath") + "buda-edit.properties");
            prop.load(input);
            input.close();
            InputStream is = new FileInputStream("/etc/buda/share/shared-private.properties");
            prop.load(is);
            is.close();
            if ("true".equals(prop.getProperty("useAuth"))) {
                AuthProps.init(prop);
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(prop.getProperty("prefixesUrl")).openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                PREFIXES = PREFIXES + inputLine + System.lineSeparator();
            }
            in.close();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static String getProperty(String key) {
        return prop.getProperty(key);
    }

    public static String getSPARQLPrefixes() {
        return PREFIXES;
    }

}
