package com.example.starburstdemo.controller;

import com.example.starburstdemo.service.StarburstQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/starburst")
@RequiredArgsConstructor
public class QueryController {

    private final StarburstQueryService queryService;

    /**
     * POST /api/starburst/query
     * Body: { "sql": "SELECT * FROM tpch.tiny.nation LIMIT 10" }
     *
     * Only SELECT statements should be submitted here.
     */
    @PostMapping("/query")
    public ResponseEntity<List<Map<String, Object>>> runQuery(@RequestBody QueryRequest request) {
        List<Map<String, Object>> results = queryService.query(request.sql());
        return ResponseEntity.ok(results);
    }

    /**
     * GET /api/starburst/tables?catalog=tpch&schema=tiny
     */
    @GetMapping("/tables")
    public ResponseEntity<List<String>> listTables(
            @RequestParam String catalog,
            @RequestParam String schema) {
        return ResponseEntity.ok(queryService.listTables(catalog, schema));
    }

    record QueryRequest(String sql) {}
}
