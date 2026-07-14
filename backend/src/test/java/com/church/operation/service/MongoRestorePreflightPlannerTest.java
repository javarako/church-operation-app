package com.church.operation.service;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoRestorePreflightPlannerTest {
    private static final String DATABASE_NAME = "restore_db";

    private final MongoRestorePreflightPlanner planner = new MongoRestorePreflightPlanner(DATABASE_NAME);

    @ParameterizedTest
    @MethodSource("invalidNamespaceNames")
    void rejectsInvalidOriginalNamespaceNamesBeforeStaging(String name) {
        assertThatThrownBy(() -> planner.validateNamespaceName(name))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid or reserved");
    }

    @Test
    void acceptsNamespaceAtMongoUtf8ByteBoundary() {
        int availableNameBytes = 255 - DATABASE_NAME.getBytes(StandardCharsets.UTF_8).length - 1;

        assertThatCode(() -> planner.validateNamespaceName("a".repeat(availableNameBytes)))
            .doesNotThrowAnyException();
    }

    @Test
    void recursivelyFindsAndRewritesEverySupportedViewDependency() {
        BsonDocument command = BsonDocument.parse("""
            {
              "create": "activity_view",
              "viewOn": "members",
              "pipeline": [
                {"$lookup": {
                  "from": "offerings",
                  "pipeline": [
                    {"$facet": {
                      "history": [
                        {"$unionWith": {
                          "coll": "audit_events",
                          "pipeline": [
                            {"$graphLookup": {
                              "from": "member_links",
                              "startWith": "$_id",
                              "connectFromField": "_id",
                              "connectToField": "memberId",
                              "as": "links"
                            }}
                          ]
                        }}
                      ]
                    }}
                  ],
                  "as": "history"
                }},
                {"$unionWith": "archived_members"}
              ]
            }
            """);
        Map<String, String> stagingNames = new LinkedHashMap<>();
        for (String name : Set.of(
            "activity_view", "members", "offerings", "audit_events", "member_links", "archived_members"
        )) {
            stagingNames.put(name, "staging_" + name);
        }

        assertThat(planner.viewDependencies(command)).containsExactlyInAnyOrder(
            "members", "offerings", "audit_events", "member_links", "archived_members"
        );

        BsonDocument staged = planner.stagingCreateCommand(
            command,
            stagingNames.get("activity_view"),
            stagingNames
        );

        assertThat(staged.getString("create").getValue()).isEqualTo("staging_activity_view");
        assertThat(planner.viewDependencies(staged)).containsExactlyInAnyOrder(
            "staging_members",
            "staging_offerings",
            "staging_audit_events",
            "staging_member_links",
            "staging_archived_members"
        );
        assertThat(command.getString("viewOn").getValue()).isEqualTo("members");
    }

    @Test
    void ordersViewsByRecursiveDependenciesAndRejectsCycles() {
        BsonDocument summary = viewCommand(
            "summary", "members", "[{\"$lookup\":{\"from\":\"details\",\"as\":\"details\"}}]"
        );
        BsonDocument details = viewCommand("details", "members", "[]");

        assertThat(planner.orderedViewNames(
            Map.of("summary", summary, "details", details),
            Set.of("members", "summary", "details")
        )).containsExactly("details", "summary");

        BsonDocument cyclicDetails = viewCommand(
            "details", "members", "[{\"$unionWith\":{\"coll\":\"summary\",\"pipeline\":[]}}]"
        );
        assertThatThrownBy(() -> planner.orderedViewNames(
            Map.of("summary", summary, "details", cyclicDetails),
            Set.of("members", "summary", "details")
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    void rejectsMissingNestedViewDependency() {
        BsonDocument view = viewCommand(
            "summary", "members", "[{\"$unionWith\":{\"coll\":\"missing\",\"pipeline\":[]}}]"
        );

        assertThatThrownBy(() -> planner.orderedViewNames(
            Map.of("summary", view),
            Set.of("members", "summary")
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("source is missing");
    }

    private BsonDocument viewCommand(String name, String viewOn, String pipelineJson) {
        return new BsonDocument("create", new BsonString(name))
            .append("viewOn", new BsonString(viewOn))
            .append("pipeline", BsonDocument.parse("{\"pipeline\":" + pipelineJson + "}").getArray("pipeline"));
    }

    private static Stream<String> invalidNamespaceNames() {
        int availableNameBytes = 255 - DATABASE_NAME.getBytes(StandardCharsets.UTF_8).length - 1;
        return Stream.of(
            "",
            "   ",
            "$members",
            "members$archive",
            "members.system.profile",
            "system.profile",
            MongoDatabaseExportService.RESTORE_STAGING_PREFIX + "existing",
            MongoDatabaseExportService.RESTORE_BACKUP_PREFIX + "existing",
            "members\0archive",
            "members\narchive",
            "members\u007farchive",
            "members\u0085archive",
            "members\ud800archive",
            "a".repeat(availableNameBytes + 1),
            "\u00e9".repeat((availableNameBytes / 2) + 1)
        );
    }
}
