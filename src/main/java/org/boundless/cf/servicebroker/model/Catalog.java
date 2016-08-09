package org.boundless.cf.servicebroker.model;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * The catalog of services offered by this broker.
 * 
 * @author sgreenberg@gopivotal.com
 */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Catalog {

	@NotEmpty
	@JsonSerialize
	@JsonProperty("services")
	private List<ServiceDefinition> serviceDefinitions = new ArrayList<ServiceDefinition>();

	private static final String BROKER_IMAGE_LOGO = "public/images/boundless-logo.png";
	
	public Catalog() {
	}

	public Catalog(List<ServiceDefinition> serviceDefinitions) {
		this.setServiceDefinitions(serviceDefinitions); 
	}
	
	public List<ServiceDefinition> getServiceDefinitions() {
		return serviceDefinitions;
	}

	private void setServiceDefinitions(List<ServiceDefinition> serviceDefinitions) {
		if ( serviceDefinitions == null ) {
			// ensure serialization as an empty array, not null
			this.serviceDefinitions = new ArrayList<ServiceDefinition>();
		} else {
			this.serviceDefinitions = serviceDefinitions;
		}
		
		String encodedImageContent = getEncodedImage();
		this.serviceDefinitions.stream().forEach( s -> s.getMetadata().setImageUrl(encodedImageContent));
	}
	
	private String getEncodedImage() {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	    int nRead;
	    byte[] data = new byte[4096];
        BufferedInputStream bis = new BufferedInputStream(getClass().getClassLoader().getResourceAsStream(BROKER_IMAGE_LOGO));
        
        try {
			while ((nRead = bis.read(data, 0, data.length)) != -1) {
			  buffer.write(data, 0, nRead);
			  
			}
			buffer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
        byte[] imageBytes = buffer.toByteArray();
        return  "data:image/png;base64," + encodeImage(imageBytes);
	}
	
    public static String encodeImage(byte[] imageByteArray) {
        return Base64.getEncoder().encodeToString(imageByteArray);
    }
 
    public static byte[] decodeImage(String imageDataString) {
        return Base64.getDecoder().decode(imageDataString);
    }
	
}