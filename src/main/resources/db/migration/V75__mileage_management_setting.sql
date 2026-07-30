INSERT INTO system_settings (key, value, description) VALUES
('mileage_management_enabled', 'true', 'When true, sales reps must record start/end odometer mileage for each work session, and the Mileage report is available. When false, the feature is hidden everywhere.')
ON CONFLICT (key) DO NOTHING;
