/*******************************************************************************
 * Copyright (c) 2009-2021 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/
package app.owlcms.utils;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import app.owlcms.init.OwlcmsFactory;
import ch.qos.logback.classic.Logger;

public class StartupUtils {

    static Logger logger = (Logger) LoggerFactory.getLogger(StartupUtils.class);
    static Logger mainLogger = (Logger) LoggerFactory.getLogger("app.owlcms.Main");

    static Integer serverPort = null;

    public static void disableWarning() {
    }

    /**
     * return true if OWLCMS_KEY = true as an environment variable, and if not, if -Dkey=true as a system property.
     *
     * Environment variables are upperCased, system properties are case-sensitive.
     * <ul>
     * <li>OWMCMS_PORT=80 is the same as -Dport=80
     * </ul>
     *
     * @param key
     * @return true if value is found and exactly "true"
     */
    public static boolean getBooleanParam(String key) {
        String envVar = "OWLCMS_" + key.toUpperCase();
        String val = System.getenv(envVar);
        if (val != null) {
            return val.equals("true");
        } else {
            return Boolean.getBoolean(key);
        }
    }

    public static Integer getIntegerParam(String key, Integer defaultValue) {
        String envVar = "OWLCMS_" + key.toUpperCase();
        String val = System.getenv(envVar);
        if (val != null) {
            return Integer.parseInt(val);
        } else {
            return Integer.getInteger(key, defaultValue);
        }
    }

    public static Logger getMainLogger() {
        return mainLogger;
    }

    public static Integer getServerPort() {
        return serverPort;
    }

    public static String getStringParam(String key) {
        String envVar = "OWLCMS_" + key.toUpperCase();
        String val = System.getenv(envVar);
        if (val != null) {
            return val;
        } else {
            return System.getProperty(key);
        }
    }
    
    public static String getRawStringParam(String key) {
        String envVar = key.toUpperCase();
        String val = System.getenv(envVar);
        if (val != null) {
            return val;
        } else {
            return System.getProperty(key);
        }
    }

    public static boolean isDebugSetting() {
        String param = StartupUtils.getStringParam("DEBUG");
        return "true".equalsIgnoreCase(param) || "debug".equalsIgnoreCase(param) || "trace".equalsIgnoreCase(param);
    }

    public static boolean isTraceSetting() {
        String param = StartupUtils.getStringParam("DEBUG");
        return "trace".equalsIgnoreCase(param);
    }

    public static void logStart(String appName, Integer serverPort) throws IOException, ParseException {
        InputStream in = StartupUtils.class.getResourceAsStream("/build.properties");
        Properties props = new Properties();
        props.load(in);
        String version = props.getProperty("version");
        OwlcmsFactory.setVersion(version);
        String buildTimestamp = props.getProperty("buildTimestamp");
        OwlcmsFactory.setBuildTimestamp(buildTimestamp);
        String buildZone = props.getProperty("buildZone");
        mainLogger.info("{} {} built {} ({})", appName, version, buildTimestamp, buildZone);
    }

    public static boolean openBrowser(Desktop desktop, String hostName)
            throws MalformedURLException, IOException, ProtocolException, URISyntaxException,
            UnsupportedOperationException {
        if (hostName == null) {
            return false;
        }

        int response;
        
        URL testingURL = new URL("http", hostName, serverPort, "/local/sounds/timeOver.mp3");
        HttpURLConnection huc = (HttpURLConnection) testingURL.openConnection();
        logger.debug("checking for {}", testingURL.toExternalForm());
        huc.setRequestMethod("GET");
        huc.connect();
        int response1 = huc.getResponseCode();
        response = response1;
        if (response == 200) {
            URL appURL = new URL("http", hostName, serverPort, "");
            String os = System.getProperty("os.name").toLowerCase();
            if (desktop != null) {
                desktop.browse(appURL.toURI());
            } else if (os.contains("win")) {
                Runtime rt = Runtime.getRuntime();
                rt.exec("rundll32 url.dll,FileProtocolHandler " + appURL.toURI());
            } else {
                return false;
            }
            return true;
        } else {
            logger.error("cannot open expected URL {}", testingURL.toExternalForm());
            return false;
        }
    }

    public static void setMainLogger(Logger mainLogger) {
        StartupUtils.mainLogger = mainLogger;
    }

    public static void setServerPort(Integer serverPort) {
        StartupUtils.serverPort = serverPort;
    }

    public static void startBrowser() {
        try {
            InetAddress localMachine = InetAddress.getLocalHost();
            String hostName = localMachine.getHostName();
            Desktop desktop = null;
            if (Desktop.isDesktopSupported()) {
                desktop = Desktop.getDesktop();
            }
            // if no desktop, will attempt Windows-specific technique
            boolean ok = openBrowser(desktop, hostName);
            if (!ok) {
                logger./**/warn("Cannot start browser on {}", System.getProperty("os.name"));
            }
        } catch (Throwable t) {
            logger./**/warn("Cannot start browser: {}", t.getCause() != null ? t.getCause() : t.getMessage());
        }
    }

}
