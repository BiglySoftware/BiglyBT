/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.utils;

import java.util.*;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.FixedHTMLTransfer;
import com.biglybt.ui.swt.FixedURLTransfer;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;

/**
 * While dragging over an widget, we don't get the data that will be dropped.
 * This utility class overcomes this by monitoring drag start events and
 * storing it for retrieval in drop events.
 */
public class DragDropUtils
{
	public static final String DROPDATA_PREFIX_TAG_UID = "TagUID";

	private static Object lastDraggedObject;

	public static DragSource createDragSource(Control control, int style) {
		return new DragSourceMonitored(control, style);
	}

	public static Object getLastDraggedObject() {
		return lastDraggedObject;
	}

	public static List<DownloadManager> getDownloadsFromDropData(Object dropData,
			boolean includeFileDrops) {
		List<DownloadManager> listDMs = new ArrayList<>();
		if (!(dropData instanceof String)) {
			return listDMs;
		}
		String[] split = RegExUtil.PAT_SPLIT_SLASH_N.split((String) dropData);
		if (split.length <= 1) {
			return listDMs;
		}

		String type = split[0];
		if (includeFileDrops) {
			if (!type.startsWith("DownloadManager")
					&& !type.startsWith("DiskManagerFileInfo")) {
				return listDMs;
			}
		} else {
			if (!type.startsWith("DownloadManager")) {
				return listDMs;
			}
		}

		GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
		if (gm == null) {
			return listDMs;
		}

		for (int i = 1; i < split.length; i++) {
			String hash = split[i];

			int sep = hash.indexOf(";"); // for files

			if (sep != -1) {
				hash = hash.substring(0, sep);
			}

			try {
				DownloadManager dm = gm.getDownloadManager(
						new HashWrapper(Base32.decode(hash)));

				if (dm != null) {

					listDMs.add(dm);

				}
			} catch (Throwable ignore) {

			}
		}

		return listDMs;
	}

	public static List<Tag> getTagsFromDroppedData(Object dropData) {
		List<Tag> listTags = new ArrayList<>();
		if (!(dropData instanceof String)) {
			return listTags;
		}
		String[] split = RegExUtil.PAT_SPLIT_SLASH_N.split((String) dropData);
		if (split.length <= 1) {
			return listTags;
		}

		String type = split[0];
		if (!type.equals(DROPDATA_PREFIX_TAG_UID)) {
			return listTags;
		}

		TagManager tagManager = TagManagerFactory.getTagManager();
		for (int i = 1; i < split.length; i++) {
			String tagUID = split[i];
			try {
				long l = Long.parseLong(tagUID);
				Tag tag = tagManager.lookupTagByUID(l);
				if (tag != null) {
					listTags.add(tag);
				}
			} catch (Throwable ignore) {
			}
		}

		return listTags;
	}

	private static void createDropTarget(Composite composite,
			DropTargetListener dropTargetListener) {

		Transfer[] transferList = new Transfer[] {
			FixedHTMLTransfer.getInstance(),
			FixedURLTransfer.getInstance(),
			FileTransfer.getInstance(),
			TextTransfer.getInstance()
		};

		final DropTarget dropTarget = new DropTarget(composite, DND.DROP_DEFAULT
				| DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK | DND.DROP_TARGET_MOVE);
		dropTarget.setTransfer(transferList);
		dropTarget.addDropListener(dropTargetListener);
		// Note: DropTarget will dipose when the parent it's on diposes

		// On Windows, dropping on children moves up to parent
		// On OSX, each child needs it's own drop.
		if (Constants.isWindows) {
			return;
		}

		Control[] children = composite.getChildren();
		for (Control control : children) {
			if (control.isDisposed()) {
				continue;
			}
			if (control instanceof Composite) {
				createDropTarget((Composite) control, dropTargetListener);
			} else {
				DropTarget dropTarget2 = new DropTarget(control,
						DND.DROP_DEFAULT | DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK
								| DND.DROP_TARGET_MOVE);
				dropTarget2.setTransfer(transferList);
				dropTarget2.addDropListener(dropTargetListener);
			}
		}
	}

	public static void createDropTarget(Composite composite,
			boolean bAllowShareAdd, Text url) {
		createDropTarget(composite, new URLDropTarget(url, bAllowShareAdd));
	}

	public static void createDropTarget(Composite composite,
			boolean bAllowShareAdd, StyledText url) {
		createDropTarget(composite, new URLDropTarget(url, bAllowShareAdd));
	}

	
	public static void createTorrentDropTarget(Composite composite,
			boolean bAllowShareAdd) {
		try {
			createDropTarget(composite, bAllowShareAdd, (Text)null);
		} catch (Exception e) {
			Debug.out(e);
		}
	}

	/**
	 * @param composite the control (usually a Shell) to add the DropTarget
	 * @param url the Text control where to set the link text
	 *
	 * @author Rene Leonhardt
	 */
	public static void createURLDropTarget(Composite composite, Text url) {
		try {
			createDropTarget(composite, false, url);
		} catch (Exception e) {
			Debug.out(e);
		}
	}

	public static void createURLDropTarget(Composite composite, StyledText url) {
		try {
			createDropTarget(composite, false, url);
		} catch (Exception e) {
			Debug.out(e);
		}
	}
	
	private static class DragSourceListenerDelegate
		implements DragSourceListener
	{
		final DragSourceListener delegate;

		public DragSourceListenerDelegate(DragSourceListener delegate) {
			this.delegate = delegate;
		}

		@Override
		public void dragStart(DragSourceEvent event) {
			delegate.dragStart(event);
			// ensure we get data
			dragSetData(event);
		}

		@Override
		public void dragSetData(DragSourceEvent event) {
			delegate.dragSetData(event);
			lastDraggedObject = event.data;
		}

		@Override
		public void dragFinished(DragSourceEvent event) {
			delegate.dragFinished(event);
			lastDraggedObject = null;
		}
	}

	private static class DragSourceMonitored
		extends DragSource
	{
		final Map<DragSourceListener, DragSourceListenerDelegate> mapToDelegates = new HashMap<>();

		@Override
		protected void checkSubclass() {
		}

		public DragSourceMonitored(Control control, int style) {
			super(control, style);
		}

		@Override
		public void addDragListener(DragSourceListener listener) {
			if (mapToDelegates.containsKey(listener)) {
				return;
			}
			DragSourceListenerDelegate delegate = new DragSourceListenerDelegate(
					listener);
			mapToDelegates.put(listener, delegate);

			super.addDragListener(delegate);
		}

		@Override
		public void removeDragListener(DragSourceListener listener) {
			DragSourceListenerDelegate remove = mapToDelegates.remove(listener);
			super.removeDragListener(remove == null ? listener : remove);
		}

		@Override
		public void dispose() {
			mapToDelegates.clear();
			super.dispose();
		}
	}

	private static class URLDropTarget
		extends DropTargetAdapter
	{
		private final Control url;

		private final boolean bAllowShareAdd;

		public URLDropTarget(Text url, boolean bAllowShareAdd) {
			this.url = url;
			this.bAllowShareAdd = bAllowShareAdd;
		}
		
		public URLDropTarget(StyledText url, boolean bAllowShareAdd) {
			this.url = url;
			this.bAllowShareAdd = bAllowShareAdd;
		}

		private static boolean isOurDrag(Object dropData) {
			if (!(dropData instanceof String)) {
				return false;
			}

			String dropText = (String) dropData;
			return dropText.startsWith("DownloadManager\n")
					|| dropText.startsWith("DiskManagerFileInfo\n")
					|| dropText.startsWith(DROPDATA_PREFIX_TAG_UID);
		}

		@Override
		public void dropAccept(DropTargetEvent event) {
			event.currentDataType = FixedURLTransfer.pickBestType(event.dataTypes,
					event.currentDataType);
		}

		@Override
		public void dragEnter(DropTargetEvent event) {
			if (isOurDrag(event.data == null ? getLastDraggedObject() : event.data)) {
				event.detail = DND.DROP_NONE;
			}
		}

		@Override
		public void dragOver(DropTargetEvent event) {
			if (isOurDrag(event.data == null ? getLastDraggedObject() : event.data)) {
				event.detail = DND.DROP_NONE;
				return;
			}
			// skip setting detail if user is forcing a drop type (ex. via the
			// ctrl key), providing that the operation is valid
			if (event.detail != DND.DROP_DEFAULT
					&& ((event.operations & event.detail) > 0))
				return;

			if ((event.operations & DND.DROP_LINK) > 0)
				event.detail = DND.DROP_LINK;
			else if ((event.operations & DND.DROP_DEFAULT) > 0)
				event.detail = DND.DROP_DEFAULT;
			else if ((event.operations & DND.DROP_COPY) > 0)
				event.detail = DND.DROP_COPY;
		}

		@Override
		public void drop(DropTargetEvent event) {
			if (url == null || url.isDisposed()) {
				TorrentOpener.openDroppedTorrents(event, bAllowShareAdd);
			} else {
				String sURL = null;
				
				if (event.data instanceof FixedURLTransfer.URLType) {
					sURL = ((FixedURLTransfer.URLType) event.data).linkURL;
				} else if (event.data instanceof String) {
					sURL = UrlUtils.parseTextForURL((String) event.data, true);
				}
				
				if (sURL != null) {
					if ( url instanceof Text ){
						((Text)url).setText(sURL);
					}else{
						((StyledText)url).setText(sURL);
					}
				}
			}
		}
	}
}
