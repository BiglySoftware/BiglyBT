/*
 * File    : SeedingRankColumnListener.java
 * Created : Sep 27, 2005
 * By      : TuxPaper
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.plugin.startstoprules.defaultplugin;

import java.util.Map;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;

/** A "My Torrents" column for displaying Seeding Rank.
 */
public class SeedingRankColumnListener implements
		TableCellRefreshListener, COConfigurationListener {
	private Map downloadDataMap;

	private PluginConfig pluginConfig;

	private int minTimeAlive;

	private int iRankType;

	private boolean bDebugLog;

	private int iTimed_MinSeedingTimeWithPeers;

	public SeedingRankColumnListener(Map _downloadDataMap, PluginConfig pc) {
		downloadDataMap = _downloadDataMap;
		pluginConfig = pc;
		COConfigurationManager.addListener(this);
		configurationSaved();
	}

	@Override
	public void refresh(TableCell cell) {
		Download dl = (Download) cell.getDataSource();
		if (dl == null)
			return;

		DefaultRankCalculator dlData = null;
		Object o = cell.getSortValue();
		if (o instanceof DefaultRankCalculator)
			dlData = (DefaultRankCalculator) o;
		else {
			dlData = (DefaultRankCalculator) downloadDataMap.get(dl);
			cell.setSortValue(dlData);
		}
		if (dlData == null)
			return;


		long sr = dl.getSeedingRank();

		String sText = "";
		if (sr >= 0) {
			if (dlData.getCachedIsFP())
				sText += MessageText.getString("StartStopRules.firstPriority") + " ";

			if (iRankType == StartStopRulesDefaultPlugin.RANK_TIMED) {
				//sText += "" + sr + " ";
				if (sr > DefaultRankCalculator.SR_TIMED_QUEUED_ENDS_AT) {
					long timeStarted = dl.getStats().getTimeStartedSeeding();
					long timeLeft;

					long lMsTimeToSeedFor = minTimeAlive;
					if (iTimed_MinSeedingTimeWithPeers > 0) {
  					PeerManager peerManager = dl.getPeerManager();
  					if (peerManager != null) {
  						int connectedLeechers = peerManager.getStats().getConnectedLeechers();
  						if (connectedLeechers > 0) {
  							lMsTimeToSeedFor = iTimed_MinSeedingTimeWithPeers;
  						}
  					}
					}

					if (dl.isForceStart())
						timeLeft = Constants.CRAPPY_INFINITY_AS_INT;
					else if (timeStarted <= 0)
						timeLeft = lMsTimeToSeedFor;
					else
						timeLeft = (lMsTimeToSeedFor - (SystemTime.getCurrentTime() - timeStarted));

					sText += TimeFormatter.format(timeLeft / 1000);
				} else if (sr > 0) {
					sText += MessageText.getString("StartStopRules.waiting");
				}
			} else if (sr > 0) {
				sText += String.valueOf(sr);
			}
		} else if (sr == DefaultRankCalculator.SR_FP0PEERS)
			sText = MessageText.getString("StartStopRules.FP0Peers");
		else if (sr == DefaultRankCalculator.SR_FP_SPRATIOMET)
			sText = MessageText.getString("StartStopRules.SPratioMet");
		else if (sr == DefaultRankCalculator.SR_RATIOMET)
			sText = MessageText.getString("StartStopRules.ratioMet");
		else if (sr == DefaultRankCalculator.SR_NUMSEEDSMET)
			sText = MessageText.getString("StartStopRules.numSeedsMet");
		else if (sr == DefaultRankCalculator.SR_NOTQUEUED)
			sText = "";
		else if (sr == DefaultRankCalculator.SR_0PEERS)
			sText = MessageText.getString("StartStopRules.0Peers");
		else if (sr == DefaultRankCalculator.SR_SHARERATIOMET)
			sText = MessageText.getString("StartStopRules.shareRatioMet");
		else {
			sText = "ERR" + sr;
		}
		// Add a Star if it's before minTimeAlive
		if (SystemTime.getCurrentTime() - dl.getStats().getTimeStartedSeeding() < minTimeAlive)
			sText = "* " + sText;
		cell.setText(sText);
		if (bDebugLog) {
			cell.setToolTip("FP:\n" + dlData.sExplainFP + "\n" + "SR:" + dlData.sExplainSR
					+ "\n" + "TRACE:\n" + dlData.sTrace);
		} else {
			cell.setToolTip(null);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.config.COConfigurationListener#configurationSaved()
	 */
	@Override
	public void configurationSaved() {
		minTimeAlive = pluginConfig
				.getUnsafeIntParameter("StartStopManager_iMinSeedingTime") * 1000;
		iTimed_MinSeedingTimeWithPeers = pluginConfig
				.getUnsafeIntParameter("StartStopManager_iTimed_MinSeedingTimeWithPeers") * 1000;
		iRankType = pluginConfig.getUnsafeIntParameter("StartStopManager_iRankType");
		bDebugLog = pluginConfig.getUnsafeBooleanParameter("StartStopManager_bDebugLog");
	}
}
