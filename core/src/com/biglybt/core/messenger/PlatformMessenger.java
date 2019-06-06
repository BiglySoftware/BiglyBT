/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.core.messenger;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.util.*;
import com.biglybt.core.util.Timer;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;
import com.biglybt.util.*;

/**
 * @author TuxPaper
 * @created Sep 25, 2006
 *
 */
public class PlatformMessenger
{
	private static final boolean DEBUG_URL = System.getProperty(
			"platform.messenger.debug.url", "0").equals("1");

	private static final String URL_PLATFORM_MESSAGE = "?service=rpc";

	private static final String URL_POST_PLATFORM_DATA = "service=rpc";

	private static final int MAX_POST_LENGTH = 1024 * 512 * 3; // 1.5M

	private static boolean USE_HTTP_POST = true;

	public static String REPLY_EXCEPTION = "exception";

	public static String REPLY_ACTION = "action";

	public static String REPLY_RESULT = "response";

	/** Key: id of queue;  Value: Map of queued messages & listeners */
	static Map<String, Map<PlatformMessage, PlatformMessengerListener>> mapQueues = new HashMap<>();

	private static final String QUEUE_NOAZID = "noazid.";

	private static final String QUEUE_NORMAL = "msg.";

	static private AEMonitor queue_mon = new AEMonitor(
			"v3.PlatformMessenger.queue");

	static private Timer timerProcess = new Timer("v3.PlatformMessenger.queue");

	//static private TimerEvent timerEvent = null;

	static Map<String, TimerEvent> mapTimerEvents = new HashMap<>();

	static AEMonitor mon_mapTimerEvents = new AEMonitor("mapTimerEvents");

	private static boolean initialized;

	private static fakeContext context;

	private static boolean allowMulti = false;

	private static AsyncDispatcher	dispatcher = new AsyncDispatcher(5000);

	private static Map<String, Object> mapExtra = new HashMap<>();

	public static synchronized void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		// The UI will initialize this
		context = new fakeContext();
	}

	public static ClientMessageContext getClientMessageContext() {
		if (!initialized) {
			init();
		}
		return context;
	}

	public static void queueMessage(PlatformMessage message,
			PlatformMessengerListener listener) {
		queueMessage(message, listener, true);
	}

	public static void queueMessage(PlatformMessage message,
			PlatformMessengerListener listener, boolean addToBottom) {

		if (!initialized) {
			init();
		}

		if (message == null) {
			debug("fire timerevent");
		}
		queue_mon.enter();
		try {
			long fireBefore;
			final String queueID;
			if (message != null) {
				if (!message.sendAZID()) {
					queueID = QUEUE_NOAZID;
				} else {
					queueID = QUEUE_NORMAL;
				}

				Map<PlatformMessage, PlatformMessengerListener> mapQueue = mapQueues.get(queueID);
				if (mapQueue == null) {
					mapQueue = new LinkedHashMap<>();
					mapQueues.put(queueID, mapQueue);
				}
				mapQueue.put(message, listener);

				debug("q " + queueID + "(" + mapQueue.size() + ") "
						+ message.toShortString() + ": " + message + " @ "
						+ new Date(message.getFireBefore()) + "; in "
						+ (message.getFireBefore() - SystemTime.getCurrentTime()) + "ms");

				fireBefore = message.getFireBefore();
			} else {
				queueID = null;
				fireBefore = SystemTime.getCurrentTime();
			}

			if (queueID != null) {
				try {
					mon_mapTimerEvents.enter();

  				TimerEvent timerEvent = mapTimerEvents.get(queueID);

    			if (timerEvent == null || timerEvent.hasRun() || fireBefore < timerEvent.getWhen()) {
    				if (timerEvent != null) {
  						mapTimerEvents.remove(queueID);
    					timerEvent.cancel();
    				}

    				timerEvent = timerProcess.addEvent(fireBefore,
    						new TimerEventPerformer() {
    							@Override
							    public void perform(TimerEvent event) {
    								try {
    									mon_mapTimerEvents.enter();

    									if ( mapTimerEvents.get(queueID) == event ){
    										mapTimerEvents.remove( queueID );
    									}
    								} finally {
  										mon_mapTimerEvents.exit();
    								}

  									Map<PlatformMessage, PlatformMessengerListener> mapQueue = mapQueues.get(queueID);
  									while (mapQueue != null && mapQueue.size() > 0) {
  										processQueue(queueID, mapQueue);
  									}
    								/*
    								Object[] keys = mapQueues.keySet().toArray();
    								for (int i = 0; i < keys.length; i++) {
    									Map mapQueue = mapQueues.get(keys[i]);
    									while (mapQueue != null && mapQueue.size() > 0) {
    										processQueue(mapQueue);
    									}
    								}
    								*/
    							}
    						});
    				mapTimerEvents.put(queueID, timerEvent);
    			}
  				if (timerEvent != null) {
    				debug(" next q process for  " + queueID + " in " + (timerEvent.getWhen() - SystemTime.getCurrentTime()));
  				}
				} finally {
					mon_mapTimerEvents.exit();
				}
			}
		} finally {
			queue_mon.exit();
		}
	}

	/**
	 * @param string
	 */
	public static void debug(String string) {
		AEDiagnosticsLogger diag_logger = AEDiagnostics.getLogger("v3.PMsgr");
		diag_logger.log(string);
		if (ConstantsVuze.DIAG_TO_STDOUT) {
			System.out.println(Thread.currentThread().getName() + "|"
					+ System.currentTimeMillis() + "] " + string);
		}
	}

	protected static void debug(String string, Throwable e) {
		debug(string + "\n\t" + e.getClass().getName() + ": " + e.getMessage()
				+ ", " + Debug.getCompressedStackTrace(e, 1, 80));
	}

	/**
	 * Sends the message almost immediately, skipping delayauthorization check
	 * @param message
	 * @param listener
	 *
	 * @since 3.0.5.3
	 */
	public static void pushMessageNow(PlatformMessage message,
			PlatformMessengerListener listener) {
		debug("push " + message.toShortString() + ": " + message);

		Map map = new HashMap(1);
		map.put(message, listener);
		processQueue(null, map);
	}

	protected static void processQueue(String queueID, Map mapQueue) {
		if (!initialized) {
			init();
		}

		final Map mapProcessing = new HashMap();

		boolean sendAZID = true;

		// Create urlStem (or post data)
		boolean isMulti = false;
		StringBuilder urlStem = new StringBuilder();
		long sequenceNo = 0;

		Map<String, Object> mapPayload = new HashMap<>();
		mapPayload.put("azv", Constants.BIGLYBT_VERSION);
		//mapPayload.put("mode", FeatureUtils.getPlusMode());
		//mapPayload.put("noadmode", FeatureUtils.getNoAdsMode());
		mapPayload.putAll(mapExtra);
		List<Map> listCommands = new ArrayList<>();
		mapPayload.put("commands", listCommands);

		boolean	forceProxy = false;

		queue_mon.enter();
		String lastServer = null;
		try {
			// add one at a time, ensure relay server messages are seperate
			boolean first = true;
			for (Iterator iter = mapQueue.keySet().iterator(); iter.hasNext();) {
				PlatformMessage message = (PlatformMessage) iter.next();
				Object value = mapQueue.get(message);

				Map<String, Object> mapCmd = new HashMap<>();

				boolean	fp = message.isForceProxy();

				if ( fp ){
					forceProxy = true;
				}

				if (first) {
					sendAZID = message.sendAZID();
					first = false;
				}

				// build urlStem
				message.setSequenceNo(sequenceNo);

				if (urlStem.length() > 0) {
					urlStem.append('&');
				}

				String listenerID = message.getListenerID();
				String messageID = message.getMessageID();
				Map params = message.getParameters();
				try {
					urlStem.append("msg=");
					urlStem.append(URLEncoder.encode(listenerID, "UTF-8"));
					urlStem.append(":");
					urlStem.append(URLEncoder.encode(message.getOperationID(),
							"UTF-8"));
				} catch (UnsupportedEncodingException e) {
				}

				mapCmd.put("seq-id", sequenceNo);
				mapCmd.put("listener-id", listenerID);
				mapCmd.put("op-id", message.getOperationID());
				if (params != null) {
					mapCmd.put("values", params);
				}
				listCommands.add(mapCmd);

				// We used to check on MAX_POST_LENGTH, but with the changes that
				// would require converting the map to JSON on every iteration to get
				// the length.  For now, just limit to 10
				if (sequenceNo > 10) {
					debug("breaking up batch at " + sequenceNo
							+ " because max limit would be exceeded");
					break;
				}

				String curServer = messageID + "-" + listenerID;
				if (lastServer != null && !lastServer.equals(curServer)) {
					isMulti = true;
				}

				PlatformMessengerListener listener = (PlatformMessengerListener) mapProcessing.get(message);
				if (listener != null) {
					listener.messageSent(message);
				}
				sequenceNo++;

				// Adjust lists
				mapProcessing.put(message, value);

				iter.remove();

				if (!getAllowMulti() ) {
					break;
				}
				lastServer = curServer;
			}
		} finally {
			queue_mon.exit();
		}
		//debug("about to process " + mapProcessing.size());

		if (mapProcessing.size() == 0) {
			return;
		}

		// Build base RPC url based on listener and server

		// Note: We used to append ContentNetworkVuzeGeneric.URL_PARAMS to this
		String sURL_RPC;
		if ((lastServer != null && (lastServer.endsWith("-subscription")) || urlStem.toString().startsWith("msg=searchtemplate"))) {
			sURL_RPC = Constants.URL_RPC2 + "?" + urlStem.toString();
		} else {
			sURL_RPC = Constants.URL_RPC + "?" + urlStem.toString();
		}

		if ( forceProxy ){

			sendAZID = false;

				// yah, well there's code in ContentNetworkUtils.getUrl that adds in the azid too :(

			sURL_RPC = sURL_RPC.replaceAll( "([\\?&])azid=.*?&", "$1" );

			mapPayload.remove( "azid" );
		}

		// Build full url and data to send
		String sURL;
		String sPostData = null;
		String sJSONPayload = UrlUtils.encode(JSONUtils.encodeToJSON(mapPayload));
		if (USE_HTTP_POST) {
			sURL = sURL_RPC;

			sPostData = URL_POST_PLATFORM_DATA + "&payload=" + sJSONPayload;
			//sPostData = cn.appendURLSuffix(sPostData, true, sendAZID);

			if (DEBUG_URL) {
				debug("POST for " + mapProcessing.size() + ": " + sURL + "\n   DATA: "
						+ sPostData);
			} else {
				debug("POST for " + mapProcessing.size() + ": " + sURL);
			}
		} else {
			sURL = sURL_RPC + URL_PLATFORM_MESSAGE + "&payload=" + sJSONPayload;

			//sURL = cn.appendURLSuffix(sURL, false, sendAZID);

			if (DEBUG_URL) {
				debug("GET: " + sURL);
			} else {
				debug("GET: " + sURL_RPC + URL_PLATFORM_MESSAGE);
			}
		}

		final String fURL = sURL;
		final String fPostData = sPostData;
		final boolean fForceProxy = forceProxy;

			// one at a time to take advantage of keep-alive connections

		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					try {
						processQueueAsync(fURL, fPostData, mapProcessing,fForceProxy);
					} catch (Throwable e) {
						e.printStackTrace();
						if (e instanceof ResourceDownloaderException) {
							debug("Error while sending message(s) to Platform: " + e.toString());
						} else {
							debug("Error while sending message(s) to Platform", e);
						}
						for (Iterator iter = mapProcessing.keySet().iterator(); iter.hasNext();) {
							PlatformMessage message = (PlatformMessage) iter.next();
							PlatformMessengerListener l = (PlatformMessengerListener) mapProcessing.get(message);
							if (l != null) {
								try {
									HashMap map = new HashMap();
									map.put("text", e.toString());
									map.put("Throwable", e);
									l.replyReceived(message, REPLY_EXCEPTION, map);
								} catch (Throwable e2) {
									debug("Error while sending replyReceived", e2);
								}
							}
						}
					}
				}
			});

	}

	/**
	 * @param mapProcessing
	 * @throws Exception
	 */
	protected static void processQueueAsync(String sURL, String sData,
			Map mapProcessing, boolean forceProxy) throws Throwable {
		URL url;
		url = new URL(sURL);

		Object[] result = downloadURL(url, sData, forceProxy);

		String s = (String)result[0];
		List listReplies = (List)result[1];

		if ( listReplies == null || listReplies.isEmpty()) {
			debug("Error while sending message(s) to Platform: reply: " + s
					+ "\nurl: " + sURL + "\nPostData: " + sData);
			for (Iterator iter = mapProcessing.keySet().iterator(); iter.hasNext();) {
				PlatformMessage message = (PlatformMessage) iter.next();
				PlatformMessengerListener l = (PlatformMessengerListener) mapProcessing.get(message);
				if (l != null) {
					try {
						HashMap map = new HashMap();
						map.put("text", "result was " + s);
						l.replyReceived(message, REPLY_EXCEPTION, map);
					} catch (Throwable e2) {
						debug("Error while sending replyReceived" + "\nurl: " + sURL
								+ "\nPostData: " + sData, e2);
					}
				}
			}
			return;
		}

		Map<Long, Map> mapOrder = new HashMap<>();
		for (Object reply : listReplies) {
			if (reply instanceof Map) {
				mapOrder.put(MapUtils.getMapLong((Map) reply, "seq-id", -1), (Map) reply);
			}
		}
		for (Iterator iter = mapProcessing.keySet().iterator(); iter.hasNext();) {
			PlatformMessage message = (PlatformMessage) iter.next();
			PlatformMessengerListener l = (PlatformMessengerListener) mapProcessing.get(message);
			if (l == null) {
				continue;
			}
			Map mapReply = mapOrder.get(new Long(message.getSequenceNo()));
			if (mapReply == null) {
				debug("No reply for " + message.toShortString());
			}
			String replyType = MapUtils.getMapString(mapReply, "type", "payload");
			Map payload;
			if (replyType.equalsIgnoreCase("payload")) {
				// parg, 12/5/216 - not sure when this broke or how significant it is but subscription-update (for example) returns a list not a map
				Object test = mapReply.get( "payload" );
				if ( test instanceof List ){
					List temp = (List)test;
					payload = new HashMap();
					try{
						for ( int i=0;i<temp.size();i+=2){
							String k = (String)temp.get(i);
							Object v = temp.get(i+1);
							payload.put( k, v );
						}
					}catch( Throwable e ){
						Debug.out( "invalid reply: " + mapReply, e );
					}
				}else{
					payload = MapUtils.getMapMap(mapReply, "payload", Collections.EMPTY_MAP);
				}
			} else {
				payload = new HashMap();
				payload.put("message", MapUtils.getMapString(mapReply, "message", "?"));
			}


			if (mapReply != null) {
  			String reply = JSONUtils.encodeToJSON(payload);
  			debug("Got a reply for "
  					+ message.toShortString() + "\n\t"
  					+ reply.substring(0, Math.min(8192, reply.length())));
			}

			try {
				l.replyReceived(message, replyType, payload);
			} catch (Exception e2) {
				debug("Error while sending replyReceived", e2);
			}
		}
	}

	private static Object[]
	downloadURL(
		URL 		rpc_url,
		String 		postData,
		boolean		forceProxy )

		throws Throwable
	{
		Throwable	error	= null;

		if ( !forceProxy ){

			try{
				Object[] result = downloadURLSupport( null, null, rpc_url, postData );

				if ( result[1] == null ){

					throw( new Exception( "Request failed" ));

				}else{

					return( result );
				}
			}catch( Throwable e ){

				error = e;
			}
		}

		try{
			PluginProxy 	plugin_proxy	= AEProxyFactory.getPluginProxy( "vuze settings", rpc_url, true );

			if ( plugin_proxy == null ){

				if ( error != null ){

					throw( error );
				}

				throw( new Exception( "Proxy unavailable" ));

			}else{

				URL 	url		= plugin_proxy.getURL();
				Proxy 	proxy	= plugin_proxy.getProxy();

				boolean	ok = false;

				try{
					String proxy_host = rpc_url.getHost() + (rpc_url.getPort()==-1?"":(":" + rpc_url.getPort()));

					Object[] result = downloadURLSupport( proxy, proxy_host, url, postData );

					ok = true;

					return( result );

				}finally{

					plugin_proxy.setOK( ok );
				}
			}
		}catch( Throwable f ){

			throw( error==null?f:error );
		}
	}

	private static Object[]
	downloadURLSupport(
		Proxy		proxy,
		String		proxy_host,
		URL 		url,
		String 		postData )

		throws Throwable
	{
		ResourceDownloaderFactory rdf = StaticUtilities.getResourceDownloaderFactory();

		ResourceDownloader rd;

		if ( proxy == null ){

			rd = rdf.create(url, postData);

		}else{

			rd = rdf.create(url, postData, proxy);
		}

		if ( proxy_host != null ){

			rd.setProperty( "URL_HOST", proxy_host );
		}

		rd.setProperty( "URL_Connection", "Keep-Alive" );

		rd = rdf.getRetryDownloader(rd, 3);

		// We could report percentage to listeners, but there's no need to atm
		//		rd.addListener(new ResourceDownloaderListener() {
		//
		//			public void reportPercentComplete(ResourceDownloader downloader,
		//					int percentage) {
		//			}
		//
		//			public void reportActivity(ResourceDownloader downloader, String activity) {
		//			}
		//
		//			public void failed(ResourceDownloader downloader,
		//					ResourceDownloaderException e) {
		//			}
		//
		//			public boolean completed(ResourceDownloader downloader, InputStream data) {
		//				return true;
		//			}
		//		});

		InputStream is = rd.download();

		byte data[];

		try {
			int length = is.available();

			data = new byte[length];

			is.read(data);

		} finally {

			is.close();
		}

		String s = new String( data, "UTF8");

		Map mapAllReplies = JSONUtils.decodeJSON(s);

		List listReplies = MapUtils.getMapList(mapAllReplies, "replies", null);

		return( new Object[]{ s, listReplies });
	}

	public static void setAllowMulti(boolean allowMulti) {
		PlatformMessenger.allowMulti = allowMulti;
	}

	public static boolean getAllowMulti() {
		return allowMulti;
	}

	public static void addExtraParam(String key, Object value) {
		synchronized (mapExtra) {
			mapExtra.put(key, value);
		}
	}

	private static class fakeContext
		extends ClientMessageContextImpl
	{
		private void log(String str) {
			if (System.getProperty("browser.route.all.external.stimuli.for.testing",
					"false").equalsIgnoreCase("true")) {

				System.err.println(str);
			}
			debug(str);
		}

		public fakeContext() {
			super("fakeContext", null);
		}

		public void deregisterBrowser() {
			log("deregisterBrowser");
		}

		public void displayBrowserMessage(String message) {
			log("displayBrowserMessage - " + message);
		}

		@Override
		public boolean executeInBrowser(String javascript) {
			log("executeInBrowser - " + javascript);
			return false;
		}

		@Override
		public Object getBrowserData(String key) {
			log("getBrowserData - " + key);
			return null;
		}

		@Override
		public boolean sendBrowserMessage(String key, String op) {
			log("sendBrowserMessage - " + key + "/" + op);
			return false;
		}

		@Override
		public boolean sendBrowserMessage(String key, String op, Map params) {
			log("sendBrowserMessage - " + key + "/" + op + "/" + params);
			return false;
		}

		@Override
		public void setBrowserData(String key, Object value) {
			log("setBrowserData - " + key + "/" + value);
		}

		@Override
		public boolean sendBrowserMessage(String key, String op, Collection params) {
			log("sendBrowserMessage - " + key + "/" + op + "/" + params);
			return false;
		}

		@Override
		public void setTorrentURLHandler(torrentURLHandler handler) {
			log("setTorrentURLHandler - " + handler);
		}

	}
}
