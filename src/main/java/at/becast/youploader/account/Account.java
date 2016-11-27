/* 
 * YouPloader Copyright (c) 2016 genuineparts (itsme@genuineparts.org)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 */
package at.becast.youploader.account;

import at.becast.youploader.Main;
import at.becast.youploader.database.SQLite;
import at.becast.youploader.oauth.OAuth2;
import at.becast.youploader.settings.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import at.becast.youploader.youtube.data.Cookie;
import at.becast.youploader.youtube.data.CookieJar;
import at.becast.youploader.youtube.upload.SimpleHTTP;

public class Account {
	public int id;
	public String refreshToken;
	public String name;
	public List<Cookie> cdata;
	private Settings s = Settings.getInstance();
	private static Connection c = SQLite.getInstance();
	private static final Logger LOG = LoggerFactory.getLogger(Account.class);

	public Account(int id, String name, String refreshToken, List<Cookie> cdata) {
		this.id = id;
		this.name = name;
		this.refreshToken = refreshToken;
		this.cdata = cdata;
	}

	public Account(String name) {
		this(0,name, null, null);
	}
  
	public void setCookie(List<Cookie> cdata){
		this.cdata = cdata;
	}
  
	public List<Cookie> getCookie(){
		return this.cdata;
	}

	public void setRefreshToken(String refreshToken){
		this.refreshToken = refreshToken;
	}
  
	public static Account read(String name) throws IOException {
		PreparedStatement stmt;
		try {
			stmt = c.prepareStatement("SELECT * FROM `accounts` WHERE `name`=? LIMIT 1");
			stmt.setString(1, name);
			ResultSet rs = stmt.executeQuery();
			ObjectMapper mapper = new ObjectMapper();
			List<Cookie> c = mapper.readValue(rs.getString("cookie"), new TypeReference<List<Cookie>>() {});
			int id = rs.getInt("id");
			String token = rs.getString("refresh_token");
			stmt.close();
			rs.close();
			return new Account(id,name,token,c);
		} catch (SQLException e) {
			LOG.error("Account read error!",e);
			return null;
		}
	}

	public static Account read(int id) throws IOException {
		PreparedStatement stmt;
		try {
			stmt = c.prepareStatement("SELECT * FROM `accounts` WHERE `id`=? LIMIT 1");
			stmt.setInt(1, id);
			ResultSet rs = stmt.executeQuery();
			ObjectMapper mapper = new ObjectMapper();
			List<Cookie> c = mapper.readValue(rs.getString("cookie"), new TypeReference<List<Cookie>>() {});
			String name = rs.getString("name");
			String token = rs.getString("refresh_token");
			stmt.close();
			rs.close();
			return new Account(id,name,token,c);
		} catch (SQLException e) {
			LOG.error("Account read error!",e);
			return null;
		}
	}
  
	public int save() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		LOG.info("Saving account");
	  	try {
			PreparedStatement stmt = c.prepareStatement("INSERT INTO `accounts` (`name`,`refresh_token`,`cookie`) VALUES(?,?,?)");
			stmt.setString(1, this.name);
			stmt.setString(2, this.refreshToken);
			stmt.setString(3, mapper.writeValueAsString(this.cdata));
			stmt.execute();
		    ResultSet rs = stmt.getGeneratedKeys();
		    stmt.close();
	        if (rs.next()){
	        	int id = rs.getInt(1);
	        	rs.close();
	        	LOG.info("Account saved");
	        	return id;
	        }else{
	        	LOG.error("Could not save account {}!",this.name);
	        	return -1;
	        }
		} catch (SQLException e) {
			LOG.error("Could not save account Ex:",e);
			return -1;
		}
	}
	
	public void updateCookie(int id) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		LOG.info("Updating account");
	  	try {
			PreparedStatement stmt = c.prepareStatement("UPDATE `accounts` SET `cookie`=? WHERE `id`=?");
			stmt.setString(1, mapper.writeValueAsString(this.cdata));
			stmt.setInt(2, id);
			stmt.execute();
		    stmt.close();
		} catch (SQLException e) {
			LOG.error("Could not update account Ex:",e);
		}
	}
	
	public void loadCookie() throws IOException {
		CookieJar persistentCookieStore = new CookieJar();
		CookieManager cmrCookieMan = new CookieManager(persistentCookieStore, null);
		persistentCookieStore.setSerializeableCookies(this.getCookie());
		CookieHandler.setDefault(cmrCookieMan);
		LOG.info("Updating cookies");
		OAuth2 auth = new OAuth2(s.setting.get("client_id"),s.setting.get("clientSecret"), this.refreshToken);
		Map<String, String> headers = new HashMap<>();
		try {
			headers.put("Authorization", auth.getHeader());
		} catch (NullPointerException e) {
		}
		SimpleHTTP Videoget = new SimpleHTTP();
		String resp = Videoget.get("https://www.googleapis.com/youtube/v3/search?part=id&forMine=true&maxResults=1&type=video", headers);
		Pattern MY_PATTERN = Pattern.compile("\"videoId\": \"(.*?)\"");
		Matcher m = MY_PATTERN.matcher(resp);
		String vidID = null;
		while (m.find()) {
		    vidID = m.group(1);
		}
		String url = String.format("https://www.youtube.com/edit?o=U&ns=1&video_id=%s", vidID);
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();

		con.setRequestMethod("GET");
		con.setRequestProperty("User-Agent", Main.APP_NAME+" "+Main.VERSION);
		int responseCode = con.getResponseCode();

		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		if(responseCode < 400){
			this.setCookie(persistentCookieStore.getSerializeableCookies());
			LOG.info("Got cookie: {}",persistentCookieStore.getSerializeableCookies().toString());
		}else{
			LOG.info("Could not fetch Cookie {}", response.toString());
		}
	}
  
	public static boolean exists(String name) {
		Connection c = SQLite.getInstance();
		Statement stmt;
		try {
			stmt = c.createStatement();
			String sql = "SELECT * FROM `accounts` WHERE `name`='"+name+"'"; 
			ResultSet rs = stmt.executeQuery(sql);
			if(rs.isBeforeFirst()){
				stmt.close();
				return true;
			}else{
				stmt.close();
				return false;
			}
		} catch (SQLException e) {
			LOG.error("Account exists error!",e);
			return false;
		}
	}
}
