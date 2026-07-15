-- Seed default product categories
INSERT INTO product_categories (id, name, description) VALUES
(uuid_generate_v4(), 'Beverages',     'Soft drinks, juices, water, energy drinks'),
(uuid_generate_v4(), 'Food & Snacks', 'Packaged food, snacks, biscuits, chips'),
(uuid_generate_v4(), 'Dairy',         'Milk, yogurt, cheese, butter'),
(uuid_generate_v4(), 'Household',     'Cleaning products, detergents, paper products'),
(uuid_generate_v4(), 'Personal Care', 'Shampoo, soap, toothpaste, deodorant'),
(uuid_generate_v4(), 'Confectionery', 'Chocolates, sweets, candies, gum'),
(uuid_generate_v4(), 'Tobacco',       'Cigarettes and tobacco products'),
(uuid_generate_v4(), 'General',       'General merchandise and other products');
