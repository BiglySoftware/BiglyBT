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

package com.biglybt.ui.swt.widgets;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.category.Category;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.utils.DragDropUtils;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;

public class TagCanvas
	extends Canvas
	implements PaintListener, Listener, DropTargetListener, DragSourceListener
{
	public interface TagButtonTrigger
	{
		void tagButtonTriggered(TagPainter painter, int stateMask, boolean longPress);

		Boolean tagSelectedOverride(Tag tag);
	}

	private boolean	initialised;
	
	private final DropTarget dropTarget;
	private final DragSource dragSource;
	
	private final TagPainter painter; 

	private boolean mouseDown = false;
	private boolean dndListenersAdded;
	
	private TimerEvent timerEvent;

	/** 
	 * Creates a Tag Canvas.<br/>  
	 * Auto Tags will be disabled.<br/>
	 * When Tag has no taggables, it will be disabled.
	 */
	public TagCanvas(Composite parent, Tag tag) {
		this(parent, tag, true, false);
	}

	public TagCanvas(Composite parent, Tag tag, boolean disableAuto,
			boolean enableWhenNoTaggables) {

		super(parent, SWT.DOUBLE_BUFFERED);
		
		try{

			painter = new TagPainter(tag, this);
			painter.enableWhenNoTaggables = enableWhenNoTaggables;
	
			boolean[] auto = tag.isTagAuto();
			
			if (auto.length >= 2 && auto[0] && auto[1]) {
				painter.font = FontUtils.getFontWithStyle(getFont(), SWT.ITALIC, 1.0f);
				setFont(painter.font);
			}
	
			painter.updateColors(getDisplay(), getForeground(), getBackground());
	
			painter.setDisableAuto(disableAuto);
	
			addListener(SWT.MouseDown, this);
			addListener(SWT.MouseUp, this);
			addListener(SWT.KeyDown, this);
			addListener(SWT.FocusOut, this);
			addListener(SWT.FocusIn, this);
			addListener(SWT.Traverse, this);
			addListener(SWT.MouseHover, this);
			addListener(SWT.MouseExit, this);
	
			dropTarget = new DropTarget(this,
					DND.DROP_DEFAULT | DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK);
			Transfer[] types = new Transfer[] {
				TextTransfer.getInstance()
			};
			dropTarget.setTransfer(types);
			dropTarget.addDropListener(this);
	
			dragSource = DragDropUtils.createDragSource(this, DND.DROP_COPY | DND.DROP_MOVE);
			dragSource.setTransfer(TextTransfer.getInstance());
			dragSource.addDragListener(this);
	
			dndListenersAdded = true;
			
			painter.updateImage();
			addPaintListener(this);
		}finally{
			initialised = true;
		}
	}

	protected boolean
	isInitialised()
	{
		return( initialised );
	}
	
	@Override
	public Point computeSize(int wHint, int hHint, boolean changed) {
		Point point = painter.computeSize(getDisplay(), getFont());
		if (point == null) {
			return super.computeSize(wHint, hHint, changed);
		}
		return point;
	}

	@Override
	public void handleEvent(Event e) {
		switch (e.type) {
			case SWT.MouseDown: {
				if (!isEnabled() || e.button != 1) {
					return;
				}
				
					// Prevent DND drop icon changes from cheesing up long presses
					// Leave DND drag as is as gets a bit more complicated to disable and
					// re-enable correctly 
				
				dropTarget.removeDropListener(this);
			
				dndListenersAdded = false;
				
				if (timerEvent == null) {
					timerEvent = SimpleTimer.addEvent("MouseHold",
							SystemTime.getOffsetTime(500), te -> {
								timerEvent = null;
								if (!mouseDown) {
									return;
								}

								// held
								if (painter.trigger == null) {
									mouseDown = false;
									return;
								}

								Utils.execSWTThread(() -> {
									if (!mouseDown) {
										return;
									}

									if (e.display.getCursorControl() != TagCanvas.this) {
										return;
									}

									mouseDown = false;

									painter.trigger.tagButtonTriggered(painter, e.stateMask, true);
								});
							});
				}

				mouseDown = true;
				break;
			}
			case SWT.MouseUp: {
				if (!isEnabled() || e.button != 1 || !mouseDown) {
					return;
				}
				mouseDown = false;
				
				if (timerEvent != null) {
					timerEvent.cancel();
					timerEvent = null;
				}

				if (!getClientArea().contains(e.x, e.y)) {
					return;
				}

				if (painter.trigger != null) {
					painter.trigger.tagButtonTriggered(painter, e.stateMask, false);
				}

				break;
			}
			case SWT.MouseHover: {
				Utils.setTT(this, TagUtils.getTagTooltip(painter.tag));
				break;
			}
			case SWT.MouseExit: {
				setToolTipText(null);
				if ( !dndListenersAdded ){
					dropTarget.addDropListener(this);
					dndListenersAdded = true;

				}
			}
			case SWT.FocusOut:
			case SWT.FocusIn: {
				redraw();
				break;
			}
			case SWT.Traverse: {
				if (!isEnabled()) {
					return;
				}
				switch (e.detail) {
					case SWT.TRAVERSE_PAGE_NEXT:
					case SWT.TRAVERSE_PAGE_PREVIOUS:
						e.doit = false;
						return;
					case SWT.TRAVERSE_RETURN:
						if (painter.trigger != null) {
							painter.trigger.tagButtonTriggered(painter, e.stateMask, false);
						}
				}
				e.doit = true;
			}
			case SWT.KeyDown: {
				if (e.keyCode == SWT.F2 && (e.stateMask & SWT.MODIFIER_MASK) == 0) {
					if (!painter.tag.getTagType().isTagTypeAuto()) {
						TagUIUtils.openRenameTagDialog(painter.tag);
						e.doit = false;
					}
				} else if (e.keyCode == SWT.SPACE) {
					if (painter.trigger != null) {
						painter.trigger.tagButtonTriggered(painter, e.stateMask, false);
					}
				}
			}
		}
	}

	@Override
	public void paintControl(PaintEvent e) {
		painter.paintControl(e.gc, 0, 0, getClientArea(), getSize(),
				isFocusControl());
	}

	@Override
	public void dispose() {
		super.dispose();
		painter.dispose();
		dropTarget.dispose();
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void setEnabled(boolean enabled) {
		painter.setEnabled(enabled);
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public boolean getEnabled() {
		return painter.isEnabled;
	}

	@Override
	public boolean isEnabled() {
		return painter.isEnabled && super.isEnabled();
	}

	public void setTrigger(TagButtonTrigger trigger) {
		painter.trigger = trigger;
	}

	private static List<DownloadManager> handleDropTargetEvent(
			DropTargetEvent e) {
		Object data = e.data == null ? DragDropUtils.getLastDraggedObject()
				: e.data;
		List<DownloadManager> dms = DragDropUtils.getDownloadsFromDropData(data,
				false);

		if (dms.isEmpty()) {
			e.detail = DND.DROP_NONE;
			return dms;
		}
		boolean doAdd = false;

		Control dropControl = ((DropTarget) e.widget).getControl();
		if (!(dropControl instanceof TagCanvas)) {
			e.detail = DND.DROP_NONE;
			return dms;
		}

		Tag tag = ((TagCanvas) dropControl).getTagPainter().getTag();
		for (DownloadManager dm : dms) {
			if (!tag.hasTaggable(dm)) {
				doAdd = true;
				break;
			}
		}

		boolean[] auto = tag.isTagAuto();
		if (auto.length < 2 || (doAdd && auto[0])
				|| (!doAdd && auto[0] && auto[1])) {
			e.detail = DND.DROP_NONE;
			return dms;
		}

		e.detail = doAdd ? DND.DROP_COPY : DND.DROP_MOVE;
		return dms;
	}

	@Override
	public void dragEnter(DropTargetEvent event) {
		handleDropTargetEvent(event);
	}

	@Override
	public void dragLeave(DropTargetEvent event) {

	}

	@Override
	public void dragOperationChanged(DropTargetEvent event) {

	}

	@Override
	public void dropAccept(DropTargetEvent event) {
		handleDropTargetEvent(event);
	}

	@Override
	public void dragOver(DropTargetEvent e) {
		handleDropTargetEvent(e);
	}

	@Override
	public void drop(DropTargetEvent e) {
		List<DownloadManager> dms = handleDropTargetEvent(e);

		if (dms.isEmpty()) {
			return;
		}

		Control dropControl = ((DropTarget) e.widget).getControl();
		if (!(dropControl instanceof TagCanvas)) {
			return;
		}

		Tag tag = ((TagCanvas) dropControl).getTagPainter().getTag();

		if (tag instanceof Category) {
			TorrentUtil.assignToCategory(dms.toArray(), (Category) tag);
			return;
		}

		boolean doAdd = e.detail == DND.DROP_COPY;

		Utils.getOffOfSWTThread(() -> {
			// handleDropTargetEvent set e.detail based on doAdd and checked if tag
			// can be assigned/unassigned
			for (DownloadManager dm : dms) {
				if (doAdd) {
					tag.addTaggable(dm);
				} else {
					tag.removeTaggable(dm);
				}
			}
		});
	}

	@Override
	public void dragStart(DragSourceEvent event) {
	}

	@Override
	public void dragSetData(DragSourceEvent event) {
		event.data = DragDropUtils.DROPDATA_PREFIX_TAG_UID + "\n" + painter.tag.getTagUID();
	}

	@Override
	public void dragFinished(DragSourceEvent event) {

	}

	public TagPainter getTagPainter() {
		return painter;
	}
}
