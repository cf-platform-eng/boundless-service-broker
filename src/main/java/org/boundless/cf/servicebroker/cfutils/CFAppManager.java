package org.boundless.cf.servicebroker.cfutils;

import java.time.Duration;

/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author: bhale@pivotal.io
 * @author: sabhap@pivotal.io
 */

import java.util.Map;

import org.apache.log4j.Logger;
import org.boundless.cf.servicebroker.model.dto.AppMetadataDTO;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.applications.CreateApplicationRequest;
import org.cloudfoundry.client.v2.applications.CreateApplicationRequest.Builder;
import org.cloudfoundry.client.v2.applications.DeleteApplicationRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.applications.SummaryApplicationRequest;
import org.cloudfoundry.client.v2.applications.SummaryApplicationResponse;
import org.cloudfoundry.client.v2.applications.UpdateApplicationRequest;
import org.cloudfoundry.client.v2.domains.DomainResource;
import org.cloudfoundry.client.v2.domains.GetDomainRequest;
import org.cloudfoundry.client.v2.domains.ListDomainsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.routes.AssociateRouteApplicationRequest;
import org.cloudfoundry.client.v2.routes.CreateRouteRequest;
import org.cloudfoundry.client.v2.routes.DeleteRouteRequest;
import org.cloudfoundry.client.v2.routes.ListRoutesRequest;
import org.cloudfoundry.client.v2.servicebindings.CreateServiceBindingRequest;
import org.cloudfoundry.client.v2.servicebindings.DeleteServiceBindingRequest;
import org.cloudfoundry.client.v2.servicebindings.GetServiceBindingRequest;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.DeleteServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.GetServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.ServiceInstanceEntity;
import org.cloudfoundry.client.v2.serviceplans.ListServicePlansRequest;
import org.cloudfoundry.client.v2.services.GetServiceRequest;
import org.cloudfoundry.client.v2.services.GetServiceResponse;
import org.cloudfoundry.client.v2.services.ListServicesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;



public class CFAppManager {

    private static final Logger log = Logger.getLogger(CFAppManager.class);

    public static Mono<String> requestDomainId(CloudFoundryClient cloudFoundryClient, String domain) {
    	/*
    	if (domain != null) {
    	return Mono
                .just(domain)
                .then(domain2 -> requestDomain(cloudFoundryClient, domain2))
                .as(Stream::from)                                               
                .switchIfEmpty(requestFirstDomain(cloudFoundryClient))
                .log("stream.swithcIfEmptyOnDomain")
                .single()                                                       
                .map(resource -> resource.getMetadata().getId());
    	} 
    	*/
    	return
    		requestFirstDomain(cloudFoundryClient)
    		.map(resource -> resource.getMetadata().getId());
    }
    
    public static Mono<String> requestDomainName(CloudFoundryClient cloudFoundryClient, String domainId) {
    	
    	GetDomainRequest request = GetDomainRequest.builder()
                    .domainId(domainId)
                    .build();
            
            return cloudFoundryClient.domains().get(request)
                    .map(response -> response.getEntity().getName());
    }
    
    public static Mono<String> requestOrganizationId(CloudFoundryClient cloudFoundryClient, String organization) {
        ListOrganizationsRequest request = ListOrganizationsRequest.builder()
                .name(organization)
                .build();

        return cloudFoundryClient.organizations().list(request)
                .flatMap(response -> Flux.fromIterable(response.getResources()))
                .as(Flux::from)
                .single()
                .map(resource -> resource.getMetadata().getId());
     
        /*
        return cloudFoundryClient.organizations().list(request)
			        .then(response -> Stream
			                .fromIterable(response.getResources())
			                .single())
			        .map(resource -> resource.getMetadata().getId())
			        .log("stream.prePromise")
			        .to(Promise.prepare())
			        .log("stream.postPromise");
		*/
    }
    
    public static Mono<String> requestSpaceId(CloudFoundryClient cloudFoundryClient, String organizationId, String space) {
        
		ListSpacesRequest request = ListSpacesRequest.builder()
		        .organizationId(organizationId)
		        .name(space)
		        .build();
		
		return cloudFoundryClient.spaces().list(request)
		        .flatMap(response -> Flux.fromIterable(response.getResources()))
		        .as(Flux::from)
		        .single()
		        .map(resource -> resource.getMetadata().getId());
    }

    public static Mono<String> requestSpaceId(CloudFoundryClient cloudFoundryClient, Mono<String> organizationId, String space) {
        return organizationId
                .then(organizationId2 -> {
                    ListSpacesRequest request = ListSpacesRequest.builder()
                            .organizationId(organizationId2)
                            .name(space)
                            .build();

                    return cloudFoundryClient.spaces().list(request)
                            .flatMap(response -> Flux.fromIterable(response.getResources()))
                            .as(Flux::from)
                            .single()
                            .map(resource -> resource.getMetadata().getId());
                });
    }
    

    public static Mono<Tuple2<String, String>> push(CloudFoundryClient cloudFoundryClient, 
    												AppMetadataDTO appMetadata) throws Exception {
    	log.info("App push for: " + appMetadata);
    	
    	String appName = appMetadata.getName();
    	String routeName = appMetadata.getRoute();
    	String spaceId = appMetadata.getSpaceGuid();
    	String domainId = appMetadata.getDomainGuid();
    	
    	Mono<String> routeId = requestRouteId(cloudFoundryClient, domainId, spaceId, routeName);

        Mono<String> appId = requestCreateApplicationId(cloudFoundryClient, 
        													spaceId, 
                											appMetadata.getName(),
                											appMetadata.getDockerImage(),
                											appMetadata.getInstances(),
                											appMetadata.getMemory(),
                											appMetadata.getDisk(),
                											appMetadata.getStartCommand(),
                											appMetadata.getEnvironmentJsons(),
                											appMetadata.getDockerCred()
                											)
					.log("stream.invokedRequestCreateApp")
                    .then(applicationId2 -> checkAppSummary(cloudFoundryClient, applicationId2))
                    .log("stream.invokedRequestAppSummary")
                    .then(applicationId3 -> requestUpdateAppState(cloudFoundryClient, applicationId3, "STARTED"))
                    .log("stream.invokedUpdateAppState");

        return Mono
                .when(appId, routeId)
                .then(tuple -> {
                    String routeId2 = tuple.getT2();
                    String applicationId2 = tuple.getT1();
                    return 
	                    Mono.delay(Duration.ofSeconds(15))
	                    .then( l ->  checkAppStaging(cloudFoundryClient, applicationId2) )
	                    .then( s -> requestAssociateRoute(cloudFoundryClient, applicationId2, routeId2))
	                    .map(r -> tuple);
                })
                .otherwise(throwable ->  {
                	cleanUp(cloudFoundryClient, spaceId, appName, routeId);
                	return Mono.<Tuple2< String, String>>error(new Throwable("App Route association failed, " + throwable.getMessage()));
                });
    }

    public static Mono<Void> update(CloudFoundryClient cloudFoundryClient, 
			AppMetadataDTO appMetadata) {
    	log.info("App Update for: " + appMetadata);
    	if (appMetadata.getAppGuid() == null) {
    		return Mono.empty();
    	} else {
    		return requestUpdateApplication(cloudFoundryClient, 
								appMetadata.getSpaceGuid(), 
								appMetadata.getAppGuid(),
								appMetadata.getState(),
								appMetadata.getDockerImage(),
								appMetadata.getInstances(),
								appMetadata.getMemory(),
								appMetadata.getDisk(),
								appMetadata.getStartCommand(),
								appMetadata.getEnvironmentJsons(),
								appMetadata.getDockerCred()
							);
    	}
    }
    
    public static Mono<Void>  delete(CloudFoundryClient cloudFoundryClient, 
			AppMetadataDTO appMetadata) {
    	log.info("App Delete for: " + appMetadata);
    	if (appMetadata.getAppGuid() == null) 
    		return Mono.empty();
    		
    	String appName = appMetadata.getName();
    	String routeId = appMetadata.getRouteGuid();
    	Mono<String> route = (routeId != null) ? Mono.just(routeId) : Mono.just("");
		return cleanUp(cloudFoundryClient, appMetadata.getSpaceGuid(), appName, route);
    }

    /*
    private static Mono<Void> cleanUp(CloudFoundryClient cloudFoundryClient, Mono<String> spaceId, String application, Mono<String> routeId) {
    	
    	if (routeId != null) {
    		routeId
    		.then(routeId2 ->  deleteRoute(cloudFoundryClient, routeId2))
    		.log("stream.deleteRoute")
    		.otherwise(throwable -> {
    			log.error("Error with route deletion..." + throwable);
    			throwable.printStackTrace();
    			return Mono.empty();
    		})
    		.after();
    	}
    	
    	return spaceId
                .then(spaceId2 -> deleteApplications(cloudFoundryClient, spaceId2, application));
    }
    */
    
    private static Mono<Void> cleanUp(CloudFoundryClient cloudFoundryClient, String spaceId, String application, Mono<String> routeId) {
    	
    	return routeId
    		.then( routeId2 ->
    			
    				deleteRoute(cloudFoundryClient, routeId2)
    				.log("stream.preDeleteApp")
    				.and(deleteApplications(cloudFoundryClient, spaceId, application))
	        		.log("stream.postDeleteApps")
	    			.then()
    		)
    		.otherwise(throwable -> {
    			log.error("Error with app or route deletion..." + throwable);
    			throwable.printStackTrace();
    			return Mono.empty();
    		});
    }

    public static Mono<Void> deleteApplication(CloudFoundryClient cloudFoundryClient, String applicationId) {
    	if (applicationId == null || applicationId == "")
    		return Mono.empty();
    	
        DeleteApplicationRequest request = DeleteApplicationRequest.builder()
                .applicationId(applicationId)
                .build();

        return Mono.delay(Duration.ofSeconds(15))
                .then( l -> cloudFoundryClient.applicationsV2()
                			.delete(request)
                			.log("stream.postDeleteApp"))
                .then();
    }

    public static Mono<Void> deleteApplications(CloudFoundryClient cloudFoundryClient, String spaceId, String application) {
        return Flux
                .from(listApplicationIds(cloudFoundryClient, spaceId, application))
                .log("stream.postListAppIds")
                .flatMap(applicationId -> deleteApplication(cloudFoundryClient, applicationId))
                .log("stream.postDeleteApps")
                .then();
    }

    private static Publisher<String> listApplicationIds(CloudFoundryClient cloudFoundryClient, String spaceId, String applicationId) {
        ListApplicationsRequest request = ListApplicationsRequest.builder()
                .name(applicationId)
                .spaceId(spaceId)
                .build();

        return cloudFoundryClient.applicationsV2().list(request)
                .flatMap(response -> Flux.fromIterable(response.getResources()))
                .map(resource -> resource.getMetadata().getId());
    }

    private static Mono<String> requestAssociateRoute(CloudFoundryClient cloudFoundryClient, String applicationId, String routeId) {
        AssociateRouteApplicationRequest request = AssociateRouteApplicationRequest.builder()
                .applicationId(applicationId)
                .routeId(routeId)
                .build();
      
        int trials = 0;
        int RETRY_LIMIT = 4;
        Mono<String> status = null;
        
        while (trials++ < RETRY_LIMIT) {
        	try {
	        	status = cloudFoundryClient.routes().associateApplication(request)
	        		.log("stream.associateRoute")
	        		.map(response -> response.getMetadata().getId());
        	} catch(Exception e) {
        		log.error("Error on associateRoute:" + e.getMessage());
        		if (trials < RETRY_LIMIT) {
        			log.error("Attempting retry again");
        			try {
        				Thread.sleep(15000);
		        	}catch(Exception e1) {} 
		        } else {
        			throw e;
		        }
        	}
    	} 
        	
        return status;
    }
    
    private static boolean isDockerCredValid(Map<String, String> dockerCredsJson) {
    	return (dockerCredsJson != null
    			&& dockerCredsJson.containsKey("user")
    			&& dockerCredsJson.containsKey("password")
    			&& dockerCredsJson.containsKey("email"));
    }

    private static Mono<String> requestCreateApplicationId(CloudFoundryClient cloudFoundryClient, 
											        		String spaceId, 
											        		String application, 
											        		String dockerImage,  
											        		int instances, 
											        		int memoryQuota, 
											        		int diskQuota, 
											        		String startCommand, 
											        		Map<String, Object> envJson,
															Map<String, String> dockerCredsJson) {
    	CreateApplicationRequest.Builder builder = CreateApplicationRequest.builder()
                .dockerImage(dockerImage)
                .name(application)
                .spaceId(spaceId)
                .diego(true)
                .instances(instances)
                .memory(memoryQuota)
                .diskQuota(diskQuota)
                .environmentJsons(envJson)
                .healthCheckTimeout(180);
    	
    	if (isDockerCredValid(dockerCredsJson)) {
			builder.dockerCredentialsJsons(dockerCredsJson);
    	} else {
    		log.error("Docker credential map does not contain user, password or email fields, ignoring it");
    	}
    		
    	CreateApplicationRequest request = builder.build();
        return cloudFoundryClient.applicationsV2().create(request)
                .map(response -> response.getMetadata().getId())
                .log("stream.requestCreateApp");
        
    }
    
    private static Mono<String> requestUpdateAppState(CloudFoundryClient cloudFoundryClient, String applicationId, String state) {
        UpdateApplicationRequest request = UpdateApplicationRequest.builder()
						.applicationId(applicationId)
						.state(state)
						.build();
						
		return cloudFoundryClient.applicationsV2().update(request)
		.map(response -> response.getMetadata().getId())
		.log("stream.requestUpdateAppState");
    }
    
    private static Mono<Void> requestUpdateApplication(CloudFoundryClient cloudFoundryClient, 
											        		String spaceId, 
											        		String applicationId,
											        		String state,
											        		String dockerImage,
											        		int instances, 
											        		int memoryQuota, 
											        		int diskQuota, 
											        		String startCommand, 
											        		Map<String, Object> envJson,
											        		Map<String, String> dockerCredsJson) {

    	UpdateApplicationRequest.Builder builder = UpdateApplicationRequest.builder()
                .dockerImage(dockerImage)
                .instances(instances)
                .memory(memoryQuota)
                .diskQuota(diskQuota)
                .spaceId(spaceId)
                .applicationId(applicationId)
                .state(state)
                .command(startCommand)
                .environmentJsons(envJson);
    	
    	if (isDockerCredValid(dockerCredsJson)) {
			builder.dockerCredentialsJsons(dockerCredsJson);
	    } else {
			log.error("Docker credential map does not contain user, password or email fields, ignoring it");
		}

    	UpdateApplicationRequest request = builder.build(); 
        return cloudFoundryClient.applicationsV2().update(request)
                .map(response -> response.getMetadata().getId())
                .log("stream.requestCompleteUpdateApp")
                .then();
    }

    private static Mono<String> requestCreateRouteId(CloudFoundryClient cloudFoundryClient, String domainId, String spaceId, String host) {
        CreateRouteRequest request = CreateRouteRequest.builder()
                .domainId(domainId)
                .host(host)
                .spaceId(spaceId)
                .build();

        return cloudFoundryClient.routes().create(request)
                .map(response -> response.getMetadata().getId())
                .log("stream.requestCreateRoute");
    }
    
    public static Mono<Void> deleteRoute(CloudFoundryClient cloudFoundryClient, String routeId) {
    	if (routeId == null || routeId == "")
    		return Mono.empty();
    	
    	DeleteRouteRequest request = DeleteRouteRequest.builder()
                .routeId(routeId)
                .build();
    	
    	return cloudFoundryClient.routes().delete(request)
                .log("stream.DeleteRoute")
                .then();	
    }

    private static Mono<DomainResource> requestDomain(CloudFoundryClient cloudFoundryClient, String domain) {
        ListDomainsRequest request = ListDomainsRequest.builder()
                .name(domain)
                .build();
        
        return cloudFoundryClient.domains().list(request)
                .flatMap(response -> Flux.fromIterable(response.getResources()))
                .log("stream.requestDomain")
                .as(Flux::from)
                .singleOrEmpty();
        
    }


    private static Mono<String> requestExistingRouteId(CloudFoundryClient cloudFoundryClient, String domainId, String host) {
        ListRoutesRequest request = ListRoutesRequest.builder()
                .domainId(domainId)
                .host(host)
                .build();

        return cloudFoundryClient.routes().list(request)
                .flatMap(response -> Flux.fromIterable(response.getResources()))
                .as(Flux::from)
                .single()
                .map(resource -> resource.getMetadata().getId());
    }
    
    private static Mono<DomainResource> requestFirstDomain(CloudFoundryClient cloudFoundryClient) {
        ListDomainsRequest request = ListDomainsRequest.builder()
                .build();

        return cloudFoundryClient.domains().list(request)
        		.as(Flux::from)
    			.flatMap(resource -> Flux.fromIterable(resource.getResources()))
                .log("stream.requestFirstDomain")
                .next();
    }


    private static Mono<String> requestRouteId(CloudFoundryClient cloudFoundryClient, String domainId, String spaceId, String host) {
        return requestCreateRouteId(cloudFoundryClient, domainId, spaceId, host)
                .as(Flux::from)
                .switchOnError(requestExistingRouteId(cloudFoundryClient, domainId, host))
                .single();
    }
    
    private static Mono<String> checkAppSummary(CloudFoundryClient cloudFoundryClient, String applicationId) {
    	 
		SummaryApplicationRequest request = SummaryApplicationRequest.builder()
		        .applicationId(applicationId)
		        .build();
		
       return cloudFoundryClient.applicationsV2().summary(request)
    		   .log("stream.checkAppSummary")
    		   .map(response -> response.getId());
    }
    
    private static Mono<String> checkAppStaging(CloudFoundryClient cloudFoundryClient, String applicationId) {
   	 
		SummaryApplicationRequest request = SummaryApplicationRequest.builder()
		        .applicationId(applicationId)
		        .build();
		
		int trials = 0;
		int RETRY_LIMIT = 15;
		String STAGED_STATE = "STAGED";
		SummaryApplicationResponse endResponse = null;
		
		do {
			try {
				if (++trials != 1)
					Thread.sleep(5000);
			} catch(Exception e) { }
			endResponse = cloudFoundryClient.applicationsV2().summary(request)
    		   .log("stream.checkAppStaging")
    		   .map(response -> response)
    		   .block();
		} while (!endResponse.getPackageState().equals(STAGED_STATE) && trials < RETRY_LIMIT);
		
		log.info("App: " + applicationId + " in packaging state: " + endResponse.getPackageState() + ", after " + trials + " trials" );
		
        return Mono.just(endResponse.getId());
    }
    
    public static Mono<Void> requestDeleteServiceBinding(CloudFoundryClient cloudFoundryClient, String serviceBindingId) {
		return cloudFoundryClient.serviceBindingsV2()
					.delete(
							DeleteServiceBindingRequest.builder()
							.serviceBindingId(serviceBindingId)
							.build())
					.log("stream.requestDeleteServiceBinding")
					.then();
	}
    
    public static Mono<String> requestCreateServiceBinding(CloudFoundryClient cloudFoundryClient, String appId, Mono<String> serviceInstanceId) {
		return serviceInstanceId
				.then(
						serviceInstanceId1  -> cloudFoundryClient.serviceBindingsV2()
							.create(
									CreateServiceBindingRequest.builder()
									.applicationId(appId)
									.serviceInstanceId(serviceInstanceId1)
									.build())
							.map(resource -> resource.getMetadata().getId())
							.log("stream.requestCreateServiceBinding")
				);
	}
    
    public static Mono<String> requestGetServiceInstanceFromBinding(CloudFoundryClient cloudFoundryClient, String serviceBindingId) {
		return cloudFoundryClient.serviceBindingsV2()
					.get(
							GetServiceBindingRequest.builder()
							.serviceBindingId(serviceBindingId)
							.build())
					.map(resource -> resource.getEntity().getServiceInstanceId())
					.log("stream.requestGetServiceInstanceFromBinding");
	}
	
	private static Mono<ServiceInstanceEntity> requestCreateServiceInstanceEntity(CloudFoundryClient cloudFoundryClient, String serviceName, String spaceId, Mono<String> servicePlanId) {
		return servicePlanId
				.then( 
						servicePlanId1  ->
							 cloudFoundryClient.serviceInstances()						
								.create( CreateServiceInstanceRequest.builder()
										.name(serviceName)
										.servicePlanId(servicePlanId1)
										.spaceId(spaceId)
										.build())
								.map(resource -> resource.getEntity())
								.log("stream.requestCreateServiceInstanceEntity")
				);
	}
	
	public static Mono<Void> requestDeleteServiceInstance(CloudFoundryClient cloudFoundryClient, String serviceInstanceId) {
			return  
				cloudFoundryClient.serviceInstances()
					.delete( DeleteServiceInstanceRequest.builder()
						.serviceInstanceId(serviceInstanceId)
						.build())
					.log("stream.requestDeleteServiceInstance")
					.then();
	}
	
	public static Mono<String> requestServiceInstanceName(CloudFoundryClient cloudFoundryClient, String serviceInstanceId ) {
		return			   					
					 cloudFoundryClient.serviceInstances()
					 		.get( GetServiceInstanceRequest.builder()
								.serviceInstanceId(serviceInstanceId)
								.build())
							.map(resource -> resource.getEntity().getName())
							.log("stream.requestServiceInstance");
	}
	
	public static Mono<String> requestCreateServiceInstance(CloudFoundryClient cloudFoundryClient, String serviceName, String spaceId, Mono<String> servicePlanId) {
		return servicePlanId
				.then(servicePlanId1 ->				   					
					 cloudFoundryClient.serviceInstances()
							.create( CreateServiceInstanceRequest.builder()
								.name(serviceName)
								.servicePlanId(servicePlanId1)
								.spaceId(spaceId)
								.build())
							.map(resource -> resource.getMetadata().getId())
							.log("stream.requestCreateServiceInstance")
				);
	}
	
	public static Mono<String> requestServicePlanId(CloudFoundryClient cloudFoundryClient, Mono<String> serviceId) {
		return serviceId
				.then( serviceIdString ->					
						cloudFoundryClient.servicePlans()
							.list( ListServicePlansRequest.builder()
								.serviceId(serviceIdString)
								.build())
							.as(Flux::from)
							.log("stream.requestServicePlanId")
							.flatMap(resource -> Flux.fromIterable(resource.getResources()))
							.next()
							.map(resource -> resource.getMetadata().getId())
				);			
	}
	
	private static Mono<GetServiceResponse> requestService(CloudFoundryClient cloudFoundryClient, Mono<String> serviceId) {
		return serviceId
				.then( serviceIdString ->					
						cloudFoundryClient.services()
							.get( GetServiceRequest.builder()
								.serviceId(serviceIdString)
								.build())
				);			
	}
	
	public static Mono<String> requestServiceId(CloudFoundryClient cloudFoundryClient, String serviceLabel) {
		return cloudFoundryClient.services()
					.list( ListServicesRequest.builder()
						.label(serviceLabel)	
						.build())
					.log("stream.requestServiceIdList")	
					.as(Flux::from)
					.log("stream.requestServiceIdStream")	
					.flatMap(resource -> Flux.fromIterable(resource.getResources()))
					.next()
					.map(resource -> resource.getMetadata().getId());			
	}
    

}

