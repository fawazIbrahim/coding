package com.example.starburstdemo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StarburstQueryService {

    private final JdbcTemplate starburstJdbcTemplate;

    /**
     * Run an arbitrary SELECT query and return rows as a list of column-name → value maps.
     * Use parameterised queries (?) to prevent SQL injection when incorporating user input.
     */
    public List<Map<String, Object>> query(String sql, Object... args) {
        return starburstJdbcTemplate.queryForList(sql, args);
    }

    /** Convenience: list all tables in a given catalog and schema. */
    public List<String> listTables(String catalog, String schema) {
        String sql = "SELECT table_name FROM " + catalog + ".information_schema.tables WHERE table_schema = ?";
        return starburstJdbcTemplate.queryForList(sql, String.class, schema);
    }
}
