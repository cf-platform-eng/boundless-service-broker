package org.boundless.cf.servicebroker.repository;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.boundless.cf.servicebroker.model.Plan;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

@Component
public interface PlanRepository extends CrudRepository<Plan, String> {
	@Transactional
	Plan save(Plan plan);
	
	@Query("SELECT p FROM Plan p where  p.id = :name or p.name = :name")
	Optional<Plan> findByPlanIdOrName(@Param("name") String name);
	
	@Query("SELECT p FROM Plan p where (p.name = :name or p.id = :name) and p.service.id = :service_id")
	Optional<Plan> findByPlanIdOrNameAndServiceId(@Param("name") String name, @Param("service_id") String service_id);	
}
