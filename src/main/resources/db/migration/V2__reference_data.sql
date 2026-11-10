-- Reference data every environment needs (docs/02-ARCHITECTURE-PROPOSAL.md, section 5.6).
-- Source ids come from the table's sequence so they never collide with ids Hibernate hands out later.

INSERT INTO event_categories (code, display_name, description, enabled) VALUES
    ('NEWS',     'News',               'Breaking and developing news stories', TRUE),
    ('MARKET',   'Markets',            'Market movements such as large price changes', TRUE),
    ('DISASTER', 'Natural disasters',  'Earthquakes and other natural disasters', TRUE);

INSERT INTO event_sources (id, code, name, type, enabled, config, created_at) VALUES
    (NEXTVAL('event_sources_seq'), 'manual',           'Manual and API push',            'PUSH',      TRUE,  '{}', CURRENT_TIMESTAMP);
INSERT INTO event_sources (id, code, name, type, enabled, config, created_at) VALUES
    (NEXTVAL('event_sources_seq'), 'simulator',        'Event simulator',                'SIMULATED', TRUE,  '{}', CURRENT_TIMESTAMP);
INSERT INTO event_sources (id, code, name, type, enabled, config, created_at) VALUES
    (NEXTVAL('event_sources_seq'), 'usgs-earthquakes', 'USGS earthquake feed (stretch)', 'POLL',      FALSE, '{}', CURRENT_TIMESTAMP);
