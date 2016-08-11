package org.boundless.cf.servicebroker.repository;

import java.util.Optional;

import org.boundless.cf.servicebroker.model.ServiceMetadata;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

@Component
public interface ServiceMetadataRepository extends CrudRepository<ServiceMetadata, Integer> {
}
