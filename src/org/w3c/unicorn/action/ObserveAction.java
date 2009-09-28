// $Id: ObserveAction.java,v 1.44 2009-09-28 14:24:19 tgambet Exp $
// Author: Jean-Guilhem Rouel & Thomas GAMBET
// (c) COPYRIGHT MIT, ERCIM and Keio, 2006.
// Please first read the full copyright statement in file COPYRIGHT.html
package org.w3c.unicorn.action;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.servlet.ServletRequestContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.unicorn.UnicornCall;
import org.w3c.unicorn.exceptions.UnicornException;
import org.w3c.unicorn.input.DirectInputParameter;
import org.w3c.unicorn.input.URIInputParameter;
import org.w3c.unicorn.input.UploadInputParameter;
import org.w3c.unicorn.output.OutputFactory;
import org.w3c.unicorn.output.OutputModule;
import org.w3c.unicorn.util.Message;
import org.w3c.unicorn.util.Property;
import org.w3c.unicorn.Framework;

/**
 * ObserveAction
 * 
 * @author Jean-Guilhem ROUEL & Thomas GAMBET
 */
public class ObserveAction extends Action {
	
	private static final long serialVersionUID = -1375355420965607571L;
	
	private static Log logger = LogFactory.getLog(ObserveAction.class);
	
	private static DiskFileItemFactory factory;
	
	private static ServletFileUpload upload;
	
	private static ArrayList<String> outputParams;
	
	@Override
	public void init(final ServletConfig aServletConfig) throws ServletException {
		logger.trace("Init ObserverAction");
		factory = new DiskFileItemFactory();
		factory.setRepository(new File(Property.get("UPLOADED_FILES_REPOSITORY")));
		upload = new ServletFileUpload(factory);
		logger.debug("Created a ServletFileUpload with repository set to: " + Property.get("UPLOADED_FILES_REPOSITORY"));
		
		outputParams = new ArrayList<String>();
		outputParams.add("output");
		outputParams.add("format");
		outputParams.add("charset");
		outputParams.add("mimetype");
		outputParams.add("lang");
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		// Protects the action in case that Framework is not initialized
		if (!Framework.isUcnInitialized) {
			Framework.init();
			if (!Framework.isUcnInitialized) {
				resp.sendError(500, "Unicorn is not initialized properly. Check logs.");
				return;
			}
		}
		
		// Objects initialization
		FileItem aFileItemUploaded = null;
		Map<String, Object> mapOfStringObject = new LinkedHashMap<String, Object>();
		Map<String, String> mapOfSpecificParameter = new Hashtable<String, String>();
		Map<String, String> mapOfOutputParameter = new Hashtable<String, String>();
		ArrayList<Message> messages = new ArrayList<Message>();
		UnicornCall aUnicornCall = new UnicornCall();
		
		// Default output parameters
		mapOfOutputParameter.put("output", "simple");
		mapOfOutputParameter.put("format", "xhtml10");
		mapOfOutputParameter.put("charset", "UTF-8");
		mapOfOutputParameter.put("mimetype", "text/html");
		
		// Retrieve parameter prefixes from unicorn.properties
		String paramPrefix = Property.get("UNICORN_PARAMETER_PREFIX");
		String outParamPrefix = Property.get("UNICORN_PARAMETER_OUTPUT_PREFIX");
		
		// queryString is the requested queryString without the ucn_lang parameter (used for language links)
		String queryString = getQueryStringWithout(paramPrefix + "lang", req);
		mapOfStringObject.put("queryString", queryString);
		// messages is the ArrayList containing the messages to display
		mapOfStringObject.put("messages", messages);
		mapOfStringObject.put("unicorncall", aUnicornCall);
		mapOfStringObject.put("baseUri", "./");
		
		// Retrieve the parameters from the request
		Map<String, Object> reqParams;
		try {
			reqParams = getRequestParameters(req);
		} catch (FileUploadException e) {
			resp.setContentType(mapOfOutputParameter.get("mimetype") + "; charset=UTF-8");
			OutputModule aOutputModule = OutputFactory.createOutputModule(mapOfOutputParameter, mapOfSpecificParameter);
			messages.add(new Message(e));
			aOutputModule.produceError(mapOfStringObject, resp.getWriter());
			return;
		}
		
		// Process the parameters
		for (String key : reqParams.keySet()) {
			if (!key.startsWith(paramPrefix) && !key.startsWith(outParamPrefix)) {
				if (reqParams.get(key) instanceof String[])
					aUnicornCall.addParameter(key, (String[]) reqParams.get(key));
				else if ((reqParams.get(key) instanceof String))
					aUnicornCall.addParameter(key, (String) reqParams.get(key));
				continue;
			}
			
			if (key.startsWith(outParamPrefix)) {
				logger.trace("Specific parameter: " + key + " - " + (String) reqParams.get(key));
				String paramName = key.substring(outParamPrefix.length());
				mapOfSpecificParameter.put(paramName, (String) reqParams.get(key));
				continue;
			}
			
			if (key.startsWith(paramPrefix)) {
				String paramName = key.substring(paramPrefix.length());
				if (paramName.equals("lang")) {
					logger.trace("Lang parameter: " + key + " - " + (String) reqParams.get(key));
					String lang = getLanguage((String) reqParams.get(key), req, null);
					mapOfOutputParameter.put(paramName, lang);
					String aLocale = convertEnumerationToString(req.getLocales());		
					if (null == aLocale)
						aUnicornCall.setLang(lang + "," + Property.get("DEFAULT_LANGUAGE"));
					else
						aUnicornCall.setLang(lang + "," + aLocale);
					if (!lang.equals(reqParams.get(key))) {
						logger.trace("Lang parameter unsupported. Resolved to: " + lang);
						reqParams.put(key, lang);
					}
				} else if (paramName.equals("task")) {
					logger.trace("Task parameter: " + key + " - " + (String) reqParams.get(key));
					String task = getTask((String) reqParams.get(key), messages);
					mapOfStringObject.put("current_task", Framework.mapOfTask.get(task));
					if (!task.equals(reqParams.get(key))) {
						mapOfStringObject.put("default_task", Framework.mapOfTask.get(Framework.mapOfTask.getDefaultTaskId()));
						logger.trace("Task parameter unsupported. Resolved to: " + task);
						reqParams.put(key, task);
					}
					aUnicornCall.setTask(task);
				} else if (outputParams.contains(paramName)) {
					logger.trace("Output parameter: " + key + " - " + (String) reqParams.get(key));
					mapOfOutputParameter.put(paramName, (String) reqParams.get(key));
				} else if (paramName.equals("uri")) {
					logger.trace("Uri parameter: " + key + " - " + (String) reqParams.get(key));
					aUnicornCall.setInputParameter(new URIInputParameter((String) reqParams.get(key)));
				} else if (paramName.equals("text")) {
					logger.trace("Text parameter: " + key + " - " + (String) reqParams.get(key));
					aUnicornCall.setInputParameter(new DirectInputParameter((String) reqParams.get(key), (String) reqParams.get(paramPrefix + "text_mime")));
				} else if (paramName.equals("file")) {
					logger.trace("File parameter: " + key + " - " + reqParams.get(key).toString());
					Object object = reqParams.get(key);
					if (object instanceof FileItem) {
						aFileItemUploaded = (FileItem) object;
						aUnicornCall.setInputParameter(new UploadInputParameter(aFileItemUploaded));
					} else {
						// should be impossible (see getRequestParameters)
						logger.warn("ucn_file is not of type FileItem!");
					}
				} else if (paramName.equals("text_mime")) {
					aUnicornCall.addParameter(paramPrefix + "mime", (String) reqParams.get(key));
				} else {
					logger.warn("Unknown parameter: " + key + " - " + (String) reqParams.get(key) +". This parameter is added to aUnicornCall. Check that this is the wanted behavior.");
					aUnicornCall.addParameter(key, reqParams.get(key).toString());
				}
			}
		}
		
		// Check that all mandatory parameters are set
		if (!reqParams.containsKey(paramPrefix + "lang")) {
			String lang = getLanguage(null, req, null);
			reqParams.put(paramPrefix + "lang", getLanguage(null, req, null));
			logger.debug("No language parameter found. Language negociation resolved language to: " + lang);
			mapOfOutputParameter.put("lang", lang);
			String aLocale = convertEnumerationToString(req.getLocales());		
			if (null == aLocale)
				aUnicornCall.setLang(lang + "," + Property.get("DEFAULT_LANGUAGE"));
			else
				aUnicornCall.setLang(lang + "," + aLocale);
		}
		if (!reqParams.containsKey(paramPrefix + "task")) {
			String task = getTask(null, messages);
			reqParams.put(paramPrefix + "task", task);
			logger.debug("No task parameter found. Task parameter is set to task id: " + task);
			mapOfStringObject.put("default_task", Framework.mapOfTask.get(Framework.mapOfTask.getDefaultTaskId()));
			mapOfStringObject.put("current_task", Framework.mapOfTask.get(task));
			aUnicornCall.setTask(task);
		}
		
		OutputModule aOutputModule = OutputFactory.createOutputModule(mapOfOutputParameter, mapOfSpecificParameter);
		resp.setContentType(mapOfOutputParameter.get("mimetype") + "; charset=UTF-8");
		
		if (!reqParams.containsKey(paramPrefix + "uri") && !reqParams.containsKey(paramPrefix + "text") && !reqParams.containsKey(paramPrefix + "file")) {
			messages.add(new Message(Message.Level.ERROR, "$message_nothing_to_validate", null));
			aOutputModule.produceError( mapOfStringObject, resp.getWriter());
			return;
		}
		
		//req.setAttribute("unicorn_parameters", reqParams);
		
		for (Object objKey : reqParams.keySet()) {
			String key = (String) objKey;
			String ref;
			if (key.startsWith(Property.get("UNICORN_PARAMETER_OUTPUT_PREFIX")))
				continue;
			if (key.startsWith(paramPrefix))
				ref = "param_" + key.substring(paramPrefix.length());
			else
				ref = "param_" + key;
			if (reqParams.get(key) instanceof String[]) {
				String[] s = (String[]) reqParams.get(key);
				ArrayList<String> array = new ArrayList<String>();
				for (int i = 0; i < s.length; i++)
					array.add(s[i]);
				mapOfStringObject.put(ref, array);
			}
			else {
				mapOfStringObject.put(ref, reqParams.get(key));
			}
		}
		
		String s = "Resolved parameters:";
		for (String key : reqParams.keySet()) {
			s += "\n\t" + key + " - ";
			if (reqParams.get(key) instanceof String[]) {
				s += "[";
				for (int i = 0; i < ((String[]) reqParams.get(key)).length; i ++)
					s += ((String[]) reqParams.get(key))[i] + ", ";
				s = s.substring(0, s.length() - 2);
				s += "]";
			} else {
				s += reqParams.get(key);
			}
		}
		logger.debug(s);
		
		// Launch the observation
		try {
			aUnicornCall.check();
			aOutputModule.produceFirstOutput(mapOfStringObject, resp.getWriter());
			aUnicornCall.doTask();
			messages.addAll(aUnicornCall.getMessages());
			if (aUnicornCall.getResponses().size() == 0) {
				messages.add(new Message(Message.Level.ERROR, "$message_no_observation_done", null));
				aOutputModule.produceError(mapOfStringObject, resp.getWriter());
			} else {
				aOutputModule.produceOutput(mapOfStringObject, resp.getWriter());
			}
		} catch (final UnicornException ucnException) {
			if (ucnException.getUnicornMessage() != null)
				messages.add(ucnException.getUnicornMessage());
			else
				messages.add(new Message(Message.Level.ERROR, ucnException.getMessage(), null));
			aOutputModule.produceError(mapOfStringObject, resp.getWriter());
		} catch (final Exception aException) {
			logger.error("Exception : " + aException.getMessage(), aException);
			messages.add(new Message(aException));
			aOutputModule.produceError(mapOfStringObject, resp.getWriter());
		} finally {
			if ("true".equals(Property.get("DELETE_UPLOADED_FILES")) && aFileItemUploaded != null)
				aFileItemUploaded.delete();
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doGet(req, resp);
	}
	
	protected Map<String, Object> getRequestParameters(HttpServletRequest req) throws FileUploadException {
		
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		
		if (req.getMethod().equals("POST") && ServletFileUpload.isMultipartContent(new ServletRequestContext(req))) {
			List<?> listOfItem = upload.parseRequest(req);
			for (Object fileItem : listOfItem) {
				FileItem aFileItem = (FileItem) fileItem;
				String key = aFileItem.getFieldName();
				if (aFileItem.isFormField()) {
					if (params.containsKey(key)) {
						if (params.get(key) instanceof String) {
							String[] t = {(String) params.get(key), aFileItem.getString()};
							params.remove(key);
							params.put(key, (String[]) t);
						} else if (params.get(key) instanceof String[]) {
							int size = ((String[]) params.get(key)).length;
							String[] aOld = (String[]) params.get(key);
							String[] aNew = new String[size+1];
							for (int i = 0; i < size; i++)
								aNew[i] = aOld[i];
							aNew[size] = aFileItem.getString();
							params.put(key, aNew);
						}
					} else {
						params.put(key, aFileItem.getString());
					}
				} else if (key.equals(Property.get("UNICORN_PARAMETER_PREFIX") + "file")) {
					params.put(key, aFileItem);
				} else {
					logger.warn("Unknown FileItem in request: " + key);
				}
			}
		} else {
			Enumeration<?> paramEnum = req.getParameterNames();
			while (paramEnum.hasMoreElements()) {
				String key = (String) paramEnum.nextElement();
				if (req.getParameterValues(key).length > 1)
					params.put(key, req.getParameterValues(key));
				else
					params.put(key, req.getParameter(key));
			}
		}
		
		String s = "Parameters: ";
		for (String key : params.keySet()) {
			s += "\n\t" + key + " - ";
			if (params.get(key) instanceof String[]) {
				s += "[";
				for (int i = 0; i < ((String[]) params.get(key)).length; i ++) {
					s += ((String[]) params.get(key))[i] + ", ";
				}
				s = s.substring(0, s.length() - 2);
				s += "]";
			}
			else {
				s += params.get(key);
			}
		}
		logger.debug(s);
		
		return params;
	}

	/**
	 * Converts an Enumeration object to a string, the terms being separated by
	 * a coma.
	 * 
	 * @param myEnum
	 *            The enumeration to convert.
	 * @return The converted string.
	 */
	private String convertEnumerationToString(Enumeration<?> myEnum) {
		String ret = "";
		while (myEnum.hasMoreElements()) {
			ret += myEnum.nextElement().toString() + ",";
		}
		return ret.substring(0, ret.length() - 1);
	}
	
}