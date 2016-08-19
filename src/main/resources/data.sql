insert into servicemetadata(id, provider_display_name, display_name, long_description, image_url, documentation_url, support_url) values(1,'Boundless Suite', 'Boundless Suite', 'The Best For Open Source Software That Solves Enterprise Geospatial Challenges.', 'http://boundlessgeo.com/wp-content/uploads/2015/10/Boundless-new-web-logo-e1444657868290.png', 'http://boundlessgeo.com/products/opengeo-suite/', 'http://boundlessgeo.com/support/');
insert into services (name, id, description, bindable, metadata_id) values ('boundless-suite', 'B8E750D3-B048-493D-8E61-8F97210C8BE0', 'Open Source Server for Sharing Geospatial Data.', true, 1);

-- memory & disk are in mb
insert into planconfigs (id, geoserver_docker_uri, geocache_docker_uri, geoserver_memory, geoserver_disk, geoserver_instance, geocache_memory, geocache_disk, geocache_instance) values ('planconfigs1', 'cuttlefish/geoserver:4.9-cf-alpha', 'cuttlefish/gwc:4.8', 2048, 2048, 1, 2048, 2048, 1);
insert into planconfigs (id, geoserver_docker_uri, geocache_docker_uri, geoserver_memory, geoserver_disk, geoserver_instance, geocache_memory, geocache_disk, geocache_instance) values ('planconfigs2', 'cuttlefish/geoserver:4.9-cf-alpha', 'cuttlefish/gwc:4.8', 4096, 2048, 4, 4096, 2048, 2);

insert into planconfig_other_attributes (planconfig_other_attrib_id, name, value) values ('planconfigs1', 'someAttributeA', 'someValue1');
insert into planconfig_other_attributes (planconfig_other_attrib_id, name, value) values ('planconfigs1', 'testMemory', '4');

insert into planconfig_other_attributes (planconfig_other_attrib_id, name, value) values ('planconfigs2', 'someAttributeB', 'someValue2');
insert into planconfig_other_attributes (planconfig_other_attrib_id, name, value) values ('planconfigs2', 'testMemory', '10');


insert into planmetadata (id) values(1);
insert into planmetadata (id) values(2);

insert into cost(id, planmetadata_id, unit) values(4, 1, 'MONTHLY');
insert into cost(id, planmetadata_id, unit) values(5, 2, 'WEEKLY');

insert into cost_amounts(cost_amounts_id, value, currency) values(4, 0, 'usd');
insert into cost_amounts(cost_amounts_id, value, currency) values(5, 0, 'usd');

insert into plan_metadata_bullets (plan_metadata_id, bullets) values (1, 'Free, Basic OpenGeo Service');
insert into plan_metadata_bullets (plan_metadata_id, bullets) values (2, 'Paid, Premium OpenGeo Service');

insert into plans (name, id, description, service_id, planconfig_id, metadata_id, is_free) values ('basic', '8D6F6623-D3D4-4D79-BDED-9F9BE661F15D', 'Basic Plan limited to 2GB Memory', 'B8E750D3-B048-493D-8E61-8F97210C8BE0', 'planconfigs1', 1, 1);
insert into plans (name, id, description, service_id, planconfig_id, metadata_id, is_free) values ('premium', '407E6597-5E04-4533-A0DC-D943D7EBF5DE', 'Premium Plan limited to 4GB Memory', 'B8E750D3-B048-493D-8E61-8F97210C8BE0', 'planconfigs2', 2, 0);


























