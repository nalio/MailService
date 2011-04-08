package com.progress.codeshare.esbservice.mail;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.sonicsw.xq.XQConstants;
import com.sonicsw.xq.XQEnvelope;
import com.sonicsw.xq.XQHeader;
import com.sonicsw.xq.XQInitContext;
import com.sonicsw.xq.XQLog;
import com.sonicsw.xq.XQMessage;
import com.sonicsw.xq.XQParameterInfo;
import com.sonicsw.xq.XQParameters;
import com.sonicsw.xq.XQPart;
import com.sonicsw.xq.XQServiceContext;
import com.sonicsw.xq.XQServiceEx;
import com.sonicsw.xq.XQServiceException;

public final class MailService implements XQServiceEx {
	
	private static final String PARAM_BCC = "bcc";
	private static final String PARAM_CC = "cc";
	private static final String PARAM_FROM = "from";
	private static final String PARAM_HOST = "host";
	private static final String PARAM_PORT = "port";
	private static final String PARAM_REPLY_TO = "replyTo";
	private static final String PARAM_SUBJECT = "subject";
	private static final String PARAM_TO = "to";

	private static int s_major = 2010; // year
	private static int s_minor = 1222; // month-day
	private static int s_buildNumber = 1016; // hour-minute
	
	// This is the XQLog (the container's logging mechanism).
	private XQLog m_xqLog = null;
	// This is the the log prefix that helps identify this service during
	// logging
	private String m_logPrefix = "";
	
	private static final Pattern PATTERN_HEADER = Pattern
			.compile("com\\.progress\\.codeshare\\.esbservice\\.mail\\.(.*)");

	private static final String PROP_HOST = "mail.host";
	private static final String PROP_PORT = "mail.port";

	private final Properties CONF = new Properties();
	
	public void destroy() {
	}

	public void init(final XQInitContext ctx) {
		final XQParameters params = ctx.getParameters();

		m_xqLog = ctx.getLog();
		setLogPrefix(params);
		
		writeStartupMessage(params);
		writeParameters(params);
		// perform initilization work.

		m_xqLog.logInformation(m_logPrefix + " Initializing ...");

		CONF.put(PROP_HOST, params.getParameter(PARAM_HOST, XQConstants.PARAM_STRING));

		final String port = params.getParameter(PARAM_PORT, XQConstants.PARAM_STRING);

		if (port != null)
			CONF.put(PROP_PORT, port);
		
		m_xqLog.logInformation(m_logPrefix + " Initialized ...");

	}

	public void service(final XQServiceContext ctx) throws XQServiceException {

		try {
			final Session session = Session.getDefaultInstance(CONF);

			final XQParameters params = ctx.getParameters();

			final String bcc = params.getParameter(PARAM_BCC, XQConstants.PARAM_STRING);
			final String cc = params.getParameter(PARAM_CC, XQConstants.PARAM_STRING);
			final String from = params.getParameter(PARAM_FROM, XQConstants.PARAM_STRING);
			final String replyTo = params.getParameter(PARAM_REPLY_TO, XQConstants.PARAM_STRING);
			final String subject = params.getParameter(PARAM_SUBJECT, XQConstants.PARAM_STRING);
			final String to = params.getParameter(PARAM_TO, XQConstants.PARAM_STRING);

			while (ctx.hasNextIncoming()) {
				final XQEnvelope env = ctx.getNextIncoming();

				final XQMessage msg = env.getMessage();

				/* Copy all headers of the message to the mail message */
				final Iterator headerNameIterator = msg.getHeaderNames();

				final Message mail = new MimeMessage(session);

				while (headerNameIterator.hasNext()) {
					final String headerName = (String) headerNameIterator.next();

					final Matcher matcher = PATTERN_HEADER.matcher(headerName);

					if (matcher.find())
						mail.setHeader(matcher.group(1), (String) msg.getHeaderValue(matcher.group()));

				}

				/* Set/Override the bcc: header */
				if (bcc != null)
					mail.setRecipient(RecipientType.BCC, new InternetAddress(bcc));

				/* Set/Override the cc: header */
				if (cc != null)
					mail.setRecipient(RecipientType.CC, new InternetAddress(cc));

				/* Set/Override the From: header */
				if (from != null)
					mail.setFrom(new InternetAddress(from));

				/* Set/Override the Reply-To: header */
				if (replyTo != null)
					mail.setReplyTo(new Address[] { new InternetAddress(replyTo) });

				/* Set/Override the Subject: header */
				if (subject != null)
					mail.setSubject(subject);

				/* Set/Override the To: header */
				if (to != null)
					mail.setRecipient(RecipientType.TO, new InternetAddress(to));

				final Multipart multipart = new MimeMultipart();

				/* Map all parts of the message to the mail message */
				for (int i = 0; i < msg.getPartCount(); i++) {
					final XQPart msgPart = msg.getPart(i);

					final XQHeader header = msgPart.getHeader();

					final Iterator keyIterator = header.getKeys();

					final BodyPart bodyPart = new MimeBodyPart();

					while (keyIterator.hasNext()) {
						final String key = (String) keyIterator.next();

						final Matcher matcher = PATTERN_HEADER.matcher(key);

						if (matcher.find())
							bodyPart.addHeader(matcher.group(1), bodyPart.getHeader(matcher.group())[0]);

					}

					bodyPart.setContent(msgPart.getContent(), msgPart.getContentType());

					multipart.addBodyPart(bodyPart);
				}

				mail.setContent(multipart);

				Transport.send(mail);

				final Iterator addressIterator = env.getAddresses();

				if (addressIterator.hasNext())
					ctx.addOutgoing(env);

			}

		} catch (final Exception e) {
			throw new XQServiceException(e);
		}

	}

	public void start() {
		
		m_xqLog.logInformation(m_logPrefix + "Starting...");
		/*
		 * removido porque a api n�o pode chamar o start apos a
		 * inicializa��o. try { if (scheduler != null) { if
		 * (scheduler.isStarted()) { m_xqLog.logInformation("Resuming
		 * scheduler..."); schedulerInitContext(ginitialContext);
		 * scheduler.resumeAll(); } else { m_xqLog.logInformation("Starting
		 * scheduler..."); scheduler.start(); } } } catch (SchedulerException e) {
		 * m_xqLog.logError(e); } catch (XQServiceException e) {
		 * m_xqLog.logError(e); }
		 */
		m_xqLog.logInformation(m_logPrefix + "Started...");
		
	}

	public void stop() {
		m_xqLog.logInformation(m_logPrefix + "Stopping...");
		/*
		 * if (scheduler != null) { try { if (scheduler.isStarted()) {
		 * m_xqLog.logInformation("Pausing scheduler..."); scheduler.pauseAll(); } }
		 * catch (final SchedulerException e) { m_xqLog.logError(e); } }
		 */m_xqLog.logInformation(m_logPrefix + "Stopped...");
	}

	/**
	 * Clean up and get ready to destroy the service.
	 * 
	 */
	protected void setLogPrefix(XQParameters params) {
		String serviceName = params.getParameter(XQConstants.PARAM_SERVICE_NAME, XQConstants.PARAM_STRING);
		m_logPrefix = "[ " + serviceName + " ]";
	}
	
	/**
	 * Provide access to the service implemented version.
	 * 
	 */
	protected String getVersion() {
		return s_major + "." + s_minor + ". build " + s_buildNumber;
	}

	/**
	 * Writes a standard service startup message to the log.
	 */
	protected void writeStartupMessage(XQParameters params) {

		final StringBuffer buffer = new StringBuffer();

		String serviceTypeName = params.getParameter(XQConstants.SERVICE_PARAM_SERVICE_TYPE, XQConstants.PARAM_STRING);

		buffer.append("\n\n");
		buffer.append("\t\t " + serviceTypeName + "\n ");

		buffer.append("\t\t Version ");
		buffer.append(" " + getVersion());
		buffer.append("\n");

		buffer
				.append("\t\t Copyright (c) 2009, Progress Sonic Software Corporation (Brazil).");
		buffer.append("\n");

		buffer.append("\t\t All rights reserved. ");
		buffer.append("\n");

		m_xqLog.logInformation(buffer.toString());
	}

	/**
	 * Writes parameters to log.
	 */
	protected void writeParameters(XQParameters params) {

		final Map map = params.getAllInfo();
		final Iterator iter = map.values().iterator();

		while (iter.hasNext()) {
			final XQParameterInfo info = (XQParameterInfo) iter.next();

			if (info.getType() == XQConstants.PARAM_XML) {
				m_xqLog.logInformation(m_logPrefix + "Parameter Name =  " + info.getName());
			} else if (info.getType() == XQConstants.PARAM_STRING) {
				m_xqLog.logInformation(m_logPrefix + "Parameter Name = " + info.getName());
			}

			if (info.getRef() != null) {
				m_xqLog.logInformation(m_logPrefix + "Parameter Reference = " + info.getRef());

				// If this is too verbose
				// /then a simple change from logInformation to logDebug
				// will ensure file content is not displayed
				// unless the logging level is set to debug for the ESB
				// Container.
				m_xqLog.logInformation(m_logPrefix + "----Parameter Value Start--------");
				m_xqLog.logInformation("\n" + info.getValue() + "\n");
				m_xqLog.logInformation(m_logPrefix + "----Parameter Value End--------");
			} else {
				m_xqLog.logInformation(m_logPrefix + "Parameter Value = " + info.getValue());
			}
		}
	}
}