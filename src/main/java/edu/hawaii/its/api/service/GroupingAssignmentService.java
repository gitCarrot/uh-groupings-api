package edu.hawaii.its.api.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import edu.hawaii.its.api.exception.AccessDeniedException;
import edu.hawaii.its.api.groupings.GroupingGroupMember;
import edu.hawaii.its.api.groupings.GroupingGroupMembers;
import edu.hawaii.its.api.type.AdminListsHolder;
import edu.hawaii.its.api.type.Group;
import edu.hawaii.its.api.type.GroupType;
import edu.hawaii.its.api.type.GroupingPath;
import edu.hawaii.its.api.type.Person;
import edu.hawaii.its.api.wrapper.GetMembersResult;
import edu.hawaii.its.api.wrapper.GetMembersResults;
import edu.hawaii.its.api.wrapper.Subject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static edu.hawaii.its.api.service.PathFilter.parentGroupingPath;
import static edu.hawaii.its.api.service.PathFilter.pathHasInclude;

@Service("groupingAssignmentService")
public class GroupingAssignmentService {

    @Value("${groupings.api.grouping_admins}")
    private String GROUPING_ADMINS;

    @Value("${groupings.api.stale_subject_id}")
    private String STALE_SUBJECT_ID;

    public static final Log logger = LogFactory.getLog(GroupingAssignmentService.class);

    @Autowired
    private GrouperApiService grouperApiService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private GroupingsService groupingsService;


    /**
     * Returns an adminLists object containing the list of all admins and all groupings.
     */
    public AdminListsHolder adminsGroupings(String adminUhIdentifier) {
        logger.info(String.format("adminsGroupings; adminUhIdentifier: %s;", adminUhIdentifier));
        if (!memberService.isAdmin(adminUhIdentifier)) {
            throw new AccessDeniedException();
        }
        AdminListsHolder adminListsHolder = new AdminListsHolder();
        List<String> adminGrouping = Arrays.asList(GROUPING_ADMINS);
        Group admin = getMembers(adminUhIdentifier, adminGrouping).get(GROUPING_ADMINS);
        adminListsHolder.setAllGroupingPaths(groupingsService.allGroupingPaths());
        adminListsHolder.setAdminGroup(admin);
        return adminListsHolder;
    }

    /**
     * Returns a group from grouper or the database.
     */
    public Map<String, Group> getMembers(String ownerUsername, List<String> groupPaths) {
        GetMembersResults getMembersResults =
                grouperApiService.getMembersResults(
                        ownerUsername,
                        groupPaths,
                        null,
                        null,
                        null,
                        false);
        return makeGroups(getMembersResults);
    }

    /**
     * As a group owner, get a list of grouping paths pertaining to the groups which optInUid can opt out of.
     */
    public List<String> optOutGroupingsPaths(String owner, String optOutUid) {
        logger.info(String.format("optOutGroupingsPaths; owner: %s; optOutUid: %s;", owner, optOutUid));

        List<String> includes = groupingsService.groupPaths(optOutUid, pathHasInclude());
        includes = includes.stream().map(path -> parentGroupingPath(path)).collect(Collectors.toList());
        List<String> optOutPaths = groupingsService.optOutEnabledGroupingPaths();
        optOutPaths.retainAll(includes);
        return new ArrayList<>(new HashSet<>(optOutPaths));
    }

    /**
     * As a group owner, get a list of grouping paths pertaining to the groups which optInUid can opt into.
     */
    public List<GroupingPath> optInGroupingPaths(String owner, String optInUid) {
        logger.info(String.format("optInGroupingsPaths; owner: %s; optInUid: %s;", owner, optInUid));

        List<String> includes = groupingsService.groupPaths(optInUid, pathHasInclude());
        includes = includes.stream().map(path -> parentGroupingPath(path)).collect(Collectors.toList());

        List<String> optInPaths = groupingsService.optInEnabledGroupingPaths();
        optInPaths.removeAll(includes);
        optInPaths = new ArrayList<>(new HashSet<>(optInPaths));

        return groupingsService.getGroupingPaths(optInPaths);
    }

    public GroupingGroupMembers groupingOwners(String currentUser, String groupingPath) {
        logger.info(String.format("groupingOwners; currentUser: %s; groupingPath: %s;", currentUser, groupingPath));
        return new GroupingGroupMembers(
                grouperApiService.getMembersResult(currentUser, groupingPath + GroupType.OWNERS.value()));
    }

    public Boolean isSoleOwner(String currentUser, String groupPath, String uidToCheck) {
        logger.debug(String.format("isSoleOwner; currentUser: %s; groupPath: %s; uidToCheck: %s",
                currentUser, groupPath, uidToCheck));
        List<GroupingGroupMember> owners = groupingOwners(currentUser, groupPath).getMembers();
        if (owners.size() >= 2) {
            return false;
        }
        return owners.stream().anyMatch(owner -> owner.getUid().contains(uidToCheck));
    }

    public Map<String, Group> makeGroups(GetMembersResults getMembersResults) {
        Map<String, Group> groupMembers = new HashMap<>();
        List<GetMembersResult> membersResults = getMembersResults.getMembersResults();
        for (GetMembersResult membersResult : membersResults) {
            Group group = new Group(membersResult.getGroup().getGroupPath());
            List<Subject> subjects = membersResult.getSubjects();
            for (Subject subject : subjects) {
                if (!subject.hasUHAttributes()) {
                    continue;
                }
                Person person = new Person(subject);
                if (group.getPath().endsWith(GroupType.BASIS.value()) && subject.getSourceId() != null
                        && subject.getSourceId()
                        .equals(STALE_SUBJECT_ID)) {
                    person.setUsername("User Not Available.");
                }
                group.addMember(new Person(subject));
            }
            groupMembers.put(group.getPath(), group);
        }
        return groupMembers;
    }
}
