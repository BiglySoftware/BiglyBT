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

package com.biglybt.ui.swt.utils;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.swt.skin.*;
import org.eclipse.swt.SWT;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;

import com.biglybt.core.tag.*;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.StandardButtonsArea;

/**
 * @author TuxPaper
 * @created Feb 26, 2015
 *
 */
public class TagUIUtilsV3
{

	public static void showCreateTagDialog(
			final UIFunctions.TagReturner tagReturner) {
		final SkinnedDialog dialog = new SkinnedDialog("skin3_dlg_addtag", "shell",
				SWT.DIALOG_TRIM);
		SWTSkin skin = dialog.getSkin();

		final SWTSkinObjectTextbox tb = (SWTSkinObjectTextbox) skin.getSkinObject(
				"tag-name");
		final SWTSkinObjectCheckbox cb = (SWTSkinObjectCheckbox) skin.getSkinObject(
				"tag-share");

		final SWTSkinObjectCheckbox ss = (SWTSkinObjectCheckbox) skin.getSkinObject(
				"tag-customize");

		if (tb == null || cb == null){
			return;
		}

		SWTSkinObjectContainer soGroupBox = (SWTSkinObjectContainer) skin.getSkinObject(
				"tag-group-area");

		final SWTSkinObjectCombo soGroup = (SWTSkinObjectCombo) skin.getSkinObject(
				"tag-group");

		if (soGroupBox != null && soGroup != null) {
			List<String> listGroups = new ArrayList<>();
			TagManager tagManager = TagManagerFactory.getTagManager();
			TagType tt = tagManager.getTagType(TagType.TT_DOWNLOAD_MANUAL);
			List<Tag> tags = tt.getTags();
			for (Tag tag : tags) {
				String group = tag.getGroup();
				if (group != null && group.length() > 0  && !listGroups.contains(group)) {
					listGroups.add(group);
				}
			}

			soGroupBox.setVisible(listGroups.size() > 0);
			soGroup.setList(listGroups.toArray(new String[0]));
		}

		cb.setChecked(COConfigurationManager.getBooleanParameter(
				"tag.sharing.default.checked"));

		if ( ss != null ){

			ss.setChecked(COConfigurationManager.getBooleanParameter(
					"tag.add.customize.default.checked"));

			ss.addSelectionListener(
				new SWTSkinCheckboxListener() {

					@Override
					public void checkboxChanged(SWTSkinObjectCheckbox so, boolean checked) {
						COConfigurationManager.setParameter(
								"tag.add.customize.default.checked", checked);
					}
				});
		}

		SWTSkinObject soButtonArea = skin.getSkinObject("bottom-area");
		if (soButtonArea instanceof SWTSkinObjectContainer) {
			StandardButtonsArea buttonsArea = new StandardButtonsArea() {
				// @see StandardButtonsArea#clicked(int)
				@Override
				protected void clicked(int buttonValue) {
					if (buttonValue == SWT.OK) {

						String tag_name = tb.getText().trim();
						TagType tt = TagManagerFactory.getTagManager().getTagType(
								TagType.TT_DOWNLOAD_MANUAL);

						Tag tag = tt.getTag(tag_name, true);

						if (tag == null) {

							try {

								tag = tt.createTag(tag_name, true);

								tag.setPublic(cb.isChecked());

								if (soGroup != null) {
									String group = soGroup.getText();
									if (group != null && group.length() > 0) {
										tag.setGroup(group);
									}
								}

							} catch (TagException e) {

								Debug.out(e);
							}
						}

						// return tag even if it already existed.
						// Case: assigning tag to DL, user enters same tag name because
						// they forgot they already had one
						if (tagReturner != null && tag != null) {
							tagReturner.returnedTags(new Tag[] {
								tag
							});
						}

						if ( ss.isChecked()){
							tag.setTransientProperty( Tag.TP_SETTINGS_REQUESTED, true );

							UIFunctionsManager.getUIFunctions().getMDI().showEntryByID(
									MultipleDocumentInterface.SIDEBAR_SECTION_TAGS);
						}
					}

					dialog.close();
				}
			};
			buttonsArea.setButtonIDs(new String[] {
				MessageText.getString("Button.add"),
				MessageText.getString("Button.cancel")
			});
			buttonsArea.setButtonVals(new Integer[] {
				SWT.OK,
				SWT.CANCEL
			});
			buttonsArea.swt_createButtons(
					((SWTSkinObjectContainer) soButtonArea).getComposite());
		}

		dialog.open();
	}

}
