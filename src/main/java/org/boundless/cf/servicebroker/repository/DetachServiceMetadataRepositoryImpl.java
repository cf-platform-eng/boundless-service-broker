package org.boundless.cf.servicebroker.repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.boundless.cf.servicebroker.model.ServiceMetadata;
import org.springframework.stereotype.Repository;

@Repository
public class DetachServiceMetadataRepositoryImpl implements DetachServiceMetadataRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void detachServiceMetadata(ServiceMetadata sm) {
        entityManager.detach(sm);
    }
}
