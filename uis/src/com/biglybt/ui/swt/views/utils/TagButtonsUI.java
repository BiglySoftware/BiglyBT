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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.widgets.TagCanvas;
import com.biglybt.ui.swt.widgets.TagCanvas.TagButtonTrigger;

/**
 * @author TuxPaper
 * @created Nov 17, 2015
 *
 */
public class TagButtonsUI
{
	private final List<TagCanvas> tagWidgets = new ArrayList<>();
	private Composite cMainComposite;
	private boolean enableWhenNoTaggables;

	private boolean disableAuto = true;
	
	public void buildTagGroup(List<Tag> tags, Composite cMainComposite,
			boolean allowContextMenu, TagButtonTrigger trigger) {

		this.cMainComposite = cMainComposite;

		cMainComposite.setLayout(new GridLayout(1, false));

		Listener menuDetectListener = allowContextMenu ? event -> {
			final TagCanvas tagCanvas = (TagCanvas) event.widget;
			Menu menu = new Menu(tagCanvas);
			tagCanvas.setMenu(menu);

			MenuBuildUtils.addMaintenanceListenerForMenu(menu, (menu1, menuEvent) -> {
				TagUIUtils.createSideBarMenuItems(menu1, tagCanvas.getTag());
			});
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
						: new Group(cMainComposite, SWT.DOUBLE_BUFFERED);
				if (group != null) {
					((Group) g).setText(group);
				}
				g.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
				RowLayout rowLayout = new RowLayout();
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

			tagWidgets.add(p);
		}
	}

	public void setSelectedTags( List<Tag> tags ){
		
		Set<Tag> tag_set = new HashSet<>( tags );
		
		for (TagCanvas widget : tagWidgets) {

			Tag tag = widget.getTag();
			if (tag == null) {
				continue;
			}
			
			boolean select = tag_set.contains( tag );

			widget.setSelected(select);
		}
	}
	
	public List<Tag>
	getSelectedTags()
	{
		List<Tag> result = new ArrayList<>();
		
		if (tagWidgets == null || tagWidgets.isEmpty()) {
			return result;
		}

		for (TagCanvas tagCanvas : tagWidgets) {
			if (tagCanvas.isSelected()){
				result.add(tagCanvas.getTag());
			}
		}

		return( result );
	}
	
	public boolean updateFields(List<Taggable> taggables) {
		if (cMainComposite.isDisposed()){
			return false;
		}
		
		for (TagCanvas tagWidget : tagWidgets) {
			tagWidget.updateState(taggables);
		}

		return false;
	}


	public void
	setDisableAuto(
		boolean	b )
	{
		disableAuto = b;
		Utils.execSWTThread(() -> {
			for (TagCanvas tagWidget : tagWidgets) {
				tagWidget.setDisableAuto(disableAuto);
			}
		});
	}
	
	public void setEnableWhenNoTaggables(boolean enableWhenNoTaggables) {
		this.enableWhenNoTaggables = enableWhenNoTaggables;
		Utils.execSWTThread(() -> {
			for (TagCanvas tagWidget : tagWidgets) {
				tagWidget.setEnableWhenNoTaggables(enableWhenNoTaggables);
			}
		});
	}


}
