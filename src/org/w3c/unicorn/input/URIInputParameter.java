package org.w3c.unicorn.input;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.net.ssl.SSLException;

import org.w3c.unicorn.contract.EnumInputMethod;
import org.w3c.unicorn.exceptions.UnicornException;
import org.w3c.unicorn.util.Message;
import org.w3c.unicorn.util.Property;

public class URIInputParameter extends InputParameter {
	
	private String uri;
	
	private int connectTimeOut;
	
	public URIInputParameter(String uri) {
		this.uri = uri;
		if (Property.get("DOCUMENT_CONNECT_TIMEOUT") != null)
			connectTimeOut = Integer.parseInt(Property.get("DOCUMENT_CONNECT_TIMEOUT"));
		else 
			connectTimeOut = 0;
	}
	
	@Override
	public void check() throws UnicornException {
		URL docUrl = null;
		try {
			if (uri == null || uri.equals(""))
				throw new UnicornException(Message.ERROR, "$message_empty_uri");
			
			if (uri.equals("referer"))
				throw new UnicornException();
			
			Pattern urlPattern = Pattern.compile("^(https?)://([A-Z0-9][A-Z0-9_-]*)(\\.[A-Z0-9][A-Z0-9_-]*)*(:(\\d+))?([/#]\\p{ASCII}*)?", Pattern.CASE_INSENSITIVE);
			if (!urlPattern.matcher(uri).matches()) {
				if (!uri.contains("://")) 
					uri = "http://" + uri;
				if (!urlPattern.matcher(uri).matches())
					throw new UnicornException(Message.ERROR, "$message_invalid_url_syntax", null, uri);
			}
			docUrl = new URL(uri);
			if (!docUrl.getProtocol().equals("http") && !docUrl.getProtocol().equals("https"))
				throw new UnicornException(Message.ERROR, "$message_unsupported_protocol", null, docUrl.getProtocol());
			HttpURLConnection con = (HttpURLConnection) docUrl.openConnection();
			con.setConnectTimeout(connectTimeOut);
			con.connect();
			int responseCode = con.getResponseCode();
			switch (responseCode) {
			case HttpURLConnection.HTTP_UNAUTHORIZED:
				throw new UnicornException(Message.ERROR, "$message_unauthorized_access");
			case HttpURLConnection.HTTP_NOT_FOUND:
				throw new UnicornException(Message.ERROR, "$message_document_not_found");
			}
			String sMimeType = con.getContentType();
			sMimeType = sMimeType.split(";")[0];
			mimeType = new MimeType(sMimeType);
			inputModule = new URIInputModule(mimeType, uri);
		} catch (MalformedURLException e) {
			throw new UnicornException(Message.ERROR, "$message_invalid_url_syntax", null, uri);
		} catch (MimeTypeParseException e) {
			throw new UnicornException(Message.ERROR, "$message_invalid_mime_type");
		} catch (UnknownHostException e) { 
			throw new UnicornException(Message.ERROR, "$message_unknown_host" , null, docUrl.getHost());
		} catch (SSLException e) {
			throw new UnicornException(Message.ERROR, "$message_ssl_exception");
		} catch (ConnectException e) {
			throw new UnicornException(Message.ERROR, "$message_connect_exception");
		} catch (SocketTimeoutException e) {
			if (e.getMessage().contains("connect timed out")) {
				throw new UnicornException(Message.ERROR, "$message_connect_exception");
			} else {
				throw new UnicornException(new Message(e));
			}
		} catch (IOException e) {
			throw new UnicornException(new Message(e));
		}
	}

	@Override
	public String getDocumentName() {
		return uri;
	}

	@Override
	public EnumInputMethod getInputMethod() {
		return EnumInputMethod.URI;
	}

}
