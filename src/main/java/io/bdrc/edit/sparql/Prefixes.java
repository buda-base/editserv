package io.bdrc.edit.sparql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.PrefixMapStd;
import org.apache.jena.shared.PrefixMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Prefixes {

    public final static Logger log = LoggerFactory.getLogger(Prefixes.class.getName());
    private final static PrefixMap pMap = new PrefixMapStd();
    private static String prefixesString;
    private final static PrefixMapping PREFIXES_MAP = PrefixMapping.Factory.create();

    static {
        loadPrefixes();
    }

    public static String getPrefixesString() {
        return prefixesString;
    }

    public static void loadPrefixes() {
        try {
            log.info("reading prefixes from {}", "https://raw.githubusercontent.com/buda-base/lds-queries/master/public/prefixes.txt");
            URL url = new URL("https://raw.githubusercontent.com/buda-base/lds-queries/master/public/prefixes.txt");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder st = new StringBuilder();
            PREFIXES_MAP.clearNsPrefixMap();
            pMap.clear();
            String line = "";
            while ((line = in.readLine()) != null) {
                st.append(line);
                if (line.length() < 10 || line.startsWith("#"))
                    continue;
                final String uri = line.substring(line.indexOf('<') + 1, line.indexOf('>'));
                final String prefix = line.substring(7, line.indexOf(':')).trim();
                pMap.add(prefix, uri);
                PREFIXES_MAP.setNsPrefix(prefix, uri);
            }
            prefixesString = st.toString();
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static PrefixMap getPrefixMap() {
        return pMap;
    }

    public static PrefixMapping getPrefixMapping() {
        return PREFIXES_MAP;
    }

    public static String getFullIRI(String prefix) {
        if (prefix != null) {
            return PREFIXES_MAP.getNsPrefixURI(prefix);
        }
        return null;
    }

    public static String getPrefix(String IRI) {
        if (IRI != null) {
            return PREFIXES_MAP.getNsURIPrefix(IRI);
        }
        return "";
    }

    public static String getPrefixedIRI(String IRI) {
        if (IRI != null) {
            return PREFIXES_MAP.shortForm(IRI);
        }
        return "";
    }

}
