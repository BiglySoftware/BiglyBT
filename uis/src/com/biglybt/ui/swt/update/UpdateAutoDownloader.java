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

package com.biglybt.ui.swt.update;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import com.biglybt.core.util.AEThread;

import com.biglybt.pif.update.Update;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener;

/**
 * @author TuxPaper
 * @created Feb 25, 2007
 *
 */
public class UpdateAutoDownloader
	implements ResourceDownloaderListener
{
	private final Update[] updates;

	private ArrayList downloaders;

	private Iterator iterDownloaders;

	private final cbCompletion completionCallback;

	public static interface cbCompletion
	{
		public void allUpdatesComplete(boolean requiresRestart, boolean bHadMandatoryUpdates);
	}

	/**
	 * @param us
	 */
	public UpdateAutoDownloader(Update[] updates, cbCompletion completionCallback) {
		this.updates = updates;
		this.completionCallback = completionCallback;
		downloaders = new ArrayList();

		start();
	}

	private void start() {
		for (int i = 0; i < updates.length; i++) {
			Update update = updates[i];
			ResourceDownloader[] rds = update.getDownloaders();
			Collections.addAll(downloaders, rds);
		}

		iterDownloaders = downloaders.iterator();
		nextUpdate();
	}

	/**
	 *
	 *
	 * @since 1.0.0.0
	 */
	private boolean nextUpdate() {
		if (iterDownloaders.hasNext()) {
			ResourceDownloader downloader = (ResourceDownloader) iterDownloaders.next();
			downloader.addListener(this);
			downloader.asyncDownload();
			return true;
		}
		return false;
	}

	/**
	 *
	 *
	 * @since 1.0.0.0
	 */
	private void allDownloadsComplete() {
		boolean bRequiresRestart = false;
		boolean bHadMandatoryUpdates = false;

		for (int i = 0; i < updates.length; i++) {
			Update update = updates[i];
				// updates with no downloaders exist for admin purposes only
			if ( update.getDownloaders().length > 0){
				if (update.getRestartRequired() != Update.RESTART_REQUIRED_NO) {
					bRequiresRestart = true;
				}
				if ( update.isMandatory()){
					bHadMandatoryUpdates = true;
				}
			}
		}

		completionCallback.allUpdatesComplete(bRequiresRestart,bHadMandatoryUpdates);
	}

	// @see com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener#completed(com.biglybt.pif.utils.resourcedownloader.ResourceDownloader, java.io.InputStream)
	@Override
	public boolean completed(ResourceDownloader downloader, InputStream data) {
		downloader.removeListener(this);
		if (!nextUpdate()) {
			// fire in another thread so completed function can exit
			AEThread thread = new AEThread("AllDownloadsComplete", true) {
				@Override
				public void runSupport() {
					allDownloadsComplete();
				}
			};
			thread.start();
		}
		return true;
	}

	// @see com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener#failed(com.biglybt.pif.utils.resourcedownloader.ResourceDownloader, com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException)
	@Override
	public void failed(ResourceDownloader downloader,
	                   ResourceDownloaderException e) {
		downloader.removeListener(this);
		iterDownloaders.remove();
		nextUpdate();
	}

	// @see com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener#reportActivity(com.biglybt.pif.utils.resourcedownloader.ResourceDownloader, java.lang.String)
	@Override
	public void reportActivity(ResourceDownloader downloader, String activity) {
	}

	// @see com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener#reportAmountComplete(com.biglybt.pif.utils.resourcedownloader.ResourceDownloader, long)
	@Override
	public void reportAmountComplete(ResourceDownloader downloader, long amount) {
	}

	// @see com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener#reportPercentComplete(com.biglybt.pif.utils.resourcedownloader.ResourceDownloader, int)
	@Override
	public void reportPercentComplete(ResourceDownloader downloader,
	                                  int percentage) {
	}
}
