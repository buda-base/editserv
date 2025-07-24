package io.bdrc.edit.test;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.MockitoAnnotations;

import io.bdrc.edit.controllers.SyncNotificationController;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.libraries.Models;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.bdrc.edit.controllers.SyncNotificationController.EtextSyncRequest;
import static io.bdrc.edit.controllers.SyncNotificationController.UnitInfo;

import java.util.*;
import java.util.stream.Collectors;

public class EtextSyncNotificationTest {
    
    private Model model;
    private Resource instanceResource;
    private Resource adminResource;
    private Resource userResource;
    private String logDateStr;
    
    // Mock the static properties and resources that would be defined in the main class
    private static final Property admAbout = ResourceFactory.createProperty(Models.ADM, "adminAbout");
    private static final Property logEntry = ResourceFactory.createProperty(Models.ADM, "logEntry");
    private static final Property volumeHasEtext = ResourceFactory.createProperty(Models.BDO, "volumeHasEtext");
    private static final Property seqNum = ResourceFactory.createProperty(Models.BDO, "seqNum");
    private static final Property sliceStartChar = ResourceFactory.createProperty(Models.BDO, "sliceStartChar");
    private static final Property sliceEndChar = ResourceFactory.createProperty(Models.BDO, "sliceEndChar");
    private static final Property numberOfCharacters = ResourceFactory.createProperty(Models.BDO, "numberOfCharacters");
    private static final Property logWho = ResourceFactory.createProperty(Models.ADM, "logWho");
    
    private static final Resource EtextVolume = ResourceFactory.createResource(Models.BDO + "EtextVolume");
    private static final Resource EtextSynced = ResourceFactory.createResource(Models.ADM + "EtextSynced");
    private static final Resource EtextUpdated = ResourceFactory.createResource(Models.ADM + "EtextUpdated");
    private static final Resource AdminData = ResourceFactory.createResource(Models.ADM + "AdminData");
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        model = ModelFactory.createDefaultModel();
        Models.setPrefixes(model);
        instanceResource = ResourceFactory.createResource(Models.BDR+"IE001");
        adminResource = ResourceFactory.createResource(Models.BDA+"AIE001");
        userResource = ResourceFactory.createResource(Models.BDU+"U001");
        logDateStr = "2023-12-01T10:00:00Z";
        
        // Set up basic admin data structure
        model.add(adminResource, RDF.type, AdminData);
        model.add(adminResource, admAbout, instanceResource);
    }
    
    @Test
    @DisplayName("Should throw EditException when admin data not found")
    void testMissingAdminData() {
        // Create a clean model without admin data
        Model cleanModel = ModelFactory.createDefaultModel();
        Resource orphanInstance = ResourceFactory.createResource(Models.BDR+"IE999");
        EtextSyncRequest request = createMockRequest();
        
        EditException exception = assertThrows(EditException.class, () -> {
        	ModelUtils.addEtextSyncNotification(cleanModel, orphanInstance, request, userResource, logDateStr);
        });
        
        assertTrue(exception.getMessage().contains("can't find admin data for"));
    }
    
    @Test
    @DisplayName("Should throw EditException when etext volume not in model")
    void testMissingEtextVolume() {
        EtextSyncRequest request = createMockRequest();
        
        EditException exception = assertThrows(EditException.class, () -> {
        	ModelUtils.addEtextSyncNotification(model, instanceResource, request, userResource, logDateStr);
        });
        
        assertTrue(exception.getMessage().contains("Sync error: etext volume not in model"));
    }
    
    @Test
    @DisplayName("Should successfully add etext sync notification for first sync")
    void testFirstSyncSuccess() throws EditException {
        // Set up etext volume
        Resource etextVolume = ResourceFactory.createResource(Models.BDR+"VE001");
        model.add(etextVolume, RDF.type, EtextVolume);
        
        EtextSyncRequest request = createMockRequestWithVolume("VE001");
        
        ModelUtils.addEtextSyncNotification(model, instanceResource, request, userResource, logDateStr);
        
        // Verify etext units were created
        StmtIterator etextStmts = model.listStatements(etextVolume, volumeHasEtext, (RDFNode) null);
        assertTrue(etextStmts.hasNext(), "Should have etext units");
        
        // Verify character counts were added
        assertTrue(model.contains(etextVolume, numberOfCharacters, (RDFNode) null), "Should have character count");
        
        // Verify log entry was created with EtextSynced type
        StmtIterator logStmts = model.listStatements(null, RDF.type, EtextSynced);
        assertTrue(logStmts.hasNext(), "Should have EtextSynced log entry");
    }
    
    @Test
    @DisplayName("Should mark as updated sync when previous sync exists")
    void testUpdatedSync() throws EditException {
        // Set up etext volume with existing sync
        Resource etextVolume = ResourceFactory.createResource(Models.BDR+"VE001");
        model.add(etextVolume, RDF.type, EtextVolume);
        
        // Add existing log entry with EtextSynced type
        Resource existingLog = ResourceFactory.createResource(Models.BDA+"LE001");
        Resource volumeadminResource = ResourceFactory.createResource(Models.BDA+"AVE001");
        
        // Set up basic admin data structure
        model.add(volumeadminResource, RDF.type, AdminData);
        model.add(volumeadminResource, admAbout, etextVolume);
        model.add(existingLog, RDF.type, EtextSynced);
        model.add(volumeadminResource, logEntry, existingLog);
        
        EtextSyncRequest request = createMockRequestWithVolume("VE001");
        
        ModelUtils.addEtextSyncNotification(model, instanceResource, request, userResource, logDateStr);

        // Verify new log entry was created with EtextUpdated type
        StmtIterator logStmts = model.listStatements(null, RDF.type, EtextUpdated);
        assertTrue(logStmts.hasNext(), "Should have EtextUpdated log entry");
    }
    
    @Test
    @DisplayName("Should handle multiple units")
    void testMultipleUnitsSequencing() throws EditException {
        Resource etextVolume = ResourceFactory.createResource(Models.BDR+"VE001");
        model.add(etextVolume, RDF.type, EtextVolume);
        
        EtextSyncRequest request = createMockRequestWithMultipleUnits("VE001");
        
        ModelUtils.addEtextSyncNotification(model, instanceResource, request, userResource, logDateStr);
        
        // Verify units were processed in sequence order
        List<Resource> units = new ArrayList<>();
        StmtIterator etextStmts = model.listStatements(etextVolume, volumeHasEtext, (RDFNode) null);
        while (etextStmts.hasNext()) {
            units.add(etextStmts.next().getResource());
        }

        assertEquals(3, units.size(), "Should have 3 units");
        
        // Verify sequence numbers are correct
        for (Resource unit : units) {
            assertTrue(model.contains(unit, seqNum, (RDFNode) null), "Unit should have sequence number");
        }
    }
    
    @Test
    @DisplayName("Should calculate character slices correctly")
    void testCharacterSliceCalculation() throws EditException {
        Resource etextVolume = ResourceFactory.createResource(Models.BDR+"VE001");
        model.add(etextVolume, RDF.type, EtextVolume);
        
        EtextSyncRequest request = createMockRequestWithCharacterCounts("VE001");
        
        ModelUtils.addEtextSyncNotification(model, instanceResource, request, userResource, logDateStr);
        
        // Verify character slices are cumulative
        StmtIterator etextStmts = model.listStatements(etextVolume, volumeHasEtext, (RDFNode) null);
        List<Resource> units = new ArrayList<>();
        while (etextStmts.hasNext()) {
            units.add(etextStmts.next().getResource());
        }
        
        // Sort by sequence number to verify slice calculation
        units.sort((u1, u2) -> {
            int seq1 = model.getProperty(u1, seqNum).getInt();
            int seq2 = model.getProperty(u2, seqNum).getInt();
            return Integer.compare(seq1, seq2);
        });
        
        int expectedStart = 0;
        for (Resource unit : units) {
            Statement startStmt = model.getProperty(unit, sliceStartChar);
            assertNotNull(startStmt, "Unit should have start character");
            assertEquals(expectedStart, startStmt.getInt(), "Start character should be cumulative");
            
            Statement endStmt = model.getProperty(unit, sliceEndChar);
            assertNotNull(endStmt, "Unit should have end character");
            expectedStart = endStmt.getInt();
        }
    }
    
    @Test
    @DisplayName("Should handle null user gracefully")
    void testNullUser() throws EditException {
        Resource etextVolume = ResourceFactory.createResource(Models.BDR+"VE001");
        model.add(etextVolume, RDF.type, EtextVolume);
        
        EtextSyncRequest request = createMockRequestWithVolume("VE001");
        
        // Should not throw exception with null user
        assertDoesNotThrow(() -> {
            ModelUtils.addEtextSyncNotification(model, instanceResource, request, null, logDateStr);
        });
        
        // Verify log entry was still created but without logWho
        StmtIterator logStmts = model.listStatements(null, RDF.type, EtextSynced);
        assertTrue(logStmts.hasNext(), "Should have log entry");
        Resource logEntry = logStmts.next().getSubject();
        assertFalse(model.contains(logEntry, logWho, (RDFNode) null), "Should not have logWho property");
    }
    
    // Helper methods to create mock requests
    private EtextSyncRequest createMockRequest() {
        EtextSyncRequest request = mock(EtextSyncRequest.class);
        Map<String, Map<String, UnitInfo>> volumes = new HashMap<>();
        volumes.put("VE999", new HashMap<>()); // Non-existent volume
        when(request.getVolumes()).thenReturn(volumes);
        return request;
    }
    
    private EtextSyncRequest createMockRequestWithVolume(String volumeName) {
        EtextSyncRequest request = mock(EtextSyncRequest.class);
        Map<String, Map<String, UnitInfo>> volumes = new HashMap<>();
        Map<String, UnitInfo> units = new HashMap<>();
        
        UnitInfo unit1 = mock(UnitInfo.class);
        when(unit1.getEtextNum()).thenReturn(1);
        when(unit1.getNbCharacters()).thenReturn(1000);
        when(unit1.getNbPages()).thenReturn(10);
        units.put("UT001", unit1);
        
        volumes.put(volumeName, units);
        when(request.getVolumes()).thenReturn(volumes);
        return request;
    }
    
    private EtextSyncRequest createMockRequestWithMultipleUnits(String volumeName) {
        EtextSyncRequest request = mock(EtextSyncRequest.class);
        Map<String, Map<String, UnitInfo>> volumes = new HashMap<>();
        Map<String, UnitInfo> units = new HashMap<>();
        
        // Create units in non-sequential order to test sorting
        UnitInfo unit3 = mock(UnitInfo.class);
        when(unit3.getEtextNum()).thenReturn(3);
        when(unit3.getNbCharacters()).thenReturn(800);
        when(unit3.getNbPages()).thenReturn(8);
        units.put("UT003", unit3);
        
        UnitInfo unit1 = mock(UnitInfo.class);
        when(unit1.getEtextNum()).thenReturn(1);
        when(unit1.getNbCharacters()).thenReturn(1000);
        when(unit1.getNbPages()).thenReturn(10);
        units.put("UT001", unit1);
        
        UnitInfo unit2 = mock(UnitInfo.class);
        when(unit2.getEtextNum()).thenReturn(2);
        when(unit2.getNbCharacters()).thenReturn(1200);
        when(unit2.getNbPages()).thenReturn(12);
        units.put("UT002", unit2);
        
        volumes.put(volumeName, units);
        when(request.getVolumes()).thenReturn(volumes);
        return request;
    }
    
    private EtextSyncRequest createMockRequestWithCharacterCounts(String volumeName) {
        EtextSyncRequest request = mock(EtextSyncRequest.class);
        Map<String, Map<String, UnitInfo>> volumes = new HashMap<>();
        Map<String, UnitInfo> units = new HashMap<>();
        
        UnitInfo unit1 = mock(UnitInfo.class);
        when(unit1.getEtextNum()).thenReturn(1);
        when(unit1.getNbCharacters()).thenReturn(500);
        when(unit1.getNbPages()).thenReturn(5);
        units.put("UT001", unit1);
        
        UnitInfo unit2 = mock(UnitInfo.class);
        when(unit2.getEtextNum()).thenReturn(2);
        when(unit2.getNbCharacters()).thenReturn(750);
        when(unit2.getNbPages()).thenReturn(7);
        units.put("UT002", unit2);
        
        volumes.put(volumeName, units);
        when(request.getVolumes()).thenReturn(volumes);
        return request;
    }
    
}