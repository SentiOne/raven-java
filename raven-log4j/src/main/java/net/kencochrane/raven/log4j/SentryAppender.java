package net.kencochrane.raven.log4j;

import net.kencochrane.raven.Client;
import net.kencochrane.raven.SentryDsn;
import net.kencochrane.raven.spi.JSONProcessor;
import net.kencochrane.raven.spi.RavenMDC;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Log4J appender that will send messages to Sentry.
 */
public class SentryAppender extends AppenderSkeleton {

    private boolean async;
    private Log4jMDC mdc;
    protected String sentryDsn;
    protected Client client;
    protected boolean messageCompressionEnabled = true;
    private List<JSONProcessor> jsonProcessors = Collections.emptyList();
	private Map<String, String> tags;
    
    private static final int MAX_MSG_LENGTH = 1000;

    public SentryAppender() {
        initMDC();
        mdc = (Log4jMDC) RavenMDC.getInstance();
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public String getSentryDsn() {
        return sentryDsn;
    }

    public void setSentryDsn(String sentryDsn) {
        this.sentryDsn = sentryDsn;
    }

    public boolean isMessageCompressionEnabled() {
        return messageCompressionEnabled;
    }

    public void setMessageCompressionEnabled(boolean messageCompressionEnabled) {
        this.messageCompressionEnabled = messageCompressionEnabled;
    }

    /**
     * Set a comma-separated list of fully qualified class names of
     * JSONProcessors to be used.
     *
     * @param setting a comma-separated list of fully qualified class names of JSONProcessors
     */
    public void setJsonProcessors(String setting) {
        this.jsonProcessors = loadJSONProcessors(setting);
    }

    /**
     * Notify processors that a message has been logged. Note that this method
     * is intended to be run on the same thread that creates the message.
     */
    public void notifyProcessorsBeforeAppending() {
        for (JSONProcessor processor : jsonProcessors) {
            processor.prepareDiagnosticContext();
        }
    }

    /**
     * Notify processors after a message has been logged. Note that this method
     * is intended to be run on the same thread that creates the message.
     */
    public void notifyProcessorsAfterAppending() {
        for (JSONProcessor processor : jsonProcessors) {
            processor.clearDiagnosticContext();
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.stop();
        }
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

    @Override
    public void activateOptions() {
        client = (sentryDsn == null ? new Client() : new Client(SentryDsn.buildOptional(sentryDsn)));
        client.setJSONProcessors(jsonProcessors);
        client.setMessageCompressionEnabled(messageCompressionEnabled);
    }

    @Override
    protected void append(LoggingEvent event) {
        mdc.setThreadLoggingEvent(event);
        try {
            // get timestamp and timestamp in correct string format.
            long timestamp = event.getTimeStamp();

            // get the log and info about the log.
            String message = getSafeMessage(event.getRenderedMessage());
            String logger = event.getLogger().getName();
            int level = (event.getLevel().toInt() / 1000);  //Need to divide by 1000 to keep consistent with sentry
            String culprit = event.getLoggerName();

            // is it an exception?
            ThrowableInformation info = event.getThrowableInformation();

            // notify processors about the message
            // (in async mode this is done by AsyncSentryAppender)
            if (!async) {
                notifyProcessorsBeforeAppending();
            }

            // send the message to the sentry server
            if (info == null) {
                client.captureMessage(message, timestamp, logger, level, culprit, tags);
            } else {
                client.captureException(message, timestamp, logger, level, culprit, info.getThrowable(), tags);
            }

            if (!async) {
                notifyProcessorsAfterAppending();
            }
        } finally {
            mdc.removeThreadLoggingEvent();
        }
    }
    
    protected String getSafeMessage(String msg){
	if (msg != null && msg.length() > MAX_MSG_LENGTH){
            return msg.substring(0,MAX_MSG_LENGTH/2) + "..." + msg.substring(msg.length() - (MAX_MSG_LENGTH/2 - 3),msg.length());
        } else {
            return msg;
        }
    }

    private static List<JSONProcessor> loadJSONProcessors(String setting) {
        if (setting == null) {
            return Collections.emptyList();
        }
        try {
            List<JSONProcessor> processors = new ArrayList<JSONProcessor>();
            String[] clazzes = setting.split(",\\s*");
            for (String clazz : clazzes) {
                JSONProcessor processor = (JSONProcessor) Class.forName(clazz).newInstance();
                processors.add(processor);
            }
            return processors;
        } catch (ClassNotFoundException exception) {
            throw new RuntimeException("Processor could not be found.", exception);
        } catch (InstantiationException exception) {
            throw new RuntimeException("Processor could not be instantiated.", exception);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Processor could not be instantiated.", exception);
        }
    }

    public static void initMDC() {
        if (RavenMDC.getInstance() != null) {
            if (!(RavenMDC.getInstance() instanceof Log4jMDC)) {
                throw new IllegalStateException("An incompatible RavenMDC instance has been set. Please check your Raven configuration.");
            }
            return;
        }
        RavenMDC.setInstance(new Log4jMDC());
    }

	public Map<String, String> getTags() {
		return tags;
	}

	public void setTags(Map<String, String> tags) {
		this.tags = tags;
	}
}
