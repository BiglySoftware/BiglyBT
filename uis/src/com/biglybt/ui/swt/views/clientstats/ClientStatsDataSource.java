/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.clientstats;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.util.BEncodableObject;

import com.biglybt.util.MapUtils;

public class ClientStatsDataSource
	implements BEncodableObject
{

	public String client;

	public int count;

	public int current;

	public long bytesReceived;

	public long bytesDiscarded;

	public long bytesSent;

	public Map<String, Map<String, Object>> perNetworkStats;

	public ClientStatsOverall overall;

	public ClientStatsDataSource() {
		perNetworkStats = new HashMap<>();
	}

	public ClientStatsDataSource(Map loadMap) {
		client = MapUtils.getMapString(loadMap, "client", "?");
		count = MapUtils.getMapInt(loadMap, "count", 0);
		bytesReceived = MapUtils.getMapLong(loadMap, "bytesReceived", 0);
		bytesDiscarded = MapUtils.getMapLong(loadMap, "bytesDiscarded", 0);
		bytesSent = MapUtils.getMapLong(loadMap, "bytesSent", 0);
		perNetworkStats = MapUtils.getMapMap(loadMap, "perNetworkStats",
				new HashMap<String, Map<String, Object>>());
	}

	@Override
	public Object toBencodeObject() {
		Map<String, Object> map = new HashMap<>();
		map.put("client", client);
		map.put("count", Long.valueOf(count));
		map.put("bytesReceived", Long.valueOf(bytesReceived));
		map.put("bytesDiscarded", Long.valueOf(bytesDiscarded));
		map.put("bytesSent", Long.valueOf(bytesSent));
		map.put("perNetworkStats", perNetworkStats);
		return map;
	}
}
