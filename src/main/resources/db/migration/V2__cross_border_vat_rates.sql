-- V2__cross_border_vat_rates.sql
-- Replace the placeholder seed data with the FinoMarket cross-border rate table.
-- Categories: STANDARD (default), BOOKS, FOOD (Stretch Goal 1 reduced rates).
-- LATAM countries have no reduced book/food rates in this prototype, so those
-- rows are seeded at the standard rate (documented simplification).

DELETE FROM tax_rates;

INSERT INTO tax_rates (country_code, category, rate_percentage) VALUES
-- Standard VAT/IVA rates
('MX', 'STANDARD', 16.0),   -- Mexico IVA
('BR', 'STANDARD', 17.0),   -- Brazil (simplified national average)
('CO', 'STANDARD', 19.0),   -- Colombia IVA
('CL', 'STANDARD', 19.0),   -- Chile IVA
('UK', 'STANDARD', 20.0),   -- United Kingdom VAT
('DE', 'STANDARD', 19.0),   -- Germany VAT
('ES', 'STANDARD', 21.0),   -- Spain VAT
('FR', 'STANDARD', 20.0),   -- France VAT

-- Reduced rates: BOOKS
('UK', 'BOOKS', 0.0),
('DE', 'BOOKS', 7.0),
('ES', 'BOOKS', 4.0),
('FR', 'BOOKS', 5.5),
('MX', 'BOOKS', 16.0),
('BR', 'BOOKS', 17.0),
('CO', 'BOOKS', 19.0),
('CL', 'BOOKS', 19.0),

-- Reduced rates: FOOD
('UK', 'FOOD', 0.0),
('DE', 'FOOD', 7.0),
('ES', 'FOOD', 10.0),
('FR', 'FOOD', 5.5),
('MX', 'FOOD', 16.0),
('BR', 'FOOD', 17.0),
('CO', 'FOOD', 19.0),
('CL', 'FOOD', 19.0);
