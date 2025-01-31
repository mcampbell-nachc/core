package com.nach.core.util.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;

public class HttpRequestClient {

	//
	// instance variables
	//

	private HttpClient client;

	private String baseUrl;

	private String url;

	private HashMap<String, String> headers = new HashMap<String, String>();

	private HashMap<String, String> formData = new HashMap<String, String>();

	private int statusCode;

	private InputStream responseInputStream;

	private BasicHttpContext context;

	//
	// constructor and init
	//

	public HttpRequestClient(String url) {
		this.baseUrl = url;
		this.url = baseUrl;
		this.client = createClient();
	}

	private HttpClient createClient() {
		return new HttpClientFactory().createClient();
	}

	//
	// trivial getters and setters
	//

	public HttpClient getClient() {
		return client;
	}

	public void setClient(HttpClient client) {
		this.client = client;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public HashMap<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(HashMap<String, String> headers) {
		this.headers = headers;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	//
	// headers and form data
	//

	public void addHeader(String name, String value) {
		this.headers.put(name, value);
	}

	public void addHeaders(Properties props) {
		Enumeration<Object> keys = props.keys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement() + "";
			String val = props.getProperty(key);
			this.addHeader(key, val);
		}
	}

	public void setContentTypeHeaderToJson() {
		this.addHeader("Content-Type", "application/json");
	}

	public void addFormData(String name, String value) {
		this.formData.put(name, value);
	}

	public void setOauthToken(String token) {
		this.addHeader("Authorization", "Bearer " + token);
	}

	public void addNtlmAuth(String uid, String pwd, String domain) {
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(AuthScope.ANY, new NTCredentials(uid, pwd, "", domain));
		List<String> authtypes = new ArrayList<String>();
		authtypes.add(AuthPolicy.NTLM);
		this.context = new BasicHttpContext();
		this.context.setAttribute(ClientContext.CREDS_PROVIDER, credsProvider);
		this.client = new DefaultHttpClient();
		this.client.getParams().setParameter(AuthPNames.TARGET_AUTH_PREF, authtypes);
	}
	
	public void addBasicAuthentication(String uid, String pwd) {
		String enc = Base64.getEncoder().encodeToString((uid + ":" + pwd).getBytes());
		this.addHeader("Authorization", "Basic " + enc);
	}
	
	//
	// get
	//

	public void doGet() {
		try {
			HttpGet request = new HttpGet(this.url);
			this.addHeaders(request);
			HttpResponse response = client.execute(request, this.context);
			this.statusCode = response.getStatusLine().getStatusCode();
			this.responseInputStream = response.getEntity().getContent();
		} catch (Exception exp) {
			throw new RuntimeException(exp);
		}
	}

	//
	// post
	//

	public void doPost(String postBody) {
		try {
			HttpPost request = new HttpPost(this.url);
			this.addHeaders(request);
			HttpEntity entity = new StringEntity(postBody);
			request.setEntity(entity);
			HttpResponse response = this.client.execute(request, this.context);
			this.responseInputStream = response.getEntity().getContent();
			this.statusCode = response.getStatusLine().getStatusCode();
		} catch (Exception exp) {
			throw new RuntimeException(exp);
		}
	}

	//
	// post
	//

	public void doPostForm() {
		try {
			HttpPost httpPost = new HttpPost(this.url);
			this.addHeaders(httpPost);
			addFormParams(httpPost);
			HttpResponse response = this.client.execute(httpPost);
			this.responseInputStream = response.getEntity().getContent();
		} catch (Exception exp) {
			throw new RuntimeException(exp);
		}
	}

	//
	// post file
	//
	
	// TODO: postFile methods need to be parameterized.  

	public void postFile(File file, String path) {
		postFile(file, path, false);
	}

	public void postFile(File file, String path, boolean replace) {
		try {
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(this.url);
			httpPost.getParams().setParameter("path", path);
			this.addFormParams(httpPost);
			FileBody uploadFilePart = new FileBody(file);
			MultipartEntity reqEntity = new MultipartEntity();
			reqEntity.addPart("path", new StringBody(path));
			if(replace == true) {
				reqEntity.addPart("overwrite", new StringBody("true"));
			}
			this.addHeaders(httpPost);
			reqEntity.addPart("upload-file", uploadFilePart);
			httpPost.setEntity(reqEntity);
			HttpResponse response = httpClient.execute(httpPost);
			this.responseInputStream = response.getEntity().getContent();
			this.setStatusCode(response.getStatusLine().getStatusCode());
		} catch (Exception exp) {
			throw new RuntimeException(exp);
		}
	}

	public void postFile(String fileName, InputStream in, String path, boolean replace) {
		try {
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(this.url);
			httpPost.getParams().setParameter("path", path);
			this.addFormParams(httpPost);
			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			byte[] bytes = IOUtils.toByteArray(in);
			builder.addBinaryBody("upload-file", bytes, ContentType.APPLICATION_OCTET_STREAM, fileName);
			builder.addPart("path", new StringBody(path));

			if(replace == true) {
				builder.addPart("overwrite", new StringBody("true"));
			}

			HttpEntity reqEntity = builder.build();
			
			this.addHeaders(httpPost);
			httpPost.setEntity(reqEntity);
			HttpResponse response = httpClient.execute(httpPost);
			this.responseInputStream = response.getEntity().getContent();
			this.setStatusCode(response.getStatusLine().getStatusCode());

		} catch (Exception exp) {
			throw new RuntimeException(exp);
		}
	}

	//
	// get response
	//

	public String getResponse() {
		return getResponse(this.responseInputStream);
	}

	public static String getResponse(InputStream in) {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			StringBuffer sb = new StringBuffer();
			String line = "";
			while ((line = reader.readLine()) != null) {
				sb.append(line);
				sb.append("\n");
			}
			return sb.toString();
		} catch (Exception exp) {
			throw new RuntimeException(exp);
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (Exception exp) {
				throw new RuntimeException(exp);
			}
		}
	}

	private void addHeaders(HttpRequest request) {
		Set<String> keys = this.headers.keySet();
		for (String key : keys) {
			String value = this.headers.get(key);
			request.addHeader(key, value);
		}
	}

	private void addFormParams(HttpPost post) {
		try {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			Set<String> keys = this.formData.keySet();
			for (String key : keys) {
				String value = formData.get(key);
				params.add(new BasicNameValuePair(key, value));
			}
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params);
			post.setEntity(entity);
		} catch (Exception exp) {
			throw new RuntimeException(exp);
		}
	}

	public void writeResponseToFile(File file) {
		InputStream is = null;
		OutputStream os = null;
		try {
			is = this.responseInputStream;
			os = new FileOutputStream(file);
			int inByte;
			while ((inByte = is.read()) != -1) {
				os.write(inByte);
			}
		} catch (Exception exp) {
			throw new RuntimeException(exp);
		} finally {
			try {
				if (is != null) {
					is.close();
				}
				if (os != null) {
					os.close();
				}
			} catch (Exception exp) {
				throw new RuntimeException(exp);
			}
		}
	}

	public void close() {
		try {
			this.responseInputStream.close();
		} catch (Exception exp) {
			throw new RuntimeException(exp);
		}
	}

}
