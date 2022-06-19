/*
 * Created on Jun 1, 2008
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

package com.biglybt.util;

import java.util.*;

import com.biglybt.activities.ActivitiesEntry;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.devices.TranscodeFile;
import com.biglybt.core.devices.TranscodeJob;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.impl.DiskManagerImpl;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagDownload;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.selectedcontent.DownloadUrlInfo;
import com.biglybt.ui.selectedcontent.ISelectedContent;

import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.torrent.Torrent;

/**
 * @author TuxPaper
 * @created Jun 1, 2008
 *
 */
public class DataSourceUtils
{
	public static com.biglybt.core.disk.DiskManagerFileInfo getFileInfo(
			Object ds) {
		try {
			if (ds instanceof DiskManagerFileInfo) {
				return PluginCoreUtils.unwrap((DiskManagerFileInfo) ds);
			} else if (ds instanceof com.biglybt.core.disk.DiskManagerFileInfo) {
				return (com.biglybt.core.disk.DiskManagerFileInfo) ds;
			} else if ((ds instanceof ISelectedContent)
					&& ((ISelectedContent) ds).getFileIndex() >= 0) {
				ISelectedContent sc = (ISelectedContent) ds;
				int idx = sc.getFileIndex();
				DownloadManager dm = sc.getDownloadManager();
				return dm.getDiskManagerFileInfoSet().getFiles()[idx];
			} else if (ds instanceof TranscodeJob) {
				TranscodeJob tj = (TranscodeJob) ds;
				try {
					return PluginCoreUtils.unwrap(tj.getFile());
				} catch (DownloadException e) {
				}
			} else if (ds instanceof TranscodeFile) {
				TranscodeFile tf = (TranscodeFile) ds;
				try {
					DiskManagerFileInfo file = tf.getSourceFile();
					return PluginCoreUtils.unwrap(file);
				} catch (DownloadException e) {
				}
			}

		} catch (Exception e) {
			Debug.printStackTrace(e);
		}
		return null;
	}

	public static DownloadManager getDM(Object ds) {
		try {
			if (ds instanceof DownloadManager) {
				return (DownloadManager) ds;
			} else if (ds instanceof ActivitiesEntry) {
				ActivitiesEntry entry = (ActivitiesEntry) ds;
				DownloadManager dm = entry.getDownloadManger();
				if (dm == null) {
					String assetHash = entry.getAssetHash();
					if (assetHash != null && CoreFactory.isCoreRunning()) {
						GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
						dm = gm.getDownloadManager(new HashWrapper(Base32.decode(assetHash)));
						entry.setDownloadManager(dm);
					}
				}
				return dm;
			} else if ((ds instanceof TOTorrent) && CoreFactory.isCoreRunning()) {
				GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
				return gm.getDownloadManager((TOTorrent) ds);
			} else if (ds instanceof ISelectedContent) {
				return getDM(((ISelectedContent)ds).getDownloadManager());
			} else 	if (ds instanceof TranscodeJob) {
				TranscodeJob tj = (TranscodeJob) ds;
				try {
					DiskManagerFileInfo file = tj.getFile();
					if (file != null) {
						Download download = tj.getFile().getDownload();
						if (download != null) {
							return PluginCoreUtils.unwrap(download);
						}
					}
				} catch (DownloadException e) {
				}
			} else if (ds instanceof TranscodeFile) {
				TranscodeFile tf = (TranscodeFile) ds;
				try {
					DiskManagerFileInfo file = tf.getSourceFile();
					if (file != null) {
						Download download = file.getDownload();
						if (download != null) {
							return PluginCoreUtils.unwrap(download);
						}
					}
				} catch (DownloadException e) {
				}
			} else if (ds instanceof Download) {
				return PluginCoreUtils.unwrap((Download) ds);
			} else if (ds instanceof byte[]) {
				byte[] hash = (byte[]) ds;
  			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
  			DownloadManager dm = gm.getDownloadManager(new HashWrapper(hash));
  			if (dm != null) {
  				return dm;
  			}
			} else if (ds instanceof Object[]) {
					Object[] o = (Object[]) ds;
					return o.length == 0 ? null : getDM(o[0]);
			}	else if ((ds instanceof String)  && CoreFactory.isCoreRunning()) {
				String hash = (String) ds;
				try {
	  			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
	  			DownloadManager dm = gm.getDownloadManager(new HashWrapper(Base32.decode(hash)));
	  			if (dm != null) {
	  				return dm;
	  			}
				} catch (Exception e) {
					// ignore
				}
			}else if (ds instanceof PEPiece){
				DiskManager diskManager = ((PEPiece) ds).getDMPiece().getManager();
				if (diskManager instanceof DiskManagerImpl) {
					DiskManagerImpl dmi = (DiskManagerImpl) diskManager;
					return dmi.getDownloadManager();
				}
			}

			com.biglybt.core.disk.DiskManagerFileInfo fileInfo = getFileInfo(ds);
			if (fileInfo != null) {
				return fileInfo.getDownloadManager();
			}


		} catch (Exception e) {
			Debug.printStackTrace(e);
		}
		return null;
	}
	
	public static DownloadManager[] getDMs(Object ds) {
		Object[] dsArray = (ds instanceof Object[]) ? (Object[]) ds : new Object[] {
			ds
		};
		LinkedHashSet<DownloadManager> managers = new LinkedHashSet<>();
		for (Object o : dsArray) {
			if (o instanceof TagDownload) {
				managers.addAll(((TagDownload) o).getTaggedDownloads());
				continue;
			}
			DownloadManager dm = getDM(o);
			if (dm != null) {
				managers.add(dm);
			}
		}
		return managers.toArray(new DownloadManager[0]);
	}

	public static PEPiece[] getPieces(Object ds) {
		Object[] dsArray = (ds instanceof Object[]) ? (Object[]) ds : new Object[] {
			ds
		};
		List<PEPiece> list = new ArrayList<>();
		for (Object o : dsArray) {
			if (o instanceof PEPiece) {
				list.add((PEPiece) o);
			}
		}
		return list.toArray(new PEPiece[0]);
	}


	public static TOTorrent getTorrent(Object ds) {
		if (ds instanceof TOTorrent) {
			return (TOTorrent) ds;
		}

		if (ds instanceof DownloadManager) {
			TOTorrent torrent = ((DownloadManager) ds).getTorrent();
			if (torrent != null) {
				return torrent;
			}
		}
		if (ds instanceof ActivitiesEntry) {
			TOTorrent torrent = ((ActivitiesEntry) ds).getTorrent();
			if (torrent == null) {
				// getDM will check hash as well
				DownloadManager dm = getDM(ds);
				if (dm != null) {
					torrent = dm.getTorrent();
				}
			}
			return torrent;
		}

		if (ds instanceof TranscodeFile) {
			TranscodeFile tf = (TranscodeFile) ds;
			try {
				DiskManagerFileInfo file = tf.getSourceFile();
				if (file != null) {
					Download download = file.getDownload();
					if (download != null) {
						Torrent torrent = download.getTorrent();
						if (torrent != null) {
							return PluginCoreUtils.unwrap(torrent);
						}
					}
				}
			} catch (Throwable e) {
			}
		}

		if (ds instanceof TranscodeJob) {
			TranscodeJob tj = (TranscodeJob) ds;
			try {
				DiskManagerFileInfo file = tj.getFile();
				if (file != null) {
					Download download = tj.getFile().getDownload();

					if (download != null) {
						Torrent torrent = download.getTorrent();
						if (torrent != null) {
							return PluginCoreUtils.unwrap(torrent);
						}
					}
				}
			} catch (DownloadException e) {
			}
		}

		if (ds instanceof ISelectedContent) {
			return ((ISelectedContent)ds).getTorrent();
		}

		if (ds instanceof String) {
			String hash = (String) ds;
			try {
  			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
  			DownloadManager dm = gm.getDownloadManager(new HashWrapper(Base32.decode(hash)));
  			if (dm != null) {
  				return dm.getTorrent();
  			}
			} catch (Exception e) {
				// ignore
			}
		}

		DownloadManager dm = getDM(ds);
		if (dm != null) {
			return dm.getTorrent();
		}

		return null;
	}

	public static String getHash(Object ds) {
		try {
			if (ds instanceof ActivitiesEntry) {
				ActivitiesEntry entry = (ActivitiesEntry) ds;
				return entry.getAssetHash();
			} else if (ds instanceof ISelectedContent) {
				return ((ISelectedContent)ds).getHash();
			} else if (ds instanceof byte[]) {
				return Base32.encode((byte[]) ds);
			} else if (ds instanceof String) {
				// XXX Check validility
				return (String) ds;
			}

			TOTorrent torrent = getTorrent(ds);
			if (torrent != null) {
				return torrent.getHashWrapper().toBase32String();
			}
		} catch (Exception e) {
			Debug.printStackTrace(e);
		}
		return null;
	}

	/**
	 * @param ds
	 *
	 * @since 3.1.1.1
	 */
	public static DownloadUrlInfo getDownloadInfo(Object ds) {
		if (ds instanceof ISelectedContent) {
			return ((ISelectedContent)ds).getDownloadInfo();
		}
		return null;
	}
	
	public static Tag getTag(Object ds) {
		if (ds instanceof Tag) {
			return (Tag) ds;
		}
		if (ds instanceof Number) {
			long tag_uid = ((Number) ds).longValue();
			return TagManagerFactory.getTagManager().lookupTagByUID( tag_uid );
		}
		if (ds instanceof Object[]) {
			Object[] array = (Object[]) ds;
			for (Object o : array) {
				Tag tag = getTag(o);
				if (tag != null) {
					return tag;
				}
			}
		}
		return null;
	}
	
	public static Tag[] getTags(Object ds) {
		if (ds instanceof Object[]) {
			List<Tag> list = new ArrayList<>();
			Object[] dsMulti = (Object[]) ds;
			for (Object dsOne : dsMulti) {
				Tag tag = getTag(dsOne);
				if (tag != null) {
					list.add(tag);
				}
			}
			return list.toArray(new Tag[0]);
		}
		Tag tag = getTag(ds);
		return tag == null ? new Tag[0] : new Tag[] {
			tag
		};
	}

	public static boolean areSame(Object ds0, Object ds1) {
		boolean isArray0 = ds0 instanceof Object[];
		boolean isArray1 = ds1 instanceof Object[];
		if (isArray0 != isArray1) {
			int len0 = isArray0 ? ((Object[])ds0).length : ds0 == null ? 0 : 1;
			int len1 = isArray1 ? ((Object[])ds1).length : ds1 == null ? 0 : 1;
			if (len0 == 1 && len1 == 1) {
				Object oneDS0 = isArray0 ? ((Object[])ds0)[0] : ds0;
				Object oneDS1 = isArray1 ? ((Object[])ds1)[0] : ds1;
				return oneDS0 == oneDS1;
			}
			return len0 == 0 && len1 == 0;
		}
		if (isArray0) {
			return Arrays.equals((Object[]) ds0, (Object[]) ds1);
		}
		return ds0 == ds1;
	}

	public static String toDebugString(Object data) {
		return data instanceof Object[] ? Arrays.toString((Object[]) data)
			: "" + data;
	}

	public static boolean
	isPluginTypeCompatible(
		Class	pluginDSClass,
		Class	DSClass )
	{
		if ( pluginDSClass.equals( DSClass )){
			
			return( true );
		}
		if ( pluginDSClass == Download.class ){
			if ( DSClass == DownloadTypeComplete.class || DSClass == DownloadTypeIncomplete.class ){
				return( true );
			}
		}
		return( false );
	}
}
