/*
 * Created on 15-Jul-2005
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.pifimpl.local.download;

import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.pif.download.DownloadManagerStats;

public class
DownloadManagerStatsImpl
	implements DownloadManagerStats
{
	private GlobalManagerStats		global_manager_stats;

	private OverallStats			overall_stats;

	protected
	DownloadManagerStatsImpl(
		GlobalManager	_gm )
	{
		global_manager_stats	= _gm.getStats();

		overall_stats = StatsFactory.getStats();
	}

	@Override
	public long
	getOverallDataBytesReceived()
	{
		return( overall_stats.getDownloadedBytes());
	}

	@Override
	public long
	getOverallDataBytesSent()
	{
		return( overall_stats.getUploadedBytes());
	}

	@Override
	public long
	getSessionUptimeSeconds()
	{
		return( overall_stats.getSessionUpTime());
	}

	@Override
	public int
	getDataReceiveRate()
	{
		return( global_manager_stats.getDataReceiveRate());
	}

	@Override
	public int
	getProtocolReceiveRate()
	{
		return( global_manager_stats.getProtocolReceiveRate());
	}

	@Override
	public int
	getDataAndProtocolReceiveRate()
	{
		return( global_manager_stats.getDataAndProtocolReceiveRate());
	}
	@Override
	public int
	getDataSendRate()
	{
		return( global_manager_stats.getDataSendRate());
	}

	@Override
	public int
	getProtocolSendRate()
	{
		return( global_manager_stats.getProtocolSendRate());
	}

	@Override
	public int
	getDataAndProtocolSendRate()
	{
		return( global_manager_stats.getDataAndProtocolSendRate());
	}

	@Override
	public long
	getDataBytesReceived()
	{
		return( global_manager_stats.getTotalDataBytesReceived());
	}

	@Override
	public long
	getProtocolBytesReceived()
	{
		return( global_manager_stats.getTotalProtocolBytesReceived());
	}

	@Override
	public long
	getDataBytesSent()
	{
		return( global_manager_stats.getTotalDataBytesSent());
	}

	@Override
	public long
	getProtocolBytesSent()
	{
		return( global_manager_stats.getTotalProtocolBytesSent());
	}

	@Override
	public long
	getSmoothedReceiveRate()
	{
		return( global_manager_stats.getSmoothedReceiveRate());
	}

	@Override
	public long
	getSmoothedSendRate()
	{
		return( global_manager_stats.getSmoothedSendRate());
	}
}
