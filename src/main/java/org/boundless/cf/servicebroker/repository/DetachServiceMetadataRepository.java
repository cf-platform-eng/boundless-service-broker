package org.boundless.cf.servicebroker.repository;

import org.boundless.cf.servicebroker.model.ServiceMetadata;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

@Component
public interface DetachServiceMetadataRepository {
		void detachServiceMetadata(ServiceMetadata sm);
}
