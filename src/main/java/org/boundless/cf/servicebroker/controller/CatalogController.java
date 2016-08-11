package org.boundless.cf.servicebroker.controller;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.boundless.cf.servicebroker.model.Catalog;
import org.boundless.cf.servicebroker.service.CatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Source from: https://github.com/cloudfoundry-community/spring-boot-cf-service-broker
 * @author sgreenberg@gopivotal.com
 * 
 */
@RestController
public class CatalogController extends BaseController  {

	public static final String BASE_PATH = "/v2/catalog";
	
	private static final Logger log = Logger.getLogger(CatalogController.class);

	private CatalogService service;
	
	@Autowired 
	public CatalogController(CatalogService service) {
		this.service = service;
	}
	
	@RequestMapping(value = BASE_PATH, method = RequestMethod.GET)
	public @ResponseBody Catalog getCatalog() {
		log.debug("GET: " + BASE_PATH + ", getCatalog()");
		return service.getCatalog();
	}
}
