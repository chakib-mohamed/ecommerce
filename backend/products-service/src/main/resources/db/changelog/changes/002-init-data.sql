-- liquibase formatted sql

-- changeset chakib:002-init-data

insert into category (id, label, parent_id) values (1, 'Dinning table', null) ON CONFLICT DO NOTHING;
insert into category (id, label, parent_id) values (2, 'Decoration table', null) ON CONFLICT DO NOTHING;
insert into category (id, label, parent_id) values (3, 'Garden table', null) ON CONFLICT DO NOTHING;
insert into category (id, label, parent_id) values (4, 'Nightstand table', null) ON CONFLICT DO NOTHING;

insert into product (id, description, price, title, uuid) values (1, 'A very unique dinning table', 100, 'Table à manger', '11111111-1111-1111-1111-111111111111') ON CONFLICT DO NOTHING;
insert into product (id, description, price, title, uuid) values (2, 'A very beautiful lack side table', 200, 'Lack side table', '22222222-2222-2222-2222-222222222222') ON CONFLICT DO NOTHING;
insert into product (id, description, price, title, uuid) values (3, 'An amazing newyork  table', 300, 'Table à manger New yorkaise', '33333333-3333-3333-3333-333333333333') ON CONFLICT DO NOTHING;
insert into product (id, description, price, title, uuid) values (4, 'Table basse biron', 200.99, 'Table basse biron', '44444444-4444-4444-4444-444444444444') ON CONFLICT DO NOTHING;
insert into product (id, description, price, title, uuid) values (5, 'Table basse jardin', 150, 'Table basse jardin', '55555555-5555-5555-5555-555555555555') ON CONFLICT DO NOTHING;
insert into product (id, description, price, title, uuid) values (6, 'Table ronde en bois tavla', 120, 'Table ronde bois', '66666666-6666-6666-6666-666666666666') ON CONFLICT DO NOTHING;

insert into product_category (product_id, category_id) values (1, 1) ON CONFLICT DO NOTHING;
insert into product_category (product_id, category_id) values (2, 1) ON CONFLICT DO NOTHING;
insert into product_category (product_id, category_id) values (3, 1) ON CONFLICT DO NOTHING;
insert into product_category (product_id, category_id) values (4, 4) ON CONFLICT DO NOTHING;
insert into product_category (product_id, category_id) values (5, 3) ON CONFLICT DO NOTHING;
insert into product_category (product_id, category_id) values (6, 2) ON CONFLICT DO NOTHING;

insert into promotion (id, active_from, active_to, label, percentage_off) values (1, '2020-09-11', '2020-09-25', 'new promo', 10) ON CONFLICT DO NOTHING;
