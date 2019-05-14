package editservio.bdrc.edit.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.service.PatchService;
import io.bdrc.edit.txn.exceptions.ServiceException;

public class PatchServiceTest {

    @BeforeClass
    public static void init() {
        EditConfig.init();
    }

    @Test
    public void testSimpleAndMultiple() throws IOException, ServiceException {
        List<String> doc = IOUtils.readLines(BasicTest.class.getClassLoader().getResourceAsStream("testPS.patch"), StandardCharsets.UTF_8);
        String s = "";
        for (String l : doc) {
            s = s + System.lineSeparator() + l;
        }
        PatchService ps = new PatchService("final", s, "testUser");
        ps.run();
        PatchService.deletePatch(ps.getId(), ps.getUserId(), false);
        doc = IOUtils.readLines(BasicTest.class.getClassLoader().getResourceAsStream("multiUpdate.patch"), StandardCharsets.UTF_8);
        s = "";
        for (String l : doc) {
            s = s + System.lineSeparator() + l;
        }
        ps = new PatchService("final", s, "testUser");
        ps.run();
        PatchService.deletePatch(ps.getId(), ps.getUserId(), false);
    }

}
