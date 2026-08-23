insert into sources (code, name)
values ('magento', 'Sklep internetowy Magento'),
       ('vending', 'Soczewkomaty');

insert into api_clients (id, name, token_hash, source_code)
values ('a0f1c1d2-0000-4000-8000-000000000001', 'magento-webhook',
        '2a131bd55315acf033df56beca378a5eba33e9baaf6d0f1044b52b2534d5155b', 'magento'),
       ('a0f1c1d2-0000-4000-8000-000000000002', 'vending-uplink',
        '1997f71b5bb206b8de929fac7820f823331edd0049f51131c7e3c81c7f87de42', 'vending');

insert into products (sku, name)
values ('SOCZ-1DAY-30', 'Soczewki jednodniowe 30 szt.'),
       ('SOCZ-MIES-6', 'Soczewki miesięczne 6 szt.'),
       ('PLYN-360', 'Płyn pielęgnacyjny 360 ml'),
       ('KROPLE-10', 'Krople nawilżające 10 ml');
