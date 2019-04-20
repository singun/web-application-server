package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Map;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;
import util.StringUtils;

public class RequestHandler extends Thread {
	private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

	private Socket connection;

	public RequestHandler(Socket connectionSocket) {
		this.connection = connectionSocket;
	}

	public void run() {
		log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(), connection.getPort());

		try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
			// TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));

			String line;

			String method = "";
			String resourcePath = "";
			boolean isFirstLine = true;
			int contentLength = 0;
			while (!StringUtils.isEmpty(line = bufferedReader.readLine())) {
				log.info("inputStream: {}", line);

				if (isFirstLine) {
					method = HttpRequestUtils.parseMethod(line);
					resourcePath = HttpRequestUtils.parseResource(line);
					isFirstLine = false;
				}

				if (line.startsWith("Content-Length")) {
					contentLength = getContentLength(line);
				}
			}
			DataOutputStream dos = new DataOutputStream(out);

			if (method.equals("POST")) {
				final String data = IOUtils.readData(bufferedReader, contentLength);
				final Map<String, String> queryStringMap = HttpRequestUtils.parseQueryString(data);

				if (resourcePath.equals("/user/create")) {
					final User user = new User(queryStringMap.get("userId"), queryStringMap.get("password"), queryStringMap.get("name"), queryStringMap.get("email"));
					log.info("usercreate, {}", user.toString());
					DataBase.addUser(user);
					response302Header(dos, "/index.html");
				} else if (resourcePath.equals("/user/login")) {
					User user = DataBase.findUserById(queryStringMap.get("userId"));
					byte[] body= null;
					if (user != null && user.getPassword().equals(queryStringMap.get("password"))) {
						body = Files.readAllBytes(new File("./webapp/index.html").toPath());
						response200CookieHeader(dos, true);
					} else {
						body = Files.readAllBytes(new File("./webapp/user/login_failed.html").toPath());
						response200CookieHeader(dos, false);
					}
					responseBody(dos, body);

				}

			} else {
				byte[] body = Files.readAllBytes(new File("./webapp" + resourcePath).toPath());
				if (resourcePath.endsWith("css")) {
					response200CssHeader(dos, body.length);
					responseBody(dos, body);
				} else {
					response200Header(dos, body.length);
					responseBody(dos, body);
				}
			}


		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void response200CssHeader(DataOutputStream dos, int length) {
		try {
			dos.writeBytes("HTTP/1.1 200 OK \r\n");
			dos.writeBytes("Content-Type: text/css;\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private int getContentLength(String line) {
		final HttpRequestUtils.Pair pair = HttpRequestUtils.parseHeader(line);
		return Integer.parseInt(pair.getValue());
	}

	private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
		try {
			dos.writeBytes("HTTP/1.1 200 OK \r\n");
			dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
			dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void response200CookieHeader(DataOutputStream dos, boolean success) {
		try {
			dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
			dos.writeBytes("Set-Cookie: logined="+success+"\r\n");
			dos.writeBytes("Location: /index.html\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void response302Header(DataOutputStream dos, String url) {
		try {
			dos.writeBytes("HTTP/1.1 302 Found \r\n");
			dos.writeBytes("Location: "+url+" \r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void responseBody(DataOutputStream dos, byte[] body) {
		try {
			dos.write(body, 0, body.length);
			dos.flush();
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}
}
