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

package com.biglybt.core.ipfilter.impl;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.*;
import com.biglybt.core.ipfilter.IpRange;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderAdapter;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pifimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

/**
 * @author TuxPaper
 * @created Jun 5, 2007
 *
 */
public class IpFilterAutoLoaderImpl
{
	static final LogIDs LOGID = LogIDs.CORE;

	public static final String CFG_AUTOLOAD_LAST 	= ConfigKeys.IPFilter.ICFG_IP_FILTER_AUTOLOAD_LAST;	// used in xmwebui


	static final AEMonitor class_mon = new AEMonitor(
			"IpFilterAutoLoaderImpl:class");

	private Object timerEventFilterReload;

	final IpFilterImpl ipFilter;

	public IpFilterAutoLoaderImpl(IpFilterImpl ipFilter) {
		this.ipFilter = ipFilter;

		COConfigurationManager.addParameterListener(ConfigKeys.IPFilter.ICFG_IP_FILTER_AUTOLOAD_DAYS, parameterName -> setNextAutoDownload(false));
	}

	/**
	 * Load dat filter as specified at http://wiki.phoenixlabs.org/wiki/DAT_Format
	 * @param fin
	 * @throws Exception
	 *
	 * @since 3.0.1.5
	 */
	private void loadDATFilters(InputStream fin) {
		try {
			class_mon.enter();

			List new_ipRanges = new ArrayList(1024);

			InputStreamReader streamReader = null;
			BufferedReader reader = null;
			try {
				Pattern pattern = Pattern.compile("^(.*):([0-9\\.]+)[^0-9]+([0-9\\.]+).*");
				int parseMode = -1;

				//open the file
				// TODO: test charset fallback (should fallback to ascii)
				streamReader = new InputStreamReader(fin, "utf8");
				reader = new BufferedReader(streamReader);

				int numConsecutiveUnknowns = 0;

				while (numConsecutiveUnknowns < 1000) {
					String line = reader.readLine();
					//System.out.println("line=" + line);
					if (line == null) {
						break;
					}

					line = line.trim();

					if (line.startsWith("#") || line.length() == 0) {
						continue;
					}

					String description = "";
					String startIp = null;
					String endIp = null;
					int level = 0;

					if (parseMode <= 0 || parseMode == 1) {
						Matcher matcher = pattern.matcher(line);
						if (matcher.find()) {
							if (parseMode != 1) {
								parseMode = 1;
							}
							description = matcher.group(1);
							startIp = matcher.group(2);
							endIp = matcher.group(3);
						} else {
							Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
									"unrecognized line while reading ip filter: " + line));
						}
					}

					if (parseMode != 1) {
						if (parseMode != 2) {
							parseMode = 2;
						}

						// spec says:
						//    1.1.1.1, 1.1.1.2, 100, moo
						// but I've seen dash format, such as
						//    1.1.1.1 - 1.1.1.2, 100, moo
						// so 	 for both
						String[] sections = line.split(" *[-,] *", 4);

						if (sections.length >= 2) {
							if (sections[0].indexOf('.') < 0 || sections[1].indexOf('.') < 0
									|| sections[0].length() > 15 || sections[1].length() > 15
									|| sections[0].length() < 7 || sections[1].length() < 7) {
								numConsecutiveUnknowns++;
								continue;
							}
						}

						if (sections.length >= 4) {
							// simple format:
							// startip, endip, level, desc
							startIp = sections[0];
							endIp = sections[1];
							description = sections[3];
							try {
								level = Integer.parseInt(sections[2]);
							} catch (NumberFormatException e) {
								description = sections[2] + " " + description;
							}
							for (int i = 4; i < sections.length; i++) {
								description += " " + sections[i];
							}
							numConsecutiveUnknowns = 0;
						} else if (sections.length == 3) {
							startIp = sections[0];
							endIp = sections[1];
							description = sections[2];
							numConsecutiveUnknowns = 0;
						} else if (sections.length == 2) {
							startIp = sections[0];
							endIp = sections[1];
							numConsecutiveUnknowns = 0;
						} else {
							numConsecutiveUnknowns++;
							continue;
						}

						if (level >= 128) {
							continue;
						}
					}

					if (startIp == null || endIp == null) {
						continue;
					}

					IpRangeV4Impl ipRange = new IpRangeV4Impl(description, startIp, endIp,
							true);

					//System.out.println(parseMode + ":" + description + ";" + ipRange.getStartIp());
					ipRange.setAddedToRangeList(true);

					new_ipRanges.add(ipRange);
				}
			} catch (IOException e) {
				Debug.out(e);
			} finally {

				if (reader != null) {
					try {
						reader.close();
					} catch (Throwable e) {
					}
				}
				if (streamReader != null) {
					try {
						streamReader.close();
					} catch (Throwable e) {
					}
				}

				Iterator it = new_ipRanges.iterator();

				while (it.hasNext()) {

					((IpRange) it.next()).checkValid();
				}
			}
		} finally {

			class_mon.exit();
		}
		
		ipFilter.markAsUpToDate();
	}

	private int getP2BFileVersion(InputStream is) {
		try {
			// first 4 are 255
			for (int i = 0; i < 4; i++) {
				int byteRead = is.read();
				if (byteRead != 255) {
					return -1;
				}
			}

			// next 'P2B'
			byte[] MAGIC = new byte[] {
				'P',
				'2',
				'B'
			};
			for (int i = 0; i < MAGIC.length; i++) {
				byte b = MAGIC[i];
				if (b != is.read()) {
					return -1;
				}
			}

			// next: version no
			int p2bVersion = is.read();
			Logger.log(new LogEvent(LOGID, "Log Filter: loading p2b version "
					+ p2bVersion));
			return p2bVersion;
		} catch (IOException e) {
			Debug.out(e);
		}

		return -1;
	}

	protected void 
	loadOtherFilters(
		boolean allowAsyncDownloading,
		boolean loadOldWhileAsyncDownloading) 
	{
		boolean setFileReloadTimer = false;
		
		try{
			class_mon.enter();

			List<IpRangeImpl> new_ipRanges = new ArrayList<>(1024);

			try {
				
				boolean isURL = loadIPv4( allowAsyncDownloading, loadOldWhileAsyncDownloading, new_ipRanges );
				
				if ( !isURL ){
					
					setFileReloadTimer = true;
				}
				
				isURL = loadIPv6( allowAsyncDownloading, loadOldWhileAsyncDownloading, new_ipRanges );
				
				if ( !isURL ){
					
					setFileReloadTimer = true;
				}
				
			} finally {

	
				Iterator<IpRangeImpl> it = new_ipRanges.iterator();

				while (it.hasNext()) {

					it.next().checkValid();
				}

				if ( setFileReloadTimer ){
					
					setFileReloadTimer();
				}
			}
		} finally {

			class_mon.exit();
		}
		
		ipFilter.markAsUpToDate();
	}

	private boolean
	loadIPv4(
		boolean 			allowAsyncDownloading,
		boolean 			loadOldWhileAsyncDownloading,
		List<IpRangeImpl>	new_ipRanges )
	{
		int p2bVersion = -1;
		
		InputStream fin = null;
		BufferedInputStream bin = null;
		boolean isURL = false;


		try{
			//open the file
			String file = COConfigurationManager.getStringParameter(ConfigKeys.IPFilter.SCFG_IP_FILTER_AUTOLOAD_FILE);
			Logger.log(new LogEvent(LOGID, "IP Filter file: " + file));
			File filtersFile = FileUtil.newFile(file);
			if (filtersFile.exists()) {
				isURL = false;
			} else {
				if (!UrlUtils.isURL(file)) {
					return( isURL );
				}
	
				isURL = true;
	
				filtersFile = FileUtil.getUserFile("ipfilter.dl");
				if (filtersFile.exists()) {
					if (allowAsyncDownloading) {
						Logger.log(new LogEvent(LOGID, "Downloading " + file + "  async"));
	
						downloadFiltersAsync(new URL(file), false );
	
						if (!loadOldWhileAsyncDownloading) {
							return( isURL );
						}
					}
				} else {
					// no old dl, download sync now
					Logger.log(new LogEvent(LOGID, "sync Downloading " + file));
					try {
						ResourceDownloader rd = ResourceDownloaderFactoryImpl.getSingleton().create(
								new URL(file));
						fin = rd.download();
						FileUtil.copyFile(fin, filtersFile);
						setNextAutoDownload(true);
					} catch (ResourceDownloaderException e) {
						return( isURL );
					}
				}
			}
	
			fin = FileUtil.newFileInputStream(filtersFile);
			bin = new BufferedInputStream(fin, 16384);
	
			// extract (g)zip'd file and open that
			byte[] headerBytes = new byte[2];
			bin.mark(3);
			bin.read(headerBytes, 0, 2);
			bin.reset();
	
			if (headerBytes[1] == (byte) 0x8b && headerBytes[0] == 0x1f) {
				GZIPInputStream gzip = new GZIPInputStream(bin);
	
				filtersFile = FileUtil.getUserFile("ipfilter.ext");
				FileUtil.copyFile(gzip, filtersFile);
				fin = FileUtil.newFileInputStream(filtersFile);
				bin = new BufferedInputStream(fin, 16384);
			} else if (headerBytes[0] == 0x50 && headerBytes[1] == 0x4b) {
				// We pick the largest file in the zip, but we should someday consider
				// merging all files into zip into one, if there's a case where
				// a zip file contains multiple ip list files.
				long largestSize = 0;
				long largestPos = -1;
				ZipInputStream zip = new ZipInputStream(bin);
	
				ZipEntry zipEntry = zip.getNextEntry();
				int zipPos = 0;
				while (zipEntry != null) {
					zipPos++;
					long size = zipEntry.getSize();
					if (size > largestSize) {
						largestPos = zipPos;
					}
					zipEntry = zip.getNextEntry();
				}
	
				if (largestPos < 0) {
					return( isURL );
				}
				bin.close();
				fin.close();
				fin = FileUtil.newFileInputStream(filtersFile);
				bin = new BufferedInputStream(fin, 16384);
				zip = new ZipInputStream(bin);
				for (int i = 0; i < largestPos; i++) {
					zip.getNextEntry();
				}
	
				filtersFile = FileUtil.getUserFile("ipfilter.ext");
				FileUtil.copyFile(zip, filtersFile);
				fin = FileUtil.newFileInputStream(filtersFile);
				bin = new BufferedInputStream(fin, 16384);
			}
	
			bin.mark(8);
	
			p2bVersion = getP2BFileVersion(bin);
	
			if (p2bVersion < 1 || p2bVersion > 3) {
				bin.reset();
				loadDATFilters(bin);
				return( isURL );
			}
	
			byte[] descBytes = new byte[255];
			byte[] ipBytes = new byte[4];
			String encoding = p2bVersion == 1 ? "ISO-8859-1" : "UTF-8";
	
			if (p2bVersion == 1 || p2bVersion == 2) {
				while (true) {
					String description = readString(bin, descBytes, encoding);
	
					int read = bin.read(ipBytes);
					if (read < 4) {
						break;
					}
					int startIp = ByteFormatter.byteArrayToInt(ipBytes);
					read = bin.read(ipBytes);
					if (read < 4) {
						break;
					}
					int endIp = ByteFormatter.byteArrayToInt(ipBytes);
	
					IpRangeV4Impl ipRange = new IpRangeV4Impl(description, startIp, endIp,
							true);
	
					ipRange.setAddedToRangeList(true);
	
					new_ipRanges.add(ipRange);
				}
			} else { // version 3
				int read = bin.read(ipBytes);
				if (read < 4) {
					return( isURL );
				}
				int numDescs = ByteFormatter.byteArrayToInt(ipBytes);
				String[] descs = new String[numDescs];
				for (int i = 0; i < numDescs; i++) {
					descs[i] = readString(bin, descBytes, encoding);
				}
	
				read = bin.read(ipBytes);
				if (read < 4) {
					return( isURL );
				}
				int numRanges = ByteFormatter.byteArrayToInt(ipBytes);
				for (int i = 0; i < numRanges; i++) {
					read = bin.read(ipBytes);
					if (read < 4) {
						return( isURL );
					}
					int descIdx = ByteFormatter.byteArrayToInt(ipBytes);
	
					read = bin.read(ipBytes);
					if (read < 4) {
						return( isURL );
					}
					int startIp = ByteFormatter.byteArrayToInt(ipBytes);
	
					read = bin.read(ipBytes);
					if (read < 4) {
						return( isURL );
					}
					int endIp = ByteFormatter.byteArrayToInt(ipBytes);
	
					String description = descIdx < descs.length && descIdx >= 0
							? descs[descIdx] : "";
	
					IpRangeV4Impl ipRange = new IpRangeV4Impl(description, startIp, endIp,
							true);
	
					ipRange.setAddedToRangeList(true);
	
					new_ipRanges.add(ipRange);
				}
			}
		} catch (IOException e) {
			Debug.out(e);
		} finally {

			if (bin != null) {
				try {
					bin.close();
				} catch (Throwable e) {
				}
			}
			if (fin != null) {
				try {
					fin.close();
				} catch (Throwable e) {
				}
			}
		}
		
		return( isURL );
	}
	
	private boolean
	loadIPv6(
		boolean 			allowAsyncDownloading,
		boolean 			loadOldWhileAsyncDownloading,
		List<IpRangeImpl>	new_ipRanges )
	{		
		InputStream fin = null;
		BufferedInputStream bin = null;
		boolean isURL = false;


		try{
			//open the file
			String file = COConfigurationManager.getStringParameter(ConfigKeys.IPFilter.SCFG_IP_FILTER_V6_AUTOLOAD_FILE);
			Logger.log(new LogEvent(LOGID, "IPv6 Filter file: " + file));
			File filtersFile = FileUtil.newFile(file);
			if (filtersFile.exists()) {
				isURL = false;
			} else {
				if (!UrlUtils.isURL(file)) {
					return( isURL );
				}
	
				isURL = true;
	
				filtersFile = FileUtil.getUserFile("ipfilterv6.dl");
				if (filtersFile.exists()) {
					if (allowAsyncDownloading) {
						Logger.log(new LogEvent(LOGID, "Downloading " + file + "  async"));
	
						downloadFiltersAsync(new URL(file), false );
	
						if (!loadOldWhileAsyncDownloading) {
							return( isURL );
						}
					}
				} else {
					// no old dl, download sync now
					Logger.log(new LogEvent(LOGID, "sync Downloading " + file));
					try {
						ResourceDownloader rd = ResourceDownloaderFactoryImpl.getSingleton().create(
								new URL(file));
						fin = rd.download();
						FileUtil.copyFile(fin, filtersFile);
						setNextAutoDownload(true);
					} catch (ResourceDownloaderException e) {
						return( isURL );
					}
				}
			}
	
			fin = FileUtil.newFileInputStream(filtersFile);
			bin = new BufferedInputStream(fin, 16384);
	
			// extract (g)zip'd file and open that
			byte[] headerBytes = new byte[2];
			bin.mark(3);
			bin.read(headerBytes, 0, 2);
			bin.reset();
	
			if (headerBytes[1] == (byte) 0x8b && headerBytes[0] == 0x1f) {
				GZIPInputStream gzip = new GZIPInputStream(bin);
	
				filtersFile = FileUtil.getUserFile("ipfilterv6.ext");
				FileUtil.copyFile(gzip, filtersFile);
				fin = FileUtil.newFileInputStream(filtersFile);
				bin = new BufferedInputStream(fin, 16384);
			} else if (headerBytes[0] == 0x50 && headerBytes[1] == 0x4b) {
				// We pick the largest file in the zip, but we should someday consider
				// merging all files into zip into one, if there's a case where
				// a zip file contains multiple ip list files.
				long largestSize = 0;
				long largestPos = -1;
				ZipInputStream zip = new ZipInputStream(bin);
	
				ZipEntry zipEntry = zip.getNextEntry();
				int zipPos = 0;
				while (zipEntry != null) {
					zipPos++;
					long size = zipEntry.getSize();
					if (size > largestSize) {
						largestPos = zipPos;
					}
					zipEntry = zip.getNextEntry();
				}
	
				if (largestPos < 0) {
					return( isURL );
				}
				bin.close();
				fin.close();
				fin = FileUtil.newFileInputStream(filtersFile);
				bin = new BufferedInputStream(fin, 16384);
				zip = new ZipInputStream(bin);
				for (int i = 0; i < largestPos; i++) {
					zip.getNextEntry();
				}
	
				filtersFile = FileUtil.getUserFile("ipfilterv6.ext");
				FileUtil.copyFile(zip, filtersFile);
				fin = FileUtil.newFileInputStream(filtersFile);
				bin = new BufferedInputStream(fin, 16384);
			}
	
			LineNumberReader lnr = new LineNumberReader( new InputStreamReader( bin, Constants.UTF_8 ));
	
			while( true ){
				
				String line = lnr.readLine();
				
				if ( line == null ){
					
					break;
				}
				
				line = line.trim();
				
				if ( line.isEmpty() || line.startsWith( "#" )){
					
					continue;
				}
				
				String[] bits = line.split( ",", 2 );
				
				if ( bits.length != 2 ){
					
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
							"unrecognized line while reading ip filter: " + line));
				}else{
					
					String	address = bits[0].trim();
					String	desc	= bits[1].trim();
			
					IpRangeV6Impl ipRange = new IpRangeV6Impl( desc, address, "", true );
	
					ipRange.setAddedToRangeList(true);
	
					new_ipRanges.add(ipRange);
				}
			}
		} catch (IOException e) {
			Debug.out(e);
		} finally {

			if (bin != null) {
				try {
					bin.close();
				} catch (Throwable e) {
				}
			}
			if (fin != null) {
				try {
					fin.close();
				} catch (Throwable e) {
				}
			}
		}
		
		return( isURL );
	}
	
	
	
	/**
	 *
	 *
	 * @since 3.0.1.5
	 */
	private void setFileReloadTimer() {
		if (timerEventFilterReload instanceof TimerEvent) {
			((TimerEvent) timerEventFilterReload).cancel();
		} else if (timerEventFilterReload instanceof TimerEventPeriodic) {
			((TimerEventPeriodic) timerEventFilterReload).cancel();
		}
		timerEventFilterReload = SimpleTimer.addPeriodicEvent("IP Filter download",
				60000, new TimerEventPerformer() {
				long lastFileModified;
				long lastFileV6Modified;

					@Override
					public void perform(TimerEvent event) {
						event.cancel();

						boolean doReload = false;
						
						{
							String file = COConfigurationManager.getStringParameter(ConfigKeys.IPFilter.SCFG_IP_FILTER_AUTOLOAD_FILE);
							File filtersFile = FileUtil.newFile(file);
							if (filtersFile.exists()) {
						
								long fileModified = filtersFile.lastModified();
		
								if (lastFileModified == 0) {
									lastFileModified = fileModified;
								} else if (lastFileModified != fileModified) {
									doReload = true;
								}
							}
						}
						
						{
							String file = COConfigurationManager.getStringParameter(ConfigKeys.IPFilter.SCFG_IP_FILTER_V6_AUTOLOAD_FILE);
							File filtersFile = FileUtil.newFile(file);
							if (filtersFile.exists()) {
						
								long fileModified = filtersFile.lastModified();
		
								if (lastFileV6Modified == 0) {
									lastFileV6Modified = fileModified;
								} else if (lastFileV6Modified != fileModified) {
									doReload = true;
								}
							}
						}
						
						if ( doReload ){
							try {
								// reload will create a new periodic time
								ipFilter.reload();
							} catch (Exception e) {
							}
						}
					}
				});
	}

	/**
	 * @param url
	 *
	 * @since 3.0.1.5
	 */
	void downloadFiltersAsync(URL url, boolean v6) {
		ResourceDownloader rd = ResourceDownloaderFactoryImpl.getSingleton().create(url);
		// old dl exists, load old one while new one downloads async
		rd.addListener(new ResourceDownloaderAdapter() {
			// @see com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderAdapter#reportPercentComplete(com.biglybt.pif.utils.resourcedownloader.ResourceDownloader, int)
			@Override
			public void reportPercentComplete(ResourceDownloader downloader,
			                                  int percentage) {
			}

			@Override
			public boolean completed(ResourceDownloader downloader, InputStream data) {
				try {
					setNextAutoDownload(true);

					Logger.log(new LogEvent(LOGID, "downloaded..waiting"));
					// since this is a different thread, we can use class_mon as
					// a cheap semaphore to wait until previous load completes
					class_mon.enter();
					Logger.log(new LogEvent(LOGID, "downloaded.. copying"));

					try {
						FileUtil.copyFile(data, FileUtil.getUserFile( v6?"ipfilterv6.dl":"ipfilter.dl"));
						
						AEThread thread = new AEThread("reload ipfilters", true) {
							@Override
							public void runSupport() {
								try {
									UIFunctions uif = UIFunctionsManager.getUIFunctions();
									if (uif != null) {
										uif.setStatusText("reloading.filters");
									}
									ipFilter.reload(false);
									if (uif != null) {
										uif.setStatusText(null);
									}
								} catch (Exception e) {
									Debug.out(e);
								}
							}
						};
						thread.setPriority(Thread.NORM_PRIORITY - 1);
						thread.start();
					} catch (Exception e) {
						Debug.out(e);
					}
				} finally {
					class_mon.exit();
				}

				return true;
			}
		});
		rd.asyncDownload();
	}

	public void setNextAutoDownload(boolean updateLastDownloadedDate) {
		long now = SystemTime.getCurrentTime();
		long lastDL;

		if (updateLastDownloadedDate) {
			COConfigurationManager.setParameter(ConfigKeys.IPFilter.ICFG_IP_FILTER_AUTOLOAD_LAST, now);
			lastDL = now;
		} else {
			lastDL = COConfigurationManager.getLongParameter(ConfigKeys.IPFilter.ICFG_IP_FILTER_AUTOLOAD_LAST);
			if (lastDL > now) {
				lastDL = now;
				COConfigurationManager.setParameter(ConfigKeys.IPFilter.ICFG_IP_FILTER_AUTOLOAD_LAST, now);
			}
		}

		int	reloadPeriod = COConfigurationManager.getIntParameter( ConfigKeys.IPFilter.ICFG_IP_FILTER_AUTOLOAD_DAYS );
		
		if ( reloadPeriod < 1 ){
			reloadPeriod = 1;
		}
		
		long nextDL = lastDL + (86400000L * reloadPeriod);

		if (timerEventFilterReload instanceof TimerEvent) {
			((TimerEvent) timerEventFilterReload).cancel();
		} else if (timerEventFilterReload instanceof TimerEventPeriodic) {
			((TimerEventPeriodic) timerEventFilterReload).cancel();
		}
		timerEventFilterReload = SimpleTimer.addEvent("IP Filter download", nextDL,
				new TimerEventPerformer() {
					@Override
					public void perform(TimerEvent event) {
						String file = COConfigurationManager.getStringParameter( ConfigKeys.IPFilter.SCFG_IP_FILTER_AUTOLOAD_FILE);
						try {
							downloadFiltersAsync(new URL(file), false );
						} catch (Throwable e) {
						}
						
						file = COConfigurationManager.getStringParameter( ConfigKeys.IPFilter.SCFG_IP_FILTER_V6_AUTOLOAD_FILE);
						try {
							downloadFiltersAsync(new URL(file), true );
						} catch (Throwable e) {
						}
					}
				});
	}

	/**
	 * @param bin
	 * @param descBytes
	 * @param encoding
	 * @return
	 *
	 * @since 3.0.1.5
	 */
	private String readString(BufferedInputStream bin, byte[] descBytes,
			String encoding) {
		int pos = 0;
		try {
			while (true) {
				int byteRead = bin.read();
				if (byteRead < 0) {
					break;
				}
				if (pos < descBytes.length) {
					descBytes[pos] = (byte) byteRead;
					pos++;
				}
				if (byteRead == 0) {
					break;
				}
			}
		} catch (IOException e) {
		}

		if (pos > 1) {
			try {
				return new String(descBytes, 0, pos - 1, encoding);
			} catch (UnsupportedEncodingException e) {
			}
		}

		return "";
	}
}
