package org.boundless.cf.servicebroker.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * The response sent to the cloud controller when a bind
 * request is successful.
 * 
 * @author sgreenberg@gopivotal.com
 * @author <A href="mailto:josh@joshlong.com">Josh Long</A>
 */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceInstanceBindingResponse {

	private ServiceInstanceBinding binding;
	
	public ServiceInstanceBindingResponse() {}
	
	public ServiceInstanceBindingResponse(ServiceInstanceBinding binding) {
		this.binding = binding;
	}

	@NotEmpty
	@JsonSerialize
	@JsonProperty("credentials")
	public Map<String, Object> getCredentials() {
		return convertToObjectMap(binding.getCredentials());
	}

	@JsonSerialize
	@JsonProperty("syslog_drain_url")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public String getSyslogDrainUrl() {
		return binding.getSyslogDrainUrl();
	}
	
	private static Map<String, Object> convertToObjectMap(Map<String, String> srcMap) {
		HashMap<String, Object> targetMap = new HashMap<String, Object>();
		for(String key: srcMap.keySet()) {
			String val = srcMap.get(key);
			Object nativeVal = val;
			if (val == null) {
				continue;
			}
			
			try {
				nativeVal = Double.valueOf(val);
				Double double1 = (Double)nativeVal;
				if (double1.doubleValue() == double1.intValue()) {
					nativeVal = new Integer(double1.intValue());
				}				
			} catch(NumberFormatException ipe) {
				String lowerVal = val.trim().toLowerCase();				
				if (lowerVal.equals("true") || lowerVal.equals("false")) {
					nativeVal = Boolean.valueOf(val);
				} 
			}
			targetMap.put(key, nativeVal);
		}
		return targetMap;
	}
}