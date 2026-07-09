-- V1__init.sql: Define schemas for tax_rates and audit_logs

CREATE TABLE IF NOT EXISTS tax_rates (
    country_code VARCHAR(10) NOT NULL,
    category VARCHAR(50) NOT NULL,
    rate_percentage DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (country_code, category)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(100) NOT NULL UNIQUE,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Seed initial tax rates for testing
INSERT INTO tax_rates (country_code, category, rate_percentage) VALUES 
('DE', 'ELECTRONICS', 19.0),
('DE', 'BOOKS', 7.0),
('IN', 'SERVICES', 18.0),
('IN', 'FOOD', 5.0),
('US', 'GENERAL', 8.25)
ON CONFLICT (country_code, category) DO NOTHING;
