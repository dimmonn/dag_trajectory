package com.uj.graph.dag_trajectory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootTest
public class SqlReproductionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reproduceSqlError() throws Exception {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS project_graphs (project_ref text PRIMARY KEY, graph_json text, source_project_ref text)");
        jdbcTemplate.execute("INSERT INTO project_graphs (project_ref, graph_json, source_project_ref) VALUES ('test-project', '{\"commits\": [{\"hash\": \"c1\", \"authoredAtEpochSecond\": 1711929600, \"parents\": []}, {\"hash\": \"c2\", \"authoredAtEpochSecond\": 1711929700, \"parents\": [\"c1\"]}]}', NULL) ON CONFLICT DO NOTHING");

        String sqlPath = "sql/run/build_quarterly_dag_snapshots.sql";
        String sql = Files.readString(Paths.get(sqlPath));
        
        String[] statements = sql.split(";");
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                try {
                    System.out.println("Executing: " + trimmed.substring(0, Math.min(trimmed.length(), 50)) + "...");
                    jdbcTemplate.execute(trimmed);
                } catch (Exception e) {
                    System.err.println("[DEBUG_LOG] Error executing statement: " + trimmed);
                    System.err.println("[DEBUG_LOG] Error message: " + e.getMessage());
                    throw e;
                }
            }
        }
    }
}
