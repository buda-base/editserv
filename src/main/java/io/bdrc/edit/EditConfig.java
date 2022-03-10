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
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.data.OntologyData;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.controllers.RIDController;
import io.bdrc.edit.controllers.ScanRequestController;
import io.bdrc.edit.helpers.Shapes;
import io.bdrc.edit.user.UsersCache;
import io.bdrc.libraries.Prefix;

public class EditConfig {

    public static final String CORE_BASE = "http://purl.bdrc.io/ontology/core/";
    public static final String CORE_RES = "http://purl.bdrc.io/resource/";

    static Properties prop = new Properties();
    public final static String QUERY_TIMEOUT = "timeout";
    public static Prefix prefix;
    public static boolean testMode = false;
    public static boolean useAuth = true;
    public static boolean dryrunmodefuseki = false;
    public static boolean dryrunmodegit = false;
    public static boolean dryrunmodeusers = false;
    public static boolean dryrunmodefusekisyncmodels = false;
    
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
        dryrunmodefuseki = "true".equals(prop.getProperty("dryrunmode.fuseki"));
        dryrunmodegit = "true".equals(prop.getProperty("dryrunmode.git"));
        dryrunmodeusers = "true".equals(prop.getProperty("dryrunmode.users"));
        dryrunmodefusekisyncmodels = "true".equals(prop.getProperty("dryrunmode.fuseki.syncModels"));
        testMode = "true".equals(prop.getProperty("testMode"));
        useAuth = !"false".equals(prop.getProperty("useAuth"));
        log.info("dry run: git {}, fuseki {}, users {}", dryrunmodegit,dryrunmodefuseki, dryrunmodeusers);
        AuthProps.init(prop);
        if (useAuth) {
            RdfAuthModel.init();
        }
        CommonsGit.init();
        UsersCache.init();
        OntologyData.init();
        Shapes.init();
        ScanRequestController.init();
        RIDController.initPrefixIndexes();
        FusekiWriteHelpers.init();
        if (prop.getProperty("prefixesFilePath") != null) {
            prefix = new Prefix(prop.getProperty("prefixesFilePath"));
        } else {
            throw new Exception("please set property prefixesFilePath with the path to a prefix file");
        }
    }

    public static void initForTests(String fusekiUrl) throws JsonParseException, JsonMappingException, IOException {
        testMode = true;
        useAuth = false;
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
            prop.setProperty("fusekiBaseUrl", fusekiUrl);
        }
    }

    public static String getProperty(String key) {
        return prop.getProperty(key);
    }

    public static Properties getProperties() {
        return prop;
    }

}
