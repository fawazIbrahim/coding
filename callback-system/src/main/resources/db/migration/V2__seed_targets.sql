-- =============================================================================
--  V2 — Sample callback targets for local development / smoke-testing
-- =============================================================================

INSERT INTO callback_targets (name, url, http_method, auth_type,
                               max_retries, retry_base_delay_seconds,
                               retry_backoff_multiplier, timeout_seconds)
VALUES
    -- No-auth webhook (use https://webhook.site for testing)
    ('webhook-example',
     'https://webhook.site/your-unique-id',
     'POST', 'NONE', 3, 60, 2.0, 30),

    -- Bearer-token protected internal service
    ('internal-service',
     'http://internal-service.local/api/callbacks',
     'POST', 'BEARER_TOKEN', 5, 30, 1.5, 10),

    -- API-key protected external partner
    ('partner-api',
     'https://partner.example.com/hooks/events',
     'POST', 'API_KEY', 3, 120, 2.0, 15);
