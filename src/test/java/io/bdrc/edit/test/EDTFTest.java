package io.bdrc.edit.test;

import org.junit.Test;

import io.bdrc.edit.helpers.EditServReasoner;

public class EDTFTest {

    @Test
    public void inferEdtf() {
        assert(EditServReasoner.intervalToEDTF("1700", "1799").equals("17XX"));
        assert(EditServReasoner.intervalToEDTF("1600", "1799").equals("1600/1799"));
        assert(EditServReasoner.intervalToEDTF("1600", "1609").equals("160X"));
    }
    
}
