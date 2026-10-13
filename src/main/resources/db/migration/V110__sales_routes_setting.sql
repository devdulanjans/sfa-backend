-- Global switch for sales routes. Off (default): reps are never restricted to a route — the
-- mobile app shows their normal customer list and no route card. On: a rep's route for the
-- day (see V109) decides which customers they see. Admins can build and assign routes either way.
INSERT INTO system_settings (key, value, description) VALUES
('sales_routes_enabled', 'false', 'When true, a sales rep with a route assigned for the day only sees that route''s customers in the mobile app. When false, routes are not applied to sales reps.')
ON CONFLICT (key) DO NOTHING;
