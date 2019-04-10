/*
 * Created on 9 juil. 2003
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */
package com.biglybt.ui.swt.config;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ScrollBar;

import com.biglybt.pifimpl.local.ui.config.UITextAreaImpl;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;

import com.biglybt.pif.ui.components.UIComponent;
import com.biglybt.pif.ui.components.UIPropertyChangeEvent;
import com.biglybt.pif.ui.components.UIPropertyChangeListener;

/**
 * Sorta like {@link StringAreaSwtParameter}, except uses StyledText and uses
 * an UITextAreaImpl instead of a ValueProcessor
 */
public class
TextAreaSwtParameter
	extends BaseSwtParameter<TextAreaSwtParameter, String>
	implements	UIPropertyChangeListener
{
	private final UITextAreaImpl	ui_text_area;

	private final StyledText	text_area;

	public TextAreaSwtParameter(
		Composite 			composite,
		UITextAreaImpl 		_ui_text_area)
	{
		super( null );

		setPluginParameter(_ui_text_area);

		ui_text_area = _ui_text_area;

		text_area = new StyledText(composite,SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		setMainControl(text_area);
		
		if (doGridData(composite)) {
			GridData gd = new GridData();
			gd.horizontalSpan = 2;

			Integer hhint = (Integer)ui_text_area.getProperty( UIComponent.PT_HEIGHT_HINT );

			if ( hhint != null ){

				gd.heightHint = hhint;
			} else {
				gd.heightHint = 150;
			}
			gd.widthHint = 10;
			gd.verticalAlignment = SWT.FILL;
			gd.grabExcessVerticalSpace = true;
			gd.grabExcessHorizontalSpace = true;
			gd.horizontalAlignment = SWT.FILL;
			
			text_area.setLayoutData(gd);
		}

		ClipboardCopy.addCopyToClipMenu(text_area,
				() -> text_area.getText().trim());

		text_area.addListener(SWT.KeyDown, event -> {
			int key = event.character;

			if (key <= 26 && key > 0) {

				key += 'a' - 1;
			}

			if (key == 'a' && event.stateMask == SWT.MOD1) {

				event.doit = false;

				text_area.selectAll();
			}
		});

		text_area.setText("" + ui_text_area.getText());

		ui_text_area.addPropertyChangeListener(this);
	}

	@Override
	public String getValue() {
		return null;
	}

	@Override
	public void
	propertyChanged(
		final UIPropertyChangeEvent ev )
	{
		if ( text_area.isDisposed() || !ui_text_area.isVisible()){

			ui_text_area.removePropertyChangeListener( this );

			return;
		}

		Utils.execSWTThreadLater(0, () -> {
					if ( text_area.isDisposed() || !ui_text_area.isVisible()){

						ui_text_area.removePropertyChangeListener( TextAreaSwtParameter.this );

						return;
					}

					String old_value = (String)ev.getOldPropertyValue();
					String new_value = (String) ev.getNewPropertyValue();

					ScrollBar bar = text_area.getVerticalBar();

					if (new_value == null || bar == null) {
						return;
					}

					boolean max = bar.getSelection() == bar.getMaximum() - bar.getThumb();

					int lineOffset = text_area.getLineCount() - text_area.getTopIndex();

					if ( new_value.startsWith( old_value )){

						String toAppend = new_value.substring(old_value.length());

						if ( toAppend.length() == 0 ){

							return;
						}

						StringBuilder builder = new StringBuilder(toAppend.length());

						String[] lines = toAppend.split("\n");


						for( int i=0;i<lines.length;i++){

							String line = lines[i];

							builder.append("\n");
							builder.append(line);
						}

						text_area.append(builder.toString());

					}else{

						StringBuilder builder = new StringBuilder(new_value.length());

						String[] lines = new_value.split("\n");

						for( int i=0;i<lines.length;i++){

							String line = lines[i];

							if (line != lines[0] ){

								builder.append("\n");
							}

							builder.append(line);
						}

						text_area.setText(builder.toString());
					}

					if ( max ){

						bar.setSelection(bar.getMaximum()-bar.getThumb());

						text_area.setTopIndex(text_area.getLineCount()-lineOffset);

						text_area.redraw();
					}

			});
	}
}
