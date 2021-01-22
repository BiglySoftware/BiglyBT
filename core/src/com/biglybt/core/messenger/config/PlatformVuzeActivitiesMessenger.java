/*
 * Created on Jan 28, 2008
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.messenger.config;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.biglybt.activities.ActivitiesEntry;
import com.biglybt.activities.ActivitiesManager;
import com.biglybt.core.messenger.PlatformMessage;
import com.biglybt.core.messenger.PlatformMessenger;
import com.biglybt.core.messenger.PlatformMessengerListener;
import com.biglybt.util.MapUtils;

/**
 * @author TuxPaper
 * @created Jan 28, 2008
 *
 */
public class PlatformVuzeActivitiesMessenger
{
	public static final String LISTENER_ID = "vuzenews";

	public static final String OP_GET = "get-entries";

	public static final long DEFAULT_RETRY_MS = 1000L * 60 * 60 * 24;

	public static void getEntries(final long agoMS,
			long maxDelayMS, String reason, final GetEntriesReplyListener replyListener) {
		PlatformMessage message = new PlatformMessage("AZMSG",
				reason.equals("shown") ? "vznews" : LISTENER_ID, OP_GET, new Object[] {
					"ago-ms",
					new Long(agoMS),
					"reason",
					reason,
				}, maxDelayMS);

		PlatformMessengerListener listener = null;
		if (replyListener != null) {
			listener = new PlatformMessengerListener() {
				@Override
				public void messageSent(PlatformMessage message) {
				}

				@Override
				public void replyReceived(PlatformMessage message, String replyType,
				                          Map reply) {
					ActivitiesEntry[] entries = new ActivitiesEntry[0];
					List entriesList = (List) MapUtils.getMapObject(reply, "entries",
							null, List.class);
					if (entriesList != null && entriesList.size() > 0) {
						entries = new ActivitiesEntry[entriesList.size()];
						int i = 0;
						for (Iterator iter = entriesList.iterator(); iter.hasNext();) {
							Object obj = iter.next();
							if  ( obj instanceof Map ){
								Map platformEntry = (Map)obj;
								entries[i] = ActivitiesManager.createEntryFromMap(
										platformEntry, false);
								if (entries[i] != null) {
									i++;
								}
							}
						}
					}
					long refreshInMS = MapUtils.getMapLong(reply, "refresh-in-ms",
							DEFAULT_RETRY_MS);
					replyListener.gotVuzeNewsEntries(entries, refreshInMS);
				}
			};
		}

		PlatformMessenger.queueMessage(message, listener);
	}

	public static interface GetEntriesReplyListener
	{
		public void gotVuzeNewsEntries(ActivitiesEntry[] entries, long refreshInMS);
	}
}
