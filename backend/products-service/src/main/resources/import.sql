insert into category values (1, 'Dinning table', 'DinningTable') ON CONFLICT DO NOTHING;
insert into category values (2, 'Decoration table', 'DecorationTable') ON CONFLICT DO NOTHING;
insert into category values (3, 'Garden table', 'GardenTable') ON CONFLICT DO NOTHING;
insert into category values (4, 'Nightstand table', 'NightstandTable') ON CONFLICT DO NOTHING;

insert into product values (1,'A very unique dinning table' ,'table-a-manger.jpg',100,'Table à manger',1) ON CONFLICT DO NOTHING;
insert into product values (2,'A very beautiful lack side table' ,'lack-side-table.webp',200,'Lack side table',1) ON CONFLICT DO NOTHING;
insert into product values (3,'An amazing newyork  table' ,'table-a-manger-new-yorkaise.jpg',300,'Table à manger New yorkaise',1) ON CONFLICT DO NOTHING;
insert into product values (4,'Table basse biron' ,'table-basse-biron-120.jpeg',200.99,'Table basse biron',4) ON CONFLICT DO NOTHING;
insert into product values (5,'Table basse jardin' ,'table-basse-jardin.jpg',150,'Table basse jardin',3) ON CONFLICT DO NOTHING;
insert into product values (6,'Table ronde en bois tavla' ,'table-ronde-bois-tavla.jpg',120,'Table ronde bois',2) ON CONFLICT DO NOTHING;

insert into promotion values (1,'2020-09-11','2020-09-25','new promo', 10,  1) ON CONFLICT DO NOTHING;
