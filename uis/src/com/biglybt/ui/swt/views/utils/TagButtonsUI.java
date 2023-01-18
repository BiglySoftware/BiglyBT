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

package com.biglybt.ui.swt.views.utils;

import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.utils.DragDropUtils;
import com.biglybt.ui.swt.widgets.TagCanvas;
import com.biglybt.ui.swt.widgets.TagCanvas.TagButtonTrigger;
import com.biglybt.ui.swt.widgets.TagPainter;

/**
 * @author TuxPaper
 * @created Nov 17, 2015
 *
 */
public class TagButtonsUI
{
	// tagWidgets is only modified in SWT Thread, so concurrent issues should be non-existant
	private final List<TagCanvas> tagWidgets = new ArrayList<>();

	private Composite cMainComposite;

	private boolean enableWhenNoTaggables;

	private boolean disableAuto = true;

	private int layoutStyle;

	public TagButtonsUI() {
		layoutStyle = COConfigurationManager.getIntParameter("TagButtons.style",
				SWT.HORIZONTAL);
	}

	public Composite buildTagGroup(List<Tag> tags, Composite parent,
			boolean allowContextMenu, TagButtonTrigger trigger) {

		cMainComposite = new Composite(parent, SWT.NONE);

		RowLayout mainLayout = Utils.getSimpleRowLayout(false);
		mainLayout.type = SWT.VERTICAL;
		cMainComposite.setLayout(mainLayout);

		tagWidgets.clear();

		Listener menuDetectListener = allowContextMenu ? event -> {
			final TagCanvas tagCanvas = (TagCanvas) event.widget;
			Menu menu = new Menu(tagCanvas);
			tagCanvas.setMenu(menu);

			MenuBuildUtils.addMaintenanceListenerForMenu(menu,
					(menu1, menuEvent) -> TagUIUtils.createSideBarMenuItems(menu1,
							tagCanvas.getTagPainter().getTag()));
		} : null;

		tags = TagUtils.sortTags(tags);
		Composite g = null;
		String group = null;
		for (Tag tag : tags) {
			String newGroup = tag.getGroup();
			if (g == null || (group != null && !group.equals(newGroup))
					|| (group == null && newGroup != null)) {
				group = newGroup;

				g = group == null ? new Composite(cMainComposite, SWT.DOUBLE_BUFFERED)
						: Utils.createSkinnedGroup(cMainComposite, SWT.DOUBLE_BUFFERED);
				if (group != null) {
					Group groupControl = (Group) g;
					groupControl.setText(group);
					Menu menu = new Menu(g);
					g.setMenu(menu);
					MenuBuildUtils.addMaintenanceListenerForMenu(menu,
							(root_menu, menuEvent) -> {
								TagUIUtils.createSideBarMenuItems(root_menu,
										tag.getGroupContainer());
							});
					// Limit menu to top area (label) of group, otherwise the menu
					// shows when right clicking next to a tag, which is confusing
					g.addMenuDetectListener(e -> {
						Group thisGroup = (Group) e.widget;
						Point point = thisGroup.toControl(e.x, e.y);
						Rectangle clientArea = thisGroup.getClientArea();
						if (point.y > clientArea.y) {
							e.doit = false;
						}
					});

					DropTarget dropTarget = new DropTarget(groupControl, DND.DROP_MOVE);
					dropTarget.setTransfer(TextTransfer.getInstance());
					dropTarget.addDropListener(new DropTargetAdapter() {
						@Override
						public void dragEnter(DropTargetEvent event) {
							Object data = event.data == null ? DragDropUtils.getLastDraggedObject() : event.data;
							List<Tag> droppedTags = DragDropUtils.getTagsFromDroppedData(data);
							if (droppedTags.isEmpty()) {
								event.detail = DND.DROP_NONE;
							}
						}

						@Override
						public void drop(DropTargetEvent event) {
							Object data = event.data == null ? DragDropUtils.getLastDraggedObject() : event.data;
							List<Tag> droppedTags = DragDropUtils.getTagsFromDroppedData(data);
							if (droppedTags.isEmpty()) {
								event.detail = DND.DROP_NONE;
								return;
							}

							for (Tag droppedTag : droppedTags) {
								droppedTag.setGroup(groupControl.getText());
							}
						}
					});
				}
				RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
				rowLayout.pack = true;
				rowLayout.spacing = 5;
				g.setLayout(rowLayout);
			}

			TagCanvas p = new TagCanvas(g, tag, disableAuto, enableWhenNoTaggables);
			p.setTrigger(trigger);
			GridLayout layout = new GridLayout(1, false);
			layout.marginHeight = 3;
			if (Constants.isWindows) {
				layout.marginWidth = 0;
				layout.marginLeft = 4;
				layout.marginRight = 7;
				layout.marginTop = 1;
			} else {
				layout.marginWidth = 0;
				layout.marginLeft = 3;
				layout.marginRight = 11;
			}
			p.setLayout(layout);

			if (allowContextMenu) {
				p.addListener(SWT.MenuDetect, menuDetectListener);
			}

			p.addDisposeListener(e -> tagWidgets.remove((TagCanvas) e.widget));

			tagWidgets.add(p);
		}

		setLayoutStyle(layoutStyle);

		return cMainComposite;
	}

	public void setSelectedTags(List<Tag> tags) {

		Set<Tag> tag_set = new HashSet<>(tags);

		for (TagCanvas widget : tagWidgets) {
			TagPainter painter = widget.getTagPainter();

			Tag tag = painter.getTag();
			if (tag == null) {
				continue;
			}

			boolean select = tag_set.contains(tag);

			painter.setSelected(select);
		}
	}

	public List<Tag> getSelectedTags() {
		List<Tag> result = new ArrayList<>();

		if (tagWidgets.isEmpty()) {
			return result;
		}

		for (TagCanvas tagCanvas : tagWidgets) {
			TagPainter painter = tagCanvas.getTagPainter();
			if (painter.isSelected()) {
				result.add(painter.getTag());
			}
		}

		return (result);
	}

	public boolean updateFields(List<Taggable> taggables) {
		if (cMainComposite.isDisposed()) {
			return false;
		}

		for (TagCanvas tagWidget : new ArrayList<>( tagWidgets )) {
			tagWidget.getTagPainter().updateState(taggables);
		}

		return false;
	}

	public void setDisableAuto(boolean b) {
		disableAuto = b;
		Utils.execSWTThread(() -> {
			for (TagCanvas tagWidget : tagWidgets) {
				tagWidget.getTagPainter().setDisableAuto(disableAuto);
			}
		});
	}

	public void setEnableWhenNoTaggables(boolean enableWhenNoTaggables) {
		this.enableWhenNoTaggables = enableWhenNoTaggables;
		Utils.execSWTThread(() -> {
			for (TagCanvas widget : tagWidgets) {
				widget.getTagPainter().setEnableWhenNoTaggables(enableWhenNoTaggables);
			}
		});
	}

	public void setLayoutStyle(int layoutStyle) {
		this.layoutStyle = layoutStyle;
		COConfigurationManager.setParameter("TagButtons.style", layoutStyle);

		if (cMainComposite == null || cMainComposite.isDisposed()) {
			return;
		}
		Control[] children = cMainComposite.getChildren();

		int style = this.layoutStyle & (SWT.HORIZONTAL | SWT.VERTICAL);
		boolean compact = style == SWT.VERTICAL || (layoutStyle & SWT.FILL) > 0;

		cMainComposite.setLayoutDeferred(true);
		if (compact) {
			RowLayout mainLayout = Utils.getSimpleRowLayout(false);
			mainLayout.type = SWT.HORIZONTAL;
			mainLayout.wrap = true;
			cMainComposite.setLayout(mainLayout);

			for (Control child : children) {
				child.setLayoutData(new RowData());
				if (child instanceof Composite) {
					RowLayout rowLayout = new RowLayout(style);
					rowLayout.pack = true;
					rowLayout.spacing = 5;
					((Composite) child).setLayout(rowLayout);
				}
			}
		} else {
			GridLayout mainLayout = new GridLayout();
			cMainComposite.setLayout(mainLayout);

			for (Control child : children) {
				child.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
				if (child instanceof Composite) {
					RowLayout rowLayout = new RowLayout(style);
					rowLayout.pack = true;
					rowLayout.spacing = 5;
					((Composite) child).setLayout(rowLayout);
				}
			}
		}
		cMainComposite.setLayoutDeferred(false);
		try {
			cMainComposite.requestLayout();
		}catch( Throwable e ) {
			// old swt no support
		}
		Composite c = cMainComposite;
		while (c != null) {
			if (c instanceof ScrolledComposite) {
				Rectangle r = c.getClientArea();
				Point size = ((ScrolledComposite) c).getContent().computeSize(r.width,
						SWT.DEFAULT);
				((ScrolledComposite) c).setMinSize(size);
			}
			c = c.getParent();
		}
	}

	public int getLayoutStyle() {
		return layoutStyle;
	}

	public static final int UPDATETAG_REQUIRES_REBUILD = 1;
	public static final int UPDATETAG_SUCCESS = 0;
	public static final int UPDATETAG_NOCHANGE = -1;
	public int updateTag(Tag tag, List<Taggable> taggables) {
		if (cMainComposite == null || cMainComposite.isDisposed()) {
			return UPDATETAG_NOCHANGE;
		}
		
		for (TagCanvas tagWidget : tagWidgets) {
			TagPainter painter = tagWidget.getTagPainter();
			if (tag.equals(painter.getTag())) {
				Composite parent = tagWidget.getParent();
				String oldGroup = (parent instanceof Group) ? ((Group) parent).getText()
						: "";
				String newGroup = painter.getTag().getGroup();
				if (newGroup == null) {
					newGroup = "";
				}
				if (!oldGroup.equals(newGroup)) {
					return UPDATETAG_REQUIRES_REBUILD;
				}
				painter.updateState(taggables);
				return UPDATETAG_SUCCESS;
			}
		}

		return UPDATETAG_SUCCESS;
	}
}
