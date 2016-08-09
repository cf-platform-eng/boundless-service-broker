package org.boundless.cf.servicebroker.service.boundless;

import java.time.Duration;

import org.apache.log4j.Logger;
import org.boundless.cf.servicebroker.cfutils.CFAppManager;
import org.boundless.cf.servicebroker.exception.ServiceBrokerException;
import org.boundless.cf.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.boundless.cf.servicebroker.exception.ServiceInstanceExistsException;
import org.boundless.cf.servicebroker.exception.ServiceInstanceUpdateNotSupportedException;
import org.boundless.cf.servicebroker.model.BoundlessAppResource;
import org.boundless.cf.servicebroker.model.BoundlessAppResourceConstants;
import org.boundless.cf.servicebroker.model.BoundlessServiceInstance;
import org.boundless.cf.servicebroker.model.BoundlessServiceInstanceMetadata;
import org.boundless.cf.servicebroker.model.OperationState;
import org.boundless.cf.servicebroker.model.ServiceDefinition;
import org.boundless.cf.servicebroker.model.ServiceInstance;
import org.boundless.cf.servicebroker.model.ServiceInstanceLastOperation;
import org.boundless.cf.servicebroker.model.dto.AppMetadataDTO;
import org.boundless.cf.servicebroker.model.dto.CreateServiceInstanceRequest;
import org.boundless.cf.servicebroker.model.dto.DeleteServiceInstanceRequest;
import org.boundless.cf.servicebroker.model.dto.UpdateServiceInstanceRequest;
import org.boundless.cf.servicebroker.repository.BoundlessAppMetadataRepository;
import org.boundless.cf.servicebroker.repository.BoundlessServiceInstanceMetadataRepository;
import org.boundless.cf.servicebroker.repository.BoundlessServiceInstanceRepository;
import org.boundless.cf.servicebroker.repository.PlanRepository;
import org.boundless.cf.servicebroker.service.CatalogService;
import org.boundless.cf.servicebroker.service.ServiceInstanceService;
import org.cloudfoundry.client.CloudFoundryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Service
public class BoundlessServiceInstanceService implements ServiceInstanceService {

	private static final Logger log = Logger
			.getLogger(BoundlessServiceInstanceService.class);
	
	@Value("${consul.host:null}")
    private String consulHost;
	
	@Value("${consul.port:8301}")
    private int consulPort;
	
	@Value("${contact.organization:PIVOTAL}")
    private String envContactOrg;

	@Value("${gwc.service.provider:null}")
    private String gwcServiceProvider;
	
	@Autowired
	CatalogService catalogService;

	@Autowired
	BoundlessServiceInstanceRepository serviceInstanceRepository;
	
	@Autowired
	BoundlessServiceInstanceMetadataRepository serviceInstanceMetadataRepository;
	
	@Autowired
	BoundlessAppMetadataRepository boundlessAppRepository;

	@Autowired
	PlanRepository planRepository;
	
	@Autowired
	CloudFoundryClient cfClient;
	

	public String getConsulHost() {
		return consulHost;
	}

	public void setConsulHost(String consulHost) {
		this.consulHost = consulHost;
	}

	public int getConsulPort() {
		return consulPort;
	}

	public void setConsulPort(int consulPort) {
		this.consulPort = consulPort;
	}

	@Override
	public ServiceInstance getServiceInstance(String id) {
		if (id == null)
			return null;
				
		BoundlessServiceInstance instance = getInstance(id);
		if (instance == null) {
			log.warn("Service instance with id: " + id + " not found!");
			return null;
		}
		
		// check the last operation
		ServiceInstanceLastOperation silo = instance
				.getLastOperation();
		
		String state = ((silo != null) ? silo.getState(): null);
		log.debug("service instance id: " + id + " is in state: " + state);

		// if this is a delete request and was successful, remove the instance
		if (instance.isCurrentOperationSuccessful()
				&& ServiceInstance.DELETE_REQUEST.equals(instance.getCurrentOperation())) {
			deleteInstance(instance);
		}

		// otherwise save the instance with the new last operation
		return saveInstance(instance);
	}

	@Override
	public ServiceInstance createServiceInstance(
			CreateServiceInstanceRequest request)
			throws ServiceInstanceExistsException, ServiceBrokerException {

		if (request == null 
				|| request.getServiceDefinitionId() == null 
				|| request.getOrganizationGuid() == null 
				|| request.getPlanId() == null 
				|| request.getServiceInstanceId() == null) {
			throw new ServiceBrokerException(
					"invalid CreateServiceInstanceRequest object.");
		}


		if (request.getServiceInstanceId() != null
				&& getInstance(request.getServiceInstanceId()) != null) {
			throw new ServiceInstanceExistsException(serviceInstanceRepository.findOne(request
					.getServiceInstanceId()));
		}

		ServiceDefinition sd = catalogService.getServiceDefinition(request
				.getServiceDefinitionId());

		if (sd == null) {
			throw new ServiceBrokerException(
					"Unable to find service definition with id: "
							+ request.getServiceDefinitionId());
		}

		BoundlessServiceInstance serviceInstance = new BoundlessServiceInstance(request);	
		serviceInstance.setCurrentOperation(ServiceInstance.CREATE_REQUEST);
		serviceInstance.setLastOperation(new ServiceInstanceLastOperation("Provisioning", OperationState.IN_PROGRESS));
    	serviceInstance = saveInstance(serviceInstance);
    	
		createApp(serviceInstance);
    	serviceInstance = saveInstance(serviceInstance);

		log.debug("Registered service instance: "
				+ serviceInstance);

		return serviceInstance;
	}

	@Override
	public ServiceInstance deleteServiceInstance(
			DeleteServiceInstanceRequest request) throws ServiceBrokerException {

		if (request == null || request.getServiceInstanceId() == null) {
			throw new ServiceBrokerException(
					"invalid DeleteServiceInstanceRequest object.");
		}

		BoundlessServiceInstance serviceInstance = getInstance(request.getServiceInstanceId());
		if (serviceInstance == null) {
			throw new ServiceBrokerException("Service instance: "
					+ request.getServiceInstanceId() + " not found.");
		}

		serviceInstance.setCurrentOperation(ServiceInstance.DELETE_REQUEST);
		serviceInstance.setLastOperation(new ServiceInstanceLastOperation("Deprovisioning", OperationState.IN_PROGRESS));
		serviceInstance = saveInstance(serviceInstance);
		log.debug("deleting service instance: " + request.getServiceInstanceId() + ", full ServiceInstance details: " + serviceInstance);

		serviceInstance = deleteInstance(serviceInstance);

		log.debug("Unregistered service instance: "
				+ serviceInstance);

		return serviceInstance;
	}

	@Override
	public ServiceInstance updateServiceInstance(
			UpdateServiceInstanceRequest request)
			throws ServiceInstanceUpdateNotSupportedException,
			ServiceBrokerException, ServiceInstanceDoesNotExistException {

		BoundlessServiceInstance existingInstance = getInstance(request.getServiceInstanceId());
		if (existingInstance == null || existingInstance.getId() == null) {
			return null;
		}
		log.debug("Update on Service Instance: " + request);
		//BoundlessServiceInstance updateToInstance = new BoundlessServiceInstance(request);
		existingInstance.update(request);
		log.debug("Updated service instance to: "
				+ existingInstance);
		
		// First persist the state so if any calls into check the state it can show as being deleted...
		existingInstance.setCurrentOperation(ServiceInstance.UPDATE_REQUEST);
		existingInstance.setLastOperation(new ServiceInstanceLastOperation("Updating", OperationState.IN_PROGRESS));
		existingInstance = saveInstance(existingInstance);	
		
		this.updateApp(existingInstance);
		existingInstance.getLastOperation().setState(OperationState.SUCCEEDED);
		
		BoundlessServiceInstanceMetadata boundlessSIMetadata = existingInstance.getMetadata();
		if (boundlessSIMetadata != null) {
			boundlessAppRepository.save(boundlessSIMetadata);	
		}

		existingInstance = saveInstance(existingInstance);
		log.debug("Updated service instance: "
				+ existingInstance);
		return existingInstance;
	}

	private BoundlessServiceInstance getInstance(String id) {
		if (id == null)
			return null;
		
		return serviceInstanceRepository.findOne(id);
	}

	private BoundlessServiceInstance deleteInstance(BoundlessServiceInstance instance) {
		if (instance == null || instance.getId() == null) {
			return null;
		}
		
		// First persist the state so if any calls into check the state it can show as being deleted...
		instance.setCurrentOperation(BoundlessServiceInstance.DELETE_REQUEST);
		instance.setLastOperation(new ServiceInstanceLastOperation("Deprovisioning", 
										OperationState.IN_PROGRESS));
		serviceInstanceRepository.save(instance);
		
		log.debug("Starting deletion of service instance: " + instance);
		
		this.deleteApp(instance);    	
		instance.getLastOperation().setState(OperationState.SUCCEEDED);
		saveInstance(instance);
		
		if (instance.isCurrentOperationSuccessful()
				&& instance.getCurrentOperation().equals(ServiceInstance.DELETE_REQUEST)) {
			
			BoundlessServiceInstanceMetadata boundlessSIMetadata = instance.getMetadata();			
			if (boundlessSIMetadata != null) {
				boundlessAppRepository.delete(boundlessSIMetadata);	
			}			
			serviceInstanceRepository.delete(instance.getId());
		}
		
		log.debug("Deleted service instance: " + instance);
		return instance;
	}

	private BoundlessServiceInstance saveInstance(BoundlessServiceInstance instance) {
		return serviceInstanceRepository.save(instance);
	}	
	
	/*
	 *  Update the GWC instance env variable 'GEOSERVER_HOST' with pointer to GeoServer route
	 *  
	 */
	private void updateEnvVariablesForInstance(BoundlessServiceInstance serviceInstance) {
		BoundlessServiceInstanceMetadata boundlessSIMetadata = serviceInstance.getMetadata();
		BoundlessAppResource geoWebCacheAppResource = boundlessSIMetadata.getResource(BoundlessAppResourceConstants.GWC_TYPE);
		BoundlessAppResource geoServerAppResource = boundlessSIMetadata.getResource(BoundlessAppResourceConstants.GEOSERVER_TYPE);
		
		if (this.getConsulHost() != null) {
			geoServerAppResource.addToEnvironment(BoundlessAppResourceConstants.CONSUL_HOST, this.getConsulHost());
			geoServerAppResource.addToEnvironment(BoundlessAppResourceConstants.CONSUL_PORT, this.getConsulPort());
			geoWebCacheAppResource.addToEnvironment(BoundlessAppResourceConstants.CONSUL_HOST, this.getConsulHost());
			geoWebCacheAppResource.addToEnvironment(BoundlessAppResourceConstants.CONSUL_PORT, this.getConsulPort());
		}
		
		// Add Service Instance name
		String serviceInstanceGuid = serviceInstance.getId();
		geoWebCacheAppResource.addToEnvironment(BoundlessAppResourceConstants.SERVICE_INSTANCE_NAME, serviceInstanceGuid);
		geoServerAppResource.addToEnvironment(BoundlessAppResourceConstants.SERVICE_INSTANCE_NAME, serviceInstanceGuid);
		
		// Add user & passwords
		geoServerAppResource.addToEnvironment(BoundlessAppResourceConstants.GEOSERVER_ADMIN_ID, geoServerAppResource.getUser());
		geoServerAppResource.addToEnvironment(BoundlessAppResourceConstants.GEOSERVER_ADMIN_PASSWD, geoServerAppResource.getPassword());	
				
		geoWebCacheAppResource.addToEnvironment(BoundlessAppResourceConstants.GWC_ADMIN_ID, geoWebCacheAppResource.getUser());
		geoWebCacheAppResource.addToEnvironment(BoundlessAppResourceConstants.GWC_ADMIN_PASSWD, geoWebCacheAppResource.getPassword());
		
		geoWebCacheAppResource.addToEnvironment(BoundlessAppResourceConstants.GEOSERVER_ADMIN_ID, geoServerAppResource.getUser());
		geoWebCacheAppResource.addToEnvironment(BoundlessAppResourceConstants.GEOSERVER_ADMIN_PASSWD, geoServerAppResource.getPassword());	
		
		// Update the GWC instance env variable 'GEOSERVER_HOST' with pointer to GeoServer route
		String geoServerUrl = geoServerAppResource.getRoute() + "." + boundlessSIMetadata.getDomain(); 
		geoWebCacheAppResource.addToEnvironment(BoundlessAppResourceConstants.GEOSERVER_HOST, geoServerUrl);
		geoWebCacheAppResource.addToEnvironment(BoundlessAppResourceConstants.GEOSERVER_PORT, 80);	
		
		geoServerAppResource.addToEnvironment(BoundlessAppResourceConstants.CONTACT_ORGANIZATION_KEY, 
				envContactOrg);
		geoWebCacheAppResource.addToEnvironment(BoundlessAppResourceConstants.CONTACT_ORGANIZATION_KEY, 
				envContactOrg);		
	}
		
	/*
	 * Create and Bind to PostGis or S3 Object store type service for GWC instances
	 * 
	 */
	private String createAndBindToGWCService(String serviceProvider, String serviceName, String spaceGuid, String appId) {
		
		if (serviceProvider == null)
			return null;
		
		Mono<String> serviceId = null;
		Mono<String> servicePlanId = null;
		Mono<String> serviceInstanceId = null;
		Mono<String> serviceBindInstanceId = null;
		try {
			serviceId = CFAppManager.requestServiceId(cfClient, serviceProvider);
			if (serviceId == null)
				return null;
			
			servicePlanId = CFAppManager.requestServicePlanId(cfClient, serviceId);
			if (servicePlanId == null)
				return null;			

			serviceInstanceId = CFAppManager.requestCreateServiceInstance( cfClient, serviceName, spaceGuid, servicePlanId);
			serviceBindInstanceId = CFAppManager.requestCreateServiceBinding( cfClient, appId, serviceInstanceId);
			
			// Need a get() on function call to really execute the logic in Reactive
			return serviceBindInstanceId.block();
		
		} catch(Exception e) {
			// In case of any errors, clean up the instance created earlier.
			if (serviceInstanceId != null) {
				CFAppManager.requestDeleteServiceInstance(cfClient, serviceInstanceId.block(Duration.ofMinutes(15)));
			}
			return null;
		}
	}
	
	/*
	 * Unbind and delete service from GWC instances
	 * 
	 */
	private void unbindAndDeleteGWCService(String serviceBindInstanceId) {
		
		String serviceInstanceId = CFAppManager.requestGetServiceInstanceFromBinding(cfClient, serviceBindInstanceId).block();
		CFAppManager.requestDeleteServiceBinding(cfClient, serviceBindInstanceId).block();
		
		if (serviceInstanceId != null) {			
			CFAppManager.requestDeleteServiceInstance(cfClient, serviceInstanceId).block();
		}
	}

	
	public void createApp(BoundlessServiceInstance serviceInstance) throws ServiceBrokerException {
		
		BoundlessServiceInstanceMetadata boundlessSIMetadata = serviceInstance.getMetadata();
		if (boundlessSIMetadata == null)
			return;
		
    	log.debug("Boundless App Metadata at create: " + boundlessSIMetadata);
    	
    	try {
    		// These org & space guids come from the service instance creation
    		String orgGuid = boundlessSIMetadata.getOrgGuid();
    		String spaceGuid = boundlessSIMetadata.getSpaceGuid();
    		
    		// If the user has provided explicitly their own org & spaces to create the apps
    		// then lookup for the associated guids and go with those.
    		// instead of going with the service instance org/space guids.
      		if (boundlessSIMetadata.isTargetOrgDefined()) {
      			String org = boundlessSIMetadata.getOrg();
        		orgGuid = CFAppManager.requestOrganizationId(cfClient, org).block();
        		boundlessSIMetadata.setOrgGuid(orgGuid);
    		}
    		
       		if (boundlessSIMetadata.isTargetSpaceDefined()) {
       			String space = boundlessSIMetadata.getSpace();
    			spaceGuid = CFAppManager.requestSpaceId(cfClient, 
    					boundlessSIMetadata.getOrgGuid(), 
    					boundlessSIMetadata.getSpace()
    					).block();
				boundlessSIMetadata.setSpaceGuid(spaceGuid);
    		}
    		
    		// Look up the domain Guid either based on provided domain name or very first available domain.
    		String domainGuid = CFAppManager.requestDomainId(cfClient, 
    				boundlessSIMetadata.getDomain()
    				).block();
    		boundlessSIMetadata.setDomainGuid(domainGuid);

    		// Whether we went with the specified domain or default domain,
    		// fill the domain name by requesting for domain details using the previously obtained domainId.
    		boundlessSIMetadata.setDomain(CFAppManager.requestDomainName(cfClient, domainGuid).block());
   		
    		// Update Env variables for app instances
    		updateEnvVariablesForInstance(serviceInstance);

    		String[] resourceTypes = BoundlessAppResourceConstants.getTypes(); 
	    	for(String resourceType: resourceTypes) {
		    	AppMetadataDTO appMetadata = boundlessSIMetadata.generateAppMetadata(resourceType);
		    	if (appMetadata == null || appMetadata.getInstances() == 0) {
		    		continue;
		    	}
		    	
		    	// Need a get() on function call to really execute the logic in Reactive
		    	BoundlessAppResource resource = boundlessSIMetadata.getResource(resourceType);
		    	
	    		Tuple2<String, String> resultPair = CFAppManager.push(cfClient, appMetadata).block(Duration.ofMinutes(15)); 
		    	if (resultPair != null) {
		    		String appId = resultPair.t1;
		    		String routeId = resultPair.t2;
		    		resource.setAppGuid(appId);
		    		resource.setRouteGuid(routeId);
		    		
		    		// Bind to PostGis or Riak-CS
		    		if (BoundlessAppResourceConstants.isOfType(resourceType, BoundlessAppResourceConstants.GWC_TYPE)) {
		    			String serviceBindingId = createAndBindToGWCService(gwcServiceProvider, gwcServiceProvider + "-" + appMetadata.getName(), spaceGuid, appId);
		    			if (serviceBindingId != null)
		    				resource.addToServiceBindings(serviceBindingId);
			    	}
		    	}
	    	}
    	} catch(Exception e) {
    		e.printStackTrace();
    		// Cleanup all left-overs
    		this.deleteApp(serviceInstance);
    		throw new ServiceBrokerException("Error with service instance creation: " 
    								+ e.getMessage());
    	}
    	
    	serviceInstance.getLastOperation().setState(OperationState.SUCCEEDED);
    	serviceInstance.setMetadata(boundlessSIMetadata);
     	log.debug("Boundless App Metadata at end of push: " + boundlessSIMetadata);
	}

	public void updateApp(BoundlessServiceInstance serviceInstance) {
		
		BoundlessServiceInstanceMetadata boundlessSIMetadata = serviceInstance.getMetadata();
		
		if (boundlessSIMetadata == null)
			return;
		
     	log.debug("Boundless App Metadata at update: " + boundlessSIMetadata);
    	String[] resourceTypes = BoundlessAppResourceConstants.getTypes(); 
    	for(String resourceType: resourceTypes) {
	    	AppMetadataDTO appMetadata = boundlessSIMetadata.generateAppMetadata(resourceType);
	    	if (appMetadata != null && appMetadata.getInstances() > 0) {
	    		// Need a get() on function call to really execute the logic in Reactive
	    		CFAppManager.update(cfClient, appMetadata).block(Duration.ofMinutes(15));
	    	}
    	}
    	
    	serviceInstance.getLastOperation().setState(OperationState.SUCCEEDED);
    	log.debug("Boundless App Metadata at end of update: " + boundlessSIMetadata);
    	serviceInstance.setMetadata(boundlessSIMetadata);
	}
	
	public void deleteApp(BoundlessServiceInstance serviceInstance) {
		
		BoundlessServiceInstanceMetadata boundlessSIMetadata = serviceInstance.getMetadata();
		if (boundlessSIMetadata == null)
			return;
		
		String[] resourceTypes = BoundlessAppResourceConstants.getTypes(); 
    	for(String resourceType: resourceTypes) {
	    	
    		BoundlessAppResource resource = boundlessSIMetadata.getResource(resourceType);
    		
    		// Unbind and delete any associated service instances
			String serviceBindingIds = resource.getServiceBindings();
			if (serviceBindingIds != null && !serviceBindingIds.equals("")) {
				for(String serviceBindInstanceId : serviceBindingIds.split(",")) {
					unbindAndDeleteGWCService(serviceBindInstanceId.trim());
				}
			}

    		if (resource != null && resource.getInstances() > 0) {
	    		// We might not have the complete app or route guid filled in case of error during app push,
	    		// Just clean up whatever is left over - do it separately for route & app
	    		// Need a get() on function call to really execute the logic in Reactive
	    		CFAppManager.deleteRoute(cfClient, resource.getRouteGuid()).block(Duration.ofMinutes(15));
	    		CFAppManager.deleteApplications(cfClient, boundlessSIMetadata.getSpaceGuid(), resource.getAppName()).block(Duration.ofMinutes(15));	    		
	    	}
    	}
    	boundlessAppRepository.delete(boundlessSIMetadata);
    	serviceInstance.getLastOperation().setState(OperationState.SUCCEEDED);
	}
}