package org.boundless.cf.servicebroker.service.boundless;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.commons.collections.IteratorUtils;
import org.apache.log4j.Logger;
import org.boundless.cf.servicebroker.model.Catalog;
import org.boundless.cf.servicebroker.model.ServiceDefinition;
import org.boundless.cf.servicebroker.model.ServiceMetadata;
import org.boundless.cf.servicebroker.repository.DetachServiceMetadataRepository;
import org.boundless.cf.servicebroker.repository.ServiceDefinitionRepository;
import org.boundless.cf.servicebroker.service.CatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BoundlessCatalogService implements CatalogService {

	private static final Logger LOG = Logger.getLogger(BoundlessCatalogService.class);
	private static final String BROKER_IMAGE_LOGO = "public/images/boundless-logo.png";
	
	@Autowired
	ServiceDefinitionRepository serviceRepo;


	@Autowired
	DetachServiceMetadataRepository detachServiceMetadataRepo;
	
	@Override
	public Catalog getCatalog() {		
		Catalog catalog = new Catalog(IteratorUtils.toList(serviceRepo.findAll().iterator()));
		
		// The image_url for service metdata can become unavailable in air gapped env.
		// So, try to use bundled image and serve it encoded.
		// Modify the service metadata with actual encoded image for image_url 
		// Some db flavors have issues with persisting large blobs
		// So, detach the metadata so it does not get saved into db
		String encodedImageContent = getEncodedImage();
		catalog.getServiceDefinitions().stream().forEach( s -> { 
				ServiceMetadata metadata = s.getMetadata(); 
				detachServiceMetadataRepo.detachServiceMetadata(metadata);
				metadata.setImageUrl(encodedImageContent);
			} 
		);
		return catalog;
	}

	@Override
	public ServiceDefinition getServiceDefinition(String id) {
		if (id == null) {
			return null;
		}

		for (ServiceDefinition sd : getCatalog().getServiceDefinitions()) {
			if (sd.getId().equals(id)) {
				return sd;
			}
		}
		return null;
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