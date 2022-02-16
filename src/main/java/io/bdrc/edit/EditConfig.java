package io.bdrc.edit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import io.bdrc.auth.AuthProps;
import io.bdrc.auth.rdf.RdfAuthModel;
import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.helpers.Shapes;
import io.bdrc.edit.user.UsersCache;
import io.bdrc.libraries.Prefix;

public class EditConfig {

    public static final String CORE_BASE = "http://purl.bdrc.io/ontology/core/";
    public static final String CORE_RES = "http://purl.bdrc.io/resource/";

    static Properties prop = new Properties();
    public static String FUSEKI_URL = "fusekiUrl";
    public static String FUSEKI_DATA = "fusekiData";
    public final static String QUERY_TIMEOUT = "timeout";
    public static Prefix prefix;
    public static boolean dryrunmode = false;
    
    final static Logger log = LoggerFactory.getLogger(Shapes.class);

    public static void init() throws Exception {
        InputStream input = EditConfig.class.getClassLoader().getResourceAsStream("userEdit.properties");
        prop.load(input);
        input = EditConfig.class.getClassLoader().getResourceAsStream("editserv.properties");
        prop.load(input);
        input.close();
        if (System.getProperty("editserv.configpath") != null) {
            input = new FileInputStream(System.getProperty("editserv.configpath") + "editserv.properties");
            prop.load(input);
            input.close();
        }
        final File privateProps = new File("/etc/buda/share/shared-private.properties");
        if (privateProps.canRead()) {
            InputStream is = new FileInputStream(privateProps);
            prop.load(is);            
            is.close();
        } else {
            log.error("cannot read /etc/buda/share/shared-private.properties, editor will not push commits");
        }
        dryrunmode = "true".equals(prop.getProperty("dryrunmode"));
        log.info("dry run: {}", dryrunmode);
        AuthProps.init(prop);
        if (useAuth()) {
            RdfAuthModel.init();
        }
        CommonsGit.init();
        UsersCache.init();
        OntologyData.init();
        Shapes.init();
        if (prop.getProperty("prefixesFilePath") != null) {
            prefix = new Prefix(prop.getProperty("prefixesFilePath"));
        } else {
            throw new Exception("please set property prefixesFilePath with the path to a prefix file");
        }
    }

    public static void initForTests(String fusekiUrl) throws JsonParseException, JsonMappingException, IOException {
        try {

            InputStream input = new FileInputStream(new File("src/test/resources/test.properties"));
            // load a properties file
            prop.load(input);
            input.close();
            AuthProps.init(prop);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (fusekiUrl != null) {
            prop.setProperty(FUSEKI_URL, fusekiUrl);
        }
    }

    static boolean useAuth() {
        return Boolean.parseBoolean(prop.getProperty("useAuth"));
    }

    public static String getProperty(String key) {
        return prop.getProperty(key);
    }

    public static Properties getProperties() {
        return prop;
    }

}
