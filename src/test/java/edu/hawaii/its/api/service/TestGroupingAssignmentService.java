package edu.hawaii.its.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import edu.hawaii.its.api.configuration.SpringBootWebApplication;
import edu.hawaii.its.api.exception.AccessDeniedException;
import edu.hawaii.its.api.groupings.GroupingGroupMembers;
import edu.hawaii.its.api.type.AdminListsHolder;
import edu.hawaii.its.api.type.Group;
import edu.hawaii.its.api.type.GroupType;
import edu.hawaii.its.api.type.Grouping;
import edu.hawaii.its.api.type.GroupingPath;
import edu.hawaii.its.api.type.OptType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.BDDMockito.given;

@ActiveProfiles("integrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = { SpringBootWebApplication.class })
public class TestGroupingAssignmentService {

    @Value("${groupings.api.test.grouping_many}")
    private String GROUPING;

    @Value("${groupings.api.test.grouping_many_basis}")
    private String GROUPING_BASIS;

    @Value("${groupings.api.test.grouping_many_include}")
    private String GROUPING_INCLUDE;

    @Value("${groupings.api.test.grouping_many_exclude}")
    private String GROUPING_EXCLUDE;

    @Value("${groupings.api.test.grouping_many_owners}")
    private String GROUPING_OWNERS;

    @Value("${groupings.api.test.admin_user}")
    private String ADMIN;

    @Value("${groupings.api.grouping_admins}")
    private String GROUPING_ADMINS;

    @Value("${groupings.api.test.grouping_large_basis}")
    private String GROUPING_LARGE_BASIS;

    @Autowired
    private GroupingAttributeService groupingAttributeService;

    @Autowired
    private GroupingAssignmentService groupingAssignmentService;

    @Autowired
    private GrouperApiService grouperApiService;

    @SpyBean
    private GroupingsService groupingsService;

    @Autowired
    private UpdateMemberService updateMemberService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private UhIdentifierGenerator uhIdentifierGenerator;

    private String testUid;
    private List<String> testUidList;

    @BeforeEach
    public void init() {
        assertTrue(memberService.isAdmin(ADMIN));

        testUid = uhIdentifierGenerator.getRandomMember().getUid();
        testUidList = Arrays.asList(testUid);
        grouperApiService.removeMember(ADMIN, GROUPING_ADMINS, testUid);
        grouperApiService.removeMember(ADMIN, GROUPING_INCLUDE, testUid);
        grouperApiService.removeMember(ADMIN, GROUPING_EXCLUDE, testUid);
        grouperApiService.removeMember(ADMIN, GROUPING_OWNERS, testUid);
    }

    @Test
    public void adminsGroupingsTest() {
        // Should throw an exception if current user is not an admin.
        try {
            groupingAssignmentService.adminsGroupings(testUid);
            fail("Should throw an exception if current user is not an admin.");
        } catch (AccessDeniedException e) {
            assertEquals("Insufficient Privileges", e.getMessage());
        }

        // Should not throw an exception if current user is an admin.
        updateMemberService.addAdmin(ADMIN, testUid);
        try {
            groupingAssignmentService.adminsGroupings(testUid);
        } catch (AccessDeniedException e) {
            fail("Should not throw an exception if current user is an admin.");
        }
        updateMemberService.removeAdmin(ADMIN, testUid);

        // Fields in AdminListsHolder should not be null.
        AdminListsHolder adminListsHolder = groupingAssignmentService.adminsGroupings(ADMIN);
        assertNotNull(adminListsHolder.getAdminGroup());
        assertNotNull(adminListsHolder.getAllGroupingPaths());
    }

    @Test
    public void getMembersTest() {
        List<String> groupPaths = new ArrayList<>();
        Collections.addAll(groupPaths, GROUPING, GROUPING_BASIS, GROUPING_INCLUDE, GROUPING_EXCLUDE, GROUPING_OWNERS);
        Map<String, Group> groupMap = groupingAssignmentService.getMembers(ADMIN, groupPaths);
        assertNotNull(groupMap);
    }

    @Test
    public void optInOutGroupingsPathsTest() {
        // Test both getOptInGroups and getOptOutGroups()
        List<GroupingPath> optInGroupingsPaths =
                groupingAssignmentService.optInGroupingPaths(ADMIN, testUid);
        List<String> optInPaths = optInGroupingsPaths.stream().map(GroupingPath::getPath).collect(Collectors.toList());
        List<String> optOutPaths = groupingAssignmentService.optOutGroupingsPaths(ADMIN, testUid);
        Set<String> intersection =
                optInPaths.stream().distinct().filter(optOutPaths::contains).collect(Collectors.toSet());
        // Should be no intersection between the two lists.
        assertTrue(intersection.isEmpty());
        // Should have no duplicates.
        Set<String> optInPathsMap = new HashSet<>();
        Set<String> optOutPathsMap = new HashSet<>();
        optInPaths.forEach(path -> {
            assertTrue(optInPathsMap.add(path));
            assertFalse(path.endsWith(GroupType.INCLUDE.value()));
            assertFalse(path.endsWith(GroupType.EXCLUDE.value()));
            assertFalse(path.endsWith(GroupType.BASIS.value()));
            assertFalse(path.endsWith(GroupType.OWNERS.value()));
            assertTrue(groupingAttributeService.isGroupAttribute(path, OptType.IN.value()));
        });
        optOutPaths.forEach(path -> {
            assertTrue(optOutPathsMap.add(path));
            assertFalse(path.endsWith(GroupType.INCLUDE.value()));
            assertFalse(path.endsWith(GroupType.EXCLUDE.value()));
            assertFalse(path.endsWith(GroupType.BASIS.value()));
            assertFalse(path.endsWith(GroupType.OWNERS.value()));
            assertTrue(groupingAttributeService.isGroupAttribute(path, OptType.OUT.value()));
        });

    }

    @Test
    public void noOptInGroupingsPathsTest() {
        given(groupingsService.optInEnabledGroupingPaths()).willReturn(Collections.emptyList());
        List<GroupingPath> optInGroupingsPaths =
                groupingAssignmentService.optInGroupingPaths(ADMIN, testUid);
        assertEquals(Collections.emptyList(), optInGroupingsPaths);
    }

    @Test
    public void groupingOwners() {
        updateMemberService.removeOwnership(ADMIN, GROUPING, testUid);
        GroupingGroupMembers groupingGroupMembers = groupingAssignmentService.groupingOwners(ADMIN, GROUPING);
        assertNotNull(groupingGroupMembers);
        assertFalse(groupingGroupMembers.getMembers().stream()
                .anyMatch(groupingsGroupMember -> groupingsGroupMember.getUid().equals(testUid)));

        updateMemberService.addOwnership(ADMIN, GROUPING, testUid);
        groupingGroupMembers = groupingAssignmentService.groupingOwners(ADMIN, GROUPING);
        assertNotNull(groupingGroupMembers);
        assertTrue(groupingGroupMembers.getMembers().stream()
                .anyMatch(groupingsGroupMember -> groupingsGroupMember.getUid().equals(testUid)));
        updateMemberService.removeOwnership(ADMIN, GROUPING, testUid);
    }

    @Test
    public void isSoleOwner() {
        updateMemberService.addOwnership(ADMIN, GROUPING, ADMIN);
        updateMemberService.addOwnership(ADMIN, GROUPING, testUid);
        assertFalse(groupingAssignmentService.isSoleOwner(ADMIN, GROUPING, ADMIN));
        updateMemberService.removeOwnership(ADMIN, GROUPING, testUid);
    }

}
