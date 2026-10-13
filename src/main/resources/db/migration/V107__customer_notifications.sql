-- WhatsApp number used for order/invoice notifications (email reuses the existing column).
ALTER TABLE customers ADD COLUMN whatsapp_number VARCHAR(20);

INSERT INTO system_settings (key, value, description) VALUES
    ('notification_email_enabled', 'false',
     'Email the customer the order/invoice details when an order is submitted or an invoice is created'),
    ('notification_whatsapp_enabled', 'false',
     'Send the customer the order/invoice details on WhatsApp when an order is submitted or an invoice is created')
ON CONFLICT (key) DO NOTHING;
