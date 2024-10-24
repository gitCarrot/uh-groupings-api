package edu.hawaii.its.api.groupings;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import edu.hawaii.its.api.configuration.SpringBootWebApplication;
import edu.hawaii.its.api.util.JsonUtil;
import edu.hawaii.its.api.wrapper.AddMemberResult;
import edu.hawaii.its.api.wrapper.AddMembersResults;

import edu.internet2.middleware.grouperClient.ws.beans.WsAddMemberResults;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("localTest")
@SpringBootTest(classes = { SpringBootWebApplication.class })
public class GroupingAddResultTest {

    @Value("${groupings.api.test.uh-usernames}")
    private List<String> TEST_USERNAMES;

    @Value("${groupings.api.test.uh-numbers}")
    private List<String> TEST_NUMBERS;

    @Value("${groupings.api.test.uh-names}")
    private List<String> TEST_NAMES;

    private static Properties properties;

    @BeforeAll
    public static void beforeAll() throws Exception {
        Path path = Paths.get("src/test/resources");
        Path file = path.resolve("grouper.test.properties");
        properties = new Properties();
        properties.load(new FileInputStream(file.toFile()));
    }

    @Test
    public void test() {
        String json = propertyValue("ws.add.member.results.success");
        WsAddMemberResults wsAddMemberResults = JsonUtil.asObject(json, WsAddMemberResults.class);
        assertNotNull(wsAddMemberResults);
        AddMembersResults addMembersResults = new AddMembersResults(wsAddMemberResults);
        assertNotNull(addMembersResults);

        AddMemberResult addMemberResult = addMembersResults.getResults().get(0);
        GroupingAddResult groupingAddResult = new GroupingAddResult(addMemberResult);
        assertNotNull(groupingAddResult);
        assertEquals("SUCCESS_ALREADY_EXISTED", groupingAddResult.getResultCode());
        assertEquals(TEST_USERNAMES.get(0), groupingAddResult.getUid());
        assertEquals(TEST_NUMBERS.get(0), groupingAddResult.getUhUuid());
        assertEquals(TEST_NAMES.get(0), groupingAddResult.getName());

        addMemberResult = addMembersResults.getResults().get(2);
        groupingAddResult = new GroupingAddResult(addMemberResult);
        assertEquals("SUCCESS", groupingAddResult.getResultCode());
    }

    private String propertyValue(String key) {
        return properties.getProperty(key);
    }
}
