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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.util.JSONUtils;
import org.json.simple.JSONObject;

/**
 * @author TuxPaper
 * @created Sep 25, 2006
 *
 */
public class PlatformMessage
{
	private final String messageID;

	private final String listenerID;

	private final String operationID;

	private final Map<?, ?> parameters;

	private final long fireBeforeDate;

	private final long messageCreatedOn;

	private long lSequenceNo = -1;

	private boolean sendAZID = true;

	private boolean	forceProxy;


	private PlatformMessage(String messageID, String listenerID,
			String operationID, JSONObject parameters, long maxDelayMS) {
		this.messageID = messageID;
		this.listenerID = listenerID;
		this.operationID = operationID;
		this.parameters = parameters;

		messageCreatedOn = SystemTime.getCurrentTime();
		fireBeforeDate = messageCreatedOn + maxDelayMS;
	}

	public PlatformMessage(String messageID, String listenerID,
			String operationID, Map<?, ?> parameters, long maxDelayMS) {
		this(messageID, listenerID, operationID,
				JSONUtils.encodeToJSONObject(parameters), maxDelayMS);
	}

	public PlatformMessage(String messageID, String listenerID,
			String operationID, Object[] parameters, long maxDelayMS) {

		this(messageID, listenerID, operationID,
				JSONUtils.encodeToJSONObject(parseParams(parameters)), maxDelayMS);
	}

	public static Map<String, Object> parseParams(Object[] parameters) {
		Map<String, Object> result = new HashMap<>();
		for (int i = 0; i < parameters.length - 1; i += 2) {
			try {
				if (parameters[i] instanceof String) {
					if (parameters[i + 1] instanceof String[]) {
						List<String> list = Arrays.asList((String[]) parameters[i + 1]);
						result.put((String) parameters[i], list);
					} else if (parameters[i + 1] instanceof Object[]) {
						result.put((String) parameters[i],
								parseParams((Object[]) parameters[i + 1]));
					} else if (parameters[i + 1] instanceof Map) {
						result.put((String) parameters[i], (Map) parameters[i + 1]);
					} else {
						result.put((String) parameters[i], parameters[i + 1]);
					}
				}
			} catch (Exception e) {
				Debug.out("making JSONObject out of parsedParams", e);
			}
		}

		return result;
	}

	public boolean isForceProxy(){
		return( forceProxy );
	}

	public void
	setForceProxy(
		boolean		fp )
	{
		forceProxy = fp;
	}

	public long getFireBefore() {
		return fireBeforeDate;
	}

	public long getMessageCreated() {
		return messageCreatedOn;
	}

	public Map<?, ?> getParameters() {
		return parameters;
	}

	public String getListenerID() {
		return listenerID;
	}

	public String getMessageID() {
		return messageID;
	}

	public String getOperationID() {
		return operationID;
	}

	protected long getSequenceNo() {
		return lSequenceNo;
	}

	protected void setSequenceNo(long sequenceNo) {
		lSequenceNo = sequenceNo;
	}

	public String toString() {
		String paramString = parameters.toString();
		return "PlaformMessage {"
				+ lSequenceNo
				+ ", "
				+ messageID
				+ ", "
				+ listenerID
				+ ", "
				+ operationID
				+ ", "
				+ (paramString.length() > 32767 ? paramString.substring(0, 32767)
						: paramString) + "}";
	}

	public String toShortString() {
		return getMessageID() + "."
				+ getListenerID() + "." + getOperationID();
	}

	/**
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	public boolean sendAZID() {
		return sendAZID;
	}

	public void setSendAZID(boolean send) {
		sendAZID = send;
	}
}
