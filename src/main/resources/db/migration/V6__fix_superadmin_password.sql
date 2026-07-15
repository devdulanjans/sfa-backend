-- Fix superadmin password hash: corrected BCrypt hash for 'Admin@123'
UPDATE users
SET password_hash = '$2a$12$UiqzZcv63C.DqsmbqA4MqepDDX6B7kAjjurgOMvazkzGs6sjN8T.6'
WHERE username = 'superadmin';
