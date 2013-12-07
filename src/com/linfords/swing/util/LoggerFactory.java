package com.linfords.swing.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

public class LoggerFactory {

	private LoggerFactory() {
	}

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat(
			"dd/MM/yyyy hh:mm:ss.SSS");

	private static final StreamHandler systemOutHandler = new StreamHandler(
			System.out, new StandardFormatter());

	public static Logger getLogger(Class<?> loggingDomain) {
		Logger logger = Logger.getLogger(loggingDomain.getName());
		logger.setUseParentHandlers(false);
		logger.addHandler(systemOutHandler);
		return logger;
	}

	public static class StandardFormatter extends java.util.logging.Formatter {
		@Override
		public String format(LogRecord rec) {
			StringBuilder sb = new StringBuilder(1000);
			sb.append(DATE_FORMAT.format(new Date(rec.getMillis())));
			sb.append(" [").append(rec.getSourceClassName()).append(".");
			sb.append(rec.getSourceMethodName()).append("] ");
			sb.append("[").append(rec.getLevel()).append("] ");
			sb.append(formatMessage(rec));
			sb.append("\n");
			return sb.toString();
		}
	}

}
