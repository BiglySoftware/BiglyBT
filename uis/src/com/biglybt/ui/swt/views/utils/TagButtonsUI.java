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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.MenuBuildUtils.MenuBuilder;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.Taggable;
import com.biglybt.ui.swt.utils.ColorCache;

/**
 * @author TuxPaper
 * @created Nov 17, 2015
 *
 */
public class TagButtonsUI
implements PaintListener
{

	private ArrayList<Button> buttons;
	private Composite cMainComposite;


	@Override
	public void paintControl(PaintEvent e) {
		Button button;
		Composite c = null;
		if (e.widget instanceof Composite) {
			c = (Composite) e.widget;
			button = (Button) c.getChildren()[0];
		} else {
			button = (Button) e.widget;
		}
		Tag tag = (Tag) button.getData("Tag");
		if (tag == null) {
			return;
		}

		//ImageLoader.getInstance().getImage(? "check_yes" : "check_no");

		if (c != null) {
			boolean checked = button.getSelection();
			Point size = c.getSize();
			Point sizeButton = button.getSize();
			e.gc.setAntialias(SWT.ON);
			e.gc.setForeground(ColorCache.getColor(e.display, tag.getColor()));
			int lineWidth = button.getSelection() ? 2 : 1;
			e.gc.setLineWidth(lineWidth);

			int curve = 20;
			int width = sizeButton.x + lineWidth + 1;
			width += Constants.isOSX ? 5 : curve / 2;
			if (checked) {
				e.gc.setAlpha(0x20);
				e.gc.setBackground(ColorCache.getColor(e.display, tag.getColor()));
				e.gc.fillRoundRectangle(-curve, lineWidth - 1, width + curve, size.y - lineWidth, curve, curve);
				e.gc.setAlpha(0xff);
			}
			if (!checked) {
				e.gc.setAlpha(0x80);
			}
			e.gc.drawRoundRectangle(-curve, lineWidth - 1, width + curve, size.y - lineWidth, curve, curve);
			e.gc.drawLine(lineWidth - 1, lineWidth, lineWidth - 1, size.y - lineWidth);
		} else {
			if (!Constants.isOSX && button.getSelection()) {
				Point size = button.getSize();
				e.gc.setBackground(ColorCache.getColor(e.display, tag.getColor()));
				e.gc.setAlpha(20);
				e.gc.fillRectangle(0, 0, size.x, size.y);
			}
		}
	}


	public void buildTagGroup(List<Tag> tags, Composite cMainComposite, final TagButtonTrigger trigger) {

		this.cMainComposite = cMainComposite;

		cMainComposite.setLayout(new GridLayout(1, false));

		buttons = new ArrayList<>();

		SelectionListener selectionListener = new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				Button button = (Button) e.widget;
				Tag tag = (Tag) button.getData("Tag");
				if (button.getGrayed()) {
					button.setGrayed(false);
					button.setSelection(!button.getSelection());
					button.getParent().redraw();
				}
				boolean doTag = button.getSelection();
				trigger.tagButtonTriggered(tag, doTag);

				button.getParent().redraw();
				button.getParent().update();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		};

		Listener menuDetectListener = new Listener() {
			@Override
			public void handleEvent(Event event) {

				final Button button = (Button) event.widget;
				Menu menu = new Menu(button);
				button.setMenu(menu);

				MenuBuildUtils.addMaintenanceListenerForMenu(menu, new MenuBuilder() {
					@Override
					public void buildMenu(final Menu menu, MenuEvent menuEvent) {
						Tag tag = (Tag) button.getData("Tag");
						TagUIUtils.createSideBarMenuItems(menu, tag);
					}
				});
			}
		};


		tags = TagUIUtils.sortTags(tags);
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
				Utils.setLayout(g, rowLayout);
			}

			Composite p = new Composite(g, SWT.DOUBLE_BUFFERED);
			GridLayout layout = new GridLayout(1, false);
			layout.marginHeight = 3;
			if (Constants.isWindows) {
				layout.marginWidth = 6;
				layout.marginLeft = 2;
				layout.marginTop = 1;
			} else {
				layout.marginWidth = 0;
				layout.marginLeft = 3;
				layout.marginRight = 11;
			}
			p.setLayout(layout);
			p.addPaintListener(this);

			Button button = new Button(p, SWT.CHECK);
			buttons.add(button);
			boolean[] auto = tag.isTagAuto();

			if ( auto[0] && auto[1] ){
				button.setEnabled( false );
			}else{
				button.addSelectionListener(selectionListener);
			}
			button.setData("Tag", tag);

			button.addListener(SWT.MenuDetect, menuDetectListener);
			button.addPaintListener(this);
		}
	}


	public boolean updateFields(List<Taggable> taggables) {
		List<Control> layoutChanges = new ArrayList<>();
		for (Button button : buttons) {

			Tag tag = (Tag) button.getData("Tag");
			if (tag == null) {
				continue;
			}
			String name = tag.getTagName(true);
			if (!button.getText().equals(name)) {
				button.setText(name);
				layoutChanges.add(button);
			}

			updateButtonState(tag, button, taggables);

			button.getParent().redraw();
		}

		if (layoutChanges.size() > 0) {
			cMainComposite.layout(layoutChanges.toArray(new Control[0]));
			return true;
		}
		return false;
	}


	private void
	updateButtonState(
		Tag			tag,
		Button		button,
		List<Taggable> taggables )
	{
		if (taggables == null) {
			button.setSelection(false);
			button.setEnabled(false);
			button.getParent().redraw();
			return;
		}

		boolean hasTag = false;
		boolean hasNoTag = false;

		for (Taggable taggable : taggables) {
			boolean curHasTag = tag.hasTaggable(taggable);
			if (!hasTag && curHasTag) {
				hasTag = true;
				if (hasNoTag) {
					break;
				}
			} else if (!hasNoTag && !curHasTag) {
				hasNoTag = true;
				if (hasTag) {
					break;
				}
			}
		}

		boolean[] auto = tag.isTagAuto();

		boolean	auto_add 	= auto[0];
		boolean auto_rem	= auto[1];

		if (hasTag && hasNoTag) {
			button.setEnabled( !auto_add );

			button.setGrayed(true);
			button.setSelection(true);
		} else {

			if ( auto_add && auto_rem ){

				button.setEnabled( false );
			}else{
				button.setEnabled(
					( hasTag ) ||
					( !hasTag && !auto_add ));
			}

			button.setGrayed(false);
			button.setSelection(hasTag);
		}
	}


	public static interface TagButtonTrigger {
		public void tagButtonTriggered(Tag tag, boolean doTag);
	}

}
