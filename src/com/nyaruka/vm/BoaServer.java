package com.nyaruka.vm;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.asfun.jangod.template.TemplateEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import com.nyaruka.db.Collection;
import com.nyaruka.db.Cursor;
import com.nyaruka.db.DB;
import com.nyaruka.db.Record;
import com.nyaruka.http.BoaHttpServer;
import com.nyaruka.http.NanoHTTPD;
import com.nyaruka.http.NanoHTTPD.Response;
import com.nyaruka.json.JSON;
import com.nyaruka.util.FileUtil;

public abstract class BoaServer {

	public BoaServer(int port, DB db) {
		try {
			new BoaHttpServer(port, this);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
		
		m_vm = new VM(db);
	}
	
	/** Define out to get the contents of a given path */
	public abstract InputStream getInputStream(String path);

	/** Direct template engines how to find files on the given platform */
	public abstract void configureTemplateEngines(TemplateEngine systemTemplates, TemplateEngine appTemplates);

	/** Read the apps from whatever storage mechanism is approrpriate for the server */
	public abstract List<BoaApp> getApps();
	
	public void start() {

		List<JSEval> evals = new ArrayList<JSEval>();
		evals.add(new JSEval(readFile("static/js/json2.js"), "json2.js"));
		evals.add(new JSEval(readFile("sys/js/jsInit.js"), "jsInit.js"));

		m_vm.reset();
		loadApps();
		m_vm.reload(evals);
		
		configureTemplateEngines(m_templates, m_appTemplates);		
		m_requestInit = new JSEval(readFile("sys/js/requestInit.js"), "requestInit.js");

	}
	
	public void stop() {
		m_vm.stop();
	}
	
	public void log(String message) {
		m_vm.getLog().append(message).append("\n");
	}

	public Response handleAppRequest(String url, String method, Properties params) {		
		try {
			HttpRequest request = new HttpRequest(url, method, params);
			HttpResponse response = m_vm.handleHttpRequest(request, m_requestInit);
	
			if (response != null) {
					
				String templateFile = response.getTemplate();
				if (templateFile == null){
					templateFile = url + ".html";
				}
				
				if (!templateFile.startsWith("/")) {
					templateFile = "/" + response.getApp().getNamespace() + "/" + templateFile;
				}
				
				Map<String,Object> data = response.getData().toMap();				
				String html = m_appTemplates.process(templateFile, data);
				
				if (html != null){
					return new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_HTML, html);				
				} else {
					return new Response(NanoHTTPD.HTTP_NOTFOUND, NanoHTTPD.MIME_HTML, "File not found: " + url);
				}			
			}
			return null;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Response renderLog() {
		HashMap<String,Object> data = new HashMap<String,Object>();
		data.put("log", m_vm.getLog().toString());
		return renderToResponse("log.html", data);
	}
	
	public String renderAdmin() {
		return renderTemplate("admin.html", new HashMap<String,Object>());
	}
	
	public Response renderEditor(File file, String filename) {
		String fileContents = FileUtil.slurpFile(file);
		Map<String,Object> data = new HashMap<String,Object>();
		data.put("contents", fileContents);		
		data.put("filename", filename);
		return renderToResponse("editor.html", data);
	}

	public Response renderDB(String url, String method, Properties params){
		Pattern COLL = Pattern.compile("^/db/([a-zA-Z]+)/$");
		Pattern RECORD = Pattern.compile("^/db/([a-zA-Z]+)/(\\d+)/$");
		Pattern DELETE_RECORD = Pattern.compile("^/db/([a-zA-Z]+)/(\\d+)/delete/$");		
		Pattern DELETE_COLLECTION = Pattern.compile("^/db/([a-zA-Z]+)/delete/$");				
		
		Matcher matcher = null;
		
		if (url.equals("/db") || url.equals("/db/")){
			if (method.equalsIgnoreCase("POST")){
				m_vm.getDB().ensureCollection(params.getProperty("name"));
			}
			
			return renderToResponse("db/index.html", getAdminContext()); 
		} 
		
		matcher = DELETE_COLLECTION.matcher(url);
		if (matcher.find()){
			String collName = matcher.group(1);
			Collection coll = m_vm.getDB().getCollection(collName);
			m_vm.getDB().deleteCollection(coll);
			return redirect("/db/");
		}
		
		matcher = COLL.matcher(url);
		if (matcher.find()){
			String collName = matcher.group(1);
			Collection coll = m_vm.getDB().getCollection(collName);
			
			// they are adding a new record
			if (method.equalsIgnoreCase("POST")){
				JSON json = new JSON(params.getProperty("json"));
				coll.save(json);
			}
			
			// our set of keys
			HashSet<String> keys = new HashSet<String>();
			
			// build our list of matches
			ArrayList records = new ArrayList();
			Cursor cursor = coll.find("{}");
			while (cursor.hasNext()){
				Record record = cursor.next();
				JSON data = record.getData();
				
				// add all our unique keys
				Iterator item_keys = data.keys();
				while(item_keys.hasNext()){
					String key = item_keys.next().toString();
					
					// get the value
					Object value = data.get(key);
					
					// skip this key if the value is complex
					if ((value instanceof JSON) || (value instanceof JSONObject) || (value instanceof JSONArray)){
						// pass
					} else {
						keys.add(key);
					}
				}
				
				records.add(record.toJSON().toMap());
			}
			
			HashMap<String, Object> context = new HashMap<String, Object>();
			context.put("keys", keys);
			context.put("collections", m_vm.getDB().getCollections());			
			context.put("collection", coll);
			context.put("records", records);
			return renderToResponse("db/list.html", context);
		}
		
		matcher = DELETE_RECORD.matcher(url);
		if (matcher.find() && method.equalsIgnoreCase("POST")){
			String collName = matcher.group(1);
			Collection coll = m_vm.getDB().getCollection(collName);
			long id = Long.parseLong(matcher.group(2));
			Record rec = coll.getRecord(id);			
			coll.delete(id);
			return redirect("/db/" + coll.getName() + "/");
		}
		
		matcher = RECORD.matcher(url);
		if (matcher.find()){
			String collName = matcher.group(1);
			Collection coll = m_vm.getDB().getCollection(collName);
			long id = Long.parseLong(matcher.group(2));
			Record rec = coll.getRecord(id);

			// they are posting new data
			if (method.equalsIgnoreCase("POST")){
				JSON json = new JSON(params.getProperty("json"));
				json.put("id", rec.getId());
				rec = coll.save(json);
			} 

			JSON data = rec.getData();
				
				// add all our unique keys
			ArrayList<String> fields = new ArrayList<String>();
			Iterator item_keys = data.keys();
			while(item_keys.hasNext()){
				fields.add(item_keys.next().toString());
			}
			
			HashMap<String, Object> context = new HashMap<String, Object>();
			context.put("collection", coll);
			context.put("record", rec);
			context.put("values", rec.toJSON().toMap());
			context.put("fields", fields);
			context.put("json", rec.getData().toString());
			context.put("collections", m_vm.getDB().getCollections());
			
			return renderToResponse("db/read.html", context);
		} 
		
		throw new RuntimeException("Unknown URL: " + url);
	}
	
	public void loadApps() {
		List<BoaApp> apps = getApps();
		log("Found " + apps.size() + " apps to load");
		for (BoaApp app : apps) {
			m_vm.addApp(app);
			log("Added " + app.getNamespace() + " app");
		}
	}
	
	/**
	 * Render an exception nicely in our error template
	 */
	public Response renderError(Throwable error) {		
		try {
			CharArrayWriter stack = new CharArrayWriter();
			error.printStackTrace(new PrintWriter(stack));
			HashMap<String, Object> context = new HashMap<String,Object>();
			context.put("error", stack.toString());
			return renderToResponse("error.html", context);
		} catch (Throwable t) {
			t.printStackTrace();
			throw new RuntimeException(t);
		}		
	}
	
	/**
	 * Read the contents of the file at the given path
	 */
	public String readFile(String path) {
		InputStream is = getInputStream(path);
		return FileUtil.slurpStream(is);
	}

	/**
	 * The base context for the admin view
	 */
	private HashMap<String,Object> getAdminContext() {
		HashMap<String, Object> context = new HashMap<String, Object>();
		context.put("collections", m_vm.getDB().getCollections());
		return context;
	}
	
	/**
	 * Render a template file with its context
	 */
	public String renderTemplate(String templateFile, Map<String,Object> context) {
		try {
			return m_templates.process(templateFile, context);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Render a template file with it's context to get an http response.
	 * This will return a status 200 with text/html
	 */
	public Response renderToResponse(String template, Map<String,Object> context) {
		String html = renderTemplate(template, context);
		return new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_HTML, html);
	}

	/**
	 * Return a redirection response at the give location
	 */
	public Response redirect(String location) {
		Response resp = new Response(NanoHTTPD.HTTP_REDIRECT, NanoHTTPD.MIME_PLAINTEXT, location);
		resp.header.put("Location", location);
		return resp;
	}

	
	private JSEval m_requestInit;
	
	private TemplateEngine m_templates = new TemplateEngine();
	private TemplateEngine m_appTemplates = new TemplateEngine();
	
	/** this vm is where all the magic happens */
	private VM m_vm;

}
