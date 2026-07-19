INSERT INTO mst_products (product_id, product_name, cost_price, selling_price, agent_fee, super_agent_fee, product_status) VALUES
(gen_random_uuid(), 'Paket Teh', 50000.00, 75000.00, 10.00, 5.00, 'ACTIVE'),
(gen_random_uuid(), 'Paket Kopi', 150000.00, 200000.00, 12.50, 2.50, 'ACTIVE'),
(gen_random_uuid(), 'Paket Susu', 10000.00, 15000.00, 15.00, 5.00, 'ACTIVE');
