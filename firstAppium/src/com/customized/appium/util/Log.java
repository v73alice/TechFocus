package com.customized.appium.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 日志类
 * 
 * @author kaliwn
 *
 */

public class Log {
	public final static int LOG_LEVEL = 6;
	public static int ERROR = 1;
	public static int WARN = 2;
	public static int INFO = 3;
	public static int DEBUG = 4;
	public static int VERBOS = 5;

	private static String error = "ERROR";
	private static String debug = "DEBUG";
	private static String verbos = "VERBOS";

	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
	private static Date date = new Date();

	public static void d(String s) {
		if(LOG_LEVEL > DEBUG)
		write(debug, s);
	}

	public static void e(String s) {
		if(LOG_LEVEL > ERROR)
		write(error, s);
	}

	public static void v(String s) {
		if(LOG_LEVEL > VERBOS)
		write(verbos, s);
	}

	public static void s(String s) {
		System.out.println(s);
	}

	public static void write(String type, String data) {
		System.out.println(sdf.format(date.getTime()) + " " + type + ":" + data);
	}

}
