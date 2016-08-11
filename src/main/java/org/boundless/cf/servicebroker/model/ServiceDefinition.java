package org.boundless.cf.servicebroker.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Entity
@Table(name = "services")
@JsonInclude(Include.NON_NULL)
@JsonSerialize(include=JsonSerialize.Inclusion.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceDefinition {
	
	private static final Logger log = Logger.getLogger(ServiceDefinition.class);
	
	@Id
	@Column(nullable = false)
	private String id;
	
	@Column(nullable = false)
	private String name;
	
	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private boolean bindable = true;

	// Dont miss the mappedBy tag - persistence of the owned relationship will falter...
	@OneToMany(mappedBy="service", orphanRemoval = true, fetch=FetchType.LAZY, cascade=CascadeType.ALL)
	private Set<Plan> plans = new HashSet<Plan>();

	// Marking the metadata as eager loading to avoid null session when we try to modify it with detach call in catalog
	@JsonProperty("metadata")
	@OneToOne(optional = true, orphanRemoval = true, fetch=FetchType.EAGER, cascade=CascadeType.ALL)
	private ServiceMetadata metadata;

	// Mark tags and label as transient and let it get lazily written by Catalog service 
	@Transient 
	@JsonSerialize
	@JsonProperty("tags")
	private List<String> tags = Arrays.asList( new String[] { "boundless-suite", "opengeo", "boundless"});
	
	@Transient 
	@JsonSerialize
	@JsonProperty("label")
	private String label = "boundless";
	
	@Transient 
	@JsonSerialize
	@JsonProperty("provider")
	private String provider = "Boundless Suite";

	public String generateId() {	
		return UUID.randomUUID().toString();
	}
	
	public synchronized void generateAndSetId() {
		if (this.id == null) {
			this.id = generateId();
		}
	}
	
	public synchronized void setId(String pk) {
		if ((this.id == null) && (pk != null))
			this.id = pk; 
		else
			generateAndSetId();
	}
	
	public synchronized String getId() {
		if (id == null)
			generateAndSetId();
		return id;
	}

	public ServiceMetadata getMetadata() {
		return metadata;
	}

	public void setMetadata(ServiceMetadata serviceMetadata) {
		this.metadata = serviceMetadata;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String descrp) {
		this.description = descrp;
	}

	public boolean isBindable() {
		return bindable;
	}

	public void setBindable(boolean bindable) {
		this.bindable = bindable;
	}

	public Set<Plan> getPlans() {
		return plans;
	}
	
	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public Plan findPlan(String planId) {
		if (planId == null)
			return null;
		
		for(Plan plan: plans) {
			if (plan.getId().equals(planId))
				return plan;
		}
		return null;
	}

	public synchronized void setPlans(Set<Plan> plans) {
		log.info("Set plans called on ServiceDefinition with: " + plans.size());
		this.plans = plans;
		for(Plan plan: plans) {
			plan.generateAndSetId();
			plan.setService(this);
		}
	}

	public synchronized void addPlan(Plan plan) {	
		log.info("Add plans called on ServiceDefinition with: " + plan);
		if (!this.plans.contains(plan)) {
			this.plans.add(plan);
			plan.setService(this);
		}
	}

	public synchronized void removePlan(Plan plan) {
		if (this.plans.contains(plan)) {
			this.plans.remove(plan);
			plan.setService(null);
		}
	}

	@Override
	public String toString() {
		return "Service [name=" + name + ", uuid=" +  id + ", description=" + description
				+ ", plans=" + plans + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ServiceDefinition other = (ServiceDefinition) obj;
		
		/*
		 * //Name can be changed but considered same as long as id matches
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		*/
		
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	public void update(ServiceDefinition from) {
		if (from == null)
			return;
		
		if (from.name != null) {
			this.name = from.name;
		}
		
		if (from.description != null) {
			this.description = from.description;
		}
		
		if (from.bindable != this.bindable ) {
			this.bindable = from.bindable;
		}
		
		if (from.metadata != null)
			this.metadata.update(from.metadata);
		
		if (from.plans != null) {
			for(Plan fromPlan: from.plans) {
				Plan existingPlan = this.findPlan(fromPlan.getId());
				if (fromPlan != null) {
					existingPlan.update(fromPlan);
				}
			}
		}
	}
	
	

}
