package org.w3c.unicorn.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.w3c.unicorn.Framework;

public class Language {
	
	public static boolean isISOLanguageCode(String languageCode) {
		String[] isoCodes = Locale.getISOLanguages();
		for (String code : isoCodes) {
			if (code.equals(languageCode))
				return true;
		}
		return false;
	}
	
	public static Locale getLocale(String languageCode) {
		Locale[] locales= Locale.getAvailableLocales();
		for (Locale locale : locales) {
			if (locale.getLanguage().equals(languageCode))
				return locale;
		}
		if (isISOLanguageCode(languageCode))
			Framework.logger.warn("Missing locale: " + languageCode + ". This locale should be installed on the system in order to translate Unicorn in this language.");
		return null;
	}
	
	public static String negociate(Enumeration<?> locales) {
		while (locales.hasMoreElements()) {
			Locale loc = (Locale) locales.nextElement();
			if (Framework.getLanguageProperties().containsKey(loc.getLanguage())) {
				return loc.getLanguage();
			}
		}
		return Property.get("DEFAULT_LANGUAGE");
	}
	
	public static VelocityContext getContext(String langParameter) {
		if (langParameter != null && Framework.getLanguageContexts().containsKey(langParameter))
			return Framework.getLanguageContexts().get(langParameter);
		else
			return Framework.getLanguageContexts().get(Property.get("DEFAULT_LANGUAGE"));
	}

	public static void complete(Properties props, Properties defaultProps) {
		props.put("complete", "true");
		for (Object key : defaultProps.keySet()) {
			if (!props.containsKey(key)) {
				props.put("complete", "false");
				//props.put(key, "<span dir=\"" + defaultProps.get("direction") + "\" xml:lang=\"" + defaultProps.get("lang") + "\">" + defaultProps.get(key) + "</span>");
				props.put(key, defaultProps.get(key));
				Framework.logger.warn(">> Missing property in " + props.getProperty("lang") + ".properties for key: \"" + (String) key + "\". Added default property for this key: \"" + defaultProps.get(key) + "\""); 
			}
		}
	}
	
	public static UCNProperties load(File langFile) throws IllegalArgumentException, FileNotFoundException, IOException {

		String localeString = langFile.getName().split("\\.")[0];
		if (!Language.isISOLanguageCode(localeString))
			throw new IllegalArgumentException("Invalid language file: " + langFile + ". " + localeString + " is not a valid ISO language code. This file will not be loaded.");

		Locale locale = Language.getLocale(localeString);
		
		FileInputStream fis = new FileInputStream(langFile);
		InputStreamReader isr;
		try {
			isr = new InputStreamReader(fis, "UTF-8");
			UCNProperties props = new UCNProperties();
			props.load(isr);
			props.put("lang", localeString);
			props.put("language", StringUtils.capitalize(locale.getDisplayLanguage(locale)));
			return props;
		} catch (UnsupportedEncodingException e) {
			// This should not happen
			Framework.logger.error(e.getMessage(), e);
			return null;
		}
	}

	public static boolean isComplete(String langParameter) {
		Properties testedProps = Framework.getLanguageProperties().get(langParameter);
		
		if (testedProps.get("complete") == null) {
			for (Object key : Framework.getLanguageProperties().get(Property.get("DEFAULT_LANGUAGE")).keySet()) {
				if (!testedProps.containsKey(key)) {
					testedProps.put("complete", "false");
					return false;
				}
			}
			return true;
		} else
			return testedProps.get("complete").equals("true");
	}
	
	public static String evaluate(String lang, String messageKey, String... args) {
		if (Framework.getLanguageProperties().get(lang) == null)
			return messageKey;
		
		if (messageKey.startsWith("$"))
			messageKey = messageKey.replace("$", "");
		
		String message = Framework.getLanguageProperties().get(lang).getProperty(messageKey);
		
		if (message == null)
			return messageKey;
		
		if (!message.contains("%"))
			return message;
		
		String result = message;
		int i = 1;
		if (args == null)
			return result;
		for (String str : args) {
			if (str.startsWith("$"))
				str = str.replace("$", "");
			
			String string = Framework.getLanguageProperties().get(lang).getProperty(str) != null ? Framework.getLanguageProperties().get(lang).getProperty(str) : str; 
			result = result.replaceAll("%"+i, string);
			i++;
		}
		return result;
	}

	public static TreeMap<String, String> getAvailablesLocales() {
		Locale[] locales= Locale.getAvailableLocales();
		TreeMap<String, String> result = new TreeMap<String, String>();
		for (Locale loc : locales) {
			if (!loc.getLanguage().equals(Property.get("DEFAULT_LANGUAGE")))
				result.put(loc.getLanguage(), StringUtils.capitalize(loc.getDisplayLanguage(loc)));
		}
		return result;
	}
	
}
